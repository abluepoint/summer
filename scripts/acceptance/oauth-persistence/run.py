"""T24 real MySQL/PKCE/dual-JVM/Chrome acceptance; never archives credentials."""
import concurrent.futures, hashlib, json, os, subprocess, threading, time, uuid
from pathlib import Path
from oauth_environment import OAuthEnvironment, Client, ROOT, OUT, immutable_candidate
from protocol import OAuthClient, ORIGIN, SCOPES, claims
from grants import bootstrap, create_role, unwrap

SCRIPT=Path(__file__).resolve().parent;NODE='/opt/homebrew/opt/node@22/bin/node'
result={'stage':'setup','checks':[],'networkMocking':False,'consentPolicyFixture':True};env=None;ui=None;browser=None
def check(name,value,**facts):
    result['checks'].append({'name':name,'passed':bool(value),**facts});print(name,'PASS' if value else 'FAIL',flush=True)
    (OUT/'progress.json').write_text(json.dumps(result,indent=2))
def code(d):return d['error']['code'] if isinstance(d,dict) and isinstance(d.get('error'),dict) else None
def failure_contract(d,h):return bool(code(d)) and code(d).startswith('SUMMER_') and d.get('success') is False and bool(d.get('requestId')) and d['requestId']==h.get('X-Request-Id')
def audit(rid,event):
    time.sleep(.2)
    return any(e['event']==event and e.get('requestId')==rid and e.get('operatorMatchesAdmin') for e in env.security_events)
def request(client,path,method='GET',body=None,port=21081):
    rid='t24-'+uuid.uuid4().hex;s,d,h=client.request(port,path,method,body,headers={'X-Request-Id':rid});return s,d,h,rid
def clients(client,port=21081):
    s,d,h,_=request(client,'/api/security/oauth-clients',port=port);assert s==200,'client_list_status_'+str(s)
    return unwrap(d)['items']
def find(client,cid,port=21081):return next(c for c in clients(client,port) if c['clientId']==cid)
def create(client,cid,scope=None):
    return request(client,'/api/security/oauth-clients','POST',{'clientId':cid,'clientName':cid,'publicClient':True,'redirectUris':[ORIGIN+'/oauth2/callback'],'scopes':scope or ['openid','profile']})
def update(client,cid,fields,port=21081):return request(client,'/api/security/oauth-clients/'+cid,'PATCH',fields,port=port)
def snapshot():
    paths=list((ROOT/'summer-ui/src').rglob('*'))+list(SCRIPT.glob('*.py'))+list(SCRIPT.glob('*.mjs'))
    paths += [ROOT/'summer-sample/src/main/java/com/abluepoint/summer/sample/authorization'/n for n in ['OAuthClientManagementController.java','JdbcSummerOAuthClientStore.java','JdbcOAuthStorageConfiguration.java','JdbcSummerAuthorizationStore.java','JdbcSummerConsentStore.java']]
    return {str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in paths if p.is_file()}
def browser_run(mode):
    global browser
    e=dict(os.environ,SUMMER_E2E_USERNAME=env.username,SUMMER_E2E_PASSWORD=env.password,SUMMER_E2E_READER=env.denied_username,SUMMER_E2E_READER_PASSWORD=env.denied_password,SUMMER_OAUTH_MODE=mode)
    browser=subprocess.Popen([NODE,str(SCRIPT/'browser.mjs')],cwd=ROOT/'summer-ui',env=e,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    status=browser.wait(timeout=240);receipt=json.loads((OUT/f'browser-{mode}.json').read_text());result.setdefault('browsers',[]).append({'exitCode':status,**receipt})

candidate=immutable_candidate();before=snapshot()
try:
    env=OAuthEnvironment();env.extra.update({'summer.security.storage.oauth-client':'jdbc','summer.security.storage.authorization':'jdbc','summer.security.storage.consent':'jdbc'})
    env.setup();env.start(21081,jar=candidate)
    admin=Client().login(21081,env.username,env.password);bootstrap(admin,env.username);admin=Client().login(21081,env.username,env.password)
    capabilities=['security.oauth-client.read','security.oauth-client.write','menu.oauthClients.read','GET:/api/security/oauth-clients','POST:/api/security/oauth-clients','PATCH:/api/security/oauth-clients/{clientId}','GET:/api/security/oauth-consents','DELETE:/api/security/oauth-consents/{clientId}/{principalName}']
    role=create_role(admin,'ent-acme',capabilities,'T24 OAuth management')
    s,d,h,_=request(admin,f'/api/v1/enterprises/ent-acme/users/{env.username}/roles');assert s==200
    s,d,h=admin.request(21081,f'/api/v1/enterprises/ent-acme/users/{env.username}/roles','PUT',{'roleIds':unwrap(d)['roleIds']+[role],'deniedCapabilityCodes':[]},headers={'If-Match':h['ETag'],'Idempotency-Key':uuid.uuid4().hex});assert s==200
    admin=Client().login(21081,env.username,env.password)
    s,d,h,_=request(admin,'/api/users','POST',{'username':env.denied_username,'password':env.denied_password,'role':'USER'});assert s==200
    reader=Client().login(21081,env.denied_username,env.denied_password)
    env.start(21082,jar=candidate)
    check('mysql_oauth_migration_applied',env.sql("SELECT COUNT(*) FROM flyway_schema_history WHERE script='V24__oauth2_persistent_storage.sql' AND success=1",env.schema)=='1')
    result['stage']='management';env.phase='management';seed=find(admin,'summer-ui')
    check('summer_ui_public_client_metadata',seed['authenticationMethods']==['none'] and seed['grantTypes']==['authorization_code'] and set(seed['scopes'])==set(SCOPES.split()))
    check('management_projection_no_secrets',all(not(set(c)&{'clientSecret','secret','accessToken','idToken','refreshToken','tokenSettings','clientSettings'}) for c in clients(admin)))
    for path,method,body in [('/api/security/oauth-clients','GET',None),('/api/security/oauth-consents','GET',None),('/api/security/oauth-clients','POST',{'clientId':'denied','clientName':'Denied','publicClient':True,'redirectUris':['https://example.test/callback'],'scopes':['openid']})]:
        s,d,h,_=request(reader,path,method,body);check('ungranted_'+method+'_'+path.rsplit('/',1)[1],s==403 and failure_contract(d,h),httpStatus=s)
    cid='t24-client';s,d,h,rid=create(admin,cid)
    check('client_registration_real_http',s==201,httpStatus=s);check('client_registration_audit',audit(rid,'oauth_client_created'))
    s,d,h,rid=create(admin,cid);check('duplicate_client_conflict',s==409 and code(d)=='SUMMER_OAUTH_CLIENT_EXISTS' and failure_contract(d,h),httpStatus=s)
    bad_cases=[{'publicClient':False},{'redirectUris':['http://example.test/callback']},{'redirectUris':['https://example.test/callback#fragment']},{'redirectUris':[None]},{'scopes':[]},{'scopes':['bad scope']},{'clientName':''}]
    for i,change in enumerate(bad_cases):
        body={'clientId':'t24-invalid-'+str(i),'clientName':'Invalid case','publicClient':True,'redirectUris':['https://example.test/callback'],'scopes':['openid'],**change}
        s,d,h,rid=request(admin,'/api/security/oauth-clients','POST',body)
        check('invalid_client_case_'+str(i),s==400 and code(d)=='SUMMER_OAUTH_CLIENT_INVALID' and failure_contract(d,h),httpStatus=s)
    check('invalid_client_failure_audit',audit(rid,'oauth_management_failed'))
    current=find(admin,cid);s,d,h,rid=update(admin,cid,{'version':current['version'],'clientName':'Changed client','redirectUris':[ORIGIN+'/oauth2/alternate'],'scopes':['openid','profile','t24.read']})
    check('client_edit_real_http_and_version',s==200 and unwrap(d)['version']==current['version']+1);check('client_edit_audit',audit(rid,'oauth_client_updated'))
    other=find(admin,cid,21082);check('client_edit_visible_on_other_node',other['clientName']=='Changed client' and other['redirectUris']==[ORIGIN+'/oauth2/alternate'] and 't24.read' in other['scopes'])
    s,d,h,_=update(admin,cid,{'version':current['version'],'clientName':'Stale'});check('stale_client_version_conflict',s==412 and failure_contract(d,h),httpStatus=s)
    barrier=threading.Barrier(2);version=other['version']
    def compete(port):
        c=Client();c.token=admin.token;barrier.wait(timeout=5);return update(c,cid,{'version':version,'clientName':'Node '+str(port)},port)
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:answers=list(pool.map(compete,[21081,21082]))
    check('dual_node_client_edit_single_winner',sorted(x[0] for x in answers)==[200,412],httpStatuses=sorted(x[0] for x in answers))
    result['stage']='protocol';env.phase='protocol';protocol=OAuthClient(env).sign_in(21081,env.username,env.password)
    authorization=protocol.authorize(21081);check('real_s256_authorization_code_and_state',authorization['status']==302 and bool(authorization['code']) and authorization['stateMatches'])
    s,tokens,h=protocol.exchange(21082,authorization)
    check('oauth_token_response_raw',s==200 and isinstance(tokens,dict) and 'access_token' in tokens and 'data' not in tokens and 'success' not in tokens,httpStatus=s)
    check('public_client_no_refresh_token','refresh_token' not in tokens)
    access=claims(tokens['access_token']);identity=claims(tokens['id_token'])
    check('issuer_scope_and_id_token_nonce',access.get('iss')==ORIGIN and identity.get('iss')==ORIGIN and identity.get('nonce')==authorization['nonce'] and set(tokens.get('scope','').split())==set(SCOPES.split()))
    check('access_and_id_token_fifteen_minute_ttl',access['exp']-access['iat']==900 and identity['exp']-identity['iat']==900)
    lifetime=env.sql("SELECT DISTINCT TIMESTAMPDIFF(SECOND,authorization_code_issued_at,authorization_code_expires_at) FROM oauth2_authorization WHERE registered_client_id=(SELECT id FROM oauth2_registered_client WHERE client_id='summer-ui') AND authorization_code_value IS NOT NULL",env.schema)
    check('summer_ui_code_five_minute_ttl',lifetime.splitlines()==['300'])
    s,d,h=protocol.exchange(21081,authorization);check('authorization_code_single_use',s==400 and d.get('error')=='invalid_grant' and 'success' not in d,httpStatus=s)
    wrong=protocol.authorize(21081);s,d,h=protocol.exchange(21082,wrong,verifier='invalid-verifier-value');check('wrong_pkce_verifier_rejected',s==400 and d.get('error')=='invalid_grant',httpStatus=s)
    for name,kwargs in [('missing_pkce',{'pkce':None}),('plain_pkce',{'pkce':'plain'}),('unknown_scope',{'scope':'openid unknown.scope'}),('wrong_redirect',{'redirect':'https://unregistered.example.test/callback'})]:
        response=protocol.authorize(21081,**kwargs);check(name+'_rejected',not response['code'] and (response['status']>=400 or bool(response['error'])),httpStatus=response['status'],oauthError=response['error'])
    for name,kwargs,wanted in [('new_redirect_scope',{'redirect':ORIGIN+'/oauth2/alternate','scope':'openid t24.read'},True),('old_redirect',{'redirect':ORIGIN+'/oauth2/callback','scope':'openid'},False),('unregistered_scope',{'redirect':ORIGIN+'/oauth2/alternate','scope':'openid customer.write'},False)]:
        response=protocol.authorize(21081,client_id=cid,**kwargs);check(name+'_protocol_effect',bool(response['code'])==wanted,httpStatus=response['status'],oauthError=response['error'])
    current=find(admin,cid);s,d,h,rid=update(admin,cid,{'version':current['version'],'status':'DISABLED'});check('disable_audit_and_status',s==200 and audit(rid,'oauth_client_updated'))
    denied=protocol.authorize(21082,client_id=cid,redirect=ORIGIN+'/oauth2/alternate',scope='openid');check('disabled_client_protocol_denied',not denied['code'],httpStatus=denied['status'])
    # Configure consent requirement only on this dedicated fixture client; records must come from the actual protocol.
    consent_cid='t24-consent';s,d,h,_=create(admin,consent_cid);assert s==201
    env.sql("UPDATE oauth2_registered_client SET client_settings=JSON_SET(client_settings, '$.\"settings.client.require-authorization-consent\"', JSON_EXTRACT('true','$')) WHERE client_id='t24-consent'",env.schema)
    consent=protocol.authorize(21081,client_id=consent_cid,scope='openid profile')
    check('real_consent_prompt_and_authorization',consent['consentShown'] and bool(consent['code']),httpStatus=consent['status'])
    s,d,h,_=request(admin,'/api/security/oauth-consents');consents=unwrap(d)['items'] if s==200 else []
    check('consent_generated_by_real_protocol_visible_in_management',s==200 and any(c['principalName']==env.username for c in consents),recordCount=len(consents))
    # Pending authorization and changed/disabled client survive both JVMs restarting.
    pending=protocol.authorize(21081);before_restart=clients(admin);consent_count=len(consents)
    result['stage']='restart';env.phase='restart';env.stop(21081);env.stop(21082);env.start(21081,jar=candidate);env.start(21082,jar=candidate)
    check('client_configuration_persists_across_both_jvm_restarts',clients(admin)==before_restart)
    s,d,h=protocol.exchange(21082,pending);check('authorization_code_exchange_survives_restart',s==200 and 'access_token' in d,httpStatus=s)
    s,d,h,_=request(admin,'/api/security/oauth-consents');check('consent_survives_restart',s==200 and len(unwrap(d)['items'])==consent_count and consent_count>0)
    current=find(admin,cid);check('disabled_client_not_reseeded_after_restart',current['status']=='DISABLED')
    s,d,h,rid=update(admin,cid,{'version':current['version'],'status':'ACTIVE'});assert s==200
    protocol=OAuthClient(env).sign_in(21081,env.username,env.password);auth=protocol.authorize(21082,client_id=cid,redirect=ORIGIN+'/oauth2/alternate',scope='openid t24.read');check('reenabled_client_protocol_restored',bool(auth['code']))
    expiring=protocol.authorize(21081)
    env.sql("UPDATE oauth2_authorization SET authorization_code_issued_at=UTC_TIMESTAMP(6)-INTERVAL 6 MINUTE, authorization_code_expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 MINUTE WHERE authorization_code_value='"+expiring['code']+"'",env.schema)
    s,d,h=protocol.exchange(21082,expiring);check('expired_code_rejected_time_fixture',s==400 and d.get('error')=='invalid_grant',httpStatus=s,fixture='own_authorization_expiry_timestamp')
    result['stage']='browser';env.phase='browser'
    ui=subprocess.Popen([NODE,str(SCRIPT.parent/'identity/ui-server.mjs')],cwd=ROOT/'summer-ui',env=os.environ,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True);assert ui.stdout.readline().strip()=='UI_READY'
    browser_run('normal')
    env.sql("CREATE TRIGGER t24_fail_update BEFORE UPDATE ON oauth2_registered_client FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='T24 controlled update failure'",env.schema)
    browser_run('fault');env.sql('DROP TRIGGER t24_fail_update',env.schema)
    check('no_sensitive_runtime_output',not env.sensitive_log_detected);result['stage']='complete'
except Exception as error:
    import traceback
    result['failureType']=type(error).__name__;result['failureFrames']=[{'file':Path(f.filename).name,'line':f.lineno} for f in traceback.extract_tb(error.__traceback__)]
finally:
    for p in (browser,ui):
        if p and p.poll() is None:
            p.terminate()
            try:p.wait(timeout=15)
            except subprocess.TimeoutExpired:p.kill();p.wait(timeout=5)
    if env:
        try:env.close();result['cleanup']='VERIFIED'
        except Exception as error:result['cleanup']='FAILED';result['cleanupFailureType']=type(error).__name__
    after=snapshot();result['inputCount']=len(before);result['inputsStable']=before==after;result['changedPaths']=[p for p in sorted(set(before)|set(after)) if before.get(p)!=after.get(p)]
    (OUT/'source-before.json').write_text(json.dumps(before,indent=2));(OUT/'source-after.json').write_text(json.dumps(after,indent=2))
    result['candidateSha256']=hashlib.sha256(candidate.read_bytes()).hexdigest();result['audit']=env.security_events if env else [];result['sensitiveFindings']=env.sensitive_findings if env else [];result['diagnostics']=env.diagnostics if env else []
    result['counts']={'tests':len(result['checks']),'failures':sum(not c['passed'] for c in result['checks']),'errors':int(result['stage']!='complete'),'skipped':0}
    result['passed']=result['stage']=='complete' and not result['counts']['failures'] and result.get('cleanup')=='VERIFIED' and all(b.get('passed') for b in result.get('browsers',[]))
    (OUT/'result.json').write_text(json.dumps(result,indent=2));print(json.dumps({'stage':result['stage'],'counts':result['counts'],'cleanup':result.get('cleanup'),'passed':result['passed']}),flush=True)
raise SystemExit(0 if result['passed'] else 1)
