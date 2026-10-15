"""T35 acceptance against the shipped trusted plugin; no test plugin is loaded."""
import hashlib,json,os,secrets,shutil,uuid,selectors,subprocess,time
from environment import Environment,Client,ROOT,OUT,SCRIPT,immutable_candidate
from grants import bootstrap,create_role,bind
B='/api/v1/enterprises/ent-acme';PL='/api/v1/platform/plugins';ID='customer-insights';ITEM=PL+'/'+ID
SUMMARY=B+'/plugins/'+ID+'/summary';DIGEST='sha256:customer-insights-v1'
result={'issue':143,'stage':'initialization','checks':[],'networkMocking':False,'testPluginLoaded':False,'businessSqlWrites':False};env=None;hidden=[]
ui=browser=None;trigger=False
def save():
 c=result['checks'];result['counts']={'tests':len(c),'failures':sum(not x['passed']for x in c),'errors':int('errorType'in result),'skipped':0};t=OUT/'result.tmp';t.write_text(json.dumps(result,indent=2));t.replace(OUT/'result.json')
def check(name,passed,**facts):
 result['checks'].append({'name':name,'passed':bool(passed),**facts});save();print(name,'PASS' if passed else 'FAIL',flush=True)
def mh(v=None):
 h={'Idempotency-Key':uuid.uuid4().hex}
 if v is not None:h['If-Match']=str(v)
 return h
def call(c,path,method='GET',body=None,expected=200,name=None,headers=None,strict=True,port=21081,expected_code=None):
 rid='t35-'+uuid.uuid4().hex;s,b,h=c.request(port,path,method,body,headers={'X-Request-Id':rid,**(headers or {})});e=b.get('error')if isinstance(b,dict)else None;code=e.get('code')if isinstance(e,dict)else None
 good=s==expected and isinstance(b,dict)and b.get('success')==(s<400)and b.get('requestId')==h.get('X-Request-Id')==rid
 if s>=400:good=good and isinstance(code,str)and (code==expected_code if expected_code else code.startswith('SUMMER_'))and b.get('data')is None and not any(v in json.dumps(b)for v in hidden)
 check(name or method+' '+path,good,httpStatus=s,expected=expected,code=code)
 if strict and s!=expected:raise AssertionError('unexpected_http_'+str(s)+'_expected_'+str(expected))
 return b.get('data')if isinstance(b,dict)else None,h

def login(u,p):return Client().login(21081,u,p)
def binding(denies,who=None,roles=None):
 subject=who or env.denied_username;_,h=call(admin,B+'/users/'+subject+'/roles');call(admin,B+'/users/'+subject+'/roles','PUT',{'roleIds':roles or [role],'deniedCapabilityCodes':denies},headers=mh(h['ETag']))
def scope(resource,tree):return call(admin,B+'/data-scopes/'+resource,'PUT',{'conditions':tree})[0]
def summary(c=None):return call(c or reader,SUMMARY)[0]
def config(d,key='customer-insights.label'):return next(x for x in d['configuration']if x['key']==key)
def rowstate():return env.sql("SELECT version,status,trusted_flag,revision FROM summer_plugin WHERE plugin_id='customer-insights'",env.schema)
try:
 jar=immutable_candidate();result['candidateSha256']=hashlib.sha256(jar.read_bytes()).hexdigest();archive=OUT/'executed-scripts';archive.mkdir()
 for p in SCRIPT.iterdir():
  if p.is_file():shutil.copy2(p,archive/p.name)
 baseline=json.loads((ROOT/'.git/summer4-closeout-20261015/t35-live/candidate-source-match.json').read_text());sources={x['path']:hashlib.sha256((ROOT/x['path']).read_bytes()).hexdigest()for x in baseline['files']};(OUT/'source-before.json').write_text(json.dumps(sources,indent=2))
 env=Environment();env.setup();env.start(21081,jar=jar);admin=login(env.username,env.password);boot=bootstrap(admin,env.username);admin=login(env.username,env.password)
 result['stage']='real_grants';catalog,_=call(admin,B+'/capabilities/catalog');known={x['code']:x['capabilityId']for x in catalog['capabilities']}
 wanted=['platform.plugins.read','platform.plugins.write','enterprise.plugins.read','enterprise.plugins.customer-insights.read','customer.read','menu.plugins.read']+[p+'.'+a for p in ['system.parameter','platform.dictionary']for a in ['read','create','update','publish','disable','rollback']]
 for code in wanted:
  if code not in known:
   cap,_=call(admin,B+'/capabilities','POST',{'code':code,'resource':code.rsplit('.',1)[0],'action':code.rsplit('.',1)[1],'scope':'PLATFORM'if code.startswith(('platform.','system.'))else'ENTERPRISE','riskLevel':'HIGH'},201,headers=mh());known[code]=cap['capabilityId']
 role=create_role(admin,'ent-acme',[known[c]for c in wanted]+['enterprise.organization.read']+[m+':'+p for m,p in [('GET','/api/v1/platform/plugins'),('POST','/api/v1/platform/plugins'),('POST','/api/v1/platform/plugins/{pluginId}:enable'),('POST','/api/v1/platform/plugins/{pluginId}:disable'),('POST','/api/v1/platform/plugins/{pluginId}:upgrade'),('GET','/api/v1/platform/plugins/{pluginId}/diagnostic')]],'T35 trusted plugin operator');binding([],env.username,[boot,role]);admin=login(env.username,env.password)
 call(admin,'/api/users','POST',{'username':env.denied_username,'password':env.denied_password,'role':'USER'});member,_=call(admin,B+'/members','POST',{'subject':env.denied_username,'displayName':'T35 plugin reader','email':'reader@example.test'},201,headers=mh());bind(admin,'ent-acme',env.denied_username,[role]);reader=login(env.denied_username,env.denied_password)
 for resource in ['system-parameters','dictionaries']:scope(resource,{'field':'TENANT'})
 import base64
 claims=json.loads(base64.urlsafe_b64decode(admin.token.split('.')[1]+'=='))
 result['grantDiagnostics']={'pluginReadPlatformClaim':'platform.plugins.read' in claims.get('platform_capabilities',[]),'pluginWritePlatformClaim':'platform.plugins.write' in claims.get('platform_capabilities',[])};save()
 result['stage']='browser_fixtures'
 for key in ['customer-insights.label','customer-insights.segment']:
  call(admin,B+'/system-parameters','POST',{'key':key,'type':'STRING','value':'T35 browser configuration','public':True},201)
 dp=B+'/dictionaries/customer-segment';call(admin,B+'/dictionaries','POST',{'code':'customer-segment','name':'T35 browser segment','publicReadable':True})
 call(admin,dp+'/items','POST',{'value':'alpha','label':'T35 browser published','sortOrder':0,'enabled':True});call(admin,dp+'/publish','POST',{'expectedVersion':0,'idempotencyKey':uuid.uuid4().hex})
 reader_role=create_role(admin,'ent-acme',['enterprise.menu.read','enterprise.plugins.read','enterprise.plugins.customer-insights.read','customer.read','system.parameter.read','platform.dictionary.read'],'T35 enterprise-only plugin reader')
 binding([],env.denied_username,[reader_role]);reader=login(env.denied_username,env.denied_password)
 reader_claims=json.loads(base64.urlsafe_b64decode(reader.token.split('.')[1]+'=='));check('enterprise_reader_has_no_platform_plugin_grants',not any(c.startswith('platform.plugins.')for c in reader_claims.get('platform_capabilities',[])))
 call(admin,PL,'POST',{'pluginId':ID,'version':'9.0.0','artifactDigest':DIGEST},201)
 ui_sources={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest()for p in (ROOT/'summer-ui/src').rglob('*')if p.is_file()};(OUT/'ui-inputs-before.json').write_text(json.dumps(ui_sources,indent=2))
 child=dict(os.environ,SUMMER_E2E_USERNAME=env.username,SUMMER_E2E_PASSWORD=env.password,SUMMER_E2E_READER=env.denied_username,SUMMER_E2E_READER_PASSWORD=env.denied_password,TRUSTED_PLUGINS_VITE_CACHE=str(OUT/'vite-cache'))
 ui=subprocess.Popen(['/opt/homebrew/opt/node@22/bin/node',str(SCRIPT/'ui-server.mjs')],cwd=ROOT/'summer-ui',env=child,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
 selector=selectors.DefaultSelector();selector.register(ui.stdout,selectors.EVENT_READ);ready=selector.select(45);selector.close()
 if not ready or ui.stdout.readline().strip()!='UI_READY':raise RuntimeError('ui_startup_failed')
 result['stage']='real_chrome';browser=subprocess.Popen(['/opt/homebrew/opt/node@22/bin/node',str(SCRIPT/'focused-browser.mjs')],cwd=ROOT/'summer-ui',env=child,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
 selector=selectors.DefaultSelector();selector.register(browser.stdout,selectors.EVENT_READ);deadline=time.monotonic()+300
 while selector.get_map():
  if time.monotonic()>deadline:raise TimeoutError('browser_deadline')
  for event,_ in selector.select(.5):
   line=event.fileobj.readline()
   if not line:selector.unregister(event.fileobj);continue
   command=line.strip()
   if command=='UPGRADE':
    call(admin,ITEM+':upgrade','POST',{'version':'1.0.0','artifactDigest':DIGEST});call(admin,ITEM+':enable','POST')
   elif command=='RESTORE_READ':binding([],env.username,[boot,role])
   elif command=='DISABLE_PLUGIN':call(admin,ITEM+':disable','POST');call(reader,SUMMARY,expected=409,name='enterprise_disabled_api_rejected',expected_code='SAMPLE_PLUGIN_INCOMPATIBLE')
   elif command=='ENABLE_PLUGIN':call(admin,ITEM+':enable','POST')
   elif command=='DENY_READER':binding(['enterprise.plugins.customer-insights.read'],env.denied_username,[reader_role]);call(reader,SUMMARY,expected=403,name='enterprise_reader_persistent_denial')
   elif command=='RESTORE_READER':binding([],env.denied_username,[reader_role])
   elif command=='VERIFY_DISABLED':call(admin,SUMMARY,expected=409,name='chrome_disabled_plugin_api_blocked',expected_code='SAMPLE_PLUGIN_INCOMPATIBLE')
   elif command=='DENY_READ':binding(['enterprise.plugins.customer-insights.read'],env.username,[boot,role])
   elif command=='AUDIT_FAULT':
    binding([],env.username,[boot,role]);auditbefore=env.sql("SELECT COUNT(*) FROM summer_audit_event WHERE resource='plugin:customer-insights'",env.schema)
    env.sql("CREATE TRIGGER t35_browser_audit_failure BEFORE INSERT ON summer_audit_event FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='controlled acceptance fault'",env.schema);trigger=True
   elif command=='RECOVER':
    env.sql('DROP TRIGGER t35_browser_audit_failure',env.schema);trigger=False
    check('chrome_failed_read_no_success_audit_appended',env.sql("SELECT COUNT(*) FROM summer_audit_event WHERE resource='plugin:customer-insights'",env.schema)==auditbefore)
   else:raise RuntimeError('unexpected_browser_signal')
   browser.stdin.write('GO\n');browser.stdin.flush()
 selector.close();browser.wait(timeout=15);result['browserExitCode']=browser.returncode
 receipt=OUT/'browser-result.json';result['browser']=json.loads(receipt.read_text())if receipt.exists()else{'errors':1}
 if browser.returncode:raise AssertionError('real_chrome_failed')
 result['stage']='complete';check('sensitive_output_scan_clear',not env.sensitive_log_detected)
except Exception as error:
 if env and env.created_schema:
  try:result['denialDiagnostics']=env.sql("SELECT permission_code,reason_code FROM summer_authorization_audit WHERE action='AUTHORIZATION_DENIED' ORDER BY audit_id DESC LIMIT 8",env.schema).splitlines()
  except Exception:pass
 result['errorType']=type(error).__name__
 if isinstance(error,AssertionError):result['safeAssertion']=str(error)
finally:
 for process in [browser,ui]:
  if process and process.poll()is None:
   process.terminate()
   try:process.wait(timeout=10)
   except subprocess.TimeoutExpired:process.kill();process.wait()
 if trigger and env:
  try:env.sql('DROP TRIGGER t35_browser_audit_failure',env.schema)
  except Exception:pass
 if 'ui_sources'in globals():result['changedUiPaths']=[p for p,h in ui_sources.items()if hashlib.sha256((ROOT/p).read_bytes()).hexdigest()!=h]
 if env:
  result['diagnostics']=env.diagnostics;result['sensitiveLogDetected']=env.sensitive_log_detected
  try:env.close();result['cleanup']='VERIFIED'
  except Exception as error:result['cleanupErrorType']=type(error).__name__
 if'sources'in globals():result['changedSourcePaths']=[p for p,h in sources.items()if hashlib.sha256((ROOT/p).read_bytes()).hexdigest()!=h]
 (OUT/'http-observations.json').write_text(json.dumps(Client.observations,indent=2));save()
print(json.dumps({'stage':result['stage'],'counts':result['counts'],'cleanup':result.get('cleanup'),'errorType':result.get('errorType')}))
raise SystemExit(0 if result['stage']=='complete'and not result['counts']['failures']and not result['counts']['errors']and result.get('cleanup')=='VERIFIED'else 1)
