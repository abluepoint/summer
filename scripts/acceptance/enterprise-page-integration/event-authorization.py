"""#142 event enterprise authorization: real MySQL/PKCE plus selected-B Chrome403.
Run only against root's newly frozen candidate and an exclusive 200xx window.
"""
import hashlib, json, os, re, runpy, selectors, shutil, signal, subprocess, sys, time, uuid
from pathlib import Path
SCRIPT=Path(__file__).resolve().parent; ROOT=SCRIPT.parents[2]
location=os.environ.get('SUMMER_T34_EVIDENCE_DIR')
if not location or Path(location).exists():raise RuntimeError('new_explicit_evidence_directory_required')
sys.path.insert(0,str(ROOT/'scripts/acceptance/business-statistics'))
from environment import Client as BaseClient, Environment, OUT, immutable_candidate
PORT=20081; A='ent-acme'; BASE='/api/v1/enterprises/'+A; READ='enterprise.roles.read'; WRITE='enterprise.roles.write'
grants=runpy.run_path(str(ROOT/'scripts/acceptance/identity/grants.py'))
browser_only=os.environ.get('SUMMER_EP_BROWSER_ONLY')=='1'
result={'issue':142,'browserOnlySupplement':browser_only,'checks':[],'stage':'setup','networkMocking':False,'businessSqlWrites':False}
env=ui=browser=None
class Client(BaseClient):
    def request(self,port,*args,**kwargs):return super().request(PORT,*args,**kwargs)
def check(name,passed,**facts):
    result['checks'].append({'name':name,'passed':bool(passed),**facts});print(name,'PASS' if passed else 'FAIL',flush=True)
def call(client,path,method='GET',body=None,expected=(200,),headers=None):
    rid='event-tenant-'+uuid.uuid4().hex
    code,data,h=client.request(PORT,path,method,body,headers={'X-Request-Id':rid,'Idempotency-Key':uuid.uuid4().hex,**(headers or {})})
    ok=code in expected
    check(method+' '+path.split('?')[0],ok,status=code,requestId=rid)
    if not ok:raise RuntimeError('http_status_'+str(code))
    check('envelope_and_requestId',isinstance(data,dict) and data.get('requestId')==h.get('X-Request-Id')==rid and data.get('success')==(code<400) and (code<400 or data.get('data') is None))
    return code,data,h
def login(username,password):
    c=Client().login(PORT,username,password);env.additional_secrets.append(c.token);return c
def bind(admin,tenant,username,roles,denies=()):
    path='/api/v1/enterprises/'+tenant+'/users/'+username+'/roles'
    code,body,h=call(admin,path,expected=(200,409))
    _,data,_=call(admin,path,'PUT',{'roleIds':roles,'deniedCapabilityCodes':list(denies)},headers={'If-Match':h['ETag'] if code==200 else '"1"'})
    check('binding_roundtrip',data['data']['roleIds']==roles and data['data']['deniedCapabilityCodes']==list(denies))
def listing(tenant):return '/api/authorization-events?enterpriseId='+tenant
def retry(tenant,event):return '/api/authorization-events/'+event+'/retry?enterpriseId='+tenant
def snapshot(event):
    if not re.fullmatch(r'[a-f0-9-]{36}',event):raise RuntimeError('invalid_fixture_event_id')
    return env.sql("SELECT status,attempts,COALESCE(last_error,''),CAST(updated_at AS CHAR),CAST(next_attempt_at AS CHAR) FROM summer_authorization_change_outbox WHERE event_id='"+event+"'",env.schema)
def settled(event):
    deadline=time.monotonic()+30
    while time.monotonic()<deadline:
        state=snapshot(event)
        if state.startswith(('PUBLISHED','DEAD_LETTER')):return state
        time.sleep(.2)
    raise RuntimeError('event_not_settled')
def deny_retry(client,tenant,event):
    before=settled(event);call(client,retry(tenant,event),'POST',expected=(403,));check('denied_retry_has_no_outbox_side_effect',snapshot(event)==before)
def inputs():
    paths=[*SCRIPT.glob('event-authorization*')]
    paths += list((ROOT/'summer-sample/src/main/java/com/abluepoint/summer/sample/authorization').glob('*.java'))
    paths += [ROOT/p for p in ['summer-ui/src/routes.ts','summer-ui/src/menu/manifest.ts','summer-ui/src/pages/roles/index.tsx','summer-ui/src/pages/authorization-events/index.tsx','summer-ui/src/main.tsx','summer-ui/src/components/NavBar/index.tsx','summer-ui/src/pages/data-scopes/useActiveEnterprise.ts']]
    return {str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in paths if p.is_file()}
before=inputs();(OUT/'inputs-before.json').write_text(json.dumps(before,indent=2)+'\n')
archive=OUT/'executed-scripts';archive.mkdir()
for p in [SCRIPT/'event-authorization.py',SCRIPT/'event-authorization-browser.mjs',ROOT/'scripts/acceptance/business-statistics/environment.py',ROOT/'scripts/acceptance/identity/grants.py',ROOT/'scripts/acceptance/foundation/ui-server.mjs',ROOT/'scripts/acceptance/menu-manifest/export-manifest.mjs']:shutil.copy2(p,archive/p.name)
try:
    candidate=immutable_candidate();result['candidateSha256']=hashlib.sha256(candidate.read_bytes()).hexdigest()
    frozen=json.loads((candidate.parent/'candidate-inputs.json').read_text());matched=all(frozen.get(p)==h for p,h in before.items() if '/src/main/java/' in p)
    check('candidate_contains_current_authorization_sources',matched)
    if not matched:raise RuntimeError('candidate_does_not_include_fix')
    env=Environment();env.setup();env.start(PORT,jar=candidate)
    listeners=subprocess.check_output(['lsof','-nP','-t','-iTCP:'+str(PORT),'-sTCP:LISTEN'],text=True).splitlines()
    owned=listeners==[str(env.nodes[PORT].pid)] and env.nodes[PORT].poll() is None
    check('ready_listener_is_this_candidate_JVM',owned)
    result['runtimeOwner']={'applicationPid':env.nodes[PORT].pid,'port':PORT,'listenerVerified':owned}
    if not owned:raise RuntimeError('candidate_listener_owner_mismatch')
    admin=login(env.username,env.password);bootstrap=grants['bootstrap'](admin,env.username);admin=login(env.username,env.password)
    subprocess.run(['/opt/homebrew/opt/node@22/bin/node',str(ROOT/'scripts/acceptance/menu-manifest/export-manifest.mjs')],cwd=ROOT/'summer-ui',env={**os.environ,'MENU_MANIFEST_EXPORT':str(OUT/'actual-route-manifest.json')},check=True,stdout=subprocess.DEVNULL)
    manifest=json.loads((OUT/'actual-route-manifest.json').read_text())
    call(admin,'/api/v1/platform/menu-manifest','PUT',manifest)
    call(admin,'/api/users','POST',{'username':env.denied_username,'password':env.denied_password,'role':'USER'})
    call(admin,BASE+'/members','POST',{'subject':env.denied_username,'displayName':'Event reader','email':'event-a@example.test'},expected=(201,))
    _,data,_=call(admin,'/api/v1/platform/enterprises','POST',{'name':'Event authorization B','code':'events-'+env.run},expected=(201,));b=data['data']['enterpriseId'];bbase='/api/v1/enterprises/'+b
    admin=login(env.username,env.password)
    manager=grants['create_role'](admin,b,[READ,WRITE,'enterprise.capabilities.read','enterprise.capabilities.write','enterprise.members.read','enterprise.members.write','enterprise.menu.read']+[m+':'+p for m,p in grants['ROUTES']],'Event B manager')
    _,current,_=call(admin,bbase+'/users/'+env.username+'/roles',expected=(200,409));manager_roles=(current.get('data') or {}).get('roleIds',[])+[manager]
    bind(admin,b,env.username,manager_roles);admin=login(env.username,env.password)
    call(admin,bbase+'/members','POST',{'subject':env.denied_username,'displayName':'Event reader B','email':'event-b@example.test'},expected=(201,))
    roles={tenant:grants['create_role'](admin,tenant,[READ,WRITE,'enterprise.menu.read'],'Event selected tenant reader') for tenant in [A,b]}
    for tenant in [A,b]:bind(admin,tenant,env.denied_username,[roles[tenant]])
    reader=login(env.denied_username,env.denied_password);original_token=reader.token
    if not browser_only:
        target={}
        for tenant in [A,b]:
            _,data,_=call(reader,listing(tenant));items=data['data']['items'];check('list_only_requested_enterprise',bool(items) and all(x['enterpriseId']==tenant for x in items));target[tenant]=next(x['eventId'] for x in items if x['resourceId']==roles[tenant])
        eventA,eventB=target[A],target[b]
        result['stage']='same_jwt_revocation'
        bind(admin,b,env.denied_username,[roles[b]],[READ]);call(reader,listing(b),expected=(403,));call(reader,listing(A))
        bind(admin,b,env.denied_username,[roles[b]],[WRITE]);deny_retry(reader,b,eventB);call(reader,listing(b));call(reader,retry(A,eventA),'POST')
        bind(admin,b,env.denied_username,[roles[b]])
        bind(admin,A,env.denied_username,[roles[A]],[READ,WRITE]);call(reader,listing(b));call(reader,retry(b,eventB),'POST');call(reader,listing(A),expected=(403,));deny_retry(reader,A,eventA)
        bind(admin,A,env.denied_username,[roles[A]])
        bind(admin,b,env.denied_username,[]);call(reader,listing(b),expected=(403,));deny_retry(reader,b,eventB);call(reader,listing(A))
        bind(admin,b,env.denied_username,[roles[b]])
        foreign_before=settled(eventA);call(reader,retry(b,eventA),'POST',expected=(404,));check('foreign_retry_has_no_side_effect',snapshot(eventA)==foreign_before)
        for query in ['', '?enterpriseId=']:
            _,data,_=call(reader,'/api/authorization-events'+query);check('default_and_empty_resolve_A',all(x['enterpriseId']==A for x in data['data']['items']))
        for query in ['?enterpriseId=invalid%2Ftenant','?enterpriseId='+A+'&enterpriseId='+b]:
            call(reader,'/api/authorization-events'+query,expected=(403,));event_before=settled(eventB);call(reader,'/api/authorization-events/'+eventB+'/retry'+query,'POST',expected=(403,));check('invalid_selection_has_no_retry_side_effect',snapshot(eventB)==event_before)
        check('same_JWT_used_throughout_permission_changes',reader.token==original_token)
    result['stage']='selected_B_Chrome'
    child={**os.environ,'SUMMER_FOUNDATION_EVIDENCE_DIR':str(OUT),'SUMMER_E2E_USERNAME':env.denied_username,'SUMMER_E2E_PASSWORD':env.denied_password,'SUMMER_EP_SECOND_ENTERPRISE':b}
    node='/opt/homebrew/opt/node@22/bin/node';ui=subprocess.Popen([node,str(ROOT/'scripts/acceptance/foundation/ui-server.mjs')],cwd=ROOT/'summer-ui',env=child,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
    with selectors.DefaultSelector() as selector:
        selector.register(ui.stdout,selectors.EVENT_READ)
        if not selector.select(timeout=45) or ui.stdout.readline().strip()!='UI_READY':raise RuntimeError('ui_not_ready')
    for port in [20573,20580]:
        listeners=subprocess.check_output(['lsof','-nP','-t','-iTCP:'+str(port),'-sTCP:LISTEN'],text=True).splitlines()
        owned=listeners==[str(ui.pid)]
        check('owned_UI_listener_'+str(port),owned)
        if not owned:raise RuntimeError('ui_listener_owner_mismatch')
    browser=subprocess.Popen([node,str(SCRIPT/'event-authorization-browser.mjs')],cwd=ROOT/'summer-ui',env=child,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,start_new_session=True)
    deadline=time.monotonic()+150;denied=False
    while browser.poll() is None and time.monotonic()<deadline:
        if not denied and (OUT/'browser-deny-B.request').exists():
            bind(admin,b,env.denied_username,[roles[b]],[READ]);(OUT/'browser-deny-B.done').write_text('done\n');denied=True
        time.sleep(.15)
    if browser.poll() is None:raise RuntimeError('browser_timeout')
    result['browserExit']=browser.returncode;result['browser']=json.loads((OUT/'browser-result.json').read_text())
    if not result['browser'].get('complete'):raise RuntimeError('browser_incomplete')
    check('sensitive_runtime_log_scan',not env.sensitive_log_detected);result['stage']='complete'
except Exception as error:
    result['failureType']=type(error).__name__
    if re.fullmatch(r'[A-Za-z_]+(?:_[0-9]+)?',str(error)):result['failureCode']=str(error)
    print('fatal',result['failureType'],result.get('failureCode',''),flush=True)
finally:
    if browser and browser.poll() is None:
        os.killpg(browser.pid,signal.SIGTERM)
        try:browser.wait(timeout=15)
        except subprocess.TimeoutExpired:result['browserForcedStop']=True;os.killpg(browser.pid,signal.SIGKILL);browser.wait(timeout=15)
    if ui and ui.poll() is None:
        ui.terminate()
        try:ui.wait(timeout=15)
        except subprocess.TimeoutExpired:ui.kill();ui.wait()
    if env:
        result['httpObservations']=Client.observations;result['diagnostics']=env.diagnostics
        try:env.close()
        except Exception as error:result['cleanupFailureType']=type(error).__name__
    result['changedInputs']=[p for p,h in before.items() if inputs().get(p)!=h]
    checks=result['checks']+result.get('browser',{}).get('checks',[])
    result['counts']={'tests':len(checks),'failures':sum(not x['passed'] for x in checks),'errors':int('failureType' in result)+int('failureType' in result.get('browser',{}))+int('cleanupFailureType' in result),'skipped':0}
    result['passed']=result['stage']=='complete' and not result['counts']['failures'] and not result['counts']['errors'] and not result['changedInputs'] and result.get('browserExit')==0
    (OUT/'result.json').write_text(json.dumps(result,indent=2)+'\n');print('counts',json.dumps(result['counts']),flush=True)
raise SystemExit(0 if result['passed'] else 1)
