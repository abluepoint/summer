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
    env=Environment();env.setup();env.start(23081,jar=candidate,acceptance_fixture=fixture)
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
    result['stage']='connection_capacity'
    idle=task(admin,'capacity')
    slots=[open_stream(admin,idle) for _ in range(32)]
    check('fresh_process_accepts_thirty_two_real_cookie_connections',all(x.status==200 for x in slots),statuses=[x.status for x in slots])
    excess=open_stream(admin,idle)
    check('connection_33_rejected_with_stable_correlated_error',excess.status==429 and excess.error_code=='SUMMER_TASK_SSE_LIMIT' and excess.correlated,**excess.metadata())
    slots[0].close();started=time.monotonic();trigger(admin,idle);terminal(admin,idle)
    deadline=started+40;replacement=None
    while time.monotonic()<deadline:
        probe=open_stream(admin,idle)
        if probe.status==200:replacement=probe;break
        time.sleep(.5)
    check('closed_socket_slot_released_within_two_heartbeats_and_margin',replacement is not None,elapsedSeconds=round(time.monotonic()-started,3))
    for x in slots:x.close()
    if replacement:replacement.close()
    started=time.monotonic();deadline=started+40;refill=[];rejections=0
    while time.monotonic()<deadline and len(refill)<32:
        probe=open_stream(admin,idle)
        if probe.status==200:refill.append(probe)
        else:rejections+=1;time.sleep(.5)
    check('all_thirty_two_slots_released_within_two_heartbeats_and_margin',len(refill)==32,accepted=len(refill),transientRejections=rejections,elapsedSeconds=round(time.monotonic()-started,3))
    for x in refill:x.close()
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
