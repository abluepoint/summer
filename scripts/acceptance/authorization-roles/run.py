"""T15 real MySQL/two-node/Chrome acceptance on an immutable candidate."""
import concurrent.futures,hashlib,json,os,secrets,subprocess,threading,time,uuid
from pathlib import Path
from environment import Environment,Client,OUT,ROOT,immutable_candidate
from grants import BASE,bootstrap,headers,data

SCRIPT=Path(__file__).resolve().parent
result={'mode':'real-api-grants-mysql-two-node-chrome','checks':[],'failures':0,'errors':0,'skipped':0}
env=None;ui=None;browser=None;stage='candidate';sensitive=[]
def check(name,condition,**evidence):
    result['checks'].append({'name':name,'status':'passed' if condition else 'failed',**evidence})
    result['failures']+=not condition;print(('passed ' if condition else 'RED ')+name,flush=True)
    return condition
def call(name,client,path,method='GET',body=None,expected=200,port=23081,h=None,critical=True):
    status,payload,response=client.request(port,path,method,body,headers=h)
    code=(payload.get('error') or {}).get('code') if isinstance(payload,dict) and isinstance(payload.get('error'),dict) else None
    ok=check(name,status==expected,http_status=status,expected=expected,code=code,requestId=response.get('X-Request-Id'))
    if not ok and critical:raise RuntimeError('required_step_failed')
    return data(payload) if isinstance(payload,dict) and 'data' in payload else payload,response
def login(username,password,port=23081):
    client=Client().login(port,username,password);env.additional_secrets.append(client.token);return client
def assign(name,admin,username,role_ids,denied,version=1,expected=200):
    return call(name,admin,BASE+'/users/'+username+'/roles','PUT',{'roleIds':role_ids,'deniedCapabilityCodes':denied},expected=expected,h=headers(version))
def create_user(admin,name,password):
    call(name+'_identity_created',admin,'/api/users','POST',{'username':name,'password':password,'role':'USER'})
    call(name+'_membership_created',admin,BASE+'/members','POST',{'subject':name,'displayName':'T15 acceptance member','email':name+'@example.test'},expected=201,h=headers())
def role_body(code,permissions):return {'name':'T15 '+code,'code':code,'capabilityIds':permissions}
def audit(admin,resource,port=23081):
    status,body,_=admin.request(port,BASE+'/authorization-audit/'+resource);assert status==200,'audit_read_failed';return data(body)
def persist(name,value):
    text=json.dumps(value,ensure_ascii=False,indent=2)
    for secret in [env.password,env.denied_password,env.mysql,env.master,env.db_password,*env.additional_secrets]:
        assert not secret or secret not in text,'sensitive_evidence'
    (OUT/name).write_text(text+'\n')
try:
    candidate=immutable_candidate();result['candidate_sha256']=hashlib.sha256(candidate.read_bytes()).hexdigest();check('immutable_candidate_verified',True)
    env=Environment();env.setup();env.start(23081,jar=candidate)
    stage='bootstrap';admin=login(env.username,env.password);check('bootstrap_real_form_pkce_login',True)
    migrations=env.sql('SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank',env.schema).splitlines()
    check('real_mysql_authorization_migrations',all(v in migrations for v in ['31','34']),versions=migrations)
    catalog,_=call('bootstrap_scanned_catalog_allowed',admin,'/api/v1/platform/interface-resources')
    check('customer_permission_obtained_from_scan',any(r['httpMethod']=='GET' and r['normalizedPath']=='/api/customers' and r['permissionCode']=='GET:/api/customers' for r in catalog['items']))
    call('bootstrap_business_is_default_denied',admin,'/api/customers',expected=403)
    bootstrap(admin,env.username);admin=login(env.username,env.password);check('managed_admin_bootstrap_via_real_apis',True)
    call('managed_admin_can_still_manage_roles',admin,BASE+'/roles')
    stage='users';user='t15-member';ui_user='t15-ui-member';password=secrets.token_urlsafe(24);ui_password=secrets.token_urlsafe(24);env.additional_secrets.extend([password,ui_password])
    for name,pwd in [(user,password),(ui_user,ui_password),(env.denied_username,env.denied_password)]:create_user(admin,name,pwd)
    member=login(user,password);call('unassigned_member_default_deny',member,'/api/customers',expected=403)
    call('anonymous_business_denied',Client(),'/api/customers',expected=401)
    role,_=call('role_created_from_scanned_permission',admin,BASE+'/roles','POST',role_body('t15-reader',['GET:/api/customers']),expected=201,h=headers())
    role_id=role['roleId'];record,etag=call('role_read_versioned',admin,BASE+'/roles/'+role_id)
    check('role_etag_matches_version',etag.get('ETag')=='"'+str(record['version'])+'"')
    binding,_=assign('real_user_role_assignment',admin,user,[role_id],[]);member=login(user,password)
    rid=str(uuid.uuid4());call('role_allows_real_business_http',member,'/api/customers',h={'X-Request-Id':rid});allow_rid=rid
    binding,_=assign('explicit_deny_saved',admin,user,[role_id],['GET:/api/customers'],binding['version'])
    rid=str(uuid.uuid4());call('explicit_deny_overrides_role_allow',member,'/api/customers',expected=403,h={'X-Request-Id':rid});deny_rid=rid
    binding,_=assign('explicit_deny_removed',admin,user,[role_id],[],binding['version']);call('removing_deny_restores_role_allow',member,'/api/customers')
    binding,_=assign('role_assignment_cleared',admin,user,[],[],binding['version']);rid=str(uuid.uuid4());call('clearing_roles_restores_default_deny',member,'/api/customers',expected=403,h={'X-Request-Id':rid});default_rid=rid
    binding,_=assign('role_assignment_restored',admin,user,[role_id],[],binding['version'])
    stage='validation';call('invalid_role_name_rejected',admin,BASE+'/roles','POST',role_body('t15-invalid',[])|{'name':' '},expected=400,h=headers())
    call('missing_idempotency_key_rejected',admin,BASE+'/roles','POST',role_body('t15-no-key',[]),expected=400)
    call('duplicate_role_code_rejected',admin,BASE+'/roles','POST',role_body('t15-reader',[]),expected=409,h=headers())
    assign('unknown_role_assignment_rejected',admin,user,['role-does-not-exist'],[],binding['version'],expected=409)
    updated,_=call('role_update_with_current_version',admin,BASE+'/roles/'+role_id,'PATCH',{'name':'T15 renamed reader'},h=headers(role['version']))
    call('stale_role_version_rejected',admin,BASE+'/roles/'+role_id,'PATCH',{'name':'T15 stale writer'},expected=412,h=headers(role['version']))
    key=headers();body=role_body('t15-idempotent',['GET:/api/customers']);first,_=call('idempotent_role_initial_create',admin,BASE+'/roles','POST',body,expected=201,h=key);again,_=call('idempotent_role_replay',admin,BASE+'/roles','POST',body,expected=201,h=key);check('idempotent_replay_same_role',first==again)
    call('idempotency_key_reuse_rejected',admin,BASE+'/roles','POST',body|{'name':'different'},expected=409,h=key)
    stage='audit';events=audit(admin,user)
    expected=[('ROLE_ALLOW','ALLOW',allow_rid),('USER_EXPLICIT_DENY','DENY',deny_rid),('DEFAULT_DENY','DENY',default_rid)]
    for reason,decision,request_id in expected:check('audit_'+reason.lower(),any(e.get('reasonCode')==reason and e.get('decision')==decision and e.get('requestId')==request_id and e.get('permissionCode')=='GET:/api/customers' for e in events))
    check('role_mutation_audited',any(e['action']=='ROLE_CREATED' for e in audit(admin,role_id)))
    persist('authorization-audit.json',events)
    stage='two_nodes';env.start(23082,jar=candidate);admin2=login(env.username,env.password,23082);member2=login(user,password,23082)
    other,_=call('role_persisted_on_second_node',admin2,BASE+'/roles/'+role_id,port=23082);check('role_versions_match_across_nodes',other==updated)
    call('second_node_real_business_allow',member2,'/api/customers',port=23082)
    binding,_=assign('cross_node_deny_saved',admin,user,[role_id],['GET:/api/customers'],binding['version']);call('second_node_observes_deny',member2,'/api/customers',expected=403,port=23082)
    binding,_=assign('cross_node_deny_removed',admin,user,[role_id],[],binding['version']);call('second_node_observes_allow',member2,'/api/customers',port=23082)
    stage='concurrency';race,_=call('concurrent_role_created',admin,BASE+'/roles','POST',role_body('t15-concurrent',[]),expected=201,h=headers())
    barrier=threading.Barrier(2)
    def contender(client,port,label):
        h=headers(race['version']);barrier.wait(timeout=10);status,body,response=client.request(port,BASE+'/roles/'+race['roleId'],'PATCH',{'name':label},headers=h)
        return {'node':port,'status':status,'submitted_version':race['version'],'returned_version':data(body).get('version') if status==200 else None,'requestId':response.get('X-Request-Id'),'code':(body.get('error') or {}).get('code') if isinstance(body,dict) else None}
    with concurrent.futures.ThreadPoolExecutor(2) as pool:
        futures=[pool.submit(contender,admin,23081,'T15 writer A'),pool.submit(contender,admin2,23082,'T15 writer B')];race_results=[future.result() for future in futures]
    after,_=call('concurrent_role_readback',admin,BASE+'/roles/'+race['roleId']);race_evidence={'writes':race_results,'final_version':after['version'],'final_name':after['name']};persist('concurrent-role.json',race_evidence)
    check('cross_node_concurrent_version_rejects_one_writer',sorted(r['status'] for r in race_results)==[200,412],**race_evidence)
    stage='restart';env.stop(23082);env.start(23082,jar=candidate);admin2=login(env.username,env.password,23082);member2=login(user,password,23082)
    restored,_=call('assignment_survives_node_restart',admin2,BASE+'/users/'+user+'/roles',port=23082);check('assignment_after_restart_matches',restored==binding)
    call('allow_survives_node_restart',member2,'/api/customers',port=23082);check('decision_audit_survives_restart',all(any(e.get('requestId')==request_id and e.get('reasonCode')==reason for e in audit(admin2,user,23082)) for reason,decision,request_id in expected))
    stage='public_configuration';call('explicitly_exposed_infrastructure_is_public',Client(),'/actuator/info')
    env.stop(23082);env.extra['summer.security.resource-server.protected-paths']=['/api/**','/actuator/info'];env.start(23082,jar=candidate)
    call('explicit_configuration_can_protect_infrastructure',Client(),'/actuator/info',expected=401,port=23082);env.extra.clear()
    stage='browser';runenv=os.environ.copy();runenv.update({'SUMMER_E2E_USERNAME':env.username,'SUMMER_E2E_PASSWORD':env.password,'ROLES_DENIED_USERNAME':env.denied_username,'ROLES_DENIED_PASSWORD':env.denied_password,'ROLES_UI_USERNAME':ui_user,'ROLES_OUTPUT':str(OUT),'AUTHORIZATION_ROLES_VITE_CACHE':str(OUT/('vite-cache-'+env.run))})
    ui=subprocess.Popen(['/opt/homebrew/opt/node@22/bin/node',str(SCRIPT/'ui-server.mjs')],cwd=ROOT/'summer-ui',env=runenv,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True);assert ui.stdout.readline().strip()=='UI_READY'
    browser=subprocess.Popen(['/opt/homebrew/opt/node@22/bin/node',str(SCRIPT/'browser.mjs')],cwd=ROOT/'summer-ui',env=runenv,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
    for line in browser.stdout:
        command=line.strip()
        if command.startswith('CONTROL '):
            action=command[8:];reply={}
            if action=='user_access':
                actual=login(ui_user,ui_password);status,body,response=actual.request(23081,'/api/customers');reply={'status':status,'requestId':response.get('X-Request-Id')}
            elif action=='denied_access':
                actual=login(env.denied_username,env.denied_password);status,body,response=actual.request(23081,BASE+'/roles');reply={'status':status,'code':(body.get('error') or {}).get('code'),'requestId':body.get('requestId') or response.get('X-Request-Id')}
            elif action=='stop':env.stop(23081)
            elif action=='restart':env.start(23081,jar=candidate)
            else:raise RuntimeError('unknown_browser_control')
            browser.stdin.write(json.dumps(reply)+'\n');browser.stdin.flush()
    browser.wait(timeout=30)
    if not (OUT/'browser-result.json').exists():raise RuntimeError('browser_receipt_missing')
    result['browser']=json.loads((OUT/'browser-result.json').read_text());check('actual_chrome_flows',result['browser']['failures']==0 and result['browser']['errors']==0,browser_tests=result['browser']['tests'])
    check('no_sensitive_values_observed_in_service_logs',not env.sensitive_log_detected)
    result['status']='PASSED' if result['failures']==0 else 'RED'
except Exception as error:
    result.update(status='INCOMPLETE',failed_stage=stage,error_type=type(error).__name__);result['errors']+=1
finally:
    for process in [browser,ui]:
        if process and process.poll() is None:
            process.terminate()
            try:process.wait(timeout=15)
            except subprocess.TimeoutExpired:process.kill();process.wait()
    if env:
        result['diagnostics']=env.diagnostics
        try:env.close();result['cleanup']='VERIFIED'
        except Exception as error:result.update(cleanup='FAILED',cleanup_error=type(error).__name__);result['errors']+=1
    result['http_observations']=Client.observations;result['tests']=len(result['checks']);result['passed']=sum(c['status']=='passed' for c in result['checks'])
    (OUT/'result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n');print(json.dumps({k:result.get(k) for k in ['status','tests','passed','failures','errors','failed_stage','cleanup']}),flush=True)
raise SystemExit(0 if result.get('status')=='PASSED' and result.get('cleanup')=='VERIFIED' else 1)
