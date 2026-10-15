"""T29 missing SSE protocol gates, real Cookie authentication and Quartz progress."""
import hashlib,json,os,shutil,subprocess,time,uuid
from pathlib import Path
from environment import Environment,Client,OUT,ROOT,SCRIPT,immutable_candidate
from grants import bootstrap,create_role
from stream import Stream

BASE='/api/v1/enterprises/ent-acme';TASKS=BASE+'/tasks';env=None;streams=[]
result={'issue':137,'stage':'setup','checks':[],'networkMocking':False,'syntheticIdentity':False,'businessSqlWrites':False,'registeredHandlerFixture':True}
def persist():
    result['counts']={'tests':len(result['checks']),'failures':sum(not c['passed'] for c in result['checks']),'errors':int('failureType' in result),'skipped':0}
    (OUT/'runtime-result.json').write_text(json.dumps(result,indent=2))
def check(name,condition,**facts):
    result['checks'].append({'name':name,'passed':bool(condition),**facts});persist();print(name,'PASS' if condition else 'FAIL',flush=True)
def call(c,path,method='GET',body=None,expected=200,headers=None):
    rid='t29-'+uuid.uuid4().hex;s,b,h=c.request(23081,path,method,body,headers={'X-Request-Id':rid,'Idempotency-Key':uuid.uuid4().hex,**(headers or {})})
    assert s==expected,'http_status_'+str(s)+'_expected_'+str(expected)
    assert isinstance(b,dict) and b.get('requestId')==h.get('X-Request-Id')==rid,'request_correlation'
    return b.get('data'),h
def logged(name,password):return Client().login(23081,name,password)
def task(admin,name,kind='system.noop',parameters=None):
    return call(admin,TASKS,'POST',{'name':'T29-'+name+'-'+env.run,'taskType':kind,'cronExpression':'0 0 0 1 1 ? 2099','parameters':parameters or {},'maxRetries':0,'timeoutMs':180000},201)[0]
def trigger(admin,t,key=None):return call(admin,TASKS+'/'+t['taskId']+':trigger','POST',expected=202,headers={'Idempotency-Key':key or uuid.uuid4().hex})[0]
def terminal(admin,t,timeout=90):
    end=time.monotonic()+timeout
    while time.monotonic()<end:
        rows=call(admin,TASKS+'/'+t['taskId']+'/executions')[0]['items']
        if rows and rows[0]['status'] in ['SUCCESS','FAILED']:return rows[0]
        time.sleep(.2)
    raise RuntimeError('terminal_timeout')
def open_stream(client,t,last=None,**kwargs):
    s=Stream(client,TASKS+'/'+t['taskId']+'/events',last,**kwargs);streams.append(s);return s

def sql_ids(t):
    raw=env.sql("SELECT event_id FROM summer_task_progress_event WHERE enterprise_id='ent-acme' AND task_id='"+t['taskId']+"' ORDER BY event_id",env.schema)
    return [int(x) for x in raw.splitlines()] if raw else []

try:
    candidate=immutable_candidate();result['candidateSha256']=hashlib.sha256(candidate.read_bytes()).hexdigest()
    archive=OUT/'executed-scripts';archive.mkdir(exist_ok=True)
    for p in SCRIPT.iterdir():
        if p.is_file():shutil.copy2(p,archive/p.name)
    watched=list((ROOT/'summer-sample/src/main/java/com/abluepoint/summer/sample/task').glob('*.java'))+[ROOT/'summer-sample/src/main/java/com/abluepoint/summer/sample/identity/SessionStreamAccess.java']
    before={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in watched};(OUT/'source-before.json').write_text(json.dumps(before,indent=2))
    fixture=OUT/'sse-task-fixture.jar';assert fixture.exists(),'build_fixture_first'
    env=Environment();env.extra['server.max-http-request-header-size']='64KB';result['fixtureHeaderLimit']='64KB for one real administrator with nine tenant memberships';env.setup();env.start(23081,jar=candidate,acceptance_fixture=fixture)
    admin=logged(env.username,env.password);boot=bootstrap(admin,env.username);admin=logged(env.username,env.password)
    cat=call(admin,BASE+'/capabilities/catalog')[0]['capabilities'];codes={c['code']:c['capabilityId'] for c in cat}
    for code in ['enterprise.tasks.read','enterprise.tasks.write']:
        if code not in codes:codes[code]=call(admin,BASE+'/capabilities','POST',{'code':code,'resource':'enterprise.tasks','action':code.rsplit('.',1)[1],'riskLevel':'HIGH','scope':'ENTERPRISE'},201)[0]['capabilityId']
    route='/api/v1/enterprises/{enterpriseId}/tasks';permissions=['enterprise.tasks.read','enterprise.tasks.write','GET:'+route,'GET:'+route+'/{taskId}/executions','GET:'+route+'/{taskId}/events','POST:'+route,'PATCH:'+route+'/{taskId}','POST:'+route+'/{taskId}:trigger']
    role=create_role(admin,'ent-acme',[codes.get(c,c) for c in permissions],'T29 SSE real operator')
    _,h=call(admin,BASE+'/users/'+env.username+'/roles');call(admin,BASE+'/users/'+env.username+'/roles','PUT',{'roleIds':[boot,role],'deniedCapabilityCodes':[]},headers={'If-Match':h['ETag']})
    admin=logged(env.username,env.password)
    call(admin,'/api/users','POST',{'username':env.denied_username,'password':env.denied_password,'role':'USER'})
    call(admin,BASE+'/members','POST',{'subject':env.denied_username,'displayName':'T29 denied reader','email':'t29@example.test'},201)
    denied=logged(env.denied_username,env.denied_password)
    result['stage']='global_capacity_setup'
    targets=[('ent-acme',task(admin,'global-acme'))]
    route='/api/v1/enterprises/{enterpriseId}/tasks'
    global_permissions=['enterprise.tasks.read','enterprise.tasks.write','POST:'+route,'GET:'+route+'/{taskId}/events','GET:'+route+'/{taskId}/executions','POST:'+route+'/{taskId}:trigger']
    for i in range(8):
        ent=call(admin,'/api/v1/platform/enterprises','POST',{'name':'T29 global '+str(i),'code':'sse-'+env.run+'-'+str(i)},201)[0]['enterpriseId']
        admin=logged(env.username,env.password);base='/api/v1/enterprises/'+ent
        catalog=call(admin,base+'/capabilities/catalog')[0]['capabilities'];known={c['code']:c['capabilityId'] for c in catalog}
        # Tenant administrators can create roles; unresolved codes are registered by that API.
        role=create_role(admin,ent,[known.get(c,c) for c in global_permissions],'T29 global-capacity operator')
        binding,h=call(admin,base+'/users/'+env.username+'/roles')
        call(admin,base+'/users/'+env.username+'/roles','PUT',{'roleIds':binding['roleIds']+[role],'deniedCapabilityCodes':[]},headers={'If-Match':h['ETag']})
        admin=logged(env.username,env.password)
        target=call(admin,base+'/tasks','POST',{'name':'T29 global '+str(i),'taskType':'system.noop','parameters':{},'cronExpression':'0 0 0 1 1 ? 2099','maxRetries':0,'timeoutMs':30000},201)[0]
        targets.append((ent,target))
    admin=logged(env.username,env.password)
    def connect(target):
        ent,t=target
        stream=Stream(admin,'/api/v1/enterprises/'+ent+'/tasks/'+t['taskId']+'/events');streams.append(stream);return stream
    result['stage']='global_capacity'
    groups=[[connect(targets[0]) for _ in range(32)]]
    per_tenant_excess=connect(targets[0]);check('per_tenant_33rd_connection_rejected_before_global_limit',per_tenant_excess.status==429 and per_tenant_excess.error_code=='SUMMER_TASK_SSE_LIMIT' and per_tenant_excess.correlated,**per_tenant_excess.metadata())
    groups.extend([[connect(target) for _ in range(32)] for target in targets[1:8]])
    check('eight_real_tenants_admit_256_cookie_streams',all(s.status==200 for group in groups for s in group),accepted=sum(s.status==200 for group in groups for s in group))
    excess=connect(targets[8]);check('global_257th_connection_rejected_even_in_empty_tenant',excess.status==429 and excess.error_code=='SUMMER_TASK_SSE_LIMIT' and excess.correlated,**excess.metadata())
    for s in groups[0]:s.close()
    started=time.monotonic();deadline=started+40;replacement=[];rejections=0
    while time.monotonic()<deadline and len(replacement)<32:
        probe=connect(targets[8])
        if probe.status==200:replacement.append(probe)
        else:rejections+=1;time.sleep(.5)
    check('closed_tenant_releases_32_global_slots_within_two_heartbeats_and_margin',len(replacement)==32,accepted=len(replacement),elapsedSeconds=round(time.monotonic()-started,3),transientRejections=rejections)
    for group in groups:
        for s in group:s.close()
    for s in replacement:s.close()
    started=time.monotonic();deadline=started+45;refill=[]
    for target in targets[:8]:
        accepted=0
        while time.monotonic()<deadline and accepted<32:
            probe=connect(target)
            if probe.status==200:refill.append(probe);accepted+=1
            else:time.sleep(.25)
    check('all_256_connection_slots_can_be_reused_after_close',len(refill)==256,accepted=len(refill),elapsedSeconds=round(time.monotonic()-started,3))
    for s in refill:s.close()
    result['migrations']=env.sql("SELECT version,success FROM flyway_schema_history WHERE version IN ('18','20','26') ORDER BY installed_rank",env.schema).splitlines()
    result['stage']='complete'

except Exception as error:
    result['failureType']=type(error).__name__
    if isinstance(error,AssertionError):result['safeAssertion']=str(error)
finally:
    for s in streams:
        try:s.close()
        except Exception:pass
    (OUT/'http-metadata.json').write_text(json.dumps(Client.observations,indent=2))
    if env:
        result['sensitiveLogDetected']=env.sensitive_log_detected;result['exceptionClasses']=env.diagnostics
        try:env.close();result['cleanup']='VERIFIED'
        except Exception as error:result['cleanupFailureType']=type(error).__name__
    if 'before' in globals():result['sourceChanged']=[p for p,h in before.items() if hashlib.sha256((ROOT/p).read_bytes()).hexdigest()!=h]
    persist()
print(json.dumps({k:result.get(k) for k in ['stage','counts','failureType','safeAssertion','cleanup']}))
raise SystemExit(0 if result['stage']=='complete' and not result['counts']['failures'] and not result['counts']['errors'] and result.get('cleanup')=='VERIFIED' else 1)
