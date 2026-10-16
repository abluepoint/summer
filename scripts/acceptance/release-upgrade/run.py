"""T36 native release rehearsal. Requires pinned old/new artifacts and an unused evidence directory."""
import concurrent.futures,hashlib,json,os,selectors,subprocess,threading,time,uuid,zipfile
from environment import Environment,Client,ROOT,OUT,SCRIPT,immutable_candidate
from support import Evidence,mutation,wait,redis,snapshot,pin_old_candidate
from grants import Grants,B
from support import pin_inputs,inputs_stable,SseStream
from archive import backup_restore

ev=Evidence();env=None;ui=browser=None;paused=False;encrypted=None;streams=[]

def grant_state(grants,denies):grants.bind(env.username,[grants.role],denies)
def run_browser(grants):
    global ui,browser,paused
    child=dict(os.environ,SUMMER_E2E_USERNAME=env.username,SUMMER_E2E_PASSWORD=env.password,RELEASE_UPGRADE_VITE_CACHE=str(OUT/'vite-cache'))
    ui=subprocess.Popen(['/opt/homebrew/opt/node@22/bin/node',str(SCRIPT/'ui-server.mjs')],cwd=ROOT/'summer-ui',env=child,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
    selector=selectors.DefaultSelector();selector.register(ui.stdout,selectors.EVENT_READ)
    if not selector.select(45)or ui.stdout.readline().strip()!='UI_READY':raise RuntimeError('ui_startup_failed')
    selector.close();browser=subprocess.Popen(['/opt/homebrew/opt/node@22/bin/node',str(SCRIPT/'browser.mjs')],cwd=ROOT/'summer-ui',env=child,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
    selector=selectors.DefaultSelector();selector.register(browser.stdout,selectors.EVENT_READ);deadline=time.monotonic()+300
    while selector.get_map():
        if time.monotonic()>deadline:raise TimeoutError('browser_deadline')
        for event,_ in selector.select(.5):
            line=event.fileobj.readline()
            if not line:selector.unregister(event.fileobj);continue
            command=line.strip()
            if command=='PAUSE_REDIS':subprocess.run(['docker','pause',env.container],check=True,stdout=subprocess.DEVNULL);paused=True
            elif command=='RESUME_REDIS':subprocess.run(['docker','unpause',env.container],check=True,stdout=subprocess.DEVNULL);paused=False
            elif command=='DENY_RELEASE':grant_state(grants,['internal.release.read'])
            elif command=='RESTORE_RELEASE':grant_state(grants,[])
            else:raise RuntimeError('unexpected_browser_signal')
            browser.stdin.write('GO\n');browser.stdin.flush()
    selector.close();browser.wait(timeout=15)
    result=json.loads((OUT/'browser-result.json').read_text());ev.result['browser']=result
    ev.check('native Chrome UI and realtime scenarios',browser.returncode==0,counts={k:result[k]for k in ['tests','failures','errors','skipped']})
    browser=None;ui.terminate();ui.wait(timeout=15);ui=None

def plugins(client):
    path='/api/v1/platform/plugins';item=path+'/customer-insights'
    ev.call(client,path,'POST',{'pluginId':'customer-insights','version':'1.0.0','artifactDigest':'sha256:untrusted'},409)
    ev.call(client,path,'POST',{'pluginId':'customer-insights','version':'1.0.0','artifactDigest':'sha256:customer-insights-v1'},201)
    ev.call(client,item+':enable','POST')
    incompatible,_=ev.call(client,item+':upgrade','POST',{'version':'9.0.0','artifactDigest':'sha256:customer-insights-v1'})
    ev.check('plugin incompatible version diagnosed',incompatible['status']=='INCOMPATIBLE')
    ev.call(client,item+':enable','POST',expected=409)
    ev.call(client,item+':upgrade','POST',{'version':'1.0.0','artifactDigest':'sha256:customer-insights-v1'})
    ev.call(client,item+':enable','POST')
    diagnostic,_=ev.call(client,item+'/diagnostic',port=23082);ev.check('compatible plugin visible on other node',diagnostic['compatible'])

try:
    candidate=immutable_candidate();old=pin_old_candidate();snapshot();inputs=pin_inputs(candidate)
    ev.result['candidateSha256']=hashlib.sha256(candidate.read_bytes()).hexdigest();ev.result['oldSha256']=hashlib.sha256(old.read_bytes()).hexdigest()
    with zipfile.ZipFile(old)as a,zipfile.ZipFile(candidate)as b:
        changed=[name for name in a.namelist()if name.startswith('BOOT-INF/classes/')and name.endswith('.class')and name in b.namelist()and a.read(name)!=b.read(name)]
    ev.check('rolling artifacts contain different application bytecode',bool(changed),changedClassCount=len(changed))
    env=Environment();env.setup();env.start(23081,old);env.start(23082,candidate);grants=Grants(env,ev).seed();admin=grants.admin
    ev.result['stage']='mixed-version-prerequisites'
    expected={'database','migrations','configuration','keys','authorization','tasks','cache','events','realtime','plugins'}
    for port in [23081,23082]:
        data,_=ev.call(admin,'/internal/v1/release/readiness',port=port)
        ev.check('ten live prerequisites pass',data['status']=='READY'and{name['name']for name in data['checks']}==expected and all(check['passed']for check in data['checks']),node=port)
        ev.call(grants.denied,'/internal/v1/release/readiness',expected=403,port=port)
    history=int(env.sql('SELECT COUNT(*) FROM flyway_schema_history WHERE success=1',env.schema));ev.check('all current migration files applied',history==39,applied=history)
    ev.call(admin,'/internal/v1/release/gates:validate','POST',{'releaseVersion':'t36-final','dryRun':False},400,mutation(),23082)
    plan_headers=mutation()
    planned,_=ev.call(admin,'/internal/v1/release/gates:validate','POST',{'releaseVersion':'t36-final','dryRun':True},headers=plan_headers,port=23082)
    ev.call(admin,'/internal/v1/release/gates:validate','POST',{'releaseVersion':'different-release','dryRun':True},expected=409,headers=plan_headers,port=23082,expected_code='SUMMER_CONFLICT')
    ev.check('unexecuted delivery gates never reported passed',planned['decision']=='NOT_VERIFIED'and all(not g['executed']and not g['passed']for g in planned['gates']))
    ev.result['stage']='mixed-version-session-cache-events'
    for port in [23081,23082]:ev.call(admin,'/api/v1/session',port=port,bearer=False)
    ev.call(admin,B+'/data-scopes/system-parameters','PUT',{'conditions':{'field':'TENANT'}})
    path=B+'/system-parameters/t36-release-value';created,_=ev.call(admin,B+'/system-parameters','POST',{'key':'t36-release-value','type':'STRING','value':'before','public':True},201)
    for port in [23081,23082]:ev.call(admin,path,port=port)
    ev.call(admin,path,'PATCH',{'value':'mixed-after','version':created['version']})
    data,_=ev.call(admin,path,port=23082);ev.check('mixed-version cache invalidation reaches other node',data['value']=='mixed-after')
    ev.check('real L2 entries exist',bool(redis(env,'KEYS','summer:cache:entry:*')))
    grants.bind(env.denied_username,[],['internal.release.read'])
    def groups_ready():
        groups=redis(env,'XINFO','GROUPS','summer:authorization-change-events');groups=[dict(zip(g[::2],g[1::2]))for g in groups]
        return groups if len(groups)>=2 and all(g.get('pending')==0 and g.get('lag')==0 for g in groups)else None
    groups=wait(groups_ready,40);ev.check('mixed nodes consume authorization events',bool(groups),groups=[{k:g.get(k)for k in ['name','pending','lag']}for g in groups or []])
    before=env.sql("SELECT COUNT(*) FROM summer_authorization_change_receipt WHERE status='PROCESSED'",env.schema)
    last=redis(env,'XREVRANGE','summer:authorization-change-events','+','-','COUNT',1);ev.check('published event available for duplicate exercise',bool(last))
    redis(env,'XADD','summer:authorization-change-events','*',*last[0][1]);ev.check('duplicate event drains on both nodes',bool(wait(groups_ready,40)))
    ev.check('duplicate does not add a processed receipt',env.sql("SELECT COUNT(*) FROM summer_authorization_change_receipt WHERE status='PROCESSED'",env.schema)==before)
    ev.result['stage']='mixed-version-tasks-and-keys'
    task,_=ev.call(admin,B+'/tasks','POST',{'name':'T36 cross-version once','taskType':'system.noop','cronExpression':'0 0 0 1 1 ? 2099','parameters':{},'maxRetries':0},201)
    taskpath=B+'/tasks/'+task['taskId'];key='cross-version-'+env.run
    streams=[SseStream(port,taskpath+'/events',admin.token)for port in [23081,23082]]
    def trigger(port):
        client=Client();client.token=admin.token
        return client.request(port,taskpath+':trigger','POST',headers={'Idempotency-Key':key})[0]
    with concurrent.futures.ThreadPoolExecutor(2)as pool:statuses=list(pool.map(trigger,[23081,23082]))
    ev.check('both nodes accept same task key',statuses==[202,202],statuses=statuses)
    def complete():
        s,b,_=admin.request(23082,taskpath+'/executions');items=b.get('data',{}).get('items',[])if isinstance(b,dict)else[]
        return items if s==200 and len(items)==1 and items[0]['status']=='SUCCESS'else None
    ev.check('one cross-node execution completes',bool(wait(complete,30)))
    ev.check('mixed-version task SSE reaches both nodes',bool(wait(lambda:all(any(e.get('event')=='execution.succeeded'and task['taskId']in e.get('data','')for e in stream.events)for stream in streams),30)))
    ids=[int(e['id'])for e in streams[1].events if 'id'in e]
    ev.check('task SSE exposes replay cursors',len(ids)>=2)
    replay=SseStream(23082,taskpath+'/events',admin.token,ids[-2]);streams.append(replay)
    ev.check('SSE Last-Event-ID replays later event',bool(wait(lambda:any(int(e.get('id','0'))==ids[-1]for e in replay.events),15)))
    for stream in streams:stream.close()
    streams=[]
    ev.call(admin,B+'/tasks','POST',{'name':'Invalid task','taskType':'untrusted.task','cronExpression':'0 0 0 1 1 ? 2099','parameters':{}},400)
    _,firstA,_=admin.request(23081,'/oauth2/jwks');_,firstB,_=admin.request(23082,'/oauth2/jwks');ev.check('nodes share public signing keys',firstA==firstB)
    _,headers=ev.call(admin,'/api/security/keys');ev.call(admin,'/api/security/keys:rotate','POST',{'retentionDays':1},headers=mutation(headers['ETag']))
    _,keysA,_=admin.request(23081,'/oauth2/jwks');_,keysB,_=admin.request(23082,'/oauth2/jwks');ev.check('active and grace public keys match',keysA==keysB and len(keysA['keys'])==2)
    plugins(admin)
    ev.result['stage']='rolling-upgrade'
    statuses=[];stop=threading.Event()
    def continuous_read():
        client=Client();client.token=admin.token
        while not stop.is_set():
            try:statuses.append(client.request(23082,path)[0])
            except Exception:statuses.append(0)
            stop.wait(.15)
    reader=threading.Thread(target=continuous_read,daemon=True);reader.start()
    try:env.stop(23081);env.start(23081,candidate)
    finally:stop.set();reader.join(timeout=10)
    ev.check('surviving node serves throughout rolling restart',bool(statuses)and all(s==200 for s in statuses),requests=len(statuses),non200=sum(s!=200 for s in statuses))
    for port in [23081,23082]:
        ev.call(admin,path,port=port);ev.call(admin,'/api/v1/session',port=port,bearer=False)
    grant_state(grants,['internal.release.read'])
    for port in [23081,23082]:ev.call(admin,'/internal/v1/release/readiness',expected=403,port=port)
    grant_state(grants,[])
    for port in [23081,23082]:ev.call(admin,'/internal/v1/release/readiness',port=port)
    ev.result['stage']='backup-and-restore'
    encrypted=backup_restore(env,ev,admin,candidate,path)
    restored=Client().login(23083,env.username,env.password);ev.call(restored,'/internal/v1/release/readiness',port=23083)
    denied_restore=Client().login(23083,env.denied_username,env.denied_password);ev.call(denied_restore,'/internal/v1/release/readiness',expected=403,port=23083)
    _,restoredKeys,_=restored.request(23083,'/oauth2/jwks');ev.check('restored signing keys retain public identity',restoredKeys==keysA)
    env.stop(23083)
    ev.result['stage']='native-ui-and-realtime';run_browser(grants)
    ev.check('no sensitive application log detected',not env.sensitive_log_detected)
    ev.check('executed backend and UI inputs remain stable',inputs_stable(inputs))
    ev.result['stage']='complete'
except Exception as error:
    ev.result['errorType']=type(error).__name__;ev.result['localFrames']=[]
    import traceback
    for frame in traceback.extract_tb(error.__traceback__):
        if str(SCRIPT)in frame.filename:ev.result['localFrames'].append({'file':frame.filename.rsplit('/',1)[-1],'line':frame.lineno})
finally:
    for stream in streams:stream.close()
    if paused and env:subprocess.run(['docker','unpause',env.container],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    for process in [browser,ui]:
        if process and process.poll()is None:
            process.terminate()
            try:process.wait(timeout=15)
            except subprocess.TimeoutExpired:process.kill();process.wait()
    if env:
        try:env.close()
        except Exception as error:ev.result['cleanupErrorType']=type(error).__name__;ev.result.setdefault('errorType',type(error).__name__)
    encrypted=OUT/'recovery.sql.enc'
    if encrypted.exists():encrypted.unlink()
    ev.save();print(json.dumps({'stage':ev.result['stage'],'counts':ev.result['counts'],'errorType':ev.result.get('errorType')}),flush=True)

raise SystemExit(1 if ev.result['counts']['failures'] or ev.result['counts']['errors'] else 0)
