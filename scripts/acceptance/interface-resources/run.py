"""Real isolated MySQL/Redis/JVM/Chrome acceptance; no HTTP interception or production edits."""
import hashlib,json,os,subprocess,time,urllib.request
from pathlib import Path
from environment import Environment,Client,OUT,ROOT
from fixtures import Fixtures,literal

CANDIDATE=Path(os.environ.get('CATALOG_CANDIDATE_PATH',str(ROOT/'.git/summer4-closeout-20261015/runtime/candidate-4c81/candidate.jar')))
EXPECTED=os.environ.get('CATALOG_CANDIDATE_SHA256','4c81a803dd19f610999a8a8b5b336b4a25771af82a981a3a47e79a5c3e86eeae')
FIXTURE=OUT/'interface-resource-fixture.jar'
API='/api/v1/platform/interface-resources'
SCRIPT=Path(__file__).resolve().parent
PLANNED=['candidate_digest','mysql_v34_first_migration','separate_management_catalog_and_health','real_managed_login','catalog_metadata_and_explicit_override','protocol_and_functional_protection','two_node_snapshot_convergence','real_denied_and_unauthenticated_reads','invalid_and_missing_history_correlation','startup_added_route_default_deny_then_grant','restart_removed_route_retains_binding_and_snapshot','replacement_cannot_inherit_old_grant','replacement_two_node_convergence','startup_change_counts','real_chrome_flows','opaque_component_reports_incomplete','mapping_conflict_fails_startup']
result={'mode':'real-mysql-two-jvm-http-chrome','candidate_sha256':EXPECTED,'checks':[],'failures':0,'errors':0,'skipped':0}
env=None;ui=None;browser=None;stage=PLANNED[0]
def passed(name=None,**evidence):
 result['checks'].append({'name':name or stage,'status':'passed',**evidence});print('passed',name or stage,flush=True)
def start(phase='base',port=23081,extra=None):
 props={'acceptance.catalog.phase':phase};props.update(extra or {})
 env.start(port,jar=CANDIDATE,acceptance_fixture=FIXTURE,extra_properties=props)
def catalog(client,port=23081):
 status,body,headers=client.request(port,API);assert status==200,'catalog_status';assert body.get('requestId'),'catalog_request_id';return body['data']
def read(client,path,expected=200,port=23081):
 status,body,headers=client.request(port,path);assert status==expected,'http_status';return body,headers
def paths(view):return {item['normalizedPath'] for item in view['items']}
def save_snapshot(name,view):
 text=json.dumps(view,ensure_ascii=False,sort_keys=True)
 for sensitive in [env.password,env.denied_password,env.mysql,env.master,env.redis]:assert sensitive not in text,'sensitive_snapshot'
 (OUT/(name+'.json')).write_text(text)
 return hashlib.sha256(text.encode()).hexdigest()
try:
 assert hashlib.sha256(CANDIDATE.read_bytes()).hexdigest()==EXPECTED;passed()
 env=Environment();env.setup();stage=PLANNED[1];start(extra={'management.server.port':23083})
 migration=env.sql("select count(*) from flyway_schema_history where version='34' and success=1",env.schema);assert migration=='1';assert env.sql("select count(*) from information_schema.tables where table_schema="+literal(env.schema)+" and table_name='summer_interface_resource_revision'")=='1';passed(mysql_version=env.sql('select version()'),migration='V34')
 stage=PLANNED[2];fixture=Fixtures(env).seed();admin=fixture.admin;denied=fixture.denied
 managed=catalog(admin);assert any(r['contextId']=='management' and r['resourceType']=='INFRASTRUCTURE' and r['normalizedPath']=='/actuator/health' for r in managed['items'])
 management_snapshot=save_snapshot('management-snapshot',managed)
 assert managed['complete'] is True and managed['issues']==[],'standard_management_catalog_incomplete'
 with urllib.request.urlopen('http://127.0.0.1:23083/actuator/health') as health:assert health.status==200
 passed(complete=managed['complete'],issues_count=len(managed['issues']),snapshot_sha256=management_snapshot)
 stage=PLANNED[3];assert env.sql('select count(*) from summer_authorization_user_binding where username='+literal(env.username),env.schema)=='1';assert admin.token and denied.token;passed(authentication='form + PKCE authorization_code',authorization='persisted managed role and binding')
 stage=PLANNED[4];required={'httpMethod','normalizedPath','handler','module','resourceType','sensitiveLevel','permissionCode','contextId','conditions'}
 assert all(required<=set(row) for row in managed['items']);assert {'BUSINESS','PROTOCOL','INFRASTRUCTURE','STATIC'}<={row['resourceType'] for row in managed['items']}
 explicit=[row for row in managed['items'] if row['normalizedPath']=='/api/acceptance/catalog/explicit'];assert explicit and all(row['permissionCode']=='acceptance.catalog.override' for row in explicit)
 read(admin,'/api/acceptance/catalog/explicit');passed(resource_count=len(managed['items']),snapshot_sha256=save_snapshot('management-snapshot',managed))
 stage=PLANNED[5];read(admin,'/api/acceptance/catalog/functional',403)
 anonymous=Client();status,body,_=anonymous.request(23081,'/oauth2/token','POST','grant_type=client_credentials',bearer=False);assert status in (400,401) and not (body and body.get('access_token'));status,_,_=anonymous.request(23081,'/login','POST','username=invalid',bearer=False);assert status==403;passed()
 stage=PLANNED[6];env.stop(23081);start();start(port=23082)
 base=catalog(admin);second=catalog(admin,23082);assert base==second
 for port in [23081,23082,23081,23082]:assert catalog(admin,port)['revision']==base['revision']
 assert env.sql('select count(*) from (select fingerprint,count(*) c from summer_interface_resource_revision group by fingerprint having c>1) duplicates',env.schema)=='0';passed(revision=base['revision'])
 stage=PLANNED[7]
 for path in [API,API+'/history',API+'/history/'+str(base['revision']),API+'/diff?from='+str(base['revision'])+'&to='+str(base['revision'])]:
  body,_=read(denied,path,403);assert body.get('requestId');read(Client(),path,401)
 passed(requests=8)
 stage=PLANNED[8]
 for path,status,code in [(API+'/history?size=0',400,'SUMMER_RESOURCE_HISTORY_INVALID'),(API+'/diff?from=0&to=1',400,'SUMMER_RESOURCE_HISTORY_INVALID'),(API+'/history/9223372036854775807',404,'SUMMER_RESOURCE_REVISION_NOT_FOUND')]:
  body,headers=read(admin,path,status);assert body['error']['code']==code and body.get('requestId') and headers.get('X-Request-Id')==body['requestId']
 passed(requests=3)
 stage=PLANNED[9];env.stop(23081);start('original');original=catalog(admin);assert original['revision']!=base['revision'];delta,_=read(admin,API+'/diff?from='+str(base['revision'])+'&to='+str(original['revision']));assert '/api/acceptance/catalog/added' in {r['normalizedPath'] for r in delta['data']['added']}
 read(admin,'/api/acceptance/catalog/reused',403);fixture.route('GET','/api/acceptance/catalog/reused');admin=Client().login(23081,env.username,env.password);read(admin,'/api/acceptance/catalog/reused',200);passed(revision=original['revision'],snapshot_sha256=save_snapshot('original-snapshot',original))
 stage=PLANNED[10];env.stop(23081);start();removed=catalog(admin);assert '/api/acceptance/catalog/added' not in paths(removed)
 historical,_=read(admin,API+'/history/'+str(original['revision']));assert historical['data']==original
 delta,_=read(admin,API+'/diff?from='+str(original['revision'])+'&to='+str(removed['revision']));assert '/api/acceptance/catalog/added' in {r['normalizedPath'] for r in delta['data']['removed']}
 assert env.sql('select count(*) from summer_authorization_capability where code='+literal('GET:/api/acceptance/catalog/reused'),env.schema)=='1';passed(revision=removed['revision'])
 stage=PLANNED[11];env.stop(23081);start('replacement');read(admin,'/api/acceptance/catalog/reused',403);replacement=catalog(admin)
 protected=[row for row in replacement['items'] if row['normalizedPath']=='/api/acceptance/catalog/reused'];assert protected and all(not row['configurable'] and row['configurationPolicy']=='REPLACED_REQUIRES_EXPLICIT_PERMISSION' for row in protected)
 delta,_=read(admin,API+'/diff?from='+str(original['revision'])+'&to='+str(replacement['revision']));assert any(row['kind']=='REPLACED' for row in delta['data']['changed']);passed(revision=replacement['revision'],snapshot_sha256=save_snapshot('replacement-snapshot',replacement))
 stage=PLANNED[12];env.stop(23082);start('replacement',23082);assert catalog(admin,23082)==replacement;read(admin,'/api/acceptance/catalog/reused',403,23082);passed()
 stage=PLANNED[13];counts=[row['catalog_counts'] for row in env.diagnostics if 'catalog_counts' in row];assert any(int(row[1])>0 for row in counts) and any(int(row[2])>0 for row in counts) and any(int(row[3])>0 for row in counts);passed(observations=len(counts))
 stage=PLANNED[14];runenv=os.environ.copy();runenv.update({'SUMMER_E2E_USERNAME':env.username,'SUMMER_E2E_PASSWORD':env.password,'CATALOG_DENIED_USERNAME':env.denied_username,'CATALOG_DENIED_PASSWORD':env.denied_password,'CATALOG_OUTPUT':str(OUT),'CATALOG_ORIGINAL':str(original['revision']),'CATALOG_REPLACEMENT':str(replacement['revision']),'CATALOG_VITE_CACHE':str(OUT/('vite-cache-'+env.run))})
 ui=subprocess.Popen(['/opt/homebrew/opt/node@22/bin/node',str(SCRIPT/'ui-server.mjs')],cwd=ROOT/'summer-ui',env=runenv,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True);assert ui.stdout.readline().strip()=='UI_READY'
 browser=subprocess.Popen(['/opt/homebrew/opt/node@22/bin/node',str(SCRIPT/'browser.mjs')],cwd=ROOT/'summer-ui',env=runenv,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
 for line in browser.stdout:
  if line.strip()=='CONTROL stop':env.stop(23081);browser.stdin.write('ACK\n');browser.stdin.flush()
  elif line.strip()=='CONTROL restart':start('replacement');browser.stdin.write('ACK\n');browser.stdin.flush()
 browser.wait(timeout=30);assert browser.returncode==0,'browser_failure';report=json.loads((OUT/'browser-result.json').read_text());assert report['status']=='PASSED';passed(browser_tests=report['tests'])
 stage=PLANNED[15];env.stop(23081);start('opaque');opaque=catalog(admin);opaque_snapshot=save_snapshot('opaque-snapshot',opaque)
 assert opaque['complete'] is False and len(opaque['issues'])==1
 assert opaque['issues'][0]['code']=='SUMMER_RESOURCE_DISCOVERY_UNSUPPORTED' and opaque['issues'][0]['source']=='acceptance.InterfaceResourceAcceptanceConfiguration#opaque'
 assert '/api/acceptance/catalog/explicit' in paths(opaque);passed(complete=opaque['complete'],issues=opaque['issues'],snapshot_sha256=opaque_snapshot)
 stage=PLANNED[16];env.stop(23081);before=len(env.diagnostics)
 try:start('conflict')
 except RuntimeError:pass
 else:raise AssertionError('conflict_startup_accepted')
 assert any(row.get('catalog_conflict') for row in env.diagnostics[before:]);passed()
 result['status']='READY_REAL_T14_ACCEPTANCE'
except Exception as error:
 result.update(status='FAILED',failedStage=stage,failureType=type(error).__name__,failures=1)
finally:
 for process in [browser,ui]:
  if process and process.poll() is None:
   process.terminate()
   try:process.wait(timeout=15)
   except subprocess.TimeoutExpired:process.kill();process.wait()
 if env:
  result['diagnostics']=env.diagnostics
  try:env.close();result['cleanup']='VERIFIED'
  except Exception as error:result['cleanup']='FAILED';result['cleanupFailureType']=type(error).__name__;result['errors']+=1
 result['http_observations']=Client.observations
 result['tests']=len(result['checks'])+result['failures'];result['skipped']=len(PLANNED)-result['tests']
 (OUT/'result.json').write_text(json.dumps(result,indent=2));print(json.dumps({key:result.get(key) for key in ['status','tests','failures','errors','skipped','failedStage','cleanup']}),flush=True)
raise SystemExit(0 if result.get('status')=='READY_REAL_T14_ACCEPTANCE' and result.get('cleanup')=='VERIFIED' else 1)
