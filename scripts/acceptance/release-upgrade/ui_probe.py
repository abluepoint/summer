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
    readiness='/internal/v1/release/readiness'
    bearer=Client();bearer.token=grants.admin.token
    for port in [23081,23082]:ev.call(grants.admin,readiness,port=port,bearer=False)
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
            if command=='PAUSE_REDIS':
                subprocess.run(['docker','pause',env.container],check=True,stdout=subprocess.DEVNULL);paused=True
                for port in [23081,23082]:
                    started=time.monotonic();status,body,headers=grants.admin.request(port,readiness,bearer=False)
                    ev.check('pure Cookie Redis timeout is controlled503',status==503 and body.get('success')is False and(body.get('error')or{}).get('code')=='SUMMER_SESSION_STORE_UNAVAILABLE' and body.get('data')is None,node=port,status=status,success=body.get('success'),code=(body.get('error')or{}).get('code'),dataAbsent=body.get('data')is None,elapsedMs=round((time.monotonic()-started)*1000))
                    ev.check('Cookie timeout has no-store and matching correlation','no-store'in headers.get('Cache-Control','')and bool(body.get('requestId'))and body['requestId']==headers.get('X-Request-Id'),node=port,cacheControl=headers.get('Cache-Control'),requestId=body.get('requestId'),responseRequestId=headers.get('X-Request-Id'))
                    data,_=ev.call(bearer,readiness,port=port)
                    ev.check('pure Bearer remains independent of session Redis timeout',data['status']=='NOT_READY'and all(not next(c['passed']for c in data['checks']if c['name']==name)for name in ['cache','events','realtime']),node=port)

            elif command=='RESUME_REDIS':
                subprocess.run(['docker','unpause',env.container],check=True,stdout=subprocess.DEVNULL);paused=False
                for port in [23081,23082]:
                    for client,cookie in [(grants.admin,True),(bearer,False)]:
                        data,_=ev.call(client,readiness,port=port,bearer=not cookie)
                        ev.check('Cookie and Bearer readiness recover after Redis resume',data['status']=='READY',node=port,cookie=cookie)

            elif command=='DENY_RELEASE':grant_state(grants,['internal.release.read'])
            elif command=='RESTORE_RELEASE':grant_state(grants,[])
            else:raise RuntimeError('unexpected_browser_signal')
            browser.stdin.write('GO\n');browser.stdin.flush()
    selector.close();browser.wait(timeout=15)
    result=json.loads((OUT/'browser-result.json').read_text());ev.result['browser']=result
    ev.check('native Chrome UI and realtime scenarios',browser.returncode==0,counts={k:result[k]for k in ['tests','failures','errors','skipped']})
    browser=None;ui.terminate();ui.wait(timeout=15);ui=None

try:
    candidate=immutable_candidate();snapshot();inputs=pin_inputs(candidate)
    env=Environment();env.setup();env.start(23081,candidate);env.start(23082,candidate);grants=Grants(env,ev).seed()
    ev.result['stage']='native-ui-and-realtime-supplement';run_browser(grants)
    ev.check('no sensitive application log detected',not env.sensitive_log_detected)
    ev.check('executed backend and UI inputs remain stable',inputs_stable(inputs));ev.result['stage']='complete'
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
        (OUT/'safe-exception-classes.json').write_text(json.dumps({'classes':sorted({(x['node'],x['class'])for x in env.diagnostics}),'sensitiveLogDetected':env.sensitive_log_detected},indent=2))
        try:env.close()
        except Exception as error:ev.result['cleanupErrorType']=type(error).__name__;ev.result.setdefault('errorType',type(error).__name__)
    encrypted=OUT/'recovery.sql.enc'
    if encrypted.exists():encrypted.unlink()
    ev.save();print(json.dumps({'stage':ev.result['stage'],'counts':ev.result['counts'],'errorType':ev.result.get('errorType')}),flush=True)

raise SystemExit(1 if ev.result['counts']['failures'] or ev.result['counts']['errors'] else 0)
