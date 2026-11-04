"""T19 real MySQL/PKCE acceptance; mutations use production HTTP only."""
import hashlib,json,os,secrets,shutil,uuid
from environment import Environment,Client,ROOT,OUT,SCRIPT,immutable_candidate
from grants import bootstrap,create_role,bind
B='/api/v1/enterprises/ent-acme'; P=B+'/system-parameters'; D=B+'/dictionaries'
result={'issue':127,'stage':'initialization','checks':[],'networkMocking':False,'businessSqlWrites':False};env=None
hidden=[]
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
 wanted=[p+'.'+a for p in ['system.parameter','platform.dictionary'] for a in ['read','create','update','publish','disable','rollback']]+['menu.systemParameters.read','menu.dictionary.read','menu.dictionaryForm.read']
 for code in wanted:
  if code not in codes:
   cap,_=call(admin,B+'/capabilities','POST',{'code':code,'resource':code.rsplit('.',1)[0],'action':code.rsplit('.',1)[1],'riskLevel':'HIGH','scope':'PLATFORM' if not code.startswith('menu.') else 'ENTERPRISE'},201,headers=mh());codes[code]=cap['capabilityId']
 role=create_role(admin,'ent-acme',[codes[c] for c in wanted]+['enterprise.organization.read'],'T19 configuration operator')
 _,h=call(admin,B+'/users/'+env.username+'/roles');call(admin,B+'/users/'+env.username+'/roles','PUT',{'roleIds':[boot,role],'deniedCapabilityCodes':[]},headers=mh(h['ETag']));admin=login(env.username,env.password)
 call(admin,'/api/users','POST',{'username':env.denied_username,'password':env.denied_password,'role':'USER'})
 member,_=call(admin,B+'/members','POST',{'subject':env.denied_username,'displayName':'T19 reader','email':'reader@example.test'},201,headers=mh());bind(admin,'ent-acme',env.denied_username,[role]);reader=login(env.denied_username,env.denied_password)
 for resource in ['system-parameters','dictionaries']:scope(resource,{'field':'TENANT'})
 key='authorized.'+env.run;code='authorized-'+env.run
 baseval=secrets.token_urlsafe(18); hidden.append(baseval);env.additional_secrets=hidden
 p,_=call(admin,P,'POST',{'key':key,'type':'STRING','value':baseval,'public':True},201)
 d,_=call(admin,D,'POST',{'code':code,'name':'T19 visible dictionary','published':False,'publicReadable':True})
 call(admin,D+'/'+code+'/items','POST',{'value':'alpha','label':'base','sortOrder':0,'enabled':True})
 call(reader,D+'/'+code+'/items','POST',{'value':'beta','label':'reader-created','sortOrder':1,'enabled':True})
 call(admin,D+'/'+code+'/publish','POST',{'expectedVersion':0,'idempotencyKey':uuid.uuid4().hex})
 check('parameter_public_base_exact',pub('system-parameters',key)['value']==baseval)
 check('dictionary_public_base_exact',len(pub('dictionaries',code)['items'])==2)
 result['stage']='persistent_action_denials'
 if not os.environ.get('SUMMER_AUTHORIZED_CONFIGURATION_SKIP_ACTIONS'):
  for resource,k,prefix,root in [('system-parameters',key,'system.parameter',P),('dictionaries',code,'platform.dictionary',D)]:
   actions={'read':('GET',root+'/'+k if resource=='system-parameters' else root,None),'create':('POST',root,{'key':'denied.'+env.run,'type':'STRING','value':'fixture'} if resource=='system-parameters' else {'code':'denied-'+env.run,'name':'denied'}),'update':('PATCH',root+'/'+k,{'description':'denied'} if resource=='system-parameters' else {'name':'denied'}),'publish':('POST',root+'/'+k+'/publish',{'revision':1,'expectedVersion':0}),'disable':('POST',root+'/'+k+('/disable' if resource=='dictionaries' else '/deactivate'),{'expectedVersion':0}),'rollback':('POST',root+'/'+k+'/rollback',{'revision':1,'targetVersion':1,'expectedVersion':0})}
   for action,(method,path,body) in actions.items():
    binding([prefix+'.'+action]);call(reader,path,method,body,403,name=resource+'_'+action+'_persisted_denial_existing_token',strict=False)
    if action=='read':
     pub(resource,k,expected=403,name=resource+'_public_persisted_read_denial')
     if resource=='dictionaries':call(reader,'/public/enterprises/ent-acme/dictionaries/'+k+'/items/alpha',expected=403,name='dictionary_selected_item_persisted_read_denial')
   binding([])
 result['stage']='override_lifecycle'
 for resource,k,root in [('system-parameters',key,P),('dictionaries',code,D)]:
  path=root+'/'+k+'/overrides/_tenant';base=baseval if resource=='system-parameters' else 'base'
  draft,_=call(admin,path+'/draft','POST',override_body(resource,'tenant'))
  check(resource+'_draft_does_not_change_runtime',value(resource,pub(resource,k))==base)
  published,_=call(admin,path+'/publish','POST',{'revision':draft['revision'],'expectedVersion':draft['version']})
  check(resource+'_tenant_precedence',value(resource,pub(resource,k))=='tenant')
  for action,body in [('update',override_body(resource,'denied',published['version'])),('publish',{'revision':1,'expectedVersion':published['version']}),('disable',{'expectedVersion':published['version']}),('rollback',{'revision':2,'expectedVersion':published['version']})]:
   prefix='system.parameter' if resource=='system-parameters' else 'platform.dictionary';binding([prefix+'.'+action]);before=state();call(reader,path+('/draft' if action=='update' else '/'+action),'POST',body,403,name=resource+'_override_'+action+'_denied',strict=False);check(resource+'_denied_'+action+'_preserves_override',state()==before)
  binding([])
  disabled,_=call(admin,path+'/disable','POST',{'expectedVersion':published['version']});pub(resource,k,expected=404,name=resource+'_disabled_tenant_blocks_base')
  restored,_=call(admin,path+'/rollback','POST',{'revision':published['revision'],'expectedVersion':disabled['version']});check(resource+'_override_rollback_restores',value(resource,pub(resource,k))=='tenant')
  before=state();call(admin,path+'/publish','POST',{'revision':1,'expectedVersion':published['version']},409,name=resource+'_stale_override_conflict');check(resource+'_stale_preserves_version',state()==before)
  invalid=override_body(resource,{'bad':True} if resource=='system-parameters' else 'invalid',restored['version'])
  if resource=='dictionaries':invalid['value'][0]['value']='absent'
  call(admin,path+'/draft','POST',invalid,400,name=resource+'_invalid_override_rejected');check(resource+'_invalid_preserves_version',state()==before)
  history,_=call(admin,path+'/versions');check(resource+'_immutable_four_revision_history',len(history)==4 and {r['state'] for r in history}=={'DRAFT','PUBLISHED','DISABLED','ROLLED_BACK'})
  orgpath=root+'/'+k+'/overrides/org-engineering';org,_=call(admin,orgpath,'PUT',override_body(resource,'organization'))
  # Assign and select actual organization; issue a fresh token for the same persisted subject.
  _,h=call(admin,B+'/org-tree');call(admin,B+'/members/'+member['memberId']+'/org-assignments','PUT',{'organizationIds':['org-engineering'],'primaryOrganizationId':'org-engineering'},headers=mh(h['ETag']))
  call(reader,B+'/context','PUT',{'organizationId':'org-engineering'});reader=login(env.denied_username,env.denied_password)
  check(resource+'_organization_over_tenant_over_base',value(resource,pub(resource,k))=='organization')
  org,_=call(admin,orgpath,'PUT',override_body(resource,'private',org['version'],public=False));pub(resource,k,expected=404,name=resource+'_private_org_blocks_tenant_and_base')
  org,_=call(admin,orgpath,'PUT',override_body(resource,'organization',org['version']))
  deny_scope(resource,{'field':'ORGANIZATION','value':'org-engineering'});pub(resource,k,expected=404,name=resource+'_scope_hidden_org_blocks_fallback')
  call(reader,orgpath+'/versions',expected=404,name=resource+'_hidden_override_history_no_values')
  deny_scope(resource,{'field':'ORGANIZATION','value':'org-operations'});check(resource+'_visible_org_restores',value(resource,pub(resource,k))=='organization')
  # Clear context so the following resource starts at tenant precedence.
  call(reader,B+'/context','PUT',{'organizationId':None});reader=login(env.denied_username,env.denied_password)
 result['stage']='visibility'
 deny_scope('dictionaries',{'field':'CREATOR','value':env.denied_username})
 listed,_=call(reader,D);target=next(x for x in listed if x['code']==code);check('dictionary_management_item_scope_filters_before_projection',[x['value'] for x in target['items']]==['alpha'])
 check('dictionary_public_item_scope_filters_snapshot',[x['value'] for x in pub('dictionaries',code)['items']]==['alpha'])
 call(reader,'/public/enterprises/ent-acme/dictionaries/'+code+'/items/beta',expected=404,name='hidden_single_item_returns_correlated_not_found')
 for resource,k,root in [('system-parameters',key,P),('dictionaries',code,D)]:
  deny_scope(resource,{'field':'CREATOR','value':env.username});data,_=call(reader,root+('?key='+key if resource=='system-parameters' else ''))
  items=data['items'] if resource=='system-parameters' else data;check(resource+'_hidden_management_list_no_key_or_value',not any((x.get('key') or x.get('code'))==k for x in items) and baseval not in json.dumps(data))
  pub(resource,k,expected=404,name=resource+'_hidden_public_404_no_value');call(reader,root+'/'+k+'/versions',expected=404,name=resource+'_hidden_versions_404_no_value')
  if resource=='system-parameters':call(reader,root+'/'+k,expected=404,name='hidden_parameter_detail_no_value')
  deny_scope(resource,{'field':'ORGANIZATION','value':'org-operations'})
 result['stage']='audit_fault'
 before=state();auditbefore=env.sql("SELECT COUNT(*) FROM summer_audit_event WHERE action LIKE 'CONFIGURATION_OVERRIDE_%'",env.schema)
 env.sql("CREATE TRIGGER t19_override_audit_failure BEFORE INSERT ON summer_audit_event FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='controlled acceptance fault'",env.schema)
 try:call(admin,P+'/'+key+'/overrides/_tenant/draft','POST',override_body('system-parameters','fault',3),500,name='real_mysql_override_audit_fault',strict=False)
 finally:env.sql('DROP TRIGGER t19_override_audit_failure',env.schema)
 check('audit_fault_preserves_override_and_audit_count',state()==before and env.sql("SELECT COUNT(*) FROM summer_audit_event WHERE action LIKE 'CONFIGURATION_OVERRIDE_%'",env.schema)==auditbefore)
 call(admin,P+'/'+key+'/overrides/_tenant/draft','POST',override_body('system-parameters','recovered',3),name='same_jvm_override_recovers')
 audit=env.sql("SELECT action,principal,request_id,detail_json FROM summer_audit_event WHERE action LIKE 'CONFIGURATION_OVERRIDE_%'",env.schema)
 check('override_audit_actions_principal_request_and_no_values',all('CONFIGURATION_OVERRIDE_'+s in audit for s in ['DRAFT','PUBLISHED','DISABLED','ROLLED_BACK']) and env.username in audit and 't19-' in audit and not any(v in audit for v in hidden))
 result['stage']='complete';check('sensitive_output_scan_clear',not env.sensitive_log_detected)
except Exception as error:
 result['errorType']=type(error).__name__
 if isinstance(error,AssertionError):result['safeAssertion']=str(error)
finally:
 if env:
  result['diagnostics']=env.diagnostics;result['sensitiveLogDetected']=env.sensitive_log_detected
  try:env.close();result['cleanup']='VERIFIED'
  except Exception as error:result['cleanupErrorType']=type(error).__name__
 if 'sources'in globals():result['changedSourcePaths']=[p for p,h in sources.items() if hashlib.sha256((ROOT/p).read_bytes()).hexdigest()!=h]
 (OUT/'http-observations.json').write_text(json.dumps(Client.observations,indent=2));save()
print(json.dumps({'stage':result['stage'],'counts':result['counts'],'cleanup':result.get('cleanup'),'errorType':result.get('errorType')}))
raise SystemExit(0 if result['stage']=='complete' and not result['counts']['failures'] and not result['counts']['errors'] and result.get('cleanup')=='VERIFIED' else 1)
