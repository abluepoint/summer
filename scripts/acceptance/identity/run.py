"""Read-only evidence plus real HTTP mutations in a disposable MySQL schema.

No business-table SQL fixtures, mocked HTTP, saved credentials, or product edits.
Memberships are created through the explicit member API using an existing login
identity. New-tenant permissions are consumed only after a fresh PKCE login.
"""
import hashlib,json,re,secrets,time,urllib.parse,uuid
from environment import Environment,Client,OUT,immutable_candidate
from grants import bootstrap,create_role,bind,unwrap

candidate=immutable_candidate();env=None
result={'stage':'setup','checks':[],'networkMocking':False,'businessSqlWrites':False,
 'limitations':['UI acceptance is recorded separately by probe.py.']}

def check(name,ok,**facts):
 result['checks'].append({'name':name,'passed':bool(ok),**facts})
 print(name,'PASS' if ok else 'FAIL',flush=True)

def request(name,client,path,method='GET',body=None,expected=(200,),port=21081,headers=None):
 rid='identity-'+uuid.uuid4().hex
 status,data,h=client.request(port,path,method,body,headers={'X-Request-Id':rid,**(headers or {})})
 error=data.get('error') if isinstance(data,dict) else None
 code=error.get('code') if isinstance(error,dict) else None
 check(name,status in expected,status=status,expected=list(expected),errorCode=code)
 if status>=400 and isinstance(data,dict):
  check(name+'_error_contract',isinstance(code,str) and code.startswith('SUMMER_') and data.get('requestId')==h.get('X-Request-Id') and bool(h.get('X-Request-Id')))
 return status,unwrap(data),h

def login_ok(name,username,password,port=21081,expected=True):
 client=Client()
 try:client.login(port,username,password);success=True
 except AssertionError:success=False
 check(name,success==expected)
 return client if success else None

def mutation_headers(etag=None):
 return {'Idempotency-Key':uuid.uuid4().hex,**({'If-Match':etag} if etag else {})}

try:
 env=Environment();env.setup();env.start(21081,jar=candidate)
 admin=login_ok('bootstrap_real_pkce',env.username,env.password)
 bootstrap(admin,env.username)
 admin=login_ok('role_configuration_real_pkce',env.username,env.password)
 result['stage']='users'
 password=secrets.token_urlsafe(24);wrong=secrets.token_urlsafe(24)
 env.additional_secrets.extend([password,wrong])
 username='live-'+env.run
 request('anonymous_users_denied',Client(),'/api/users',expected=(401,403))
 _,created,_=request('user_created',admin,'/api/users','POST',{'username':username,'password':password,'role':'USER'})
 check('user_create_safe_view',isinstance(created,dict) and set(created)=={'username','role','active'} and created.get('active') is True)
 request('duplicate_username_rejected',admin,'/api/users','POST',{'username':username,'password':password,'role':'USER'},expected=(409,))
 request('invalid_username_rejected',admin,'/api/users','POST',{'username':'','password':password,'role':'USER'},expected=(400,))
 request('invalid_short_password_rejected',admin,'/api/users','POST',{'username':'invalid-'+env.run,'password':'x','role':'USER'},expected=(400,))
 hashed=env.sql("SELECT password_hash FROM summer_auth_user WHERE username='"+username+"'",env.schema)
 match=re.fullmatch(r'(?:\{bcrypt\})?\$2[aby]\$(\d\d)\$.{53}',hashed)
 check('password_strong_hash_only',bool(match) and int(match.group(1))>=10 and hashed!=password)
 del hashed
 user=login_ok('created_user_can_login',username,password)
 login_ok('wrong_password_denied',username,wrong,expected=False)
 request('ungranted_user_list_denied',user,'/api/users',expected=(403,))
 request('ungranted_user_create_denied',user,'/api/users','POST',{'username':'forbidden-'+env.run,'password':password,'role':'USER'},expected=(403,))
 request('user_disabled',admin,'/api/users/'+username,'PATCH',{'active':False})
 login_ok('disabled_user_login_denied',username,password,expected=False)
 env.start(21082,jar=candidate)
 admin2=login_ok('second_node_admin_login',env.username,env.password,port=21082)
 _,users,_=request('users_read_on_second_node',admin2,'/api/users',port=21082)
 check('disabled_user_persisted_across_nodes',any(u['username']==username and u['active'] is False for u in users))
 request('user_restored',admin,'/api/users/'+username,'PATCH',{'active':True})
 login_ok('restored_user_login_second_node',username,password,port=21082)
 _,new_member,_=request('existing_identity_added_as_member',admin,'/api/v1/enterprises/ent-acme/members','POST',{'subject':username,'displayName':'Live member','email':'member@example.test'},expected=(201,),headers=mutation_headers())
 member_id=new_member['memberId']
 role=create_role(admin,'ent-acme',['enterprise.organization.read','enterprise.members.read','enterprise.tasks.read','GET:/api/users','GET:/api/v1/enterprises/{enterpriseId}/tasks'])
 bind(admin,'ent-acme',username,[role])
 alice=login_ok('created_member_role_login',username,password)
 result['stage']='organizations'
 base='/api/v1/enterprises/ent-acme'
 _,root,h=request('organization_root_created',admin,base+'/org-units','POST',{'name':'Acceptance root','code':'live-root-'+env.run,'unitType':'DEPARTMENT'},expected=(201,),headers=mutation_headers())
 root_id=root['id'];root_etag=h['ETag']
 _,child,h=request('organization_child_created',admin,base+'/org-units','POST',{'name':'Acceptance child','code':'live-child-'+env.run,'parentId':root_id,'unitType':'TEAM'},expected=(201,),headers=mutation_headers())
 child_id=child['id']
 request('organization_duplicate_rejected',admin,base+'/org-units','POST',{'name':'Duplicate root','code':root['code']},expected=(409,),headers=mutation_headers())
 request('organization_invalid_rejected',admin,base+'/org-units','POST',{'name':'','code':'invalid-'+env.run},expected=(400,),headers=mutation_headers())
 request('organization_cycle_rejected',admin,base+'/org-units/'+root_id,'PATCH',{'parentId':child_id},expected=(409,),headers=mutation_headers(root_etag))
 request('organization_stale_version_rejected',admin,base+'/org-units/'+child_id,'PATCH',{'name':'Stale'},expected=(412,),headers=mutation_headers('"stale"'))
 _,_,h=request('child_before_reparent',admin,base+'/org-units/'+child_id)
 _,detached,_=request('child_moved_to_root',admin,base+'/org-units/'+child_id,'PATCH',{'parentId':None},headers=mutation_headers(h['ETag']))
 check('explicit_null_parent_persisted',detached.get('parentId') is None)
 request('read_role_write_denied',alice,base+'/org-units','POST',{'name':'Denied','code':'denied'},expected=(403,),headers=mutation_headers())
 request('nonmember_context_rejected',alice,base+'/context','PUT',{'organizationId':root_id},expected=(403,))
 request('unassigned_organization_detail_denied',alice,base+'/org-units/'+root_id,expected=(403,404))
 _,_,tree_h=request('organization_tree_before_assignment',admin,base+'/org-tree')
 request('member_assigned_multiple_organizations',admin,base+'/members/'+member_id+'/org-assignments','PUT',{'organizationIds':[root_id,child_id],'primaryOrganizationId':root_id},headers=mutation_headers(tree_h['ETag']))
 for label,org in [('root',root_id),('child',child_id)]:
  _,members,_=request('assigned_'+label+'_members',admin,base+'/org-units/'+org+'/members')
  check('created_member_in_'+label+'_organization',any(m['memberId']==member_id for m in members['items']))
 _,context,_=request('current_context_selected',alice,base+'/context','PUT',{'organizationId':child_id})
 check('context_selection_value',context['organizationId']==child_id)
 _,context,_=request('current_context_second_node',alice,base+'/context',port=21082)
 check('context_persisted_across_nodes',context['organizationId']==child_id)
 request('cross_tenant_tree_denied',alice,'/api/v1/enterprises/ent-foreign/org-tree',expected=(403,404))
 request('cross_tenant_member_denied',alice,'/api/v1/enterprises/ent-foreign/members/'+member_id,expected=(403,404))
 request('unrefreshed_membership_claims_denied',user,base+'/org-tree',expected=(403,))
 for label,rid in [('root',root_id),('child',child_id),('assignment',member_id),('context',username)]:
  _,events,_=request(label+'_audit_read',admin,base+'/org-units/'+rid+'/audit')
  check(label+'_mutation_audited',isinstance(events,list) and len(events)>0)
 request('old_jwt_tasks_before_disable',alice,base+'/tasks')
 _,_,h=request('member_version_before_disable',admin,base+'/members/'+member_id)
 request('member_disabled',admin,base+'/members/'+member_id+':disable','POST',{},headers=mutation_headers(h['ETag']))
 request('old_jwt_tasks_denied_after_member_disable',alice,base+'/tasks',expected=(403,),port=21082)
 request('old_jwt_org_denied_after_member_disable',alice,base+'/org-tree',expected=(403,),port=21082)
 request('duplicate_member_cannot_restore_disabled_member',admin,base+'/members','POST',{'subject':username,'displayName':'Live member','email':'member@example.test'},expected=(409,),headers=mutation_headers())
 _,binding,binding_headers=request('role_binding_before_disabled_rebind',admin,base+'/users/'+username+'/roles')
 request('role_rebinding_is_separate_from_membership',admin,base+'/users/'+username+'/roles','PUT',{'roleIds':[role],'deniedCapabilityCodes':[]},headers=mutation_headers(binding_headers.get('ETag') or '"'+str(binding['version'])+'"'))
 _,disabled_member,_=request('member_after_role_rebind',admin,base+'/members/'+member_id)
 check('role_rebind_keeps_member_disabled',disabled_member['status']=='DISABLED')
 request('old_jwt_still_denied_after_rebind',alice,base+'/tasks',expected=(403,),port=21082)
 _,_,h=request('member_disabled_second_node',admin2,base+'/members/'+member_id,port=21082)
 request('member_restored',admin,base+'/members/'+member_id+':enable','POST',{},headers=mutation_headers(h['ETag']))
 request('old_jwt_read_restored',alice,base+'/org-tree',port=21082)
 _,events,_=request('member_audit_read',admin,base+'/members/'+member_id+'/audit')
 check('member_status_changes_audited',isinstance(events,list) and len(events)>=2)
 result['stage']='new_enterprise'
 _,enterprise,enterprise_headers=request('enterprise_created',admin,'/api/v1/platform/enterprises','POST',{'name':'Acceptance enterprise','code':'live-'+env.run},expected=(201,))
 if isinstance(enterprise,dict) and enterprise.get('enterpriseId'):
  eid=enterprise['enterpriseId']
  request('new_enterprise_old_claims_denied',admin,'/api/v1/enterprises/'+eid+'/org-tree',expected=(403,))
  admin=login_ok('creator_claims_refreshed',env.username,env.password)
  request('new_enterprise_organization_creation',admin,'/api/v1/enterprises/'+eid+'/org-units','POST',{'name':'First organization','code':'first'},expected=(201,),headers=mutation_headers())
  _,events,_=request('enterprise_creation_audit_read',admin,'/api/v1/platform/enterprises/'+eid+'/audit')
  check('enterprise_creation_audited',any(e.get('action')=='ENTERPRISE_CREATED' and e.get('requestId')==enterprise_headers.get('X-Request-Id') for e in events))
  request('other_member_new_enterprise_denied',alice,'/api/v1/enterprises/'+eid+'/org-tree',expected=(403,))
 result['stage']='restart'
 env.stop(21081);env.start(21081,jar=candidate)
 admin=login_ok('admin_login_after_restart',env.username,env.password)
 alice=login_ok('created_member_login_after_restart',username,password)
 login_ok('created_user_login_after_restart',username,password)
 _,context,_=request('context_after_restart',alice,base+'/context')
 check('context_survives_restart',context['organizationId']==child_id)
 _,members,_=request('assignments_after_restart',admin,base+'/org-units/'+child_id+'/members')
 check('assignment_survives_restart',any(m['memberId']==member_id for m in members['items']))
 _,events,_=request('audit_after_restart',admin,base+'/org-units/'+root_id+'/audit')
 check('audit_survives_restart',len(events)>0)
 time.sleep(.2)
 counts={name:sum(e['event']==name for e in env.security_events) for name in ['authentication_success','authentication_failure']}
 check('successful_login_security_audited',counts['authentication_success']>0)
 check('failed_login_security_audited',counts['authentication_failure']>=2)
 result['securityAuditCounts']=counts
 check('no_sensitive_runtime_output',not env.sensitive_log_detected)
 result['stage']='complete'
except Exception as error:
 result['failureType']=type(error).__name__
 if isinstance(error,AssertionError):result['assertion']=str(error)
finally:
 if env:
  try:env.close();result['cleanup']='VERIFIED'
  except Exception as error:result['cleanup']='FAILED';result['cleanupFailureType']=type(error).__name__
 result['candidateSha256']=hashlib.sha256(candidate.read_bytes()).hexdigest()
 result['diagnostics']=env.diagnostics if env else []
 result['counts']={'checks':len(result['checks']),'passed':sum(c['passed'] for c in result['checks']),'failed':sum(not c['passed'] for c in result['checks'])}
 result['passed']=result['stage']=='complete' and result['counts']['failed']==0 and result.get('cleanup')=='VERIFIED'
 (OUT/'api-result.json').write_text(json.dumps(result,indent=2))
 print(json.dumps({key:result.get(key) for key in ['stage','passed','counts','cleanup','failureType','assertion']}),flush=True)
raise SystemExit(0 if result['passed'] else 1)
