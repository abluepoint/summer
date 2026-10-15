"""Real slow TCP readers; separate from the already-green protocol matrix."""
import hashlib,json,shutil,subprocess,time,uuid
from environment import Environment,Client,OUT,ROOT,SCRIPT,JAVA,immutable_candidate
from grants import bootstrap,create_role
from stream import Stream
BASE='/api/v1/enterprises/ent-acme';TASKS=BASE+'/tasks';env=None;streams=[]
result={'issue':137,'mode':'actual_tcp_backpressure','stage':'setup','checks':[],'businessSqlWrites':False,'syntheticIdentity':False}
def check(name,condition,**facts):
    result['checks'].append({'name':name,'passed':bool(condition),**facts});persist();print(name,'PASS' if condition else 'FAIL',flush=True)
def persist():
    result['counts']={'tests':len(result['checks']),'failures':sum(not c['passed'] for c in result['checks']),'errors':int('failureType' in result),'skipped':0};(OUT/'runtime-result.json').write_text(json.dumps(result,indent=2))
def call(c,path,method='GET',body=None,expected=200,headers=None):
    s,b,h=c.request(23081,path,method,body,headers={'X-Request-Id':'t29-'+uuid.uuid4().hex,'Idempotency-Key':uuid.uuid4().hex,**(headers or {})});assert s==expected,'status_'+str(s);return b['data'],h
def task(c,name,kind,params):return call(c,TASKS,'POST',{'name':'T29-'+name+'-'+env.run,'taskType':kind,'parameters':params,'cronExpression':'0 0 0 1 1 ? 2099','maxRetries':0,'timeoutMs':180000},201)[0]
def stream(c,t,**kw):
    s=Stream(c,TASKS+'/'+t['taskId']+'/events',**kw);streams.append(s);return s
def terminal(c,t,timeout=15):
    end=time.monotonic()+timeout
    while time.monotonic()<end:
        rows=call(c,TASKS+'/'+t['taskId']+'/executions')[0]['items']
        if rows and rows[0]['status'] in ['SUCCESS','FAILED']:return rows[0]
        time.sleep(.2)
    return None

def delivery_states():
    # Read only class/method frames for our four named worker threads; no JVM properties or env.
    text=subprocess.check_output([str(JAVA.with_name('jcmd')),str(env.nodes[23081].pid),'Thread.print'],text=True,timeout=10)
    found=[]
    for section in text.split('\n\n'):
        if section.startswith('"summer-task-sse-delivery'):
            frames=[line.strip() for line in section.splitlines() if line.strip().startswith(('java.lang.Thread.State:','at java.base/sun.nio','at org.apache.tomcat.util.net.','at org.springframework.web.servlet.mvc.method.annotation.','at com.abluepoint.summer.sample.task.TaskProgressEventService.'))]
            found.append({'activeDelivery':any('TaskProgressEventService.lambda$pollCommitted' in line for line in frames),'socketWriteBlocked':any('doWrite' in line or 'writeBlocking' in line for line in frames),'frames':frames})
    return found
try:
    candidate=immutable_candidate();result['candidateSha256']=hashlib.sha256(candidate.read_bytes()).hexdigest()
    archive=OUT/'executed-scripts';archive.mkdir(exist_ok=True)
    for p in SCRIPT.iterdir():
        if p.is_file():shutil.copy2(p,archive/p.name)
    fixture=OUT/'sse-task-fixture.jar';assert fixture.exists(),'build_fixture_first'
    env=Environment();env.setup();env.start(23081,jar=candidate,acceptance_fixture=fixture)
    admin=Client().login(23081,env.username,env.password);boot=bootstrap(admin,env.username);admin=Client().login(23081,env.username,env.password)
    catalog=call(admin,BASE+'/capabilities/catalog')[0]['capabilities'];codes={c['code']:c['capabilityId'] for c in catalog}
    code='enterprise.tasks.write'
    if code not in codes:codes[code]=call(admin,BASE+'/capabilities','POST',{'code':code,'resource':'enterprise.tasks','action':'write','riskLevel':'HIGH','scope':'ENTERPRISE'},201)[0]['capabilityId']
    route='/api/v1/enterprises/{enterpriseId}/tasks';caps=['enterprise.tasks.read','enterprise.tasks.write','POST:'+route,'GET:'+route+'/{taskId}/events','GET:'+route+'/{taskId}/executions','POST:'+route+'/{taskId}:trigger']
    role=create_role(admin,'ent-acme',[codes.get(c,c) for c in caps],'T29 backpressure operator');_,h=call(admin,BASE+'/users/'+env.username+'/roles');call(admin,BASE+'/users/'+env.username+'/roles','PUT',{'roleIds':[boot,role],'deniedCapabilityCodes':[]},headers={'If-Match':h['ETag']});admin=Client().login(23081,env.username,env.password)
    burst=task(admin,'slow-tcp','acceptance.sse-burst',{'count':20000});control=task(admin,'control','system.noop',{})
    slow=[stream(admin,burst,read=False,receive_buffer=1024) for _ in range(4)]
    check('four_actual_unread_cookie_tcp_streams',all(s.status==200 and s.cookie_present for s in slow))
    call(admin,TASKS+'/'+burst['taskId']+':trigger','POST',expected=202);result['stage']='saturating'
    deadline=time.monotonic()+100;blocked=False;observed=[]
    while time.monotonic()<deadline:
        time.sleep(2);states=delivery_states();observed.append({'elapsed':round(100-(deadline-time.monotonic()),2),'blockedWorkers':sum(s['socketWriteBlocked'] for s in states)})
        if sum(s['socketWriteBlocked'] for s in states)==4:
            blocked=True;result['blockedThreadFrames']=states;break
    result['saturationObservations']=observed
    check('real_socket_write_backpressure_observed',blocked,blockedWorkers=observed[-1]['blockedWorkers'] if observed else 0)
    if not blocked:raise RuntimeError('fixture_did_not_prove_saturation')
    healthy=stream(admin,control);assert healthy.status==200,'healthy_stream_open'
    started=time.monotonic();call(admin,TASKS+'/'+control['taskId']+':trigger','POST',expected=202);finished=terminal(admin,control,8)
    check('slow_readers_do_not_block_quartz_database_completion',finished is not None and finished['status']=='SUCCESS',elapsedSeconds=round(time.monotonic()-started,3))
    delivered=healthy.wait(lambda s:any(e.get('type')=='execution.succeeded' for e in s.events),5)
    check('healthy_stream_progress_survives_four_slow_readers',delivered,executionEvents=[e.get('type') for e in healthy.events if e.get('type','').startswith('execution.')])
    for s in slow:s.close()
    recovered=healthy.wait(lambda s:any(e.get('type')=='execution.succeeded' for e in s.events),8)
    check('delivery_recovers_when_slow_sockets_close',recovered)
    healthy.close()
    deadline=time.monotonic()+8;states=[]
    while time.monotonic()<deadline:
        states=delivery_states()
        if not any(x['socketWriteBlocked'] or x['activeDelivery'] for x in states):break
        time.sleep(.3)
    check('closed_streams_release_all_active_delivery_calls',not any(x['socketWriteBlocked'] or x['activeDelivery'] for x in states),activeCalls=sum(x['activeDelivery'] for x in states),blockedWorkers=sum(x['socketWriteBlocked'] for x in states))
    ended=terminal(admin,burst,90);check('burst_handler_completes_after_socket_release',ended is not None and ended['status']=='SUCCESS')
    deadline=time.monotonic()+35
    while time.monotonic()<deadline:
        states=delivery_states()
        if len(states)<=4:break
        time.sleep(1)
    check('extra_delivery_threads_retire_to_core_bound',len(states)<=4,remainingWorkers=len(states))
    result['stage']='complete'
except Exception as error:
    result['failureType']=type(error).__name__
    if isinstance(error,AssertionError):result['safeAssertion']=str(error)
finally:
    for s in streams:
        try:s.close()
        except Exception:pass
    if env:
        result['sensitiveLogDetected']=env.sensitive_log_detected
        try:env.close();result['cleanup']='VERIFIED'
        except Exception as error:result['cleanupErrorType']=type(error).__name__
    (OUT/'http-metadata.json').write_text(json.dumps(Client.observations,indent=2));persist()
print(json.dumps({k:result.get(k) for k in ['stage','counts','failureType','cleanup']}))
raise SystemExit(0 if result['stage']=='complete' and not result['counts']['failures'] and not result['counts']['errors'] and result.get('cleanup')=='VERIFIED' else 1)
