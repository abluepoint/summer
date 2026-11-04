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
 result['stage']='arbitrary_tenants'
 tenants=[]
 for index in range(2):
  tenant,_=call(admin,'/api/v1/platform/enterprises','POST',{'name':'T19 tenant '+str(index),'code':'authz-'+env.run+'-'+str(index)},201);tid=tenant['enterpriseId'];tenants.append(tid);tb='/api/v1/enterprises/'+tid
  admin=login(env.username,env.password)
  tr=create_role(admin,tid,wanted+['enterprise.data-scope.read','enterprise.data-scope.write','enterprise.organization.read'],'T19 isolated configuration manager')
  b,h=call(admin,tb+'/users/'+env.username+'/roles');call(admin,tb+'/users/'+env.username+'/roles','PUT',{'roleIds':b['roleIds']+[tr],'deniedCapabilityCodes':[]},headers=mh(h['ETag']));admin=login(env.username,env.password)
  for resource in ['system-parameters','dictionaries']:call(admin,tb+'/data-scopes/'+resource,'PUT',{'conditions':{'field':'TENANT'}})
  call(admin,tb+'/system-parameters','POST',{'key':key,'type':'STRING','value':'tenant-'+str(index),'public':True},201)
  call(admin,tb+'/dictionaries','POST',{'code':code,'name':'isolated','published':False,'publicReadable':True})
  call(admin,tb+'/dictionaries/'+code+'/items','POST',{'value':'alpha','label':'tenant-'+str(index),'sortOrder':0,'enabled':True})
  call(admin,tb+'/dictionaries/'+code+'/publish','POST',{'expectedVersion':0,'idempotencyKey':uuid.uuid4().hex})
  call(admin,tb+'/system-parameters/'+key+'/overrides/_tenant','PUT',override_body('system-parameters','override-'+str(index)))
  call(admin,tb+'/dictionaries/'+code+'/overrides/_tenant','PUT',override_body('dictionaries','override-'+str(index)))
  # Explicit tenant without membership cannot borrow another tenant's roles or cache.
  for resource,k in [('system-parameters',key),('dictionaries',code)]:call(reader,'/public/enterprises/'+tid+'/'+resource+'/'+k,expected=403,name=resource+'_nonmember_tenant_'+str(index)+'_denied')
  call(admin,tb+'/members','POST',{'subject':env.denied_username,'displayName':'T19 tenant reader','email':'reader@example.test'},201,headers=mh());bind(admin,tid,env.denied_username,[tr])
 reader=login(env.denied_username,env.denied_password)
 import base64
 claims=json.loads(base64.urlsafe_b64decode(reader.token.split('.')[1]+'=='))
 result['grantDiagnostics']={'memberships':len(claims.get('enterprise_memberships',[])),'dictionaryReadInPlatformClaims':'platform.dictionary.read' in claims.get('platform_capabilities',[]),'dictionaryReadByTenant':[{'tenant':t,'present':'platform.dictionary.read' in claims.get('enterprise_capabilities',{}).get(t,[])}for t in tenants]}
 result['capabilityDiagnostics']=env.sql("SELECT capability_id,COALESCE(enterprise_id,'GLOBAL'),scope,code,status FROM summer_authorization_capability WHERE code='platform.dictionary.read'",env.schema).splitlines();save()
 for index in [0,1,0]:
  tid=tenants[index];tb='/api/v1/enterprises/'+tid
  for resource,k in [('system-parameters',key),('dictionaries',code)]:
   view,_=call(reader,'/public/enterprises/'+tid+'/'+resource+'/'+k);check(resource+'_same_key_isolated_override_'+str(index),value(resource,view)=='override-'+str(index))
   anonymous,_=call(Client(),'/public/enterprises/'+tid+'/'+resource+'/'+k);check(resource+'_anonymous_explicit_tenant_scope_'+str(index),value(resource,anonymous)=='override-'+str(index))
   history,_=call(reader,tb+'/'+resource+'/'+k+'/overrides/_tenant/versions');check(resource+'_tenant_history_contains_only_own_revision_'+str(index),len(history)==1 and (history[0]['value']=='override-'+str(index) if resource=='system-parameters' else history[0]['value'][0]['label']=='override-'+str(index)))
 result['stage']='selector_denials'
 for resource,k in [('system-parameters',key),('dictionaries',code)]:
  call(reader,'/public/enterprises/'+tenants[0]+'/'+resource+'/'+k,headers={'X-Enterprise-Id':tenants[1]},expected=403,name=resource+'_conflicting_tenant_selectors_rejected',strict=False)
  call(Client(),'/public/'+resource+'/'+k,expected=403,name=resource+'_anonymous_without_tenant_fails_closed',strict=False)
  call(reader,'/public/enterprises/ent-missing/'+resource+'/'+k,expected=403,name=resource+'_unknown_tenant_fails_closed',strict=False)
 result['stage']='additional_visibility_edges'
 _,h=call(admin,B+'/org-tree');call(admin,B+'/members/'+member['memberId']+'/org-assignments','PUT',{'organizationIds':['org-engineering'],'primaryOrganizationId':'org-engineering'},headers=mh(h['ETag']))
 call(reader,B+'/context','PUT',{'organizationId':'org-engineering'});reader=login(env.denied_username,env.denied_password)
 for resource,k,root,prefix in [('system-parameters',key,P,'system.parameter'),('dictionaries',code,D,'platform.dictionary')]:
  op=root+'/'+k+'/overrides/org-engineering'
  binding([prefix+'.create']);call(reader,op,'PUT',override_body(resource,'denied'),403,name=resource+'_new_immediate_override_requires_create');call(reader,op+'/draft','POST',override_body(resource,'denied'),403,name=resource+'_new_override_draft_requires_create');binding([])
  call(admin,root+'/'+k+'/overrides/_tenant','PUT',override_body(resource,'tenant'))
  org,_=call(admin,op,'PUT',override_body(resource,'organization'))
  disabled,_=call(admin,op+'/disable','POST',{'expectedVersion':org['version']});pub(resource,k,expected=404,name=resource+'_disabled_organization_blocks_visible_tenant')
  call(admin,op+'/rollback','POST',{'revision':org['revision'],'expectedVersion':disabled['version']});check(resource+'_organization_rollback_restores_precedence',value(resource,pub(resource,k))=='organization')
  private={'publicValue':False,'version':call(admin,root+'/'+k)[0]['version']} if resource=='system-parameters' else {'name':'private','publicReadable':False}
  call(admin,root+'/'+k,'PATCH',private)
  pub(resource,k,expected=404,name=resource+'_private_base_blocks_public_override');pub(resource,k,c=Client(),expected=404,name=resource+'_private_base_anonymous_hidden')
  private={'publicValue':True,'version':call(admin,root+'/'+k)[0]['version']} if resource=='system-parameters' else {'name':'public','publicReadable':True};call(admin,root+'/'+k,'PATCH',private)
  scope(resource,{'field':'OWNER'});pub(resource,k,c=Client(),expected=404,name=resource+'_anonymous_has_no_owner_scope');scope(resource,{'field':'TENANT'})
 migration=env.sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('29','30','32','33') AND success=1",env.schema)
 check('required_binding_override_tenant_revision_migrations_applied',migration=='4')
 # Each tenant retains independently versioned override and audit evidence after interleaved reads.
 rows=env.sql("SELECT tenant_id,resource,version,latest_revision FROM summer_configuration_override WHERE tenant_id IN ('"+"','".join(tenants)+"') ORDER BY tenant_id,resource",env.schema)
 check('two_arbitrary_tenants_independent_override_rows',len(rows.splitlines())==4 and all(line.endswith('\t0\t1') for line in rows.splitlines()))
 audit=env.sql("SELECT tenant_id,COUNT(*) FROM summer_audit_event WHERE action='CONFIGURATION_OVERRIDE_PUBLISHED' AND tenant_id IN ('"+"','".join(tenants)+"') GROUP BY tenant_id",env.schema)
 check('override_audit_partitioned_by_real_tenant',len(audit.splitlines())==2 and all(line.endswith('\t2') for line in audit.splitlines()))
 result['stage']='complete';check('sensitive_output_scan_clear',not env.sensitive_log_detected)
except Exception as error:
 if env and env.created_schema:
  try:result['denialDiagnostics']=env.sql("SELECT enterprise_id,permission_code,reason_code FROM summer_authorization_audit WHERE action='AUTHORIZATION_DENIED' AND principal='"+env.denied_username+"' ORDER BY audit_id DESC LIMIT 8",env.schema).splitlines()
  except Exception:result['denialDiagnosticsUnavailable']=True
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
