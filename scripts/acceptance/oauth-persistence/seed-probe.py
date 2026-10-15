"""T24 focused supplement: configured seed restart, denied writes, valid expiry fixture."""
import hashlib, json, os, time, urllib.parse, uuid
from pathlib import Path
from oauth_environment import OAuthEnvironment, Client, ROOT, OUT, immutable_candidate
from protocol import OAuthClient, SCOPES
from grants import bootstrap, create_role, unwrap

result={'stage':'setup','checks':[],'networkMocking':False};env=None
def check(name,passed,**facts):
    result['checks'].append({'name':name,'passed':bool(passed),**facts});print(name,'PASS' if passed else 'FAIL',flush=True)
def seed(admin):
    s,d,h=admin.request(21081,'/api/security/oauth-clients');assert s==200
    return next(c for c in unwrap(d)['items'] if c['clientId']=='summer-ui')
def code(d):return d['error']['code'] if isinstance(d,dict) and isinstance(d.get('error'),dict) else None
candidate=immutable_candidate()
try:
    env=OAuthEnvironment();env.extra.update({'summer.security.storage.oauth-client':'jdbc','summer.security.storage.authorization':'jdbc','summer.security.storage.consent':'jdbc'})
    env.setup();env.start(21081,jar=candidate)
    admin=Client().login(21081,env.username,env.password);bootstrap(admin,env.username);admin=Client().login(21081,env.username,env.password)
    role=create_role(admin,'ent-acme',['security.oauth-client.read','security.oauth-client.write','GET:/api/security/oauth-clients','PATCH:/api/security/oauth-clients/{clientId}','GET:/api/security/oauth-consents','DELETE:/api/security/oauth-consents/{clientId}/{principalName}'],'T24 seed verification')
    s,d,h=admin.request(21081,f'/api/v1/enterprises/ent-acme/users/{env.username}/roles');assert s==200
    s,d,h=admin.request(21081,f'/api/v1/enterprises/ent-acme/users/{env.username}/roles','PUT',{'roleIds':unwrap(d)['roleIds']+[role],'deniedCapabilityCodes':[]},headers={'If-Match':h['ETag'],'Idempotency-Key':uuid.uuid4().hex});assert s==200
    admin=Client().login(21081,env.username,env.password)
    s,d,h=admin.request(21081,'/api/users','POST',{'username':env.denied_username,'password':env.denied_password,'role':'USER'});assert s==200
    reader=Client().login(21081,env.denied_username,env.denied_password)
    before=seed(admin)
    for name,body in [('metadata',{'version':before['version'],'clientName':'Unauthorized'}),('disable',{'version':before['version'],'status':'DISABLED'})]:
        s,d,h=reader.request(21081,'/api/security/oauth-clients/summer-ui','PATCH',body)
        check('ungranted_client_'+name+'_denied',s==403 and code(d)=='SUMMER_FORBIDDEN',httpStatus=s)
    check('denied_updates_leave_seed_unchanged',seed(admin)==before)
    s,d,h=reader.request(21081,'/api/security/oauth-consents/not-registered/not-owned','DELETE')
    check('ungranted_consent_revoke_denied_before_lookup',s==403 and code(d)=='SUMMER_FORBIDDEN',httpStatus=s)
    protocol=OAuthClient(env).sign_in(21081,env.username,env.password)
    authorization=protocol.authorize(21081);assert authorization['code']
    affected=env.sql("UPDATE oauth2_authorization SET authorization_code_issued_at=UTC_TIMESTAMP(6)-INTERVAL 6 MINUTE, authorization_code_expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 MINUTE WHERE authorization_code_value='"+authorization['code']+"'; SELECT ROW_COUNT()",env.schema)
    assert affected=='1','expiry_fixture_row_count'
    s,d,h=protocol.exchange(21081,authorization)
    check('expired_code_rejected_time_fixture',s==400 and d.get('error')=='invalid_grant' and 'success' not in d,httpStatus=s,fixture='valid_five_minute_interval_entirely_in_past')
    for grant in ('refresh_token','client_credentials'):
        s,d,h=protocol.request(21081,'/oauth2/token','POST',urllib.parse.urlencode({'grant_type':grant,'client_id':'summer-ui'}),bearer=False)
        check('public_client_'+grant+'_grant_rejected',s>=400 and not (isinstance(d,dict) and d.get('access_token')),httpStatus=s)
    # This is the actual configured startup seed, not an API-only client.
    s,d,h=admin.request(21081,'/api/security/oauth-clients/summer-ui','PATCH',{'version':before['version'],'clientName':'Persisted configured UI','status':'DISABLED'});assert s==200
    changed=seed(admin);check('configured_seed_disabled_and_metadata_changed',changed['status']=='DISABLED' and changed['clientName']=='Persisted configured UI')
    result['stage']='restart';env.stop(21081);env.start(21081,jar=candidate);env.start(21082,jar=candidate)
    check('configured_disabled_seed_not_overwritten_on_restart',seed(admin)==changed)
    s,d,h=admin.request(21082,'/api/security/oauth-clients');other=next(c for c in unwrap(d)['items'] if c['clientId']=='summer-ui') if s==200 else None
    check('configured_disabled_seed_consistent_on_new_node',other==changed)
    blocked=protocol.authorize(21082);check('configured_disabled_seed_protocol_rejected_after_restart',not blocked['code'],httpStatus=blocked['status'])
    s,d,h=admin.request(21081,'/api/security/oauth-clients/summer-ui','PATCH',{'version':changed['version'],'status':'ACTIVE'});assert s==200
    restored=Client().login(21082,env.username,env.password)
    check('configured_seed_reenable_restores_actual_pkce',bool(restored.token) and seed(admin)['clientName']=='Persisted configured UI')
    check('no_sensitive_runtime_output',not env.sensitive_log_detected);result['stage']='complete'
except Exception as error:
    import traceback
    result['failureType']=type(error).__name__;result['failureFrames']=[{'file':Path(f.filename).name,'line':f.lineno} for f in traceback.extract_tb(error.__traceback__)]
finally:
    if env:
        try:env.close();result['cleanup']='VERIFIED'
        except Exception as error:result['cleanup']='FAILED';result['cleanupFailureType']=type(error).__name__
    result['candidateSha256']=hashlib.sha256(candidate.read_bytes()).hexdigest();result['audit']=env.security_events if env else [];result['sensitiveFindings']=env.sensitive_findings if env else []
    result['counts']={'tests':len(result['checks']),'failures':sum(not c['passed'] for c in result['checks']),'errors':int(result['stage']!='complete'),'skipped':0}
    result['passed']=result['stage']=='complete' and not result['counts']['failures'] and result.get('cleanup')=='VERIFIED'
    (OUT/'result.json').write_text(json.dumps(result,indent=2));print(json.dumps({'stage':result['stage'],'counts':result['counts'],'cleanup':result.get('cleanup'),'passed':result['passed']}),flush=True)
raise SystemExit(0 if result['passed'] else 1)
