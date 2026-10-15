"""Real PKCE + MySQL + Chrome permission probe. Does not edit product code or write secrets/logs."""
import os,json,subprocess,time,hashlib
from environment import Environment,Client,OUT,ROOT,SCRIPT,immutable_candidate
from grants import bootstrap,unwrap
candidate=immutable_candidate();env=None;ui=None
result={'stage':'setup','checks':[],'networkMocking':False}
def check(name,passed,**facts):
 result['checks'].append({'name':name,'passed':bool(passed),**facts});print(name,'PASS' if passed else 'FAIL',flush=True)
try:
 env=Environment();env.setup();env.start(21081,jar=candidate)
 result['stage']='bootstrap_login';admin=Client().login(21081,env.username,env.password);check('dynamic_bootstrap_real_pkce_login',True)
 result['stage']='role_configuration';bootstrap(admin,env.username);admin=Client().login(21081,env.username,env.password);check('capabilities_and_role_binding_via_real_api',True)
 result['stage']='real_read_access'
 for label,path in [('users','/api/users'),('organizations','/api/v1/enterprises/ent-acme/org-tree'),('members','/api/v1/enterprises/ent-acme/members')]:
  status,data,_=admin.request(21081,path);check('authorized_http_'+label,status==200,status=status)
 result['stage']='browser';runenv=dict(os.environ,SUMMER_E2E_USERNAME=env.username,SUMMER_E2E_PASSWORD=env.password,SUMMER_E2E_RUN=env.run)
 ui=subprocess.Popen([os.environ.get('SUMMER_IDENTITY_NODE','/opt/homebrew/opt/node@22/bin/node'),str(SCRIPT/'ui-server.mjs')],cwd=ROOT/'summer-ui',env=runenv,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
 assert ui.stdout.readline().strip()=='UI_READY','ui_start_failed'
 run=subprocess.run([os.environ.get('SUMMER_IDENTITY_NODE','/opt/homebrew/opt/node@22/bin/node'),str(SCRIPT/'browser.mjs')],cwd=ROOT/'summer-ui',env=runenv,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=180)
 result['browserExit']=run.returncode
 browser=json.loads((OUT/'browser-permissions.json').read_text());result['checks'].extend(browser['checks']);result['browserComplete']=browser.get('complete',False)
 check('no_sensitive_runtime_output',not env.sensitive_log_detected)
 result['securityAuditCounts']={name:sum(e['event']==name for e in env.security_events) for name in ['authentication_success','authentication_failure']}
 result['stage']='complete'
except Exception as error:
 result['failureType']=type(error).__name__
 if isinstance(error,AssertionError):result['assertion']=str(error)
finally:
 if ui:
  ui.terminate()
  try:ui.wait(timeout=15)
  except subprocess.TimeoutExpired:ui.kill();ui.wait(timeout=5)
 if env:
  try:env.close();result['cleanup']='VERIFIED'
  except Exception as error:result['cleanup']='FAILED';result['cleanupFailureType']=type(error).__name__
 result['candidateSha256']=hashlib.sha256(candidate.read_bytes()).hexdigest();result['diagnostics']=env.diagnostics if env else []
 result['passed']=result['stage']=='complete' and all(c['passed'] for c in result['checks']) and result.get('browserComplete') and result.get('browserExit')==0 and result.get('cleanup')=='VERIFIED'
 (OUT/'probe-result.json').write_text(json.dumps(result,indent=2));print(json.dumps({'stage':result['stage'],'passed':result['passed'],'checks':len(result['checks']),'cleanup':result.get('cleanup')}),flush=True)
raise SystemExit(0 if result['passed'] else 1)
