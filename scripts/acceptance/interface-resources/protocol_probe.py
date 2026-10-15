"""Production candidate only: protocol metadata, retained security, MySQL restart persistence."""
import hashlib,json,os
from pathlib import Path
from environment import Environment,Client,OUT,ROOT
from fixtures import Fixtures
candidate=Path(os.environ.get('CATALOG_CANDIDATE_PATH',str(ROOT/'.git/summer4-closeout-20261015/runtime/candidate-4c81/candidate.jar')))
expected=os.environ.get('CATALOG_CANDIDATE_SHA256','4c81a803dd19f610999a8a8b5b336b4a25771af82a981a3a47e79a5c3e86eeae')
planned=['protocol_infrastructure_configurability_ac','production_login_csrf_retained','production_token_client_auth_retained','production_mysql_restart_history_retained']
result={'candidate_sha256':expected,'external_fixture_loaded':False,'checks':[],'failures':0,'errors':0,'skipped':0};env=None;stage=planned[0]
def start():env.start(23081,jar=candidate,extra_properties={'management.server.port':23083})
def mark():result['checks'].append({'name':stage,'status':'passed'})
try:
 assert hashlib.sha256(candidate.read_bytes()).hexdigest()==expected
 env=Environment();env.setup();start();fixture=Fixtures(env).seed()
 status,body,headers=fixture.admin.request(23081,'/api/v1/platform/interface-resources');assert status==200
 view=body['data'];rows=[{k:row[k] for k in ['contextId','httpMethod','normalizedPath','resourceType','source','configurable','configurationPolicy']} for row in view['items'] if row['resourceType'] in ['PROTOCOL','INFRASTRUCTURE']]
 result.update(http_status=status,request_id=body['requestId'],revision=view['revision'],protocol_infrastructure_count=len(rows),non_configurable=[row for row in rows if not row['configurable']])
 snapshot=json.dumps(view,indent=2)
 for secret in [env.password,env.denied_password,env.mysql,env.master,env.redis]:assert secret not in snapshot
 (OUT/'protocol-snapshot.json').write_text(snapshot)
 assert rows and not result['non_configurable'],'protocol_configurability_ac_failed'
 assert any(r['normalizedPath']=='/ws/v1/messages' and r['configurationPolicy']=='COMPONENT_OWNED' for r in rows)
 assert any(r['contextId']=='management' and r['normalizedPath']=='/actuator/health' for r in rows);mark()
 stage=planned[1];anonymous=Client();status,_,_=anonymous.request(23081,'/login','POST','username=invalid',bearer=False);assert status==403;mark()
 stage=planned[2];status,body,_=anonymous.request(23081,'/oauth2/token','POST','grant_type=client_credentials',bearer=False);assert status in (400,401) and not (body and body.get('access_token'));mark()
 stage=planned[3];before=env.sql('select count(*) from summer_interface_resource_revision',env.schema);env.stop(23081);start()
 status,body,_=fixture.admin.request(23081,'/api/v1/platform/interface-resources');assert status==200 and body['data']==view
 assert env.sql('select count(*) from summer_interface_resource_revision',env.schema)==before;mark();result['status']='PASSED'
except Exception as error:result.update(status='RED',failures=1,failure_type=type(error).__name__,failed_stage=stage)
finally:
 if env:
  try:env.close();result['cleanup']='VERIFIED'
  except Exception as error:result['cleanup']='FAILED';result['errors']+=1
 result['tests']=len(result['checks'])+result['failures'];result['skipped']=len(planned)-result['tests'];result['http_observations']=Client.observations
 (OUT/'protocol-result.json').write_text(json.dumps(result,indent=2));print(json.dumps({k:result.get(k) for k in ['status','tests','failures','errors','skipped','external_fixture_loaded','cleanup']}),flush=True)
raise SystemExit(0 if result.get('status')=='PASSED' and result.get('cleanup')=='VERIFIED' else 1)
