import os,sys,json,time,subprocess,hashlib,urllib.parse
from pathlib import Path
from environment import Environment,Client,OUT,ROOT,SCRIPT,immutable_candidate
from strict_fixtures import Fixtures,literal

env=None;ui=None;result={'stage':'setup','checks':[],'gaps':[]};candidate=immutable_candidate()
def check(name,condition,**facts):
 result['checks'].append({'name':name,'passed':bool(condition),**facts});(OUT/'result.json').write_text(json.dumps(result,indent=2));print(name, 'PASS' if condition else 'FAIL',flush=True)
def unwrap(data):return data.get('data',data) if isinstance(data,dict) else data
try:
 env=Environment();result['candidate_sha256']=hashlib.sha256(candidate.read_bytes()).hexdigest();env.setup();env.start(20081,jar=candidate)
 result['stage']='fixtures';f=Fixtures(env).seed();admin=f.admin
 client=admin
 duplicate='unique'+env.run
 status,_,_=client.request(20081,'/api/customers','POST',{'username':duplicate,'email':duplicate+'@example.test','displayName':'Unique fixture'});assert status==200
 checks=[]
 for column,values in [('username',(duplicate,'different@example.test')),('email',('different'+env.run,duplicate+'@example.test'))]:
  sql="INSERT INTO summer_customer (username,email,display_name,active,created_at,updated_at) VALUES ("+','.join(["'"+values[0]+"'","'"+values[1]+"'","'Constraint probe'",'true','CURRENT_TIMESTAMP','CURRENT_TIMESTAMP'])+")"
  try:env.sql(sql,env.schema);denied=False
  except RuntimeError as error:denied=str(error)=='sql_error_1062'
  checks.append({'name':'mysql_unique_'+column,'passed':denied})
 status,body,_=client.request(20081,'/api/customers','POST',{'username':'different'+env.run,'email':duplicate+'@example.test','displayName':'Email conflict'});checks.append({'name':'http_unique_email_conflict','passed':status==409})
 pages=[]
 for direction in ['asc','desc']:
  status,body,_=client.request(20081,'/api/customers?size=100&sort=username&direction='+direction);items=body['data']['items'];names=[i['username'] for i in items];checks.append({'name':'real_mysql_sort_'+direction,'passed':status==200 and names==sorted(names,reverse=direction=='desc')})
 for index in [0,1]:
  status,body,_=client.request(20081,'/api/customers?size=2&page='+str(index));pages.append([i['username'] for i in body['data']['items']])
 checks.append({'name':'real_mysql_disjoint_pages','passed':len(pages[0])==2 and not(set(pages[0])&set(pages[1]))})
 result['checks'].extend(checks)
 check('mysql_schema_limited_nonroot_runtime',env.db_user!='root')
 check('anonymous_customer_denied',Client().request(20081,'/api/customers')[0] in [401,403])
 status,data,h=admin.request(20081,'/api/customers?page=0&size=1&sort=username&direction=asc');check('real_mysql_customer_paging',status==200 and len(unwrap(data)['items'])==1,status=status)
 runenv=os.environ.copy();runenv.update({'SUMMER_E2E_USERNAME':env.username,'SUMMER_E2E_PASSWORD':env.password,'SUMMER_E2E_RUN':env.run})
 ui=subprocess.Popen([os.environ.get('SUMMER_FOUNDATION_NODE','/opt/homebrew/opt/node@22/bin/node'),str(SCRIPT/'ui-server.mjs')],cwd=ROOT/'summer-ui',env=runenv,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
 assert ui.stdout.readline().strip()=='UI_READY','ui_start_failed'
 def browser(phase):
  run=subprocess.run([os.environ.get('SUMMER_FOUNDATION_NODE','/opt/homebrew/opt/node@22/bin/node'),str(SCRIPT/'browser.mjs'),phase],cwd=ROOT/'summer-ui',env=runenv,capture_output=True,text=True,timeout=180)
  check('real_browser_'+phase,run.returncode==0)
  if run.returncode:raise RuntimeError('browser_'+phase+'_failed')
 result['stage']='customer_ui_before_restart';browser('before')
 name='foundation'+env.run
 status,data,_=admin.request(20081,'/api/customers','POST',{'username':name,'email':'duplicate@example.test','displayName':'Duplicate'});check('mysql_unique_username_conflict',status==409,status=status)
 status,data,h=admin.request(20081,'/api/customers?size=0',headers={'X-Request-Id':'foundation-invalid-'+env.run});check('invalid_parameter_envelope_and_correlation',status==400 and data.get('requestId')==h.get('X-Request-Id') and data.get('error',{}).get('code','').startswith('SUMMER_'),status=status)
 history=env.sql('SELECT version,checksum,success FROM flyway_schema_history ORDER BY installed_rank',env.schema)
 result['stage']='restart';env.stop(20081);env.start(20081,jar=candidate);admin=Client().login(20081,env.username,env.password)
 status,data,_=admin.request(20081,'/api/customers/'+name);check('customer_survives_restart',status==200 and unwrap(data)['active']==False and unwrap(data)['displayName']=='Edited foundation')
 check('flyway_repeat_preserves_history',env.sql('SELECT version,checksum,success FROM flyway_schema_history ORDER BY installed_rank',env.schema)==history)
 result['stage']='customer_ui_after_restart';browser('after')
 status,data,_=admin.request(20081,'/api/customers/'+name);check('real_browser_delete_persists',status==404,status=status)
 status,data,_=admin.request(20081,'/api/audit-events?resource='+urllib.parse.quote('customer:'+name)+'&size=100');events=unwrap(data)['items']
 actions={e['action'] for e in events};expected={'CUSTOMER_CREATED','CUSTOMER_UPDATED','CUSTOMER_DEACTIVATED','CUSTOMER_REACTIVATED','CUSTOMER_DELETED'}
 check('audit_five_real_crud_actions',expected<=actions,event_count=len(events))
 check('audit_subject_tenant_requestid',all(e['principal']==env.username and e['tenantId']=='ent-acme' and e['requestId'] for e in events))
 check('audit_organization_null_without_selected_context',all(e['organizationId'] is None for e in events),context_selected=False)
 # Exercise the actual runtime account; transaction rolls back any successful mutation.
 statement="USE `"+env.schema+"`; START TRANSACTION; UPDATE summer_audit_event SET result='FAILURE' WHERE event_id="+literal(events[0]['eventId'])+"; DELETE FROM summer_audit_event WHERE event_id="+literal(events[0]['eventId'])+"; ROLLBACK;"
 args=['kubectl','exec','-i','-n','local-base','mysql-0','--','sh','-c','IFS= read -r MYSQL_PWD; export MYSQL_PWD; IFS= read -r MYSQL_USER; exec mysql -u"$MYSQL_USER" --batch --skip-column-names']
 q=subprocess.run(args,input=env.db_password+'\n'+env.db_user+'\n'+statement,text=True,capture_output=True)
 result['supplemental_database_admin_boundary']={'mutation_succeeded':q.returncode==0,'rolled_back':True,'acceptance_gate':False,'reason':'T04 requires append-only application API, not DBA resistance'}
 browser('extra')
 result['stage']='complete'
except Exception as error:
 result['failure_type']=type(error).__name__;result['failure_assertion']=str(error) if isinstance(error,AssertionError) or (isinstance(error,RuntimeError) and not str(error).startswith('http')) else None
finally:
 if ui:
  ui.terminate()
  try:ui.wait(timeout=15)
  except subprocess.TimeoutExpired:ui.kill();ui.wait()
 if env:
  try:env.close();result['cleanup']='VERIFIED'
  except Exception as error:result['cleanup']='FAILED';result['cleanup_failure_type']=type(error).__name__
 result['stage_exceptions']=env.diagnostics if env else []
 (OUT/'result.json').write_text(json.dumps(result,indent=2));print(json.dumps({'stage':result['stage'],'failure_type':result.get('failure_type'),'cleanup':result.get('cleanup'),'checks':len(result['checks'])}),flush=True)

raise SystemExit(0 if result.get('stage')=='complete' and all(c['passed'] for c in result['checks']) and result.get('cleanup')=='VERIFIED' else 1)
