"""T19 real MySQL/PKCE acceptance; mutations use production HTTP only."""
import hashlib,json,os,secrets,shutil,uuid,selectors,subprocess,time
from environment import Environment,Client,ROOT,OUT,SCRIPT,immutable_candidate
from grants import bootstrap,create_role,bind
B='/api/v1/enterprises/ent-acme'; P=B+'/system-parameters'; D=B+'/dictionaries'
result={'issue':127,'stage':'initialization','checks':[],'networkMocking':False,'businessSqlWrites':False};env=None
hidden=[]
ui=browser=None;trigger=False
def save():
 c=result['checks'];result['counts']={'tests':len(c),'failures':sum(not x['passed'] for x in c),'errors':int('errorType'in result),'skipped':0}
 t=OUT/'result.tmp';t.write_text(json.dumps(result,indent=2));t.replace(OUT/'result.json')
def check(name,passed,**facts):
 result['checks'].append({'name':name,'passed':bool(passed),**facts});save();print(name,'PASS' if passed else 'FAIL',flush=True)
def mh(v=None):
 h={'Idempotency-Key':uuid.uuid4().hex}
 if v is not None:h['If-Match']=str(v)
 return h
import sys as _dictionary_sys
from pathlib import Path as _DictionaryPath
_dictionary_sys.path.insert(0, str(_DictionaryPath(__file__).resolve().parents[1]))
from dictionary_drafts import with_draft_revision

def call(c,path,method='GET',body=None,expected=200,headers=None,name=None,strict=True):
 if expected < 403: body = with_draft_revision(c, 21081, path, method, body)
 rid='t19-'+uuid.uuid4().hex;s,b,h=c.request(21081,path,method,body,headers={'X-Request-Id':rid,**(headers or {})})
 e=b.get('error') if isinstance(b,dict) else None;code=e.get('code') if isinstance(e,dict) else None
 good=s==expected and isinstance(b,dict) and b.get('success')==(s<400) and b.get('requestId')==h.get('X-Request-Id')==rid
 if s>=400:good=good and isinstance(code,str) and code.startswith('SUMMER_') and not any(v in json.dumps(b) for v in hidden)
 check(name or method+' '+path,good,httpStatus=s,expected=expected,code=code)
 if strict and s!=expected:raise AssertionError('unexpected_status_'+str(s)+'_expected_'+str(expected))
 return b.get('data') if isinstance(b,dict) else None,h

def login(u,p):return Client().login(21081,u,p)
def binding(denies):
 _,h=call(admin,B+'/users/'+env.denied_username+'/roles')
 call(admin,B+'/users/'+env.denied_username+'/roles','PUT',{'roleIds':[role],'deniedCapabilityCodes':denies},headers=mh(h['ETag']))
def scope(resource,tree):return call(admin,B+'/data-scopes/'+resource,'PUT',{'conditions':tree})[0]
def deny_scope(resource,tree):return call(admin,B+'/data-scopes/'+resource+'/bindings/USER/'+env.denied_username,'PUT',{'conditions':tree})[0]
def pub(resource,key,c=None,expected=200,name=None):return call(c or reader,'/public/enterprises/ent-acme/'+resource+'/'+key,expected=expected,name=name)[0]
def value(resource,v):return v['value'] if resource=='system-parameters' else v['items'][0]['label']
def override_body(resource,v,ver=None,public=True,enabled=True):
 return {'value':v if resource=='system-parameters' else [{'value':'alpha','label':v,'sortOrder':0,'enabled':True}],'publicReadable':public,'enabled':enabled,**({'expectedVersion':ver} if ver is not None else {})}
def state():
 return env.sql("SELECT CONCAT(tenant_id,':',resource,':',config_key,':',scope_id,':',version,':',latest_revision,':',COALESCE(published_revision,0)) FROM summer_configuration_override ORDER BY id",env.schema)
try:
 jar=immutable_candidate();result['candidateSha256']=hashlib.sha256(jar.read_bytes()).hexdigest()
 archive=OUT/'executed-scripts';archive.mkdir()
 for p in SCRIPT.iterdir():
  if p.is_file():shutil.copy2(p,archive/p.name)
 matched=json.loads((ROOT/'.git/summer4-closeout-20261015/t19-live/candidate-source-match.json').read_text())
 sources={r['path']:hashlib.sha256((ROOT/r['path']).read_bytes()).hexdigest() for r in matched['files']}
 (OUT/'source-before.json').write_text(json.dumps(sources,indent=2))
 env=Environment();env.setup();env.start(21081,jar=jar);admin=login(env.username,env.password);boot=bootstrap(admin,env.username);admin=login(env.username,env.password)
 result['stage']='fixtures';catalog,_=call(admin,B+'/capabilities/catalog');codes={c['code']:c['capabilityId'] for c in catalog['capabilities']}
 wanted=[p+'.'+a for p in ['system.parameter','platform.dictionary'] for a in ['read','create','update','publish','disable','rollback']]+['menu.systemParameters.read','menu.dictionary.read','menu.dictionary.form.read']
 for code in wanted:
  if code not in codes:
   cap,_=call(admin,B+'/capabilities','POST',{'code':code,'resource':code.rsplit('.',1)[0],'action':code.rsplit('.',1)[1],'riskLevel':'HIGH','scope':'PLATFORM' if not code.startswith('menu.') else 'ENTERPRISE'},201,headers=mh());codes[code]=cap['capabilityId']
 role=create_role(admin,'ent-acme',[codes[c] for c in wanted]+['enterprise.organization.read'],'T19 configuration operator')
 _,h=call(admin,B+'/users/'+env.username+'/roles');call(admin,B+'/users/'+env.username+'/roles','PUT',{'roleIds':[boot,role],'deniedCapabilityCodes':[]},headers=mh(h['ETag']));admin=login(env.username,env.password)
 call(admin,'/api/users','POST',{'username':env.denied_username,'password':env.denied_password,'role':'USER'})
 member,_=call(admin,B+'/members','POST',{'subject':env.denied_username,'displayName':'T19 reader','email':'reader@example.test'},201,headers=mh());bind(admin,'ent-acme',env.denied_username,[role]);reader=login(env.denied_username,env.denied_password)
 for resource in ['system-parameters','dictionaries']:scope(resource,{'field':'TENANT'})
 key='authorized.'+env.run;code='customer-segment'
 baseval=secrets.token_urlsafe(18); hidden.append(baseval);env.additional_secrets=hidden
 p,_=call(admin,P,'POST',{'key':key,'type':'STRING','value':baseval,'public':True},201)
 d,_=call(admin,D,'POST',{'code':code,'name':'T19 visible dictionary','published':False,'publicReadable':True})
 call(admin,D+'/'+code+'/items','POST',{'value':'alpha','label':'T19 基础选项','sortOrder':0,'enabled':True})
 call(reader,D+'/'+code+'/items','POST',{'value':'beta','label':'reader-created','sortOrder':1,'enabled':True})
 call(admin,D+'/'+code+'/publish','POST',{'expectedVersion':0,'idempotencyKey':uuid.uuid4().hex})
 check('parameter_public_base_exact',pub('system-parameters',key)['value']==baseval)
 check('dictionary_public_base_exact',len(pub('dictionaries',code)['items'])==2)
 result['stage']='browser_setup';binding(['platform.dictionary.publish'])
 ui_sources={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in (ROOT/'summer-ui/src').rglob('*') if p.is_file()};(OUT/'ui-inputs-before.json').write_text(json.dumps(ui_sources,indent=2))
 child=dict(os.environ,SUMMER_E2E_USERNAME=env.username,SUMMER_E2E_PASSWORD=env.password,SUMMER_E2E_READER=env.denied_username,SUMMER_E2E_READER_PASSWORD=env.denied_password,AUTHORIZED_CONFIGURATION_VITE_CACHE=str(OUT/'vite-cache'))
 ui=subprocess.Popen(['/opt/homebrew/opt/node@22/bin/node',str(SCRIPT/'ui-server.mjs')],cwd=ROOT/'summer-ui',env=child,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
 selector=selectors.DefaultSelector();selector.register(ui.stdout,selectors.EVENT_READ);ready=selector.select(45);selector.close()
 if not ready or ui.stdout.readline().strip()!='UI_READY':raise RuntimeError('ui_startup_failed')
 result['stage']='real_chrome';browser=subprocess.Popen(['/opt/homebrew/opt/node@22/bin/node',str(SCRIPT/'browser.mjs')],cwd=ROOT/'summer-ui',env=child,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
 selector=selectors.DefaultSelector();selector.register(browser.stdout,selectors.EVENT_READ);deadline=time.monotonic()+300
 while selector.get_map():
  if time.monotonic()>deadline:raise TimeoutError('browser_deadline')
  for event,_ in selector.select(.5):
   line=event.fileobj.readline()
   if not line:selector.unregister(event.fileobj);continue
   command=line.strip()
   if command=='AUDIT_FAULT':
    before=state();auditbefore=env.sql("SELECT COUNT(*) FROM summer_audit_event WHERE action LIKE 'CONFIGURATION_OVERRIDE_%'",env.schema)
    env.sql("CREATE TRIGGER t19_browser_audit_failure BEFORE INSERT ON summer_audit_event FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='controlled acceptance fault'",env.schema);trigger=True
   elif command=='RECOVER':
    env.sql('DROP TRIGGER t19_browser_audit_failure',env.schema);trigger=False
    check('chrome_fault_rolls_back_override_and_audit',state()==before and env.sql("SELECT COUNT(*) FROM summer_audit_event WHERE action LIKE 'CONFIGURATION_OVERRIDE_%'",env.schema)==auditbefore)
   elif command=='REVOKE_READ':binding(['platform.dictionary.publish','platform.dictionary.read'])
   else:raise RuntimeError('unexpected_browser_signal')
   browser.stdin.write('GO\n');browser.stdin.flush()
 selector.close();browser.wait(timeout=15);result['browserExitCode']=browser.returncode
 receipt=OUT/'browser-result.json';result['browser']=json.loads(receipt.read_text()) if receipt.exists() else {'errors':1}
 if browser.returncode:raise AssertionError('real_chrome_failed')
 result['stage']='complete';check('sensitive_output_scan_clear',not env.sensitive_log_detected)
except Exception as error:
 result['errorType']=type(error).__name__
 if isinstance(error,AssertionError):result['safeAssertion']=str(error)
finally:
 for process in [browser,ui]:
  if process and process.poll() is None:
   process.terminate()
   try:process.wait(timeout=10)
   except subprocess.TimeoutExpired:process.kill();process.wait()
 if trigger and env:
  try:env.sql('DROP TRIGGER t19_browser_audit_failure',env.schema)
  except Exception:pass
 if 'ui_sources' in globals():result['changedUiPaths']=[p for p,h in ui_sources.items() if hashlib.sha256((ROOT/p).read_bytes()).hexdigest()!=h]
 if env:
  result['diagnostics']=env.diagnostics;result['sensitiveLogDetected']=env.sensitive_log_detected
  try:env.close();result['cleanup']='VERIFIED'
  except Exception as error:result['cleanupErrorType']=type(error).__name__
 if 'sources'in globals():result['changedSourcePaths']=[p for p,h in sources.items() if hashlib.sha256((ROOT/p).read_bytes()).hexdigest()!=h]
 (OUT/'http-observations.json').write_text(json.dumps(Client.observations,indent=2));save()
print(json.dumps({'stage':result['stage'],'counts':result['counts'],'cleanup':result.get('cleanup'),'errorType':result.get('errorType')}))
raise SystemExit(0 if result['stage']=='complete' and not result['counts']['failures'] and not result['counts']['errors'] and result.get('cleanup')=='VERIFIED' else 1)
