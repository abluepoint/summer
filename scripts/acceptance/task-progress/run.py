"""T29 missing SSE protocol gates, real Cookie authentication and Quartz progress."""
import argparse,hashlib,json,os,shutil,subprocess,time,uuid
from pathlib import Path
from environment import Environment,Client,OUT,ROOT,SCRIPT,immutable_candidate
from grants import bootstrap,create_role
from stream import Stream

parser=argparse.ArgumentParser();parser.add_argument('--protocol-only',action='store_true');options=parser.parse_args()

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
    result['stage']='cookie_and_protocol';t=task(admin,'replay','system.sleep',{'millis':2000});path=TASKS+'/'+t['taskId']+'/events'
    s=open_stream(admin,t);check('real_cookie_sse_raw_correlated',s.status==200 and s.cookie_present and s.correlated and s.content_type.startswith('text/event-stream') and s.wait(lambda x:any(e.get('type')=='connected' for e in x.events)),**s.metadata())
    noauth=open_stream(Client(),t);check('anonymous_cannot_subscribe',noauth.status in [401,403],**noauth.metadata())
    disallowed=open_stream(denied,t);check('real_member_without_read_capability_denied',disallowed.status==403,**disallowed.metadata())
    foreign=call(admin,'/api/v1/platform/enterprises','POST',{'name':'T29 foreign tenant','code':'sse-'+env.run},201)[0]
    crossed=Stream(denied,'/api/v1/enterprises/'+foreign['enterpriseId']+'/tasks/'+t['taskId']+'/events');streams.append(crossed)
    check('real_foreign_tenant_cookie_denied',crossed.status in [403,404],**crossed.metadata())
    for cursor in ['-1','invalid','9223372036854775808']:
        invalid=open_stream(admin,t,cursor);check('invalid_cursor_'+cursor,invalid.status==400 and invalid.error_code=='SUMMER_TASK_SSE_LAST_EVENT_ID' and invalid.correlated,**invalid.metadata())
    key='t29-replay-'+env.run;trigger(admin,t,key);execution=terminal(admin,t)
    expected=sql_ids(t);s.wait(lambda x:len([e for e in x.events if 'id' in e])>=len(expected))
    received=[e['id'] for e in s.events if 'id' in e]
    check('live_quartz_events_match_persisted_order',execution['status']=='SUCCESS' and received==expected and len(set(received))==len(received),eventCount=len(received))
    check('actual_progress_and_safe_log_events',any(e.get('type')=='execution.progress' for e in s.events) and any(e.get('type')=='execution.log' and e['data'].get('message')=='Task execution succeeded' for e in s.events))
    cursor=received[-4];s.close();resumed=open_stream(admin,t,cursor)
    remaining=[i for i in expected if i>cursor];resumed.wait(lambda x:len([e for e in x.events if 'id' in e])>=len(remaining))
    check('last_event_id_replays_only_missing_events',[e['id'] for e in resumed.events if 'id' in e]==remaining,cursor=cursor,replayedIds=[e['id'] for e in resumed.events if 'id' in e])
    heartbeat_start=time.monotonic();heartbeat=resumed.wait(lambda x:bool(x.comments),18)
    check('real_idle_heartbeat_within_eighteen_seconds',heartbeat and resumed.comments[0]['comment']=='keep-alive',elapsedSeconds=round(time.monotonic()-heartbeat_start,3))
    rows=call(admin,TASKS+'/'+t['taskId']+'/executions')[0]['items'];check('reconnect_does_not_repeat_business_execution',len(rows)==1 and rows[0]==execution and sql_ids(t)==expected)
    resumed.close();result['stage']='retained_window'
    burst=task(admin,'window','acceptance.sse-burst',{'count':700});trigger(admin,burst);burst_execution=terminal(admin,burst);ids=sql_ids(burst)
    check('window_generated_by_actual_quartz_handler',burst_execution['status']=='SUCCESS' and len(ids)>500,persistedEvents=len(ids))
    window=open_stream(admin,burst);window.wait(lambda x:len([e for e in x.events if 'id' in e])>=500)
    check('initial_replay_is_bounded_to_latest_500',[e['id'] for e in window.events if 'id' in e]==ids[-500:]);window.close()
    stale=open_stream(admin,burst,ids[0]);stale.wait(lambda x:any(e.get('type')=='resync' for e in x.events))
    rs=[e for e in stale.events if e.get('type')=='resync'];check('expired_window_requires_resync_without_old_event_replay',len(rs)==1 and rs[0]['data'].get('reason')=='EVENT_WINDOW_EXCEEDED' and rs[0]['data'].get('latestEventId')==ids[-1] and not any('id' in e for e in stale.events));stale.close()
    future=open_stream(admin,burst,ids[-1]+1000);future.wait(lambda x:any(e.get('type')=='resync' for e in x.events));check('future_cursor_returns_explicit_resync',any(e.get('type')=='resync' for e in future.events));future.close()
    inwindow=open_stream(admin,burst,ids[-6]);inwindow.wait(lambda x:len([e for e in x.events if 'id' in e])>=5)
    check('within_window_replay_uses_last_event_id',[e['id'] for e in inwindow.events if 'id' in e]==ids[-5:]);inwindow.close()
    if not options.protocol_only:
        result['stage']='connection_capacity'
        idle=task(admin,'capacity');time.sleep(1)
        deadline=time.monotonic()+40;slots=[]
        while time.monotonic()<deadline and len(slots)<32:
            probe=open_stream(admin,idle)
            if probe.status==200:slots.append(probe)
            else:time.sleep(.5)
        check('thirty_two_real_cookie_connections_open',len(slots)==32)
        excess=open_stream(admin,idle);check('connection_33_rejected_with_stable_correlated_error',excess.status==429 and excess.error_code=='SUMMER_TASK_SSE_LIMIT' and excess.correlated,**excess.metadata())
        slots[0].close();trigger(admin,idle);terminal(admin,idle)
        deadline=time.monotonic()+40;replacement=None
        while time.monotonic()<deadline:
            probe=open_stream(admin,idle)
            if probe.status==200:replacement=probe;break
            time.sleep(.5)
        check('disconnected_socket_releases_connection_slot',replacement is not None)
        for x in slots:x.close()
        if replacement:replacement.close()
        trigger(admin,idle);terminal(admin,idle);time.sleep(1)
        deadline=time.monotonic()+40;refill=[]
        while time.monotonic()<deadline and len(refill)<32:
            probe=open_stream(admin,idle)
            if probe.status==200:refill.append(probe)
            else:time.sleep(.5)
        check('all_closed_connections_release_all_thirty_two_slots',len(refill)==32)
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
