"""Narrow real schema and HTTP probe after the T34 success-path 500."""
import hashlib,json,os,runpy,selectors,signal,subprocess,uuid
from pathlib import Path
from environment import Client as BaseClient,Environment,OUT,ROOT,immutable_candidate
PORT=20081;BASE='/api/v1/enterprises/ent-acme';result={'issue':142,'checks':[],'networkMocking':False,'businessSqlWrites':False};env=ui=browser=None
class Client(BaseClient):
    def request(self,port,*args,**kwargs):return super().request(PORT,*args,**kwargs)
def check(name,passed,**facts):result['checks'].append({'name':name,'passed':bool(passed),**facts});print(name,'PASS' if passed else 'FAIL',flush=True)
def request(client,path,method='GET',body=None,expected=200):
    rid='t34-probe-'+uuid.uuid4().hex;code,data,h=client.request(PORT,path,method,body,headers={'X-Request-Id':rid,'Idempotency-Key':uuid.uuid4().hex});assert code==expected,'fixture_http_'+str(code);return data
source={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in Path('summer-sample/src/main/java/com/abluepoint/summer/sample/statistics').glob('*.java')}
try:
    candidate=immutable_candidate();result['candidateSha256']=hashlib.sha256(candidate.read_bytes()).hexdigest();env=Environment();env.setup();env.start(PORT,jar=candidate)
    admin=Client().login(PORT,env.username,env.password);env.additional_secrets.append(admin.token);g=runpy.run_path(str(ROOT/'scripts/acceptance/identity/grants.py'));bootstrap=g['bootstrap'](admin,env.username);admin=Client().login(PORT,env.username,env.password);env.additional_secrets.append(admin.token)
    catalog=request(admin,BASE+'/capabilities/catalog')['data']['capabilities'];known={x['code']:x['capabilityId'] for x in catalog}
    if 'enterprise.statistics.read' not in known:known['enterprise.statistics.read']=request(admin,BASE+'/capabilities','POST',{'code':'enterprise.statistics.read','resource':'enterprise.statistics','action':'read','scope':'ENTERPRISE','riskLevel':'LOW'},201)['data']['capabilityId']
    role=g['create_role'](admin,'ent-acme',[known['enterprise.statistics.read'],'GET:/api/v1/enterprises/{enterpriseId}/statistics'],'T34 query failure probe');binding_headers=admin.request(PORT,BASE+'/users/'+env.username+'/roles')[2];code,_,_=admin.request(PORT,BASE+'/users/'+env.username+'/roles','PUT',{'roleIds':[bootstrap,role],'deniedCapabilityCodes':[]},headers={'If-Match':binding_headers['ETag'],'Idempotency-Key':uuid.uuid4().hex});assert code==200;admin=Client().login(PORT,env.username,env.password);env.additional_secrets.append(admin.token)
    table=lambda name:int(env.sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='"+env.schema+"' AND table_name='"+name+"'"))
    result['schemaTables']={name:table(name) for name in ['summer_audit_event','aict_audit_event','summer_task_definition','summer_task_execution']};result['migrationCount']=int(env.sql('SELECT COUNT(*) FROM flyway_schema_history WHERE success=1',env.schema))
    check('statistics_referenced_audit_table_exists_after_real_migrations',result['schemaTables']['aict_audit_event']==1,tableCounts=result['schemaTables'])
    rid='t34-red-'+uuid.uuid4().hex;code,body,h=admin.request(PORT,BASE+'/statistics',headers={'X-Request-Id':rid});check('authorized_statistics_success',code==200,httpStatus=code)
    result['errorResponse']={'status':code,'success':body.get('success'),'code':(body.get('error') or {}).get('code'),'requestId':body.get('requestId'),'requestIdMatches':body.get('requestId')==h.get('X-Request-Id')==rid}
    node='/opt/homebrew/opt/node@22/bin/node';child={**os.environ,'SUMMER_FOUNDATION_EVIDENCE_DIR':str(OUT),'SUMMER_T34_EVIDENCE_DIR':str(OUT),'SUMMER_E2E_USERNAME':env.username,'SUMMER_E2E_PASSWORD':env.password}
    ui=subprocess.Popen([node,str(ROOT/'scripts/acceptance/foundation/ui-server.mjs')],cwd=ROOT/'summer-ui',env=child,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
    with selectors.DefaultSelector() as selector:
        selector.register(ui.stdout,selectors.EVENT_READ);assert selector.select(timeout=45);assert ui.stdout.readline().strip()=='UI_READY'
    browser=subprocess.Popen([node,str(Path(__file__).parent/'failure-browser.mjs')],cwd=ROOT/'summer-ui',env=child,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True,start_new_session=True)
    try:browser.communicate(timeout=150);result['browserExit']=browser.returncode
    except subprocess.TimeoutExpired:result['browserTimeout']=True;os.killpg(browser.pid,signal.SIGTERM);browser.wait(timeout=15)
    result['browser']=json.loads((OUT/'browser-result.json').read_text());check('sensitive_runtime_log_scan',not env.sensitive_log_detected);result['complete']=True
except Exception as error:result['failureType']=type(error).__name__;print('fatal',type(error).__name__,flush=True)
finally:
    if browser and browser.poll() is None:os.killpg(browser.pid,signal.SIGTERM);browser.wait(timeout=15)
    if ui and ui.poll() is None:
        ui.terminate()
        try:ui.wait(timeout=15)
        except subprocess.TimeoutExpired:ui.kill();ui.wait()
    if env:env.close()
    result['changedProductionInputs']=[p for p,h in source.items() if hashlib.sha256(Path(p).read_bytes()).hexdigest()!=h]
    checks=result['checks']+result.get('browser',{}).get('checks',[]);result['counts']={'tests':len(checks),'failures':sum(not c['passed'] for c in checks),'errors':int('failureType' in result)+int('failureType' in result.get('browser',{}))+int(result.get('browserTimeout',False)),'skipped':0};result['passed']=result.get('complete',False) and not result['counts']['failures'] and not result['counts']['errors']
    (OUT/'result.json').write_text(json.dumps(result,indent=2)+'\n');print('counts',json.dumps(result['counts']),flush=True)
raise SystemExit(0 if result['passed'] else 1)
