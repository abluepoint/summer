"""T35 acceptance against the shipped trusted plugin; no test plugin is loaded."""
import hashlib,json,os,secrets,shutil,uuid
from environment import Environment,Client,ROOT,OUT,SCRIPT,immutable_candidate
from grants import bootstrap,create_role,bind
B='/api/v1/enterprises/ent-acme';PL='/api/v1/platform/plugins';ID='customer-insights';ITEM=PL+'/'+ID
SUMMARY=B+'/plugins/'+ID+'/summary';DIGEST='sha256:customer-insights-v1'
result={'issue':143,'stage':'initialization','checks':[],'networkMocking':False,'testPluginLoaded':False,'businessSqlWrites':False};env=None;hidden=[]
def save():
 c=result['checks'];result['counts']={'tests':len(c),'failures':sum(not x['passed']for x in c),'errors':int('errorType'in result),'skipped':0};t=OUT/'result.tmp';t.write_text(json.dumps(result,indent=2));t.replace(OUT/'result.json')
def check(name,passed,**facts):
 result['checks'].append({'name':name,'passed':bool(passed),**facts});save();print(name,'PASS' if passed else 'FAIL',flush=True)
def mh(v=None):
 h={'Idempotency-Key':uuid.uuid4().hex}
 if v is not None:h['If-Match']=str(v)
 return h
def call(c,path,method='GET',body=None,expected=200,name=None,headers=None,strict=True,port=21081):
 rid='t35-'+uuid.uuid4().hex;s,b,h=c.request(port,path,method,body,headers={'X-Request-Id':rid,**(headers or {})});e=b.get('error')if isinstance(b,dict)else None;code=e.get('code')if isinstance(e,dict)else None
 good=s==expected and isinstance(b,dict)and b.get('success')==(s<400)and b.get('requestId')==h.get('X-Request-Id')==rid
 if s>=400:good=good and isinstance(code,str)and code.startswith('SUMMER_')and b.get('data')is None and not any(v in json.dumps(b)for v in hidden)
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
 result['stage']='trust_lifecycle'
 call(admin,ITEM+':enable','POST',expected=409,name='unregistered_plugin_cannot_enable')
 call(admin,PL,'POST',{'pluginId':'outside-catalog','version':'1.0.0','artifactDigest':DIGEST},409,name='unregistered_artifact_id_rejected')
 call(admin,PL,'POST',{'pluginId':ID,'version':'1.0.0','artifactDigest':'sha256:untrusted'},409,name='wrong_artifact_digest_rejected')
 call(admin,PL,'POST',{'pluginId':'INVALID','version':'','artifactDigest':DIGEST},400,name='invalid_registration_rejected')
 plugin,_=call(admin,PL,'POST',{'pluginId':ID,'version':'1.0.0','artifactDigest':DIGEST},201);check('catalog_accepted_registered_trusted',plugin['trusted']and plugin['status']=='REGISTERED')
 call(admin,ITEM+':enable','POST');d,_=call(admin,ITEM+'/diagnostic');check('enabled_diagnostic_compatible_trusted',d['compatible']and d['trusted']and d['status']=='ENABLED'and not d['problems'])
 call(admin,ITEM+':upgrade','POST',{'version':'9.0.0','artifactDigest':DIGEST});before=rowstate();call(admin,ITEM+':enable','POST',expected=409,name='incompatible_version_cannot_enable');check('incompatible_enable_preserves_persisted_state',before==rowstate())
 d,_=call(admin,ITEM+'/diagnostic');check('incompatibility_diagnostic_has_problems',not d['compatible']and d['status']=='INCOMPATIBLE'and bool(d['problems']))
 call(admin,ITEM+':upgrade','POST',{'version':'1.0.0','artifactDigest':DIGEST});call(admin,ITEM+':enable','POST')
 result['stage']='configuration'
 marker=secrets.token_urlsafe(20);hidden.append(marker);env.additional_secrets=hidden
 label,_=call(admin,B+'/system-parameters','POST',{'key':'customer-insights.label','type':'STRING','value':marker,'public':True,'organizationId':'org-engineering'},201)
 call(admin,B+'/system-parameters','POST',{'key':'customer-insights.segment','type':'STRING','value':'standard','public':True,'organizationId':'org-engineering'},201)
 dp=B+'/dictionaries/customer-segment';call(admin,B+'/dictionaries','POST',{'code':'customer-segment','name':'T35 published segment','publicReadable':True,'organizationId':'org-engineering'})
 call(admin,dp+'/items','POST',{'value':'alpha','label':'T35 published','sortOrder':0,'enabled':True});call(admin,dp+'/publish','POST',{'expectedVersion':0,'idempotencyKey':uuid.uuid4().hex})
 d=summary();check('shipped_plugin_reads_authorized_parameter_and_published_dictionary',config(d)['value']==marker and d['dictionary']['items'][0]['label']=='T35 published'and d['requestId']and d['auditRevision']>0)
 call(admin,dp+'/items/alpha','PATCH',{'label':'T35 draft','sortOrder':0,'enabled':True});check('plugin_uses_snapshot_not_dictionary_draft',summary()['dictionary']['items'][0]['label']=='T35 published')
 result['stage']='persistent_denials'
 for denied in ['enterprise.plugins.customer-insights.read','customer.read','system.parameter.read','platform.dictionary.read']:
  binding([denied]);call(reader,SUMMARY,expected=403,name='plugin_denies_'+denied,strict=False)
 binding(['enterprise.plugins.customer-insights.read']);listed,_=call(reader,B+'/plugins');check('persisted_plugin_menu_denial_filters_manifest',all(not x['menus']for x in listed['items']))
 binding([])
 result['stage']='data_scope'
 for resource in ['system-parameters','dictionaries']:scope(resource,{'field':'CREATOR'})
 d=summary();check('plugin_self_scope_hides_other_creator_values',not config(d)['configured']and 'value'not in config(d)and not d['dictionary']['items'])
 for resource in ['system-parameters','dictionaries']:scope(resource,{'field':'TENANT'})
 # Current organization is selected through production organization APIs before issuing a fresh token.
 _,h=call(admin,B+'/org-tree');call(admin,B+'/members/'+member['memberId']+'/org-assignments','PUT',{'organizationIds':['org-engineering'],'primaryOrganizationId':'org-engineering'},headers=mh(h['ETag']));call(reader,B+'/context','PUT',{'organizationId':'org-engineering'});reader=login(env.denied_username,env.denied_password)
 op=B+'/system-parameters/customer-insights.label/overrides/org-engineering';override,_=call(admin,op,'PUT',{'value':'T35 organization','publicReadable':True,'enabled':True});check('plugin_resolves_current_organization_override',config(summary())['value']=='T35 organization')
 call(admin,op+'/disable','POST',{'expectedVersion':override['version']});d=summary();check('plugin_hidden_override_does_not_fall_back_to_secret_base',not config(d)['configured']and marker not in json.dumps(d))
 call(admin,dp+'/disable','POST',{'expectedVersion':1});d=summary();check('plugin_disabled_dictionary_is_absent',not d['dictionary']['items'])
 result['stage']='lifecycle_and_audit'
 call(admin,ITEM+':disable','POST');call(reader,SUMMARY,expected=409,name='disabled_plugin_summary_blocked');listed,_=call(reader,B+'/plugins');check('disabled_plugin_not_enterprise_manifest',not listed['items']);call(admin,ITEM+':enable','POST')
 audit=env.sql("SELECT action,principal,request_id,detail_json FROM summer_audit_event WHERE resource='plugin:customer-insights'",env.schema);check('plugin_success_lifecycle_and_read_audited_without_configuration_values',all(x in audit for x in ['PLUGIN_REGISTERED','PLUGIN_ENABLED','PLUGIN_UPGRADED','PLUGIN_DISABLED','PLUGIN_CUSTOMER_INSIGHTS_READ'])and't35-'in audit and not any(v in audit for v in hidden))
 before=env.sql("SELECT COUNT(*) FROM summer_audit_event WHERE resource='plugin:customer-insights'",env.schema)
 env.sql("CREATE TRIGGER t35_audit_failure BEFORE INSERT ON summer_audit_event FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='controlled acceptance fault'",env.schema)
 try:call(reader,SUMMARY,expected=500,name='real_mysql_plugin_audit_failure_no_payload',strict=False)
 finally:env.sql('DROP TRIGGER t35_audit_failure',env.schema)
 check('failed_plugin_read_no_success_audit_appended',env.sql("SELECT COUNT(*) FROM summer_audit_event WHERE resource='plugin:customer-insights'",env.schema)==before);summary()
 # Persisted platform denial must apply to an already issued managed token, too.
 binding(['POST:/api/v1/platform/plugins/{pluginId}:disable'],env.username,[boot,role]);before=rowstate();call(admin,ITEM+':disable','POST',expected=403,name='persisted_platform_interface_denial_existing_token',strict=False);check('denied_platform_lifecycle_preserves_state',rowstate()==before)
 binding(['platform.plugins.write'],env.username,[boot,role]);admin=login(env.username,env.password);call(admin,ITEM+':disable','POST',expected=403,name='fresh_identity_without_platform_write_claim_denied');binding([],env.username,[boot,role])
 result['stage']='complete';check('sensitive_output_scan_clear',not env.sensitive_log_detected)
except Exception as error:
 if env and env.created_schema:
  try:result['denialDiagnostics']=env.sql("SELECT permission_code,reason_code FROM summer_authorization_audit WHERE action='AUTHORIZATION_DENIED' ORDER BY audit_id DESC LIMIT 8",env.schema).splitlines()
  except Exception:pass
 result['errorType']=type(error).__name__
 if isinstance(error,AssertionError):result['safeAssertion']=str(error)
finally:
 if env:
  result['diagnostics']=env.diagnostics;result['sensitiveLogDetected']=env.sensitive_log_detected
  try:env.close();result['cleanup']='VERIFIED'
  except Exception as error:result['cleanupErrorType']=type(error).__name__
 if'sources'in globals():result['changedSourcePaths']=[p for p,h in sources.items()if hashlib.sha256((ROOT/p).read_bytes()).hexdigest()!=h]
 (OUT/'http-observations.json').write_text(json.dumps(Client.observations,indent=2));save()
print(json.dumps({'stage':result['stage'],'counts':result['counts'],'cleanup':result.get('cleanup'),'errorType':result.get('errorType')}))
raise SystemExit(0 if result['stage']=='complete'and not result['counts']['failures']and not result['counts']['errors']and result.get('cleanup')=='VERIFIED'else 1)
