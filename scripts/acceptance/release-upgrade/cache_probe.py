"""Real Redis outage: safe origin read or controlled denial, then eventual convergence."""
import json,subprocess,time,uuid
from environment import Environment,Client,OUT,immutable_candidate
from support import Evidence,mutation,snapshot,pin_inputs,inputs_stable,redis,wait
from grants import Grants,B

ev=Evidence();env=None;paused=False

def read_fault(client,path,port,expected_value):
    rid='t36-'+uuid.uuid4().hex;started=time.monotonic()
    status,body,headers=client.request(port,path,headers={'X-Request-Id':rid})
    code=(body.get('error')or{}).get('code')if isinstance(body,dict)else None
    ev.check('fault read has matching request correlation',isinstance(body,dict)and body.get('requestId')==rid and headers.get('X-Request-Id')==rid,node=port,status=status,code=code,requestId=rid,elapsedMs=round((time.monotonic()-started)*1000))
    if status==200:
        ev.check('Redis fault read returns current authorized database value',body.get('success')is True and body.get('data',{}).get('value')==expected_value,node=port)
    else:
        ev.check('Redis fault read is a controlled refusal without data',status==503 and body.get('success')is False and code=='SUMMER_DEPENDENCY_UNAVAILABLE' and body.get('data')is None,node=port,status=status,code=code)
    return status

try:
    candidate=immutable_candidate();snapshot();inputs=pin_inputs(candidate)
    env=Environment();env.setup();env.start(23081,candidate);env.start(23082,candidate);grants=Grants(env,ev).seed()
    admin=Client();admin.token=grants.admin.token
    denied=Client();denied.token=grants.denied.token
    path=B+'/system-parameters/t36-cache-fault'
    ev.call(admin,B+'/data-scopes/system-parameters','PUT',{'conditions':{'field':'TENANT'}})
    created,_=ev.call(admin,B+'/system-parameters','POST',{'key':'t36-cache-fault','type':'STRING','value':'warm-before','public':True},201)
    for port in [23081,23082]:
        data,_=ev.call(admin,path,port=port);ev.check('both nodes warm same parameter',data['value']=='warm-before',node=port)
    ev.check('real L2 entries exist before fault',bool(redis(env,'KEYS','summer:cache:entry:*')))
    ev.result['stage']='paused-redis-business-cache'
    subprocess.run(['docker','pause',env.container],check=True,stdout=subprocess.DEVNULL);paused=True
    for port in [23081,23082]:read_fault(admin,path,port,'warm-before')
    ev.call(admin,path,'PATCH',{'value':'fault-written','version':created['version']})
    read_fault(admin,path,23082,'fault-written')
    ev.call(denied,path,expected=403,port=23082,expected_code='SUMMER_FORBIDDEN')
    subprocess.run(['docker','unpause',env.container],check=True,stdout=subprocess.DEVNULL);paused=False
    ev.result['stage']='post-fault-eventual-convergence'
    for port in [23081,23082]:
        observations=[]
        def converged():
            status,body,headers=admin.request(port,path)
            matches=status==200 and body.get('data',{}).get('value')=='fault-written'
            observations.append({'status':status,'currentValue':matches,'requestId':headers.get('X-Request-Id')})
            return matches
        ev.check('fault-window write eventually converges after Redis recovery',bool(wait(converged,45)),node=port,observations=observations)
        ev.call(denied,path,expected=403,port=port,expected_code='SUMMER_FORBIDDEN')
    ev.check('no sensitive application log detected',not env.sensitive_log_detected)
    ev.check('executed backend and UI inputs remain stable',inputs_stable(inputs));ev.result['stage']='complete'
except Exception as error:
    import traceback
    ev.result['errorType']=type(error).__name__;ev.result['localFrames']=[{'file':f.filename.rsplit('/',1)[-1],'line':f.lineno}for f in traceback.extract_tb(error.__traceback__)if 'release-upgrade'in f.filename]
finally:
    if paused and env:subprocess.run(['docker','unpause',env.container],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    if env:
        (OUT/'safe-exception-classes.json').write_text(json.dumps({'classes':sorted({(x['node'],x['class'])for x in env.diagnostics}),'sensitiveLogDetected':env.sensitive_log_detected},indent=2))
        try:env.close()
        except Exception as error:ev.result['cleanupErrorType']=type(error).__name__;ev.result.setdefault('errorType',type(error).__name__)
    ev.save();print(json.dumps({'stage':ev.result['stage'],'counts':ev.result['counts'],'errorType':ev.result.get('errorType')}),flush=True)
raise SystemExit(1 if ev.result['counts']['failures']or ev.result['counts']['errors']else 0)
