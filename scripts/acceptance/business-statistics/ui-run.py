"""T34 real MySQL/HTTP acceptance. Business fixtures are written only through HTTP."""
import datetime, hashlib, json, os, re, runpy, selectors, subprocess, time, uuid, shutil, signal
from pathlib import Path
from urllib.parse import urlencode
from environment import Client as BaseClient, Environment, OUT, ROOT, immutable_candidate

PORT=20081; BASE='/api/v1/enterprises/ent-acme'; STATS=BASE+'/statistics'
grants=runpy.run_path(str(ROOT/'scripts/acceptance/identity/grants.py'))
result={'issue':142,'stage':'setup','checks':[],'networkMocking':False,'businessSqlWrites':False}
env=ui=browser=None
class Client(BaseClient):
    def request(self,port,*args,**kwargs):return super().request(PORT,*args,**kwargs)
def check(name,passed,**facts):
    result['checks'].append({'name':name,'passed':bool(passed),**facts});print(name,'PASS' if passed else 'FAIL',flush=True)
def call(client,path,method='GET',body=None,expected=200,headers=None,fixture=True):
    rid='t34-'+uuid.uuid4().hex
    code,data,h=client.request(PORT,path,method,body,headers={'X-Request-Id':rid,'Idempotency-Key':uuid.uuid4().hex,**(headers or {})})
    accepted=code in expected if isinstance(expected,tuple) else code==expected
    check(method+' '+path.split('?')[0],accepted,httpStatus=code,expected=expected)
    if isinstance(data,dict) and 'success' in data:
        check('response_contract_'+rid,data.get('requestId')==h.get('X-Request-Id')==rid and data.get('success')==(code<400))
        if code>=400:check('summer_error_'+rid,str((data.get('error') or {}).get('code','')).startswith('SUMMER_'))
    if fixture:assert accepted,'fixture_http_'+str(code)
    return code,data,h
def value(data):return data.get('data') if isinstance(data,dict) else None
def login(username,password):
    c=Client().login(PORT,username,password);env.additional_secrets.append(c.token);return c
def bind(admin,username,roles,denies=None):
    status,body,h=call(admin,BASE+'/users/'+username+'/roles',expected=(200,409))
    if status==409:assert (body.get('error') or {}).get('code')=='SUMMER_AUTHORIZATION_CONFLICT'
    return call(admin,BASE+'/users/'+username+'/roles','PUT',{'roleIds':roles,'deniedCapabilityCodes':denies or []},headers={'If-Match':h['ETag'] if status==200 else '"1"'})
def wait_history(admin,task,status):
    deadline=time.monotonic()+30
    while time.monotonic()<deadline:
        code,data,_=admin.request(PORT,BASE+'/tasks/'+task+'/executions')
        if code==200 and any(x['status']==status for x in data['data']['items']):return data['data']['items']
        time.sleep(.25)
    return []
def source_inputs():
    files=[p for path in ['summer-sample/src/main/java/com/abluepoint/summer/sample/statistics','summer-ui/src/pages/statistics'] for p in (ROOT/path).rglob('*') if p.is_file()]
    files += list(Path(__file__).parent.glob('*.py'))+list(Path(__file__).parent.glob('*.mjs'))
    return {str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in files}
executed=OUT/'executed-scripts';executed.mkdir(exist_ok=False)
for source in Path(__file__).parent.iterdir():
    if source.suffix in ('.py','.mjs'):shutil.copy2(source,executed/source.name)
before=source_inputs();(OUT/'inputs-before.json').write_text(json.dumps(before,indent=2)+'\n')
try:
    candidate=immutable_candidate(); result['candidateSha256']=hashlib.sha256(candidate.read_bytes()).hexdigest()
    cinputs=json.loads((candidate.parent/'candidate-inputs.json').read_text())
    check('statistics_production_matches_candidate',all(cinputs.get(p)==h for p,h in before.items() if '/src/main/' in p))
    env=Environment();env.setup();env.start(PORT,jar=candidate)
    result['stage']='real_grants'
    admin=login(env.username,env.password);bootstrap=grants['bootstrap'](admin,env.username);admin=login(env.username,env.password)
    _,catalog,_=call(admin,BASE+'/capabilities/catalog');known={x['code']:x['capabilityId'] for x in catalog['data']['capabilities']}
    for code in ['enterprise.statistics.read','enterprise.tasks.write','platform.dictionary.create']:
        if code not in known:
            _,d,_=call(admin,BASE+'/capabilities','POST',{'code':code,'resource':code.rsplit('.',1)[0],'action':code.rsplit('.',1)[1],'riskLevel':'LOW','scope':'PLATFORM' if code.startswith('platform.') else 'ENTERPRISE'},201)
            known[code]=d['data']['capabilityId']
    taskroute='/api/v1/enterprises/{enterpriseId}/tasks';statsroute='/api/v1/enterprises/{enterpriseId}/statistics'
    extra=grants['create_role'](admin,'ent-acme',[known[x] for x in ['enterprise.statistics.read','enterprise.tasks.write','platform.dictionary.create']]+['GET:'+statsroute,'POST:'+taskroute,'GET:'+taskroute+'/{taskId}/executions','POST:'+taskroute+'/{taskId}:trigger','POST:/api/customers'],'T34 statistics fixtures')
    bind(admin,env.username,[bootstrap,extra]);admin=login(env.username,env.password)
    call(admin,'/api/users','POST',{'username':env.denied_username,'password':env.denied_password,'role':'USER'})
    _,member,_=call(admin,BASE+'/members','POST',{'subject':env.denied_username,'displayName':'T34 reader','email':'t34-reader@example.test'},201)
    reader=login(env.denied_username,env.denied_password)
    tasks=[]
    for kind,expected_status in [('system.noop','SUCCESS'),('system.fail','FAILED')]:
        _,d,_=call(admin,BASE+'/tasks','POST',{'name':'T34-'+kind+'-'+env.run,'taskType':kind,'cronExpression':'0 0 0 1 1 ? 2099','parameters':{},'maxRetries':1 if expected_status=='FAILED' else 0,'timeoutMs':10000},201)
        task=d['data']['taskId'];tasks.append(task)
        call(admin,BASE+'/tasks/'+task+':trigger','POST',expected=202)
        check('real_persisted_task_'+expected_status,bool(wait_history(admin,task,expected_status)))
    call(admin,BASE+'/data-scopes/dictionaries','PUT',{'operator':'AND','rules':[{'type':'SELF'}]})
    rrole=grants['create_role'](admin,'ent-acme',[known['enterprise.statistics.read'],'platform.dictionary.read','GET:'+statsroute],'T34 empty-scope reader')
    bind(admin,env.denied_username,[rrole])
    call(admin,'/api/v1/platform/enterprises','POST',{'name':'T34 UI second enterprise','code':'ui-'+env.run},201)
    admin=login(env.username,env.password)
    _,org,_=call(admin,BASE+'/org-units','POST',{'name':'T34 UI context','code':'ui-'+env.run,'unitType':'DEPARTMENT'},201)
    orgid=org['data']['id'];admin_member=env.sql("SELECT member_id FROM summer_enterprise_member WHERE enterprise_id='ent-acme' AND subject='t34-admin'",env.schema)
    _,_,tree_h=call(admin,BASE+'/org-tree')
    call(admin,BASE+'/members/'+admin_member+'/org-assignments','PUT',{'organizationIds':[orgid],'primaryOrganizationId':orgid},headers={'If-Match':tree_h['ETag']})
    call(admin,BASE+'/context','PUT',{'organizationId':orgid});admin=login(env.username,env.password)
    result['stage']='real_chrome'
    node='/opt/homebrew/opt/node@22/bin/node';child={**os.environ,'SUMMER_FOUNDATION_EVIDENCE_DIR':str(OUT),'SUMMER_T34_EVIDENCE_DIR':str(OUT),'SUMMER_E2E_USERNAME':env.username,'SUMMER_E2E_PASSWORD':env.password,'SUMMER_E2E_READER':env.denied_username,'SUMMER_E2E_READER_PASSWORD':env.denied_password}
    ui=subprocess.Popen([node,str(ROOT/'scripts/acceptance/foundation/ui-server.mjs')],cwd=ROOT/'summer-ui',env=child,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
    with selectors.DefaultSelector() as selector:
        selector.register(ui.stdout,selectors.EVENT_READ);assert selector.select(timeout=45);assert ui.stdout.readline().strip()=='UI_READY'
    browser=subprocess.Popen([node,str(Path(__file__).parent/('empty-browser.mjs' if os.environ.get('SUMMER_T34_BROWSER_MODE')=='empty' else 'browser.mjs'))],cwd=ROOT/'summer-ui',env=child,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,start_new_session=True)
    handled=set();deadline=time.monotonic()+240
    while browser.poll() is None and time.monotonic()<deadline:
        for command in ['fault','recover','deny']:
            if command in handled or not (OUT/(command+'-request')).exists():continue
            if command=='fault':
                tables=env.sql("SELECT table_name FROM information_schema.tables WHERE table_schema='"+env.schema+"'").splitlines()
                for table in tables:
                    assert re.fullmatch(r'[A-Za-z0-9_]+',table)
                    if table!='summer_audit_event':env.sql("GRANT SELECT ON `"+env.schema+"`.`"+table+"` TO '"+env.db_user+"'@'%'")
                env.sql("REVOKE SELECT ON `"+env.schema+"`.* FROM '"+env.db_user+"'@'%'")
                check('new_app_connection_audit_select_denied',env.audit_select_denied_for_new_connection())
                recycled=env.recycle_owned_database_connections();check('only_owned_app_connections_recycled',recycled>0,connectionCount=recycled)
                warmup=[]
                for _ in range(3):
                    code,_,_=admin.request(PORT,BASE+'/menu-manifest');warmup.append(code)
                    if code==200:break
                    time.sleep(.2)
                check('menu_remains_available_during_statistics_fault',warmup[-1]==200,httpStatuses=warmup)
                call(admin,STATS,expected=500)

            elif command=='recover':
                env.sql("GRANT SELECT ON `"+env.schema+"`.summer_audit_event TO '"+env.db_user+"'@'%'")
                env.sql("GRANT SELECT ON `"+env.schema+"`.* TO '"+env.db_user+"'@'%'")
            else:bind(admin,env.denied_username,[])
            handled.add(command);(OUT/(command+'-ready')).write_text('ready')
        time.sleep(.1)
    if browser.poll() is None:
        result['browserTimeout']=True;os.killpg(browser.pid,signal.SIGTERM);browser.wait(timeout=15)
    result['browser']=json.loads((OUT/'browser-result.json').read_text());result['browserExit']=browser.returncode
    if result['browser'].get('complete'):
        call(admin,BASE+'/context','PUT',{'organizationId':None})
        probe=subprocess.Popen([node,str(Path(__file__).parent/'context-browser.mjs')],cwd=ROOT/'summer-ui',env=child,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,start_new_session=True)
        try:probe.wait(timeout=90)
        except subprocess.TimeoutExpired:os.killpg(probe.pid,signal.SIGTERM);probe.wait(timeout=15)
        result['contextProbeExit']=probe.returncode
    check('sensitive_runtime_log_scan',not env.sensitive_log_detected)
    result['stage']='complete'
except Exception as error:
    result['failureType']=type(error).__name__
    if re.fullmatch(r'[A-Za-z_]+(?:_[0-9]+)?',str(error)):result['failureCode']=str(error)
    print('fatal',result['failureType'],result.get('failureCode',''),flush=True)
finally:
    if browser and browser.poll() is None:
        os.killpg(browser.pid,signal.SIGTERM);browser.wait(timeout=15)
    if ui and ui.poll() is None:
        ui.terminate()
        try:ui.wait(timeout=15)
        except subprocess.TimeoutExpired:ui.kill();ui.wait()
    if env:env.close()
    after=source_inputs();result['changedInputs']=[p for p in set(before)|set(after) if before.get(p)!=after.get(p)]
    checks=result['checks']+result.get('browser',{}).get('checks',[])
    result['counts']={'tests':len(checks),'failures':sum(not c['passed'] for c in checks),'errors':int('failureType' in result)+int('failureType' in result.get('browser',{}))+int(result.get('browserTimeout',False)),'skipped':0}
    result['passed']=result['stage']=='complete' and not result['counts']['failures'] and not result['counts']['errors'] and result.get('browserExit')==0
    (OUT/'result.json').write_text(json.dumps(result,indent=2)+'\n');print('counts',json.dumps(result['counts']),flush=True)
raise SystemExit(0 if result['passed'] else 1)
