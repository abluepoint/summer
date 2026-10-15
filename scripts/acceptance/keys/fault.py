"""Narrow T21 fault follow-up; retains only error metadata and secret-match booleans."""
import json,hashlib,time
from key_environment import KeyEnvironment,Client,OUT,immutable_candidate
candidate=immutable_candidate();env=None;result={'stage':'setup','checks':[]}
def check(name,passed,**facts):result['checks'].append({'name':name,'passed':bool(passed),**facts});print(name,'PASS' if passed else 'FAIL',flush=True)
try:
 env=KeyEnvironment();env.setup();env.start(21081,jar=candidate)
 admin=Client().login(21081,env.username,env.password);env.named_secrets['bearer_token']=admin.token
 check('actual_pkce_token_obtained',True)
 env.sql('RENAME TABLE summer_security_keys TO summer_security_keys_fault',env.schema);env.phase='key_table_unavailable';start=len(env.security_events)
 s,d,h=Client().request(21081,'/oauth2/jwks',bearer=False)
 error=d.get('error') if isinstance(d,dict) else None
 result['publicJwkFailure']={'status':s,'bodyType':type(d).__name__,'errorType':type(error).__name__,'errorCode':error.get('code') if isinstance(error,dict) else None,'success':d.get('success') if isinstance(d,dict) else None,'bodyRequestIdPresent':bool(d.get('requestId')) if isinstance(d,dict) else False,'headerRequestIdPresent':bool(h.get('X-Request-Id')),'correlationMatches':isinstance(d,dict) and d.get('requestId')==h.get('X-Request-Id')}
 check('jwk_fault_fails_closed',s>=500)
 check('jwk_failure_not_wrapped_as_success',not(isinstance(d,dict) and d.get('success') is True))
 s,d,h=admin.request(21081,'/api/security/keys');check('bearer_fault_fails_closed',s in [401,403] or s>=500,status=s)
 signing_failed=False
 try:Client().login(21081,env.username,env.password)
 except Exception:signing_failed=True
 check('actual_signing_fault_fails_closed',signing_failed)
 time.sleep(1)
 check('raw_bearer_not_written_to_security_audit',not any('bearer_token' in f['secretKinds'] for f in env.sensitive_findings))
 events=[e['event'] for e in env.security_events[start:]];check('key_failure_has_specific_audit','key_read_failure' in events,events=sorted(set(events)))
 check('signing_failure_has_specific_audit','key_sign_failure' in events)
 env.sql('RENAME TABLE summer_security_keys_fault TO summer_security_keys',env.schema);env.phase='recovered'
 Client().login(21081,env.username,env.password);check('actual_pkce_recovers_after_fault_removed',True)
 result['stage']='complete'
except Exception as error:result['failureType']=type(error).__name__
finally:
 if env:
  try:env.close();result['cleanup']='VERIFIED'
  except Exception as error:result['cleanup']='FAILED';result['cleanupFailureType']=type(error).__name__
 result['candidateSha256']=hashlib.sha256(candidate.read_bytes()).hexdigest();result['sensitiveFindings']=env.sensitive_findings if env else []
 result['counts']={'checks':len(result['checks']),'passed':sum(c['passed'] for c in result['checks']),'failed':sum(not c['passed'] for c in result['checks'])}
 result['passed']=result['stage']=='complete' and not result['counts']['failed'] and result.get('cleanup')=='VERIFIED'
 (OUT/'fault-result.json').write_text(json.dumps(result,indent=2));print(json.dumps({'counts':result['counts'],'cleanup':result.get('cleanup')}),flush=True)
raise SystemExit(0 if result['passed'] else 1)
