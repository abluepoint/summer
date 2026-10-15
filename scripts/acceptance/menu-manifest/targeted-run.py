"""Run only T16's three fixed defects against a coordinator-built immutable candidate."""
import concurrent.futures,hashlib,json,os,secrets,shutil,subprocess,threading,urllib.parse
from pathlib import Path
from environment import Environment,Client,OUT,ROOT,immutable_candidate
from grants import BASE,bootstrap,headers,data
SCRIPT=Path(__file__).resolve().parent
UI_ONLY=os.environ.get('SUMMER_MENU_MANIFEST_UI_ONLY')=='1'
VISIBLE=BASE+'/menu-manifest';MANAGED=VISIBLE+'/management';result={'status':'RUNNING','tests':0,'failures':0,'errors':0,'skipped':0,'checks':[]};env=ui=browser=None;fault_user=None
result['mode']='targeted-ui-only' if UI_ONLY else 'targeted-api-and-ui'
assert not (OUT/'result.json').exists(),'unique_evidence_directory_required'
def persist():
 p=OUT/'result.json.tmp';p.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n');p.replace(OUT/'result.json')
def check(name,ok,**evidence):
 result['tests']+=1;result['failures']+=not ok;result['checks'].append({'name':name,'status':'passed' if ok else 'failed',**evidence});persist();print(('passed ' if ok else 'RED ')+name,flush=True)
def fresh(name,password,port=23081):
 c=Client().login(port,name,password);env.additional_secrets.append(c.token);return c
def required(c,path,method='GET',body=None,expected=200,port=23081,h=None):
 status,payload,response=c.request(port,path,method,body,headers=h)
 if status!=expected:raise RuntimeError('setup_http_'+str(status))
 return data(payload)
def manifest(c,port=23081):return required(c,MANAGED,port=port)
def override(c,menu,patch,version=None,port=23081):
 if version is None:version=next(i['version'] for i in manifest(c,port)['items'] if i['menuId']==menu)
 return c.request(port,VISIBLE+'?'+urllib.parse.urlencode({'menuId':menu}),'PATCH',patch,headers=headers(version))
def counts():
 return [int(env.sql("SELECT COUNT(*) FROM "+table+" WHERE resource_id='security-keys' AND "+column+"='MENU_OVERRIDE_UPDATED'",env.schema)) for table,column in [('summer_authorization_audit','action'),('summer_authorization_change_outbox','event_type')]]
try:
 candidate=immutable_candidate();result['candidate_sha256']=hashlib.sha256(candidate.read_bytes()).hexdigest()
 archive=OUT/'scripts';archive.mkdir();
 for p in SCRIPT.iterdir():
  if p.is_file():shutil.copy2(p,archive/p.name)
 result['script_sha256']={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in archive.iterdir()}
 frozen=ROOT/'.git/summer4-closeout-20261015/t16-fix/source-frozen.json';result['frozen_inputs']=json.loads(frozen.read_text())['paths']
 check('frozen_worktree_matches_published_inputs',all(hashlib.sha256((ROOT/p).read_bytes()).hexdigest()==sha for p,sha in result['frozen_inputs'].items()))
 subprocess.run(['/opt/homebrew/opt/node@22/bin/node',str(SCRIPT/'export-manifest.mjs')],cwd=ROOT/'summer-ui',env={**os.environ,'MENU_MANIFEST_EXPORT':str(OUT/'actual-route-manifest.json')},check=True,stdout=subprocess.DEVNULL)
 actual=json.loads((OUT/'actual-route-manifest.json').read_text());env=Environment();env.setup();env.start(23081,jar=candidate)
 admin=fresh(env.username,env.password);bootstrap(admin,env.username);admin=fresh(env.username,env.password)
 required(admin,'/api/v1/platform/menu-manifest','PUT',actual,h=headers());check('real_mysql_manifest_and_api_grants_ready',True)
 if not UI_ONLY:
  env.start(23082,jar=candidate);admin2=fresh(env.username,env.password,23082)
  for label in ['initial','existing']:
   version=next(i['version'] for i in manifest(admin)['items'] if i['menuId']=='security-keys');before=counts();barrier=threading.Barrier(2)
   def write(client,port,order):
    barrier.wait(timeout=10);status,body,response=override(client,'security-keys',{'sortOrder':order},version,port)
    return {'node':port,'submittedVersion':version,'sortOrder':order,'httpStatus':status,'response':body,'requestId':response.get('X-Request-Id')}
   with concurrent.futures.ThreadPoolExecutor(2) as pool:
    left=pool.submit(write,admin,23081,101);right=pool.submit(write,admin2,23082,102);writes=[left.result(),right.result()]
   check(label+'_cross_jvm_override_has_one_winner',sorted(w['httpStatus'] for w in writes)==[200,412],responses=writes)
   after=counts();check(label+'_only_winner_has_mutation_audit_and_outbox',[a-b for a,b in zip(after,before)]==[1,1],delta=[a-b for a,b in zip(after,before)])
   value=next(i for i in manifest(admin)['items'] if i['menuId']=='security-keys');check(label+'_persisted_version_advanced_once',value['version']==version+1,version=value['version'])
  saved=manifest(admin);env.stop(23082);env.start(23082,jar=candidate);admin2=fresh(env.username,env.password,23082);check('new_node_reads_committed_menu_state',manifest(admin2,23082)==saved)
  # Real transaction-tail fault: only this isolated account lacks outbox INSERT.
  fault_user='t16f_'+env.run;fault_password=secrets.token_urlsafe(32);env.additional_secrets.append(fault_password)
  env.sql("CREATE USER '"+fault_user+"'@'%' IDENTIFIED BY '"+fault_password+"'")
  env.sql("GRANT SELECT,UPDATE,DELETE,CREATE,ALTER,INDEX,DROP,REFERENCES ON `"+env.schema+"`.* TO '"+fault_user+"'@'%'")
  tables=env.sql("SELECT table_name FROM information_schema.tables WHERE table_schema='"+env.schema+"'").splitlines()
  import re
  for table in tables:
   assert re.fullmatch(r'[A-Za-z0-9_]+',table)
   if table!='summer_authorization_change_outbox':env.sql("GRANT INSERT ON `"+env.schema+"`.`"+table+"` TO '"+fault_user+"'@'%'")
  env.extra={'spring.datasource.username':fault_user,'spring.datasource.password':fault_password};env.start(23083,jar=candidate);env.extra={}
  fault_admin=fresh(env.username,env.password,23083);prior=next(i for i in manifest(admin)['items'] if i['menuId']=='security-keys');before=counts();same_headers=headers(prior['version']);patch={'sortOrder':333};path=VISIBLE+'?menuId=security-keys'
  status,body,_=fault_admin.request(23083,path,'PATCH',patch,headers=same_headers)
  check('transaction_tail_outbox_fault_returns_failure',status==500,httpStatus=status,response=body)
  check('failed_transaction_keeps_version_metadata_audit_outbox',next(i for i in manifest(admin)['items'] if i['menuId']=='security-keys')==prior and counts()==before)
  env.sql("GRANT INSERT ON `"+env.schema+"`.summer_authorization_change_outbox TO '"+fault_user+"'@'%'")
  status,body,_=fault_admin.request(23083,path,'PATCH',patch,headers=same_headers)
  check('same_jvm_same_idempotency_key_retries_after_rollback',status==200 and data(body)['version']==prior['version']+1,httpStatus=status,response=body)
  committed=next(i for i in manifest(admin)['items'] if i['menuId']=='security-keys');after=counts()
  check('retried_transaction_persists_once_with_one_audit_outbox',committed['version']==prior['version']+1 and committed['sortOrder']==333 and [a-b for a,b in zip(after,before)]==[1,1],version=committed['version'],delta=[a-b for a,b in zip(after,before)])
  env.stop(23083);env.sql("DROP USER '"+fault_user+"'@'%'");fault_user=None
 reader_name='t16-target-reader';reader_password=secrets.token_urlsafe(24);env.additional_secrets.append(reader_password)
 required(admin,'/api/users','POST',{'username':reader_name,'password':reader_password,'role':'USER'})
 required(admin,BASE+'/members','POST',{'subject':reader_name,'displayName':'T16 Reader','email':'t16-reader@example.test'},201,h=headers())
 role=required(admin,BASE+'/roles','POST',{'name':'T16 operation deny','code':'t16-operation-deny','capabilityIds':['enterprise.menu.read','menu.menuManifest.read','GET:/api/customers','GET:/api/customers/{username}','POST:/api/customers']},201,h=headers())
 required(admin,BASE+'/users/'+reader_name+'/roles','PUT',{'roleIds':[role['roleId']],'deniedCapabilityCodes':['POST:/api/customers']},h=headers(1));reader=fresh(reader_name,reader_password)
 visible=required(reader,VISIBLE);row=next(i for i in visible['items'] if i['menuId']=='customer/drawer');check('explicit_deny_projects_only_read_action',row['actions']==['read'],menu=row)
 status,body,_=reader.request(23081,'/api/customers','POST',{'username':'t16-must-not-create','email':'denied@example.test','displayName':'Denied'});check('business_api_independently_rejects_denied_write',status==403 and body['error']['code']=='SUMMER_FORBIDDEN',httpStatus=status,response=body)
 admin_visible=required(admin,VISIBLE);check('allowed_post_projects_create_action','create' in next(i for i in admin_visible['items'] if i['menuId']=='customer/drawer')['actions'])
 runenv={**os.environ,'SUMMER_E2E_USERNAME':env.username,'SUMMER_E2E_PASSWORD':env.password,'MENU_READER_USERNAME':reader_name,'MENU_READER_PASSWORD':reader_password,'MENU_OUTPUT':str(OUT),'MENU_MANIFEST_VITE_CACHE':str(OUT/('vite-cache-'+env.run))}
 ui=subprocess.Popen(['/opt/homebrew/opt/node@22/bin/node',str(SCRIPT/'ui-server.mjs')],cwd=ROOT/'summer-ui',env=runenv,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True);assert ui.stdout.readline().strip()=='UI_READY'
 browser=subprocess.Popen(['/opt/homebrew/opt/node@22/bin/node',str(SCRIPT/'targeted-browser.mjs')],cwd=ROOT/'summer-ui',env=runenv,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
 for line in browser.stdout:
  if not line.startswith('CONTROL '):continue
  action=line.strip()[8:]
  if action not in ['disable_customer','disable_group_child']:raise RuntimeError('unexpected_browser_control')
  menu='customer/drawer' if action=='disable_customer' else 'dictionary/form'
  status,body,_=override(admin,menu,{'enabled':False});items=required(fresh(reader_name,reader_password),VISIBLE)['items'];reply={'status':status,'visible':any(i['menuId']==menu for i in items),'parentVisible':any(i['menuId']=='dictionary' for i in items)}
  result.setdefault('disable_responses',{})[menu]=body;browser.stdin.write(json.dumps(reply)+'\n');browser.stdin.flush()
 browser.wait(timeout=30);result['browser']=json.loads((OUT/'browser-result.json').read_text());result['browser_exit_code']=browser.returncode
 check('frozen_sources_unchanged_after_real_run',all(hashlib.sha256((ROOT/p).read_bytes()).hexdigest()==sha for p,sha in result['frozen_inputs'].items()))
 result['status']='INCOMPLETE' if result['browser']['errors'] else ('RED' if result['failures'] or result['browser']['failures'] or browser.returncode else 'PASSED')
except Exception as e:result.update(status='INCOMPLETE',error_type=type(e).__name__);result['errors']+=1
finally:
 for process in [browser,ui]:
  if process and process.poll() is None:
   process.terminate()
   try:process.wait(timeout=15)
   except subprocess.TimeoutExpired:process.kill();process.wait()
 if env:
  try:
   if fault_user:
    if 23083 in env.nodes:env.stop(23083)
    env.sql("DROP USER '"+fault_user+"'@'%'");fault_user=None
   check('isolated_fault_account_removed',env.sql("SELECT COUNT(*) FROM mysql.user WHERE User='t16f_"+env.run+"'")== '0')
   env.close();result['cleanup']='VERIFIED';cache=OUT/('vite-cache-'+env.run)
   if cache.exists():shutil.rmtree(cache)
   check('runtime_logs_exclude_known_secrets',not env.sensitive_log_detected)
  except Exception as e:result.update(cleanup='FAILED',cleanup_error=type(e).__name__);result['errors']+=1
 if result['errors'] or result.get('cleanup')!='VERIFIED':result['status']='INCOMPLETE'
 elif result['failures']:result['status']='RED'
 result['http_observations']=Client.observations;persist();print(json.dumps({k:result.get(k) for k in ['status','tests','failures','errors','skipped','cleanup']}),flush=True)
raise SystemExit(0 if result['status']=='PASSED' and not result['failures'] and not result['errors'] and result.get('cleanup')=='VERIFIED' else 1)
