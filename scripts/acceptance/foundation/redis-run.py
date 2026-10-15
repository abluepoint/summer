import os,json,subprocess,socket,threading,time,secrets,urllib.request,urllib.error,hashlib
from environment import Environment,Client,OUT,ROOT,SCRIPT,immutable_candidate
from strict_fixtures import Fixtures

env=None;ui=None;browser=None;container=None;blackhole=None;connections=[];result={'stage':'setup','cases':[]};candidate=immutable_candidate()
def line(process,expected):
 text=process.stdout.readline().strip()
 if text!=expected:
  result['browser_exit_code']=process.poll();result['control_received']=text if text in ['BROWSER_FAILED','CASE_OK','BROWSER_READY',''] else 'unexpected_nonprotocol_output'
  raise AssertionError('browser_control_'+expected)
def redis_secret(container,password):
 def resp(*args):return b'*'+str(len(args)).encode()+b'\r\n'+b''.join(b'$'+str(len(a.encode())).encode()+b'\r\n'+a.encode()+b'\r\n' for a in args)
 r=subprocess.run(['docker','exec','-i',container,'redis-cli','--pipe'],input=resp('CONFIG','SET','requirepass',password)+resp('AUTH','default',password),capture_output=True)
 assert r.returncode==0 and b'errors: 0' in r.stdout,'isolated_redis_configuration'
try:
 env=Environment();env.setup();result['candidate_sha256']=hashlib.sha256(candidate.read_bytes()).hexdigest();env.start(20081,jar=candidate);f=Fixtures(env).seed()
 credentials=os.environ.copy();credentials.update({'SUMMER_E2E_USERNAME':env.username,'SUMMER_E2E_PASSWORD':env.password,'SUMMER_REDIS_SECRET':env.redis})
 ui=subprocess.Popen([os.environ.get('SUMMER_FOUNDATION_NODE','/opt/homebrew/opt/node@22/bin/node'),str(SCRIPT/'ui-server.mjs')],cwd=ROOT/'summer-ui',env=credentials,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True);line(ui,'UI_READY')
 browser=subprocess.Popen([os.environ.get('SUMMER_FOUNDATION_NODE','/opt/homebrew/opt/node@22/bin/node'),str(SCRIPT/'redis-browser.mjs')],cwd=ROOT/'summer-ui',env=credentials,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True);line(browser,'BROWSER_READY')
 def capture(status,label):
  browser.stdin.write(json.dumps({'status':status,'label':label})+'\n');browser.stdin.flush();line(browser,'CASE_OK');result['cases'].append(status)
  health_status,health,_=Client().request(20081,'/actuator/health');encoded=json.dumps(health)
  safe=not any(value in encoded for value in [env.mysql,env.redis,env.master,env.password,env.db_password]) and not any(value in encoded.lower() for value in ['redis://','jdbc:mysql','password','127.0.0.1'])
  result.setdefault('health',[]).append({'http_status':health_status,'fields':sorted(health or {}),'secret_scan_passed':safe})
  assert health_status in [200,503] and safe and set(health or {})<={'status','groups'},'health_redaction_failed'
  (OUT/'redis-runtime-result.json').write_text(json.dumps(result,indent=2));print('real_browser_redis',status,flush=True)
 capture('UP','正常')
 container='summer-foundation-fault-'+env.run;subprocess.run(['docker','run','--detach','--name',container,'--publish','127.0.0.1::6379','redis:7-alpine','redis-server','--save','','--appendonly','no'],check=True,stdout=subprocess.DEVNULL)
 fault_port=int(subprocess.check_output(['docker','port',container,'6379/tcp'],text=True).strip().rsplit(':',1)[1]);time.sleep(.5);redis_secret(container,secrets.token_urlsafe(32))
 # Keep shared Redis servlet sessions enabled; bearer isolation must survive the same real Redis failures.
 common={'summer.sample.realtime.redis-enabled':False,'spring.data.redis.connect-timeout':'1s','spring.data.redis.timeout':'1s'}
 env.stop(20081);env.allow_degraded=True;env.extra={**common,'spring.data.redis.port':fault_port};env.start(20081,jar=candidate);capture('AUTHENTICATION_FAILED','认证失败')
 env.stop(20081);subprocess.run(['docker','rm','-f',container],check=True,stdout=subprocess.DEVNULL);container=None
 # No listener exists on this formerly allocated isolated Redis port.
 env.extra={**common,'spring.data.redis.port':fault_port};env.start(20081,jar=candidate);capture('UNAVAILABLE','服务不可用')
 env.stop(20081);blackhole=socket.socket();blackhole.bind(('127.0.0.1',0));blackhole.listen();blackhole_port=blackhole.getsockname()[1]
 def accept():
  while True:
   try:connection,_=blackhole.accept();connections.append(connection)
   except OSError:return
 threading.Thread(target=accept,daemon=True).start();env.extra={**common,'spring.data.redis.port':blackhole_port};env.start(20081,jar=candidate);capture('TIMEOUT','连接超时')
 env.stop(20081);blackhole.close();blackhole=None
 for connection in connections:connection.close()
 connections=[];env.extra={};env.allow_degraded=False;env.start(20081,jar=candidate);capture('UP','正常');result['recovery_verified']=True
 result['stage']='complete';browser.stdin.write('{"stop":true}\n');browser.stdin.flush();browser.wait(timeout=20)
except Exception as error:result['failure_type']=type(error).__name__;result['safe_assertion']=str(error) if isinstance(error,AssertionError) else None
finally:
 if browser and browser.poll() is None:
  browser.terminate()
  try:browser.wait(timeout=5)
  except subprocess.TimeoutExpired:browser.kill();browser.wait()
 if ui and ui.poll() is None:
  ui.terminate()
  try:ui.wait(timeout=10)
  except subprocess.TimeoutExpired:ui.kill();ui.wait()
 if blackhole:blackhole.close()
 for connection in connections:connection.close()
 if container:subprocess.run(['docker','rm','-f',container],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
 if env:
  try:env.close();result['cleanup']='VERIFIED'
  except Exception as error:result['cleanup']='FAILED';result['cleanup_failure_type']=type(error).__name__
 result['fault_container_absent']=not env or ('summer-foundation-fault-'+env.run) not in subprocess.check_output(['docker','ps','-a','--format','{{.Names}}'],text=True).splitlines()
 (OUT/'redis-runtime-result.json').write_text(json.dumps(result,indent=2));print(json.dumps(result),flush=True)

raise SystemExit(0 if result.get('stage')=='complete' and not result.get('failure_type') and result.get('cleanup')=='VERIFIED' and result.get('fault_container_absent') else 1)
