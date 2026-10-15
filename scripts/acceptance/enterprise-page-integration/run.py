"""#142 selected-tenant pages: isolated real MySQL/Redis, strict PKCE, Chrome.
The shared T34 environment is reused without changing its historical scripts.
"""
import hashlib, json, os, re, runpy, selectors, shutil, signal, subprocess, sys, time, uuid
from pathlib import Path
SCRIPT=Path(__file__).resolve().parent
ROOT=SCRIPT.parents[2]
sys.path.insert(0,str(ROOT/'scripts/acceptance/business-statistics'))
if not os.environ.get('SUMMER_T34_EVIDENCE_DIR'):raise RuntimeError('explicit_evidence_directory_required')
from environment import Client as BaseClient, Environment, OUT, immutable_candidate
PORT=20081; BASE='/api/v1/enterprises/ent-acme'
grants=runpy.run_path(str(ROOT/'scripts/acceptance/identity/grants.py'))
supplement=os.environ.get('SUMMER_EP_SUPPLEMENT')=='1'
result={'issue':142,'supplement':supplement,'eventsDeniedEnterprise':'ent-acme','networkMocking':False,'businessSqlWrites':False,'checks':[],'stage':'setup'}
env=ui=browser=None
class Client(BaseClient):
    def request(self,port,*args,**kwargs):return super().request(PORT,*args,**kwargs)
def check(name,passed,**facts):
    result['checks'].append({'name':name,'passed':bool(passed),**facts}); print(name,'PASS' if passed else 'FAIL',flush=True)
def call(client,path,method='GET',body=None,expected=(200,),headers=None):
    rid='ep-'+uuid.uuid4().hex
    status,data,h=client.request(PORT,path,method,body,headers={'X-Request-Id':rid,'Idempotency-Key':uuid.uuid4().hex,**(headers or {})})
    ok=status in expected
    check(method+' '+path.split('?')[0],ok,httpStatus=status,requestId=rid)
    if not ok:raise RuntimeError('fixture_http_'+str(status))
    check('response_contract',data.get('requestId')==h.get('X-Request-Id')==rid and data.get('success')==(status<400))
    return status,data,h
def login(username,password):
    c=Client().login(PORT,username,password);env.additional_secrets.append(c.token);return c
def bind(client,tenant,username,roles,denies=()):
    path='/api/v1/enterprises/'+tenant+'/users/'+username+'/roles'
    status,data,h=call(client,path,expected=(200,409))
    return call(client,path,'PUT',{'roleIds':roles,'deniedCapabilityCodes':list(denies)},headers={'If-Match':h['ETag'] if status==200 else '"1"'})
def inputs():
    paths=[p for directory in ['summer-ui/src/pages/roles','summer-ui/src/pages/authorization-events','summer-sample/src/main/java/com/abluepoint/summer/sample/authorization'] for p in (ROOT/directory).rglob('*') if p.is_file()]
    paths += [ROOT/p for p in ['summer-ui/src/main.tsx','summer-ui/src/components/NavBar/index.tsx','summer-ui/src/pages/data-scopes/useActiveEnterprise.ts']]
    paths += list(SCRIPT.glob('*.py'))+list(SCRIPT.glob('*.mjs'))
    return {str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}
before=inputs();(OUT/'inputs-before.json').write_text(json.dumps(before,indent=2)+'\n')
archive=OUT/'executed-scripts';archive.mkdir(exist_ok=False)
for path in [*SCRIPT.glob('*.py'),*SCRIPT.glob('*.mjs'),ROOT/'scripts/acceptance/business-statistics/environment.py',ROOT/'scripts/acceptance/identity/grants.py',ROOT/'scripts/acceptance/foundation/ui-server.mjs']:
    shutil.copy2(path,archive/path.name)
try:
    candidate=immutable_candidate();result['candidateSha256']=hashlib.sha256(candidate.read_bytes()).hexdigest()
    frozen=json.loads((candidate.parent/'candidate-inputs.json').read_text())
    match=all(frozen.get(p)==h for p,h in before.items() if '/src/main/java/' in p)
    check('authorization_sources_match_fixed_candidate',match)
    if not match:raise RuntimeError('authorization_candidate_mismatch')
    env=Environment();env.setup();env.start(PORT,jar=candidate)
    admin=login(env.username,env.password);bootstrap=grants['bootstrap'](admin,env.username);admin=login(env.username,env.password)
    extra=grants['create_role'](admin,'ent-acme',['platform.interface-resources.read','GET:/api/v1/platform/interface-resources','GET:/api/authorization-events','POST:/api/authorization-events/{eventId}/retry'],'Page integration catalog and events')
    _,acme_bound,_=bind(admin,'ent-acme',env.username,[bootstrap,extra]);acme_version=acme_bound['data']['version'];admin=login(env.username,env.password)
    call(admin,'/api/users','POST',{'username':env.denied_username,'password':env.denied_password,'role':'USER'})
    call(admin,BASE+'/members','POST',{'subject':env.denied_username,'displayName':'Page integration member','email':'ep@example.test'},expected=(201,))
    _,second,_=call(admin,'/api/v1/platform/enterprises','POST',{'name':'Page integration tenant B','code':'ep-'+env.run},expected=(201,))
    tenant=second['data']['enterpriseId'];secondbase='/api/v1/enterprises/'+tenant
    admin=login(env.username,env.password)
    caps=['enterprise.roles.read','enterprise.roles.write','enterprise.members.read','enterprise.members.write','enterprise.menu.read','enterprise.organization.read','enterprise.capabilities.read','enterprise.capabilities.write','GET:/api/authorization-events','POST:/api/authorization-events/{eventId}/retry']+[m+':'+p for m,p in grants['ROUTES']]
    role=grants['create_role'](admin,tenant,caps,'Page integration B manager')
    _,existing,_=call(admin,secondbase+'/users/'+env.username+'/roles',expected=(200,409))
    roles=(existing.get('data') or {}).get('roleIds',[])+[role]
    _,bound,_=bind(admin,tenant,env.username,roles);binding_version=bound['data']['version']
    call(admin,secondbase+'/members','POST',{'subject':env.denied_username,'displayName':'Page integration B member','email':'ep-b@example.test'},expected=(201,))
    call(admin,BASE+'/context','PUT',{'organizationId':None});admin=login(env.username,env.password)
    _,initial_roles,_=call(admin,BASE+'/roles');acme_ids=sorted(r['roleId'] for r in initial_roles['data'])
    if supplement:
        _,seeded,_=call(admin,secondbase+'/roles','POST',{'name':'Page integration Chrome role','code':'ep-browser-role','capabilityIds':['GET:/api/customers']},expected=(201,))
        bind(admin,tenant,env.denied_username,[seeded['data']['roleId']])
    result['stage']='real_chrome'
    child={**os.environ,'SUMMER_FOUNDATION_EVIDENCE_DIR':str(OUT),'SUMMER_T34_EVIDENCE_DIR':str(OUT),'SUMMER_E2E_USERNAME':env.username,'SUMMER_E2E_PASSWORD':env.password,'SUMMER_E2E_MEMBER':env.denied_username,'SUMMER_EP_SECOND_ENTERPRISE':tenant}
    node='/opt/homebrew/opt/node@22/bin/node'
    ui=subprocess.Popen([node,str(ROOT/'scripts/acceptance/foundation/ui-server.mjs')],cwd=ROOT/'summer-ui',env=child,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
    with selectors.DefaultSelector() as selector:
        selector.register(ui.stdout,selectors.EVENT_READ)
        if not selector.select(timeout=45) or ui.stdout.readline().strip()!='UI_READY':raise RuntimeError('ui_not_ready')
    browser=subprocess.Popen([node,str(SCRIPT/'browser.mjs')],cwd=ROOT/'summer-ui',env=child,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,start_new_session=True)
    deadline=time.monotonic()+240;handled=set()
    while browser.poll() is None and time.monotonic()<deadline:
        for command in ('roles-deny','roles-restore','events-deny','events-restore'):
            if command not in handled and (OUT/(command+'.request')).exists():
                is_event=command.startswith('events-')
                path=(BASE if is_event else secondbase)+'/users/'+env.username+'/roles'
                denies=[] if command.endswith('restore') else ['enterprise.roles.read']
                _,updated,_=call(admin,path,'PUT',{'roleIds':[bootstrap,extra] if is_event else roles,'deniedCapabilityCodes':denies},headers={'If-Match':'"'+str(acme_version if is_event else binding_version)+'"'})
                check('persisted_denies_match_'+command,updated['data']['deniedCapabilityCodes']==denies)
                if is_event:acme_version=updated['data']['version']
                else:binding_version=updated['data']['version']
                (OUT/(command+'.done')).write_text('done\n');handled.add(command)
        time.sleep(.15)
    if browser.poll() is None:raise RuntimeError('browser_timeout')
    result['browserExit']=browser.returncode;result['browser']=json.loads((OUT/'browser-result.json').read_text())
    if not result['browser'].get('complete'):raise RuntimeError('browser_incomplete')
    # Restore through the still-authorized write endpoint, then independently read persistence.
    call(admin,secondbase+'/users/'+env.username+'/roles','PUT',{'roleIds':roles,'deniedCapabilityCodes':[]},headers={'If-Match':'\"'+str(binding_version)+'\"'})
    _,after_roles,_=call(admin,BASE+'/roles');check('tenant_A_role_ids_unchanged',sorted(r['roleId'] for r in after_roles['data'])==acme_ids)
    _,b_roles,_=call(admin,secondbase+'/roles');created=[r for r in b_roles['data'] if r['code']=='ep-browser-role']
    check('seeded_role_persisted_only_in_B' if supplement else 'Chrome_role_persisted_only_in_B',len(created)==1 and created[0]['enterpriseId']==tenant)
    _,binding,_=call(admin,secondbase+'/users/'+env.denied_username+'/roles')
    check('seeded_binding_persisted_in_B' if supplement else 'Chrome_binding_persisted_in_B',len(created)==1 and binding['data']['roleIds']==[created[0]['roleId']])
    check('sensitive_runtime_log_scan',not env.sensitive_log_detected)
    result['stage']='complete'
except Exception as error:
    result['failureType']=type(error).__name__
    if re.fullmatch(r'[A-Za-z_]+(?:_[0-9]+)?',str(error)):result['failureCode']=str(error)
    print('fatal',result['failureType'],result.get('failureCode',''),flush=True)
finally:
    if browser and browser.poll() is None:
        os.killpg(browser.pid,signal.SIGTERM)
        try:browser.wait(timeout=15)
        except subprocess.TimeoutExpired:
            result['browserForcedStop']=True
            os.killpg(browser.pid,signal.SIGKILL);browser.wait(timeout=15)
    if ui and ui.poll() is None:
        ui.terminate()
        try:ui.wait(timeout=15)
        except subprocess.TimeoutExpired:ui.kill();ui.wait()
    if env:
        result['diagnostics']=env.diagnostics
        result['authenticationEvents']=env.security_events
        result['httpObservations']=Client.observations
        try:env.close()
        except Exception as cleanup_error:result['cleanupFailureType']=type(cleanup_error).__name__
    result['changedInputs']=[p for p,h in before.items() if inputs().get(p)!=h]
    checks=result['checks']+result.get('browser',{}).get('checks',[])
    result['counts']={'tests':len(checks),'failures':sum(not c['passed'] for c in checks),'errors':int('failureType' in result)+int('failureType' in result.get('browser',{})),'skipped':0}
    result['passed']=result['stage']=='complete' and result.get('browserExit')==0 and result.get('browser',{}).get('complete',False) and not result['changedInputs'] and not result['counts']['failures'] and not result['counts']['errors'] and 'cleanupFailureType' not in result
    (OUT/'result.json').write_text(json.dumps(result,indent=2)+'\n');print('counts',json.dumps(result['counts']),flush=True)
raise SystemExit(0 if result['passed'] else 1)
