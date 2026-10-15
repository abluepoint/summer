"""T18 incremental real protocol/MySQL/Chrome acceptance; no business SQL fixtures."""
import hashlib, json, os, selectors, shutil, subprocess, uuid
from urllib.parse import urlencode
from environment import Environment, Client, ROOT, OUT, SCRIPT, immutable_candidate
from grants import bootstrap, create_role, bind

BASE='/api/v1/enterprises/ent-acme'; POLICY=BASE+'/data-scopes/system-parameters'; PARAMS=BASE+'/system-parameters'
result={'issue':126,'stage':'initialization','checks':[],'networkMocking':False,'businessSqlWrites':False}
env=ui=None
def save():
    c=result['checks'];result['counts']={'tests':len(c),'failures':sum(not x['passed'] for x in c),'errors':int('errorType' in result),'skipped':0}
    tmp=OUT/'result.tmp';tmp.write_text(json.dumps(result,indent=2));tmp.replace(OUT/'result.json')
def check(name,passed,**facts):
    result['checks'].append({'name':name,'passed':bool(passed),**facts});save();print(name,'PASS' if passed else 'FAIL',flush=True)
def mh(version=None):
    h={'Idempotency-Key':uuid.uuid4().hex}
    if version is not None:h['If-Match']=str(version)
    return h
def call(client,path,method='GET',body=None,expected=200,headers=None,name=None):
    rid='t18-'+uuid.uuid4().hex;s,b,h=client.request(21081,path,method,body,headers={'X-Request-Id':rid,**(headers or {})})
    error=b.get('error') if isinstance(b,dict) else None;code=error.get('code') if isinstance(error,dict) else None
    ok=s==expected and isinstance(b,dict) and b.get('success')==(s<400) and b.get('requestId')==h.get('X-Request-Id')==rid
    if s>=400:ok=ok and isinstance(code,str) and code.startswith('SUMMER_')
    check(name or method+' '+path,ok,httpStatus=s,expected=expected,code=code)
    if s!=expected:raise AssertionError('unexpected_http_status_'+str(s)+'_expected_'+str(expected))
    return b.get('data'),h
def login(username,password):return Client().login(21081,username,password)
def policy(tree,path=POLICY):return call(admin,path,'PUT',{'conditions':tree})[0]
def evidence():
    return env.sql("SELECT version,checksum,(SELECT COUNT(*) FROM summer_data_scope_policy_audit WHERE enterprise_id='ent-acme' AND resource='system-parameters') FROM summer_data_scope_policy WHERE enterprise_id='ent-acme' AND resource='system-parameters'",env.schema)
def checksum_matches(view):
    raw=env.sql("SELECT operator_name,rules_json,conditions_json,checksum FROM summer_data_scope_policy WHERE enterprise_id='ent-acme' AND resource='system-parameters'",env.schema).split('\t')
    return len(raw)==4 and hashlib.sha256('|'.join(raw[:3]).encode()).hexdigest()==raw[3]==view['checksum']
def matrix(label,expected):
    page,_=call(reader,PARAMS+'?'+urlencode({'key':prefix,'size':100}),name=label+'_sql_list')
    count,_=call(reader,PARAMS+':count?'+urlencode({'key':prefix}),name=label+'_sql_count')
    check(label+'_exact_sql_rows',set(x['key'] for x in page['items'])==set(expected) and page['totalElements']==count['totalElements']==len(expected))
    for key,row in fixtures.items():
        allow=key in expected
        call(reader,PARAMS+'/'+key,expected=200 if allow else 404,name=label+'_repository_'+key.rsplit('.',1)[1])
        preview,_=call(admin,POLICY+':preview','POST',{'subject':env.denied_username,'organizationId':row['org'],'creatorId':row['creator'],'ownerId':row['creator']},name=label+'_preview_'+key.rsplit('.',1)[1])
        check(label+'_preview_matches_database_'+key.rsplit('.',1)[1],preview['allowed']==allow)
def browser(mode,child):
    child=dict(child,COMPOSED_SCOPES_MODE=mode)
    p=subprocess.run(['/opt/homebrew/opt/node@22/bin/node',str(SCRIPT/'browser.mjs')],cwd=ROOT/'summer-ui',env=child,capture_output=True,text=True,timeout=240)
    receipt=OUT/('browser-'+mode+'.json')
    result.setdefault('browsers',[]).append({'mode':mode,'exitCode':p.returncode,**(json.loads(receipt.read_text()) if receipt.exists() else {'errors':1,'receiptAbsent':True})});save()
    if p.returncode:raise AssertionError('browser_'+mode+'_failed')
try:
    candidate=immutable_candidate();result['candidateSha256']=hashlib.sha256(candidate.read_bytes()).hexdigest()
    archive=OUT/'executed-scripts';archive.mkdir()
    for p in SCRIPT.iterdir():
        if p.is_file():shutil.copy2(p,archive/p.name)
    watched=list((ROOT/'summer-sample/src/main/java/com/abluepoint/summer/sample/authorization').glob('DataScope*.java'))+list((ROOT/'summer-ui/src/pages/data-scopes').glob('*'))
    source={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in watched if p.is_file()}
    (OUT/'source-before.json').write_text(json.dumps(source,indent=2))
    env=Environment();env.setup();env.start(21081,jar=candidate)
    result['stage']='bootstrap';admin=login(env.username,env.password);boot=bootstrap(admin,env.username);admin=login(env.username,env.password)
    catalog,_=call(admin,BASE+'/capabilities/catalog');existing={c['code']:c['capabilityId'] for c in catalog['capabilities']}
    codes=['system.parameter.'+x for x in ['read','create','publish']]+['menu.dataScopes.read']
    for code in codes:
        if code not in existing:
            cap,_=call(admin,BASE+'/capabilities','POST',{'code':code,'resource':code.rsplit('.',1)[0],'action':code.rsplit('.',1)[1],'riskLevel':'HIGH','scope':'PLATFORM' if code.startswith('system.') else 'ENTERPRISE'},201,headers=mh());existing[code]=cap['capabilityId']
    configrole=create_role(admin,'ent-acme',[existing[c] for c in codes]+['enterprise.organization.read'],'T18 configuration operator')
    unionrole=create_role(admin,'ent-acme',['enterprise.organization.read'],'T18 second scope role')
    binding,h=call(admin,BASE+'/users/'+env.username+'/roles');call(admin,BASE+'/users/'+env.username+'/roles','PUT',{'roleIds':[boot,configrole],'deniedCapabilityCodes':[]},headers=mh(h['ETag']));admin=login(env.username,env.password)
    call(admin,'/api/users','POST',{'username':env.denied_username,'password':env.denied_password,'role':'USER'})
    call(admin,BASE+'/members','POST',{'subject':env.denied_username,'displayName':'T18 target','email':'t18@example.test'},201,headers=mh());bind(admin,'ent-acme',env.denied_username,[configrole,unionrole]);reader=login(env.denied_username,env.denied_password)
    if not os.environ.get('SUMMER_COMPOSED_SCOPES_UI_ONLY'):
        result['stage']='rows';prefix='composed.'+env.run;fixtures={}
        for suffix,client,creator,org in [('admineng',admin,env.username,'org-engineering'),('adminops',admin,env.username,'org-operations'),('readereng',reader,env.denied_username,'org-engineering'),('readerops',reader,env.denied_username,'org-operations')]:
            key=prefix+'.'+suffix;call(client,PARAMS,'POST',{'key':key,'type':'STRING','value':'T18 fixture','organizationId':org},201);fixtures[key]={'creator':creator,'org':org}
        result['stage']='nested_semantics'
        tree={'operator':'OR','children':[{'field':'CREATOR','value':env.denied_username},{'operator':'AND','children':[{'field':'ORGANIZATION','value':'org-engineering'},{'field':'OWNER','value':env.username}]}]}
        previous,_=call(admin,POLICY);p=policy(tree)
        check('version_increment_and_checksum_matches_persisted_bytes',p['version']==previous['version']+1 and checksum_matches(p) and p['checksum']!=previous['checksum'])
        matrix('nested_or_and',[k for k in fixtures if not k.endswith('adminops')])
        p2=policy(tree);check('same_content_same_checksum_new_version',p2['checksum']==p['checksum'] and p2['version']==p['version']+1)
        audit,_=call(admin,POLICY+'/audit');check('audit_exact_version_checksum_operator_request',any(x['version']==p2['version'] and x['checksum']==p2['checksum'] and x['principal']==env.username and x['requestId'] and x['action']=='DATA_SCOPE_POLICY_UPDATED' for x in audit))
        policy({'operator':'OR','children':[{'field':'TENANT'},{'operator':'AND','children':[{'field':'ORGANIZATION','value':'org-operations'},{'field':'CREATOR','value':env.denied_username,'effect':'DENY'}]}]})
        matrix('branch_gated_deny',[k for k in fixtures if not k.endswith('readerops')])
        policy({'operator':'AND','children':[{'field':'ORGANIZATION','operator':'IN','value':['org-engineering','org-operations']},{'field':'OWNER','operator':'NOT_IN','value':[env.username]}]})
        matrix('set_comparators',[k for k in fixtures if 'reader' in k])
        result['stage']='invalid_inputs';before=evidence()
        invalid=[('unknown_field',{'conditions':{'field':'password'}}),('unknown_property',{'conditions':{'field':'OWNER','sql':'1=1'}}),('raw_sql',{'sql':'select 1','conditions':{'field':'TENANT'}}),('unknown_operator',{'conditions':{'field':'OWNER','operator':'LIKE','value':'x'}}),('conflicting_field_type',{'conditions':{'field':'OWNER','type':'CREATOR'}}),('two_tree_forms',{'conditions':{'field':'TENANT'},'conditionTree':{'field':'TENANT'}}),('wrong_set_shape',{'conditions':{'field':'OWNER','operator':'IN','value':'x'}})]
        for label,body in invalid:call(admin,POLICY,'PUT',body,400,name=label+'_rejected')
        check('invalid_policy_preserves_version_checksum_audit',before==evidence())
        call(reader,POLICY,'PUT',{'conditions':{'field':'TENANT'}},403,name='unauthorized_policy_write')
        call(reader,POLICY+':preview','POST',{'subject':env.username},403,name='unauthorized_preview')
        call(admin,POLICY+':preview','POST',{'subject':env.denied_username,'organizationIds':['forged']},400,name='hypothetical_organization_authority_rejected')
        result['stage']='persisted_union';policy({'field':'TENANT'})
        call(admin,POLICY+'/bindings/ROLE/'+configrole,'PUT',{'conditions':{'field':'CREATOR','value':env.denied_username}})
        call(admin,POLICY+'/bindings/ROLE/'+unionrole,'PUT',{'conditions':{'field':'ORGANIZATION','value':'org-engineering'}})
        matrix('two_role_union',[k for k in fixtures if not k.endswith('adminops')])
        denied,_=call(admin,POLICY+'/bindings/USER/'+env.denied_username,'PUT',{'conditions':{'field':'ORGANIZATION','value':'org-engineering'}})
        matrix('user_deny_after_union',[k for k in fixtures if k.endswith('readerops')])
        pv,_=call(admin,POLICY+':preview','POST',{'subject':env.denied_username,'organizationId':'org-engineering','creatorId':env.denied_username,'ownerId':env.denied_username})
        check('effective_explanation_has_both_roles_and_user_deny',pv['reasonCode']=='USER_DENY' and set(x.split(':')[0] for x in pv['effectiveScope']['bindings'])=={'ROLE','USER'} and len(pv['effectiveScope']['bindings'])==3)
    otherid=''
    if not os.environ.get('SUMMER_COMPOSED_SCOPES_SINGLE_TENANT'):
        result['stage']='second_tenant';other,_=call(admin,'/api/v1/platform/enterprises','POST',{'name':'T18 preview tenant','code':'composed-'+env.run},201);otherid=other['enterpriseId'];otherbase='/api/v1/enterprises/'+otherid;admin=login(env.username,env.password)
        call(admin,otherbase+'/members','POST',{'subject':env.denied_username,'displayName':'T18 target','email':'target@example.test'},201,headers=mh())
        role=create_role(admin,otherid,[existing[c] for c in codes if c.startswith('system.')]+['enterprise.data-scope.read','enterprise.data-scope.write'],'T18 second tenant manager')
        b,h=call(admin,otherbase+'/users/'+env.username+'/roles');call(admin,otherbase+'/users/'+env.username+'/roles','PUT',{'roleIds':b['roleIds']+[role],'deniedCapabilityCodes':[]},headers=mh(h['ETag']));admin=login(env.username,env.password)
        otherpolicy=otherbase+'/data-scopes/system-parameters';policy({'field':'TENANT'},otherpolicy)
        pv,_=call(admin,otherpolicy+':preview','POST',{'subject':env.denied_username});check('same_user_other_tenant_deny_not_inherited',pv['allowed'] and pv['enterpriseId']==otherid and pv['effectiveScope']['bindings']==[])
    # Prepare deterministic browser targets: A denies the target in operations, B permits it.
    call(admin,POLICY+'/bindings/ROLE/'+configrole,'PUT',{'conditions':{'field':'TENANT'}})
    call(admin,POLICY+'/bindings/USER/'+env.denied_username,'PUT',{'conditions':{'field':'ORGANIZATION','value':'org-operations'}})
    # Multiple tenant memberships require an explicit active context for the shared menu.
    member=env.sql("SELECT member_id FROM summer_enterprise_member WHERE enterprise_id='ent-acme' AND subject='"+env.username+"' AND status='ACTIVE'",env.schema)
    assert member and '\n' not in member,'unique_active_member_required'
    _,h=call(admin,BASE+'/org-tree')
    call(admin,BASE+'/members/'+member+'/org-assignments','PUT',{'organizationIds':['org-engineering'],'primaryOrganizationId':'org-engineering'},headers=mh(h['ETag']))
    call(admin,BASE+'/context','PUT',{'organizationId':'org-engineering'})
    import base64
    fresh=login(env.username,env.password)
    claims=json.loads(base64.urlsafe_b64decode(fresh.token.split('.')[1]+'=='))
    result['adminClaims']={'membershipCount':len(claims.get('enterprise_memberships',[])),'activeIsAcme':claims.get('active_enterprise_id')=='ent-acme','menuGranted':'menu.dataScopes.read' in claims.get('enterprise_capabilities',{}).get('ent-acme',[])}
    save()
    result['stage']='real_chrome'
    child=dict(os.environ,SUMMER_E2E_USERNAME=env.username,SUMMER_E2E_PASSWORD=env.password,COMPOSED_SCOPES_READER=env.denied_username,COMPOSED_SCOPES_READER_PASSWORD=env.denied_password,COMPOSED_SCOPES_OTHER=otherid,COMPOSED_SCOPES_OUTPUT=str(OUT),COMPOSED_SCOPES_VITE_CACHE=str(OUT/'vite-cache'))
    ui=subprocess.Popen(['/opt/homebrew/opt/node@22/bin/node',str(SCRIPT/'ui-server.mjs')],cwd=ROOT/'summer-ui',env=child,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
    selector=selectors.DefaultSelector();selector.register(ui.stdout,selectors.EVENT_READ);ready=selector.select(45);selector.close()
    if not ready or ui.stdout.readline().strip()!='UI_READY':raise RuntimeError('ui_startup_failed')
    browser('multi' if os.environ.get('SUMMER_COMPOSED_SCOPES_MULTI_TENANT_ONLY') else 'normal',child)
    if not os.environ.get('SUMMER_COMPOSED_SCOPES_MULTI_TENANT_ONLY'):
        result['stage']='audit_fault';before=evidence()
        env.sql("CREATE TRIGGER t18_audit_failure BEFORE INSERT ON summer_data_scope_policy_audit FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='T18 controlled audit fault'",env.schema)
        try:browser('fault',child)
        finally:env.sql('DROP TRIGGER t18_audit_failure',env.schema)
        check('audit_failure_rolls_back_policy_version_checksum_and_audit',evidence()==before)
        p=policy({'field':'TENANT'});check('same_jvm_recovers_after_audit_fault',checksum_matches(p))
    result['stage']='complete';check('sensitive_output_scan_clear',not env.sensitive_log_detected)
except Exception as error:
    result['errorType']=type(error).__name__
    if isinstance(error,AssertionError):result['safeAssertion']=str(error)
finally:
    if ui and ui.poll() is None:
        ui.terminate()
        try:ui.wait(timeout=10)
        except subprocess.TimeoutExpired:ui.kill();ui.wait()
    if env:
        result['diagnostics']=env.diagnostics;result['sensitiveLogDetected']=env.sensitive_log_detected
        try:env.close();result['cleanup']='VERIFIED'
        except Exception as error:result['cleanupErrorType']=type(error).__name__
    if 'source' in globals():result['changedSourcePaths']=[p for p,h in source.items() if hashlib.sha256((ROOT/p).read_bytes()).hexdigest()!=h]
    (OUT/'http-observations.json').write_text(json.dumps(Client.observations,indent=2));save()
print(json.dumps({'stage':result['stage'],'counts':result['counts'],'cleanup':result.get('cleanup'),'errorType':result.get('errorType')}))
raise SystemExit(0 if result['stage']=='complete' and not result['counts']['failures'] and not result['counts']['errors'] and result.get('cleanup')=='VERIFIED' else 1)
