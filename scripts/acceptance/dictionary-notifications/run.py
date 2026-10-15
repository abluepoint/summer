"""T31 actual Cookie SSE, MySQL and Chrome; all business changes use HTTP."""
import hashlib,json,os,selectors,shutil,subprocess,time,uuid
from pathlib import Path
from environment import Client,Environment,ROOT,OUT,SCRIPT,immutable_candidate
from grants import bootstrap,create_role
from stream import Stream

BASE='/api/v1/enterprises/ent-acme';PATH=BASE+'/dictionaries/customer-segment'
PUBLIC='/public/enterprises/ent-acme/dictionaries/customer-segment'
result={'issue':139,'stage':'setup','checks':[],'networkMocking':False,'syntheticIdentity':False,'businessSqlWrites':False}
recovery_only=os.environ.get('SUMMER_DICTIONARY_NOTIFICATIONS_RECOVERY_ONLY')=='1'
counting=not recovery_only
result['recoveryOnly']=recovery_only
env=ui=browser=None;streams=[]
def persist():
    result['counts']={'tests':len(result['checks']),'failures':sum(not c['passed'] for c in result['checks']),'errors':int('failureType' in result),'skipped':0}
    temporary=OUT/'runtime-result.tmp';temporary.write_text(json.dumps(result,ensure_ascii=False,indent=2));temporary.replace(OUT/'runtime-result.json')
def check(name,condition,**facts):
    if not counting:return
    result['checks'].append({'name':name,'passed':bool(condition),**facts});persist();print(name,'PASS' if condition else 'FAIL',flush=True)
def call(client,path,method='GET',body=None,expected=200,port=23081,headers=None):
    rid='t31-'+uuid.uuid4().hex
    status,data,h=client.request(port,path,method,body,headers={'X-Request-Id':rid,'Idempotency-Key':uuid.uuid4().hex,**(headers or {})})
    assert status==expected,'http_status_'+str(status)+'_expected_'+str(expected)
    assert isinstance(data,dict) and data.get('requestId')==h.get('X-Request-Id')==rid,'http_request_correlation'
    return data.get('data'),h
def login():return Client().login(23081,env.username,env.password)
def publish(version,port=23082):
    data,h=call(admin,PATH+'/publish','POST',{'expectedVersion':version,'idempotencyKey':uuid.uuid4().hex},port=port)
    return {'version':data['version'],'requestId':h['X-Request-Id']}
def ui_inputs():return {str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in (ROOT/'summer-ui/src').rglob('*') if p.is_file()}
def stream(client,last=None):
    s=Stream(client,PATH+'/events',last);streams.append(s);return s
def transport(command):
    ui.stdin.write(command+'\n');ui.stdin.flush()
    watcher=selectors.DefaultSelector();watcher.register(ui.stdout,selectors.EVENT_READ)
    try:
        end=time.monotonic()+10
        while time.monotonic()<end:
            if ui.poll() is not None:raise RuntimeError('proxy_control_process_exited')
            for key,_ in watcher.select(timeout=.25):
                line=key.fileobj.readline()
                if line.startswith('CONTROL '):
                    reply=json.loads(line[8:]);assert reply['command']==command,'proxy_control_reply';return reply
        raise TimeoutError('proxy_control_timeout')
    finally:watcher.close()

try:
    candidate=immutable_candidate();result['candidateSha256']=hashlib.sha256(candidate.read_bytes()).hexdigest()
    before=ui_inputs();archive=OUT/'executed-scripts';archive.mkdir(exist_ok=True)
    for p in SCRIPT.iterdir():
        if p.is_file():shutil.copy2(p,archive/p.name)
    env=Environment();env.setup();env.start(23081,jar=candidate)
    admin=login();boot=bootstrap(admin,env.username);admin=login()
    catalog=call(admin,BASE+'/capabilities/catalog')[0]['capabilities'];known={row['code']:row['capabilityId'] for row in catalog}
    codes=['platform.dictionary.'+a for a in ['read','create','update','publish','disable','rollback','write']]+['menu.dictionary.form.read']
    for code in codes:
        if code not in known:
            known[code]=call(admin,BASE+'/capabilities','POST',{'code':code,'resource':code.rsplit('.',1)[0],'action':code.rsplit('.',1)[1],'riskLevel':'HIGH','scope':'PLATFORM' if code.startswith('platform.') else 'ENTERPRISE'},201)[0]['capabilityId']
    role=create_role(admin,'ent-acme',[known[code] for code in codes],'T31 real notification operator')
    _,h=call(admin,BASE+'/users/'+env.username+'/roles')
    call(admin,BASE+'/users/'+env.username+'/roles','PUT',{'roleIds':[boot,role],'deniedCapabilityCodes':[]},headers={'If-Match':h['ETag']});admin=login()
    call(admin,'/api/users','POST',{'username':env.denied_username,'password':env.denied_password,'role':'USER'})
    call(admin,BASE+'/members','POST',{'subject':env.denied_username,'displayName':'T31 denied member','email':'t31@example.test'},201)
    reader=Client().login(23081,env.denied_username,env.denied_password)
    call(admin,BASE+'/dictionaries','POST',{'code':'customer-segment','name':'T31 实时字典','published':False,'publicReadable':True,'enabled':True})
    for value,label in [('alpha','初始甲'),('bravo','初始乙')]:call(admin,PATH+'/items','POST',{'value':value,'label':label,'sortOrder':0,'enabled':True})
    initial=publish(0,23081);env.start(23082,jar=candidate)
    if not recovery_only:
        result['stage']='protocol'
        subscription=stream(admin);subscription.wait(lambda s:any(e.get('type')=='connected' for e in s.events))
        check('real_cookie_notification_stream_is_raw_and_correlated',subscription.status==200 and subscription.cookie_present and subscription.correlated and subscription.content_type.startswith('text/event-stream'),**subscription.metadata())
        anonymous=stream(Client());check('anonymous_notification_subscription_rejected',anonymous.status in [401,403],**anonymous.metadata())
        denied=stream(reader);check('real_member_without_dictionary_read_cannot_subscribe',denied.status==403 and denied.error_code=='SUMMER_FORBIDDEN' and denied.correlated,**denied.metadata())
        invalid=stream(admin,'invalid');check('invalid_last_event_id_has_stable_correlated_error',invalid.status==400 and invalid.error_code=='SUMMER_DICTIONARY_SSE_LAST_EVENT_ID' and invalid.correlated,**invalid.metadata())
        status,data,h=reader.request(23081,PUBLIC,headers={'X-Request-Id':'t31-'+uuid.uuid4().hex})
        check('public_reread_obeys_current_reader_capability',status==403 and (data.get('error') or {}).get('code')=='SUMMER_FORBIDDEN' and data.get('requestId')==h.get('X-Request-Id'),status=status)
        subscription.wait(lambda s:any(e.get('type')=='DICTIONARY_PUBLISHED' for e in s.events))
        notices=[e for e in subscription.events if e.get('type')=='DICTIONARY_PUBLISHED']
        allowed={'tenantId','dictionaryCode','eventType','version','requestId'}
        check('persisted_notification_contains_only_invalidation_metadata',bool(notices) and all(set(e['data'])==allowed and e['data']['version']==1 and e['data']['requestId']==initial['requestId'] for e in notices))
        check('required_dictionary_migrations_succeeded',env.sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('4','23','27') AND success=1",env.schema)=='3')
    runtime=dict(os.environ,SUMMER_E2E_USERNAME=env.username,SUMMER_E2E_PASSWORD=env.password,DICTIONARY_NOTIFICATIONS_VITE_CACHE=str(OUT/'vite-cache'))
    ui=subprocess.Popen(['/opt/homebrew/opt/node@22/bin/node',str(SCRIPT/'ui-server.mjs')],cwd=ROOT/'summer-ui',env=runtime,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
    import urllib.request
    for _ in range(180):
        if ui.poll() is not None:raise RuntimeError('ui_startup_exit')
        try:
            with urllib.request.urlopen('http://127.0.0.1:23573/login',timeout=1) as response:
                if response.status==200:break
        except Exception:pass
        time.sleep(.25)
    else:raise RuntimeError('ui_startup_timeout')
    result['stage']='browser'
    browser=subprocess.Popen(['/opt/homebrew/opt/node@22/bin/node',str(SCRIPT/'browser.mjs')],cwd=ROOT/'summer-ui',env=runtime,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
    selector=selectors.DefaultSelector();selector.register(browser.stdout,selectors.EVENT_READ);deadline=time.monotonic()+420
    while selector.get_map():
        if time.monotonic()>deadline:raise TimeoutError('browser_deadline')
        for key,_ in selector.select(timeout=.5):
            line=key.fileobj.readline()
            if not line:selector.unregister(key.fileobj);continue
            action=line.strip();reply={}
            if action=='PUBLISH_DISABLED_SELECTION':
                call(admin,PATH+'/items/alpha','PATCH',{'label':'已停用甲','sortOrder':0,'enabled':False},port=23082)
                call(admin,PATH+'/items/bravo','PATCH',{'label':'更新乙','sortOrder':1,'enabled':True},port=23082)
                reply=publish(1)
                if not recovery_only:
                    subscription.wait(lambda s:any(e.get('type')=='DICTIONARY_PUBLISHED' and e['data'].get('version')==2 for e in s.events),25)
                    received=[e for e in subscription.events if e.get('type')=='DICTIONARY_PUBLISHED' and e['data'].get('version')==2]
                    check('other_jvm_publish_reaches_existing_cookie_subscription',len(received)==1 and received[0]['data']['requestId']==reply['requestId'])
            elif action=='BREAK_SSE_TRANSPORT':
                counting=True
                reply=transport('SSE_DOWN');result['transportFault']=reply
                check('real_upstream_sockets_interrupted_and_abort_propagated',reply['interruptedSockets']>0 and reply['upstreamAborts']>0 and reply['relayAborts']>0,**reply)
                call(admin,BASE+'/menu-manifest');call(admin,PUBLIC)
                check('menu_and_business_http_remain_available_during_sse_outage',True)
            elif action=='PUBLISH_WHILE_OFFLINE_AND_RESTORE':
                call(admin,PATH+'/items/bravo','PATCH',{'label':'恢复后乙','sortOrder':1,'enabled':True},port=23082)
                reply=publish(2);result['transportRecovery']=transport('SSE_UP')
            elif action=='DISABLE':
                _,h=call(admin,PATH+'/disable','POST',{'expectedVersion':3,'idempotencyKey':uuid.uuid4().hex},port=23082);reply={'requestId':h['X-Request-Id']}
            elif action=='ROLLBACK':
                _,h=call(admin,PATH+'/rollback','POST',{'expectedVersion':3,'targetVersion':1,'idempotencyKey':uuid.uuid4().hex},port=23082);reply={'requestId':h['X-Request-Id']}
            else:raise RuntimeError('unexpected_browser_command')
            result.setdefault('commands',[]).append(action);persist();browser.stdin.write(json.dumps(reply)+'\n');browser.stdin.flush()
    selector.close();browser.wait(timeout=15);result['browserExitCode']=browser.returncode
    observed=json.loads((OUT/'browser-result.json').read_text());result['browserCounts']=observed['counts']
    assert observed['complete'] and observed['counts']['tests']>0 and observed['counts']['errors']==0 and browser.returncode==0,'browser_incomplete_or_failed'
    persisted=env.sql("SELECT COUNT(*),COUNT(DISTINCT event_id) FROM summer_dictionary_notification_event WHERE tenant_id='ent-acme' AND dictionary_code='customer-segment'",env.schema)
    check('all_five_lifecycle_notifications_persisted_once',persisted=='5\t5')
    result['stage']='complete'
except Exception as error:
    result['failureType']=type(error).__name__
    if isinstance(error,AssertionError):result['safeAssertion']=str(error)
finally:
    for s in streams:
        try:s.close()
        except Exception:pass
    for process in [browser,ui]:
        if process and process.poll() is None:
            process.terminate()
            try:process.wait(timeout=8)
            except subprocess.TimeoutExpired:process.kill();process.wait()
    if env:
        result['sensitiveLogDetected']=env.sensitive_log_detected;result['exceptionClasses']=env.diagnostics
        try:env.close();result['cleanup']='VERIFIED'
        except Exception as error:result['cleanupFailureType']=type(error).__name__
    if 'before' in globals():
        after=ui_inputs();(OUT/'ui-inputs-before.json').write_text(json.dumps(before,indent=2));result['uiInputCount']=len(before);result['changedUiInputs']=[p for p in sorted(set(before)|set(after)) if before.get(p)!=after.get(p)]
    (OUT/'http-metadata.json').write_text(json.dumps(Client.observations,indent=2));persist()
print(json.dumps({k:result.get(k) for k in ['stage','counts','browserCounts','failureType','safeAssertion','cleanup']}))
raise SystemExit(0 if result['stage']=='complete' and result['counts']['failures']==result['counts']['errors']==0 and result.get('cleanup')=='VERIFIED' and not result.get('sensitiveLogDetected') else 1)
