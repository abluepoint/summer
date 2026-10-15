"""T21 real MySQL, PKCE, two JVMs and Chrome. Secrets stay in memory/stdin/env."""
import json,os,sys,hashlib,subprocess,uuid,time,base64
from pathlib import Path
from key_environment import KeyEnvironment,Client,ROOT,OUT,immutable_candidate
from grants import bootstrap,create_role,unwrap
SCRIPT=Path(__file__).resolve().parent
NODE=os.environ.get('SUMMER_IDENTITY_NODE','/opt/homebrew/opt/node@22/bin/node')
result={'stage':'setup','checks':[],'networkMocking':False,'businessAuthorizationSqlWrites':False}
env=None;ui=None;browser=None;renamed=False
def check(name,passed,**facts):
 result['checks'].append({'name':name,'passed':bool(passed),**facts});print(name,'PASS' if passed else 'FAIL',flush=True)
def jwt_part(token,index):
 s=token.split('.')[index];return json.loads(base64.urlsafe_b64decode(s+'='*((4-len(s)%4)%4)))
def crypto(**data):
 r=subprocess.run([NODE,str(SCRIPT/'crypto.mjs')],input=json.dumps(data),text=True,capture_output=True,check=True)
 return json.loads(r.stdout)
def failure_contract(data,headers):
 error=data.get('error') if isinstance(data,dict) else None
 return isinstance(error,dict) and str(error.get('code','')).startswith('SUMMER_') and bool(data.get('requestId')) and data.get('requestId')==headers.get('X-Request-Id')
def ui_inputs():
 return {str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((ROOT/'summer-ui/src').rglob('*')) if p.is_file()}
candidate=immutable_candidate();before_ui=ui_inputs()
try:
 env=KeyEnvironment();env.setup();env.start(21081,jar=candidate)
 admin=Client().login(21081,env.username,env.password);check('real_bootstrap_pkce',True)
 bootstrap(admin,env.username);admin=Client().login(21081,env.username,env.password)
 # T16 permits a separately configured menu capability; grant it explicitly through the role API.
 key_codes=['GET:/api/security/keys','POST:/api/security/keys:rotate','security.key.read','security.key.rotate','menu.securityKeys.read']
 result['menuCapability']='menu.securityKeys.read'
 role=create_role(admin,'ent-acme',key_codes,'T21 keys management')
 s,d,h=admin.request(21081,f'/api/v1/enterprises/ent-acme/users/{env.username}/roles');binding=unwrap(d);assert s==200
 s,d,h=admin.request(21081,f'/api/v1/enterprises/ent-acme/users/{env.username}/roles','PUT',{'roleIds':binding['roleIds']+[role],'deniedCapabilityCodes':[]},headers={'If-Match':h.get('ETag'),'Idempotency-Key':uuid.uuid4().hex});assert s==200
 admin=Client().login(21081,env.username,env.password);claims=jwt_part(admin.token,1)
 codes=claims.get('enterprise_capabilities',{}).get('ent-acme',[])
 check('real_role_api_grants_in_fresh_pkce_claims',all(c in codes for c in ['GET:/api/security/keys','POST:/api/security/keys:rotate','security.key.read','security.key.rotate']))
 result['oauthScopes']=claims.get('scope');result['stage']='key_api'
 s,d,h=admin.request(21081,'/api/security/keys');error=(d or {}).get('error') or {};check('authorized_key_metadata_read',s==200,status=s,errorCode=error.get('code') if isinstance(error,dict) else error)
 if s==200:
  items=unwrap(d)['items'];check('metadata_contains_no_private_material',all(set(i)<=set(['kid','algorithm','source','status','version','health','graceUntil']) for i in items))
  version=max(i['version'] for i in items)
  s,d,h=admin.request(21081,'/api/security/keys:rotate','POST',{'retentionDays':0},headers={'If-Match':f'"{version}"','Idempotency-Key':uuid.uuid4().hex});check('invalid_generate_input_rejected',s==400,status=s);check('invalid_error_contract',failure_contract(d,h))
 else:result['blockedManagementChecks']=['metadata shape','authorized generation','invalid input reaches controller','management success Chrome']
 s,d,h=Client().request(21081,'/api/security/keys');check('anonymous_key_management_denied',s in [401,403],status=s)
 s,d,h=admin.request(21081,'/api/users','POST',{'username':env.denied_username,'password':env.denied_password,'role':'USER'});assert s==200
 reader=Client().login(21081,env.denied_username,env.denied_password)
 s,d,h=reader.request(21081,'/api/security/keys');check('ungranted_key_management_denied',s==403,status=s);check('denied_error_contract',failure_contract(d,h))
 s,jwks,h=Client().request(21081,'/oauth2/jwks',bearer=False);check('public_jwks_available',s==200,status=s)
 check('jwks_contains_only_public_material',all(not(set(k)&set(['d','p','q','dp','dq','qi','oth','k'])) for k in jwks['keys']))
 kid=jwt_part(admin.token,0)['kid'];check('actual_pkce_token_verifies_with_jwk',crypto(token=admin.token,jwks=jwks).get('signatureValid'))
 row=env.sql("SELECT kid,algorithm,ciphertext,version,status FROM summer_security_keys WHERE status='ACTIVE'",env.schema).split('\t');assert len(row)==5
 check('mysql_single_active_key_matches_signing_kid',row[0]==kid and row[1]=='RS256' and env.sql("SELECT COUNT(*) FROM summer_security_keys WHERE status='ACTIVE'",env.schema)=='1')
 proof=crypto(ciphertext=row[2],master=env.master,jwks=jwks)
 check('mysql_ciphertext_decrypts_to_matching_private_key_in_memory',proof.get('encryptedPrivateKeyMatchesPublicJwk'))
 check('mysql_ciphertext_is_not_plain_key_or_master',proof.get('ciphertextDiffersFromPlaintext'))
 columns=env.sql("SELECT column_name FROM information_schema.columns WHERE table_schema='"+env.schema+"' AND table_name='summer_security_keys' ORDER BY ordinal_position").splitlines()
 check('mysql_key_schema_has_no_master_or_plain_private_column',not any('master' in c.lower() or 'private' in c.lower() for c in columns),columns=columns)
 migration=env.sql("SELECT COUNT(*) FROM flyway_schema_history WHERE script='V5__security_keys.sql' AND success=1",env.schema);check('real_mysql_key_migration_applied',migration=='1')
 env.start(21082,jar=candidate);other=Client().login(21082,env.username,env.password)
 check('second_node_issues_same_kid',jwt_part(other.token,0)['kid']==kid);check('second_node_token_verifies',crypto(token=other.token,jwks=jwks).get('signatureValid'))
 env.stop(21081);env.start(21081,jar=candidate);admin=Client().login(21081,env.username,env.password)
 check('restart_issues_same_effective_kid',jwt_part(admin.token,0)['kid']==kid);check('restart_token_verifies',crypto(token=admin.token,jwks=jwks).get('signatureValid'))
 check('restart_does_not_regenerate_database_key',env.sql("SELECT kid FROM summer_security_keys WHERE status='ACTIVE'",env.schema)==kid)
 result['stage']='browser';runenv=dict(os.environ,SUMMER_E2E_USERNAME=env.username,SUMMER_E2E_PASSWORD=env.password,SUMMER_E2E_READER=env.denied_username,SUMMER_E2E_READER_PASSWORD=env.denied_password)
 ui=subprocess.Popen([NODE,str(ROOT/'scripts/acceptance/identity/ui-server.mjs')],cwd=ROOT/'summer-ui',env=runenv,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True);assert ui.stdout.readline().strip()=='UI_READY'
 browser=subprocess.Popen([NODE,str(SCRIPT/'browser.mjs')],cwd=ROOT/'summer-ui',env=runenv,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL);result['browserExit']=browser.wait(timeout=150)
 br=json.loads((OUT/'browser-result.json').read_text());result['browser']=br
 if br.get('generated'):
  refreshed=Client().login(21082,env.username,env.password)
  s,new_jwks,_=Client().request(21082,'/oauth2/jwks',bearer=False)
  next_kid=jwt_part(refreshed.token,0)['kid']
  check('ui_generated_key_used_for_real_token_on_other_node',next_kid!=kid)
  check('ui_generated_token_verifies_against_public_jwk',s==200 and crypto(token=refreshed.token,jwks=new_jwks).get('signatureValid'))
  kid=next_kid
 result['stage']='fault'
 # Controlled fault in the run-owned schema only; no roles/grants or business authorization are inserted with SQL.
 env.sql('RENAME TABLE summer_security_keys TO summer_security_keys_fault',env.schema);renamed=True;start=len(env.security_events)
 s,d,h=Client().request(21081,'/oauth2/jwks',bearer=False);check('key_read_fault_fails_closed',s>=500,status=s)
 result['keyReadFailureEnvelope']={'status':s,'frameworkContract':failure_contract(d,h),'oauthError':d.get('error') if isinstance(d,dict) and isinstance(d.get('error'),str) else None,'hasRequestId':bool(h.get('X-Request-Id')),'parsedBodyType':type(d).__name__,'topLevelKeys':sorted(d.keys()) if isinstance(d,dict) else []}
 s,d,h=admin.request(21081,'/api/security/keys')
 check('management_key_read_fault_fails_closed',s in [401,403] or s>=500,status=s)
 check('management_key_read_failure_unified_error',failure_contract(d,h),status=s,errorCode=d.get('error',{}).get('code') if isinstance(d,dict) and isinstance(d.get('error'),dict) else None)
 try:Client().login(21082,env.username,env.password);check('signing_fault_does_not_issue_token',False)
 except AssertionError as error:check('signing_fault_does_not_issue_token',True,failureStage=str(error) if str(error).startswith(('token_status_','authorize_status_')) else type(error).__name__)
 time.sleep(1);events=[x['event'] for x in env.security_events[start:]]
 check('key_read_or_signing_failure_security_audited',any('key' in x and ('fail' in x or 'error' in x) for x in events),events=sorted(set(events)))
 env.sql('RENAME TABLE summer_security_keys_fault TO summer_security_keys',env.schema);renamed=False
 recovered=Client().login(21081,env.username,env.password);check('key_read_recovery_restores_same_signing_kid',jwt_part(recovered.token,0)['kid']==kid)
 check('no_sensitive_runtime_output',not env.sensitive_log_detected)
 result['stage']='complete'
except Exception as error:
 result['failureType']=type(error).__name__
 if isinstance(error,AssertionError):result['assertion']=str(error)[:100]
finally:
 for process in [browser,ui]:
  if process and process.poll() is None:
   process.terminate()
   try:process.wait(timeout=15)
   except subprocess.TimeoutExpired:process.kill();process.wait(timeout=5)
 if env:
  try:env.close();result['cleanup']='VERIFIED'
  except Exception as error:result['cleanup']='FAILED';result['cleanupFailureType']=type(error).__name__
 after_ui=ui_inputs();result['candidateSha256']=hashlib.sha256(candidate.read_bytes()).hexdigest();result['uiInputsStable']=before_ui==after_ui
 result['changedUiPaths']=[p for p in sorted(set(before_ui)|set(after_ui)) if before_ui.get(p)!=after_ui.get(p)]
 (OUT/'ui-input-before.json').write_text(json.dumps(before_ui,indent=2));(OUT/'ui-input-after.json').write_text(json.dumps(after_ui,indent=2))
 result['diagnostics']=env.diagnostics if env else [];result['securityAuditEvents']=sorted(set(e['event'] for e in env.security_events)) if env else []
 result['counts']={'checks':len(result['checks']),'passed':sum(c['passed'] for c in result['checks']),'failed':sum(not c['passed'] for c in result['checks'])}
 result['passed']=result['stage']=='complete' and result['counts']['failed']==0 and result.get('browser',{}).get('passed') and result.get('cleanup')=='VERIFIED'
 (OUT/'result.json').write_text(json.dumps(result,indent=2));print(json.dumps({'stage':result['stage'],'counts':result['counts'],'cleanup':result.get('cleanup'),'passed':result['passed']}),flush=True)
raise SystemExit(0 if result['passed'] else 1)
