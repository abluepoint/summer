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
    result['stage']='initial_contract'
    call(reader,STATS,expected=403);call(Client(),STATS,expected=401)
    code,initial,_=call(admin,STATS,fixture=False)
    result['initialStatistics']=value(initial)
    if code!=200:raise RuntimeError('statistics_success_path_failed')
    check('bounded_default_window',27*86400<=datetime.timedelta.total_seconds(datetime.datetime.fromisoformat(initial['data']['window']['to'].replace('Z','+00:00'))-datetime.datetime.fromisoformat(initial['data']['window']['from'].replace('Z','+00:00')))<=31*86400)
    for query in ['unexpected=1','from=invalid','from=2026-01-02T00%3A00%3A00Z&to=2026-01-01T00%3A00%3A00Z','from=2020-01-01T00%3A00%3A00Z&to=2026-01-01T00%3A00%3A00Z','organizationId=foreign']:
        _,invalid,_=call(admin,STATS+'?'+query,expected=403 if query.startswith('organizationId') else 400,fixture=False)
        if not query.startswith('organizationId'):
            check('invalid_statistics_uses_framework_argument_code',(invalid.get('error') or {}).get('code')=='SUMMER_INVALID_ARGUMENT')
    call(admin,'/api/v1/enterprises/ent-foreign/statistics',expected=403)
    _,past,_=call(admin,STATS+'?to=2025-02-01T00%3A00%3A00Z');check('default_from_is_relative_to_requested_to',past['data']['window']['from']=='2025-01-02T00:00:00Z')
    result['stage']='persistent_tasks_and_audit'
    tasks=[]
    for kind,expected_status in [('system.noop','SUCCESS'),('system.fail','FAILED')]:
        _,d,_=call(admin,BASE+'/tasks','POST',{'name':'T34-'+kind+'-'+env.run,'taskType':kind,'cronExpression':'0 0 0 1 1 ? 2099','parameters':{},'maxRetries':1 if expected_status=='FAILED' else 0,'timeoutMs':10000},201)
        task=d['data']['taskId'];tasks.append(task)
        call(admin,BASE+'/tasks/'+task+':trigger','POST',expected=202)
        check('real_persisted_task_'+expected_status,bool(wait_history(admin,task,expected_status)))
    username='t34.'+env.run
    call(admin,'/api/customers','POST',{'username':username,'email':username+'@example.test','displayName':'T34 audit fixture'})
    _,with_tasks,_=call(admin,STATS);stats=with_tasks['data'];result['persistentStatistics']=stats
    rows=env.sql("SELECT COUNT(*),SUM(status='SUCCESS'),SUM(status='FAILED') FROM summer_task_execution WHERE enterprise_id='ent-acme'",env.schema).split('\t')
    check('statistics_tasks_match_real_execution_rows',[stats['taskExecutionCount'],stats['successfulTaskExecutionCount'],stats['failedTaskExecutionCount']]==list(map(int,rows)),executionCounts=list(map(int,rows)))
    check('task_success_rate_matches_completed_fixture',stats['taskExecutionCount']==2 and stats['taskSuccessRate']==.5)
    check('retries_do_not_inflate_execution_count',int(env.sql("SELECT MAX(attempt) FROM summer_task_execution WHERE enterprise_id='ent-acme' AND status='FAILED'",env.schema))==2 and stats['taskExecutionCount']==2)
    first=env.sql("SELECT DATE_FORMAT(CONVERT_TZ(MIN(started_at),@@session.time_zone,'+00:00'),'%Y-%m-%dT%H:%i:%s.%fZ') FROM summer_task_execution WHERE enterprise_id='ent-acme'",env.schema)
    boundary=datetime.datetime.fromisoformat(first.replace('Z','+00:00'))
    _,boundary_stats,_=call(admin,STATS+'?'+urlencode({'from':first,'to':(boundary+datetime.timedelta(microseconds=1)).isoformat()}))
    check('execution_window_includes_start',boundary_stats['data']['taskExecutionCount']==1)
    _,boundary_stats,_=call(admin,STATS+'?'+urlencode({'from':(boundary-datetime.timedelta(seconds=1)).isoformat(),'to':first}))
    check('execution_window_excludes_end',boundary_stats['data']['taskExecutionCount']==0)
    ledger_count=int(env.sql("SELECT COUNT(*) FROM summer_audit_event WHERE tenant_id='ent-acme'",env.schema))
    result['auditSources']={'summerAuditEvents':ledger_count,'reported':stats['auditEventCount']}
    check('all_real_migrations_succeeded',int(env.sql('SELECT COUNT(*) FROM flyway_schema_history WHERE success=0',env.schema))==0)
    check('statistics_include_actual_task_and_business_audit',ledger_count>0 and stats['auditEventCount']==ledger_count)
    future={'from':'2098-01-01T00:00:00Z','to':'2098-01-02T00:00:00Z'}
    _,d,_=call(admin,STATS+'?'+urlencode(future));check('execution_and_audit_window_excludes_earlier_rows',d['data']['taskExecutionCount']==0 and d['data']['auditEventCount']==0)
    result['stage']='effective_visibility'
    call(admin,BASE+'/data-scopes/dictionaries','PUT',{'operator':'AND','rules':[{'type':'SELF'}]})
    _,dictionary,_=call(admin,BASE+'/dictionaries','POST',{'code':'stats-'+env.run,'name':'T34 private dictionary','published':False,'publicReadable':False})
    rrole=grants['create_role'](admin,'ent-acme',[known['enterprise.statistics.read'],'platform.dictionary.read','GET:'+statsroute],'T34 scoped reader')
    bind(admin,env.denied_username,[rrole]);reader=login(env.denied_username,env.denied_password)
    _,visible,_=call(reader,BASE+'/dictionaries');_,reader_stats,_=call(reader,STATS)
    visible_count=len(visible['data']);reported=reader_stats['data']['dictionaryCount']
    check('statistics_respects_current_user_dictionary_data_scope',reported==visible_count,visibleDictionaryCount=visible_count,reportedDictionaryCount=reported)
    _,before_deny,_=call(admin,STATS);before_count=before_deny['data']['accessibleInterfaceCount']
    _,_,denied_binding=bind(admin,env.username,[bootstrap,extra],['GET:/api/users','enterprise.roles.read'])
    call(admin,'/api/users',expected=403)
    _,after_deny,_=call(admin,STATS);after_count=after_deny['data']['accessibleInterfaceCount']
    check('accessible_interface_count_reloads_persisted_deny_for_old_jwt',after_count<before_count,before=before_count,after=after_count)
    check('revoked_metric_is_unavailable_not_zero',after_deny['data']['roleCount'] is None)
    call(admin,BASE+'/users/'+env.username+'/roles','PUT',{'roleIds':[bootstrap,extra],'deniedCapabilityCodes':[]},headers={'If-Match':denied_binding['ETag']})
    result['stage']='restart'
    env.stop(PORT);env.start(PORT,jar=candidate);admin=login(env.username,env.password)
    _,retained,_=call(admin,STATS)
    check('task_execution_statistics_survive_restart',retained['data']['taskExecutionCount']==stats['taskExecutionCount'] and retained['data']['successfulTaskExecutionCount']==stats['successfulTaskExecutionCount'])
    result['stage']='organization_and_tenant_visibility'
    call(admin,BASE+'/data-scopes/dictionaries','PUT',{'operator':'AND','rules':[{'type':'TENANT'}]})
    _,org,_=call(admin,BASE+'/org-units','POST',{'name':'T34 scope','code':'t34-'+env.run,'unitType':'DEPARTMENT'},201)
    orgid=org['data']['id']
    admin_member=env.sql("SELECT member_id FROM summer_enterprise_member WHERE enterprise_id='ent-acme' AND subject='t34-admin'",env.schema)
    _,_,tree_h=call(admin,BASE+'/org-tree')
    call(admin,BASE+'/members/'+admin_member+'/org-assignments','PUT',{'organizationIds':[orgid],'primaryOrganizationId':orgid},headers={'If-Match':tree_h['ETag']})
    call(admin,BASE+'/context','PUT',{'organizationId':orgid});admin=login(env.username,env.password)
    call(admin,BASE+'/dictionaries','POST',{'code':'org-'+env.run,'name':'T34 organization dictionary','organizationId':orgid,'published':False,'publicReadable':False})
    call(admin,'/api/customers','POST',{'username':'org.'+env.run,'email':'org.'+env.run+'@example.test','displayName':'Organization audit'})
    _,scoped,_=call(admin,STATS);scoped=scoped['data']
    org_audits=int(env.sql("SELECT COUNT(*) FROM summer_audit_event WHERE tenant_id='ent-acme' AND organization_id='"+orgid+"'",env.schema))
    check('real_organization_filters_members_dictionaries_audit',scoped['scope']['organizationId']==orgid and scoped['userCount']==1 and scoped['dictionaryCount']==1 and scoped['auditEventCount']==org_audits and org_audits>0)
    call(admin,STATS+'?organizationId=org-engineering',expected=403)
    _,_,tree_h=call(admin,BASE+'/org-tree')
    call(admin,BASE+'/members/'+admin_member+'/org-assignments','PUT',{'organizationIds':[],'primaryOrganizationId':None},headers={'If-Match':tree_h['ETag']})
    call(admin,STATS,expected=403)
    call(admin,BASE+'/context','PUT',{'organizationId':None});admin=login(env.username,env.password)
    bind(admin,env.username,[bootstrap,extra],['enterprise.statistics.read']);call(admin,STATS,expected=403)
    bind(admin,env.username,[bootstrap,extra])
    _,enterprise,_=call(admin,'/api/v1/platform/enterprises','POST',{'name':'T34 other enterprise','code':'t34-'+env.run},201)
    eid=enterprise['data']['enterpriseId'];other='/api/v1/enterprises/'+eid;admin=login(env.username,env.password)
    newrole=grants['create_role'](admin,eid,['enterprise.statistics.read','GET:'+statsroute,'enterprise.members.read','enterprise.roles.read','enterprise.tasks.read','GET:'+taskroute,'enterprise.audit.read'],'T34 new enterprise statistics')
    assignment_status,existing,new_h=call(admin,other+'/users/'+env.username+'/roles',expected=(200,409))
    new_roles=(existing['data']['roleIds'] if assignment_status==200 else [])+[newrole]
    call(admin,other+'/users/'+env.username+'/roles','PUT',{'roleIds':new_roles,'deniedCapabilityCodes':[]},headers={'If-Match':new_h['ETag'] if assignment_status==200 else '"1"'})
    admin=login(env.username,env.password);_,other_stats,_=call(admin,other+'/statistics');other_stats=other_stats['data']
    check('new_enterprise_does_not_include_acme_tasks_or_audit',other_stats['scope']['enterpriseId']==eid and other_stats['userCount']==1 and other_stats['taskCount']==0 and other_stats['taskExecutionCount']==0 and other_stats['auditEventCount']==0)
    call(reader,other+'/statistics',expected=403)
    call(admin,BASE+'/context','PUT',{'organizationId':None});admin=login(env.username,env.password)
    call(admin,BASE+'/data-scopes/dictionaries','PUT',{'operator':'AND','rules':[{'type':'SELF'}]})
    result['stage']='real_chrome'
    node='/opt/homebrew/opt/node@22/bin/node';child={**os.environ,'SUMMER_FOUNDATION_EVIDENCE_DIR':str(OUT),'SUMMER_T34_EVIDENCE_DIR':str(OUT),'SUMMER_E2E_USERNAME':env.username,'SUMMER_E2E_PASSWORD':env.password,'SUMMER_E2E_READER':env.denied_username,'SUMMER_E2E_READER_PASSWORD':env.denied_password}
    ui=subprocess.Popen([node,str(ROOT/'scripts/acceptance/foundation/ui-server.mjs')],cwd=ROOT/'summer-ui',env=child,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
    with selectors.DefaultSelector() as selector:
        selector.register(ui.stdout,selectors.EVENT_READ);assert selector.select(timeout=45);assert ui.stdout.readline().strip()=='UI_READY'
    browser=subprocess.Popen([node,str(Path(__file__).parent/'browser.mjs')],cwd=ROOT/'summer-ui',env=child,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,start_new_session=True)
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
            elif command=='recover':env.sql("GRANT SELECT ON `"+env.schema+"`.* TO '"+env.db_user+"'@'%'")
            else:bind(admin,env.denied_username,[])
            handled.add(command);(OUT/(command+'-ready')).write_text('ready')
        time.sleep(.1)
    if browser.poll() is None:
        result['browserTimeout']=True;os.killpg(browser.pid,signal.SIGTERM);browser.wait(timeout=15)
    result['browser']=json.loads((OUT/'browser-result.json').read_text());result['browserExit']=browser.returncode
    check('response_and_log_sensitive_scan',not env.sensitive_log_detected and not any(secret and secret in json.dumps([initial,with_tasks,reader_stats,retained]) for secret in [env.password,env.denied_password,env.db_password,env.mysql,env.master,admin.token,reader.token]))
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
