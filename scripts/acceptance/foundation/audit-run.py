import os,json,subprocess
from environment import Environment,OUT,ROOT,SCRIPT,immutable_candidate
from strict_fixtures import Fixtures

env=None;ui=None;result={'stage':'setup'}
try:
 env=Environment();env.setup();env.start(20081,jar=immutable_candidate());fixture=Fixtures(env).seed();client=fixture.admin
 name='foundation'+env.run
 status,_,_=client.request(20081,'/api/customers','POST',{'username':name,'email':name+'@example.test','displayName':'Audit pagination'});assert status==200
 for i in range(25):
  status,_,_=client.request(20081,'/api/customers/'+name,'PATCH',{'displayName':'Audit change '+str(i)});assert status==200
 status,_,_=client.request(20081,'/api/customers/'+name,'DELETE');assert status==204
 result['real_mutations']=27;result['stage']='browser'
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
 status,_,_=client.request(20081,'/api/audit-events?tenantId=ent-other');checks.append({'name':'audit_cross_tenant_query_denied','passed':status==403})
 status,_,_=fixture.denied.request(20081,'/internal/v1/diagnostics/redis');checks.append({'name':'real_reader_diagnostic_403','passed':status==403})
 result['checks']=checks

 e=os.environ.copy();e.update({'SUMMER_E2E_USERNAME':env.username,'SUMMER_E2E_PASSWORD':env.password,'SUMMER_E2E_RUN':env.run})
 ui=subprocess.Popen([os.environ.get('SUMMER_FOUNDATION_NODE','/opt/homebrew/opt/node@22/bin/node'),str(SCRIPT/'ui-server.mjs')],cwd=ROOT/'summer-ui',env=e,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True);assert ui.stdout.readline().strip()=='UI_READY'
 extra=subprocess.run([os.environ.get('SUMMER_FOUNDATION_NODE','/opt/homebrew/opt/node@22/bin/node'),str(SCRIPT/'browser.mjs'),'extra'],cwd=ROOT/'summer-ui',env=e,capture_output=True,text=True,timeout=120);result['extra_browser_exit']=extra.returncode
 reader=e.copy();reader.update({'SUMMER_E2E_USERNAME':env.denied_username,'SUMMER_E2E_PASSWORD':env.denied_password});denied=subprocess.run([os.environ.get('SUMMER_FOUNDATION_NODE','/opt/homebrew/opt/node@22/bin/node'),str(SCRIPT/'browser.mjs'),'denied'],cwd=ROOT/'summer-ui',env=reader,capture_output=True,text=True,timeout=120);result['denied_browser_exit']=denied.returncode
 run=subprocess.run([os.environ.get('SUMMER_FOUNDATION_NODE','/opt/homebrew/opt/node@22/bin/node'),str(SCRIPT/'browser.mjs'),'audit'],cwd=ROOT/'summer-ui',env=e,capture_output=True,text=True,timeout=160);result['exit']=run.returncode
except Exception as error:result['failure_type']=type(error).__name__;result['safe_runtime_failure']=str(error) if isinstance(error,RuntimeError) else None;result['exception_classes']=env.diagnostics if env else []
finally:
 if ui and ui.poll() is None:ui.terminate();ui.wait(timeout=20)
 if env:
  try:env.close();result['cleanup']='VERIFIED'
  except Exception as error:result['cleanup']='FAILED'
 (OUT/'audit-runtime-result.json').write_text(json.dumps(result,indent=2));print(json.dumps(result),flush=True)

raise SystemExit(0 if result.get('exit')==0 and result.get('extra_browser_exit')==0 and result.get('denied_browser_exit')==0 and all(c['passed'] for c in result.get('checks',[])) and result.get('cleanup')=='VERIFIED' else 1)
