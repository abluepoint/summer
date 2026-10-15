"""Real PKCE + MySQL + Chrome permission probe. Does not edit product code or write secrets/logs."""
import os,json,subprocess,time,hashlib
from environment import Environment,Client,OUT,ROOT,SCRIPT,immutable_candidate
from grants import bootstrap,unwrap
candidate=immutable_candidate();env=None;ui=None;browser_process=None
result={'stage':'setup','checks':[],'networkMocking':False}
def ui_inputs():
 paths=[p for p in (ROOT/'summer-ui/src').rglob('*') if p.is_file()]
 paths.extend(p for p in (ROOT/'summer-ui').iterdir() if p.is_file() and p.suffix in {'.json','.ts','.js','.mjs'})
 return {str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(paths)}
ui_before=ui_inputs()
(OUT/'ui-input-before.json').write_text(json.dumps(ui_before,indent=2))
def check(name,passed,**facts):
 result['checks'].append({'name':name,'passed':bool(passed),**facts});print(name,'PASS' if passed else 'FAIL',flush=True)
try:
 env=Environment()
 if os.environ.get('SUMMER_IDENTITY_PROTOCOL_FLOWS')=='1':
  env.extra.update({'server.servlet.session.timeout':'15s','spring.session.timeout':'15s'})
 env.setup();env.start(21081,jar=candidate)
 result['stage']='bootstrap_login';admin=Client().login(21081,env.username,env.password);check('dynamic_bootstrap_real_pkce_login',True)
 result['stage']='role_configuration';bootstrap(admin,env.username);admin=Client().login(21081,env.username,env.password);check('capabilities_and_role_binding_via_real_api',True)
 result['stage']='real_read_access'
 for label,path in [('users','/api/users'),('organizations','/api/v1/enterprises/ent-acme/org-tree'),('members','/api/v1/enterprises/ent-acme/members')]:
  status,data,_=admin.request(21081,path);check('authorized_http_'+label,status==200,status=status)
 result['stage']='browser';runenv=dict(os.environ,SUMMER_E2E_USERNAME=env.username,SUMMER_E2E_PASSWORD=env.password,SUMMER_E2E_NEW_PASSWORD=env.denied_password,SUMMER_E2E_RUN=env.run)
 ui=subprocess.Popen([os.environ.get('SUMMER_IDENTITY_NODE','/opt/homebrew/opt/node@22/bin/node'),str(SCRIPT/'ui-server.mjs')],cwd=ROOT/'summer-ui',env=runenv,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
 assert ui.stdout.readline().strip()=='UI_READY','ui_start_failed'
 browser_process=subprocess.Popen([os.environ.get('SUMMER_IDENTITY_NODE','/opt/homebrew/opt/node@22/bin/node'),str(SCRIPT/'browser.mjs')],cwd=ROOT/'summer-ui',env=runenv,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
 result['browserExit']=browser_process.wait(timeout=300)
 browser=json.loads((OUT/'browser-permissions.json').read_text());result['checks'].extend(browser['checks']);result['browserComplete']=browser.get('complete',False)
 check('no_sensitive_runtime_output',not env.sensitive_log_detected)
 result['securityAuditCounts']={name:sum(e['event']==name for e in env.security_events) for name in ['authentication_success','authentication_failure']}
 result['stage']='complete'
except Exception as error:
 result['failureType']=type(error).__name__
 if isinstance(error,AssertionError):result['assertion']=str(error)
finally:
 if browser_process and browser_process.poll() is None:
  browser_process.terminate()
  try:browser_process.wait(timeout=15)
  except subprocess.TimeoutExpired:browser_process.kill();browser_process.wait(timeout=5)
 if ui:
  ui.terminate()
  try:ui.wait(timeout=15)
  except subprocess.TimeoutExpired:ui.kill();ui.wait(timeout=5)
 if env:
  try:env.close();result['cleanup']='VERIFIED'
  except Exception as error:result['cleanup']='FAILED';result['cleanupFailureType']=type(error).__name__
 result['candidateSha256']=hashlib.sha256(candidate.read_bytes()).hexdigest();result['diagnostics']=env.diagnostics if env else []
 ui_after=ui_inputs();(OUT/'ui-input-after.json').write_text(json.dumps(ui_after,indent=2))
 result['uiInputs']={'files':len(ui_before),'stable':ui_before==ui_after,'changedPaths':[p for p in sorted(set(ui_before)|set(ui_after)) if ui_before.get(p)!=ui_after.get(p)]}
 result['passed']=result['stage']=='complete' and all(c['passed'] for c in result['checks']) and result.get('browserComplete') and result.get('browserExit')==0 and result.get('cleanup')=='VERIFIED'
 (OUT/'probe-result.json').write_text(json.dumps(result,indent=2));print(json.dumps({'stage':result['stage'],'passed':result['passed'],'checks':len(result['checks']),'cleanup':result.get('cleanup')}),flush=True)
raise SystemExit(0 if result['passed'] else 1)
