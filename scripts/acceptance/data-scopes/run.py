"""T17 real PKCE/HTTP/SQL evidence against one hash-pinned production artifact."""
import hashlib, json, os, subprocess, uuid, shutil, time, tarfile, io
from pathlib import Path
from urllib.parse import urlencode
from environment import Environment, Client, OUT, ROOT, SCRIPT, immutable_candidate
from grants import bootstrap, create_role, bind

BASE='/api/v1/enterprises/ent-acme'; PARAMS=BASE+'/system-parameters'; POLICY=BASE+'/data-scopes/system-parameters'
result={'issue':125,'stage':'initialization','checks':[],'businessSqlWrites':False,'networkMocking':False}
env=ui=None

def save():
    checks=result['checks'];result['counts']={'tests':len(checks),'failures':sum(not c['passed'] for c in checks),'errors':int('errorType' in result),'skipped':0}
    (OUT/'result.json').write_text(json.dumps(result,indent=2))
def check(name,passed,**facts):
    result['checks'].append({'name':name,'passed':bool(passed),**facts});save();print(name,'PASS' if passed else 'FAIL',flush=True)
def mh(version=None):
    h={'Idempotency-Key':uuid.uuid4().hex}
    if version is not None:h['If-Match']=str(version)
    return h
def call(client,path,method='GET',body=None,expected=200,headers=None,name=None):
    rid='t17-'+uuid.uuid4().hex;s,b,h=client.request(23081,path,method,body,headers={'X-Request-Id':rid,**(headers or {})})
    code=(b.get('error') or {}).get('code') if isinstance(b,dict) else None
    ok=s==expected and isinstance(b,dict) and b.get('success')==(s<400) and b.get('requestId')==h.get('X-Request-Id')==rid
    if s>=400:ok=ok and isinstance(code,str) and code.startswith('SUMMER_')
    check(name or method+' '+path,ok,status=s,expected=expected,code=code)
    if s!=expected:raise AssertionError('unexpected_http_status_'+str(s)+'_expected_'+str(expected))
    return b.get('data'),h

def policy(admin,condition):return call(admin,POLICY,'PUT',{'conditions':condition})[0]
def logged(username,password):return Client().login(23081,username,password)
def assign(admin,subject):
    member=env.sql("SELECT member_id FROM summer_enterprise_member WHERE enterprise_id='ent-acme' AND subject='"+subject+"' AND status='ACTIVE'",env.schema)
    assert member and '\n' not in member,'unique_active_member_required'
    _,h=call(admin,BASE+'/org-tree')
    call(admin,BASE+'/members/'+member+'/org-assignments','PUT',{'organizationIds':['org-engineering','org-operations'],'primaryOrganizationId':'org-engineering'},headers=mh(h['ETag']))
def context(client,organization):call(client,BASE+'/context','PUT',{'organizationId':organization})
def matrix(label,client,expected_keys,fixtures):
    page,_=call(client,PARAMS+'?'+urlencode({'key':prefix,'size':100}),name=label+'_list')
    count,_=call(client,PARAMS+':count?'+urlencode({'key':prefix}),name=label+'_count')
    check(label+'_same_database_scope',set(x['key'] for x in page['items'])==set(expected_keys) and page['totalElements']==count['totalElements']==len(expected_keys),listCount=len(page['items']),total=page['totalElements'],count=count['totalElements'])
    batch,_=call(client,PARAMS+'?'+urlencode({'key':prefix,'size':100,'projection':'summary'}),name=label+'_batch_summary')
    check(label+'_batch_same_keys',set(x['key'] for x in batch['items'])==set(expected_keys))
    for key,row in fixtures.items():
        allowed=key in expected_keys
        call(client,PARAMS+'/'+key,expected=200 if allowed else 404,name=label+'_detail_'+key.rsplit('.',1)[1])
        if not allowed:
            before=env.sql("SELECT version,value_json,status FROM summer_system_parameter WHERE tenant_id='ent-acme' AND parameter_key='"+key+"'",env.schema)
            call(client,PARAMS+'/'+key,'PATCH',{'version':row['version'],'value':'forbidden change'},404,name=label+'_update_denied_'+key.rsplit('.',1)[1])
            call(client,PARAMS+'/'+key+'/disable','POST',expected=404,name=label+'_disable_denied_'+key.rsplit('.',1)[1])
            after=env.sql("SELECT version,value_json,status FROM summer_system_parameter WHERE tenant_id='ent-acme' AND parameter_key='"+key+"'",env.schema)
            check(label+'_denied_writes_unchanged_'+key.rsplit('.',1)[1],before==after)

try:
    candidate=immutable_candidate()
    archive=OUT/'executed-scripts';archive.mkdir(exist_ok=True)
    for p in SCRIPT.iterdir():
        if p.is_file():shutil.copy2(p,archive/p.name)
    watched=list((ROOT/'summer-sample/src/main/java/com/abluepoint/summer/sample/authorization').glob('DataScope*.java'))+list((ROOT/'summer-ui/src/pages/data-scopes').glob('*'))
    source={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in watched if p.is_file()}
    (OUT/'source-before.json').write_text(json.dumps(source,indent=2))
    result['candidateSha256']=hashlib.sha256(candidate.read_bytes()).hexdigest()
    env=Environment();env.setup();env.start(23081,jar=candidate)
    result['stage']='real_management_api_bootstrap'
    admin=logged(env.username,env.password);boot=bootstrap(admin,env.username);admin=logged(env.username,env.password)
    catalog,_=call(admin,BASE+'/capabilities/catalog');existing={c['code']:c['capabilityId'] for c in catalog['capabilities']}
    codes=['system.parameter.'+x for x in ['read','create','update','publish','disable','write','rollback']]+['menu.systemParameters.read','menu.dataScopes.read']
    for code in codes:
        if code not in existing:
            cap,_=call(admin,BASE+'/capabilities','POST',{'code':code,'resource':code.rsplit('.',1)[0],'action':code.rsplit('.',1)[1],'riskLevel':'HIGH','scope':'PLATFORM' if code.startswith('system.') else 'ENTERPRISE'},201,headers=mh());existing[code]=cap['capabilityId']
    configrole=create_role(admin,'ent-acme',[existing[c] for c in codes]+['enterprise.organization.read'],'T17 real configuration operator')
    binding,h=call(admin,BASE+'/users/'+env.username+'/roles')
    call(admin,BASE+'/users/'+env.username+'/roles','PUT',{'roleIds':[boot,configrole],'deniedCapabilityCodes':[]},headers=mh(h['ETag']))
    admin=logged(env.username,env.password)
    call(admin,'/api/users','POST',{'username':env.denied_username,'password':env.denied_password,'role':'USER'})
    call(admin,BASE+'/members','POST',{'subject':env.denied_username,'displayName':'T17 operator','email':'t17@example.test'},201,headers=mh())
    bind(admin,'ent-acme',env.denied_username,[configrole]);reader=logged(env.denied_username,env.denied_password)
    assign(admin,env.username);assign(admin,env.denied_username);context(admin,'org-engineering');context(reader,'org-engineering')
    admin=logged(env.username,env.password);reader=logged(env.denied_username,env.denied_password)
    result['stage']='real_business_rows';prefix='scope.'+env.run;fixtures={}
    for suffix,client,org in [('admineng',admin,'org-engineering'),('adminops',admin,'org-operations'),('readereng',reader,'org-engineering'),('readerops',reader,'org-operations')]:
        key=prefix+'.'+suffix
        fixtures[key]=call(client,PARAMS,'POST',{'key':key,'type':'STRING','value':'T17 '+suffix,'organizationId':org},201)[0]
    check('four_real_rows_distinct_creator_owner_org',env.sql("SELECT COUNT(DISTINCT created_by),COUNT(DISTINCT owner_id),COUNT(DISTINCT organization_id),COUNT(*) FROM summer_system_parameter WHERE parameter_key LIKE '"+prefix+"%'",env.schema)=='2\t2\t2\t4')
    result['stage']='scope_matrix'
    versions=[]
    for field in ['TENANT','SELF','CREATOR','OWNER','ORGANIZATION','DEPARTMENT','ORGANIZATION_TREE']:
        p=policy(admin,{'field':field});versions.append(p['version'])
        expected=list(fixtures) if field=='TENANT' else [k for k in fixtures if ('reader' in k if field in ['SELF','CREATOR','OWNER'] else k.endswith('eng'))]
        matrix(field.lower(),reader,expected,fixtures)
    check('policy_version_increments',all(b>a for a,b in zip(versions,versions[1:])),versions=versions)
    policy(admin,{'field':'ORGANIZATION'});context(reader,'org-operations');reader=logged(env.denied_username,env.denied_password)
    matrix('organization_context_changed',reader,[k for k in fixtures if k.endswith('ops')],fixtures)
    result['stage']='structured_rules'
    policy(admin,{'operator':'OR','children':[{'field':'TENANT','value':'foreign-enterprise'},{'field':'CREATOR','value':"x' OR 1=1 --"}]})
    matrix('tenant_or_and_sql_literal',reader,[],fixtures)
    policy(admin,{'operator':'AND','children':[{'field':'TENANT'},{'field':'CREATOR','value':env.denied_username,'effect':'DENY'}]})
    matrix('deny_last',reader,[k for k in fixtures if 'admin' in k],fixtures)
    call(reader,PARAMS,'POST',{'key':prefix+'.denied','type':'STRING','value':'blocked'},403,name='create_scope_denied')
    before,_=call(admin,POLICY)
    for name,body in [('raw_sql',{'sql':'select * from summer_system_parameter','rules':[{'type':'TENANT'}]}),('invalid_comparator',{'conditions':{'field':'OWNER','operator':'MATCHES','value':'x'}}),('empty_tree',{'conditions':{'operator':'AND','children':[]}})]:
        call(admin,POLICY,'PUT',body,400,name=name+'_rejected')
    after,_=call(admin,POLICY);check('invalid_policies_do_not_replace_version_checksum',before['version']==after['version'] and before['checksum']==after['checksum'])
    call(reader,POLICY,'PUT',{'rules':[{'type':'TENANT'}]},403,name='operator_cannot_manage_scope')
    policy(admin,{'field':'TENANT'})
    result['stage']='real_ui'
    ui_dir=ROOT/'summer-ui'
    ui_commit=subprocess.check_output(['git','--no-optional-locks','rev-parse','HEAD'],text=True).strip()
    watched_ui=list((ui_dir/'src/pages/data-scopes').glob('*'))+list((ui_dir/'src/menu').glob('*'))+[ui_dir/'src/main.tsx',ui_dir/'src/layout.tsx',ui_dir/'src/routes.ts']
    ui_hashes={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in watched_ui if p.is_file()}
    (OUT/'ui-provenance.json').write_text(json.dumps({'mode':'current_shared_workspace','commit':ui_commit,'cwd':str(ui_dir),'sourceHashes':ui_hashes},indent=2))
    child=os.environ.copy();child.update({'SUMMER_E2E_USERNAME':env.username,'SUMMER_E2E_PASSWORD':env.password,'DATA_SCOPES_READER_USERNAME':env.denied_username,'DATA_SCOPES_READER_PASSWORD':env.denied_password,'DATA_SCOPES_OUTPUT':str(OUT),'DATA_SCOPES_VITE_CACHE':str(OUT/'vite-cache')})
    node='/opt/homebrew/opt/node@22/bin/node'
    ui=subprocess.Popen([node,str(SCRIPT/'ui-server.mjs')],cwd=ui_dir,env=child,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
    import selectors
    selector=selectors.DefaultSelector();selector.register(ui.stdout,selectors.EVENT_READ)
    ready=selector.select(timeout=45);selector.close()
    if not ready or ui.stdout.readline().strip()!='UI_READY':raise RuntimeError('ui_startup_failed')
    browser=subprocess.run([node,str(SCRIPT/'browser.mjs')],cwd=ui_dir,env=child,capture_output=True,text=True,timeout=180)
    result['browserExit']=browser.returncode
    if (OUT/'browser-result.json').exists():
        br=json.loads((OUT/'browser-result.json').read_text());result['browserCounts']={k:br[k] for k in ['tests','failures','errors','skipped']}
    ui.terminate()
    try:ui.wait(timeout=10)
    except subprocess.TimeoutExpired:ui.kill();ui.wait()
    ui=None
    result['stage']='real_second_tenant'
    context(admin,None);admin=logged(env.username,env.password)
    other,_=call(admin,'/api/v1/platform/enterprises','POST',{'name':'T17 isolated tenant','code':'scope-'+env.run},201)
    otherbase='/api/v1/enterprises/'+other['enterpriseId'];admin=logged(env.username,env.password)
    role=create_role(admin,other['enterpriseId'],[existing[c] for c in codes if c.startswith('system.')]+['enterprise.data-scope.read','enterprise.data-scope.write'],'T17 foreign configuration role')
    b,h=call(admin,otherbase+'/users/'+env.username+'/roles')
    call(admin,otherbase+'/users/'+env.username+'/roles','PUT',{'roleIds':b['roleIds']+[role],'deniedCapabilityCodes':[]},headers=mh(h['ETag']));admin=logged(env.username,env.password)
    for suffix in ['',':count','/'+prefix+'.admineng']:
        call(admin,otherbase+'/system-parameters'+suffix,expected=403,name='missing_scope_default_deny_'+suffix)
    call(admin,otherbase+'/system-parameters','POST',{'key':prefix+'.admineng','type':'STRING','value':'missing scope'},403,name='missing_scope_create_default_deny')
    call(admin,otherbase+'/data-scopes/system-parameters','PUT',{'rules':[{'type':'TENANT'}]})
    foreign,_=call(admin,otherbase+'/system-parameters','POST',{'key':prefix+'.admineng','type':'STRING','value':'foreign'},201)
    own,_=call(admin,PARAMS+'/'+prefix+'.admineng')
    check('same_key_tenant_detail_isolated',foreign['id']!=own['id'] and own['value']=='T17 admineng')
    for suffix in ['',':count','/'+prefix+'.admineng']:
        call(reader,otherbase+'/system-parameters'+suffix,expected=403,name='nonmember_tenant_denied_'+suffix)
    matrix('tenant_after_foreign_insert',reader,list(fixtures),fixtures)
    result['stage']='persisted_bindings'
    call(admin,POLICY+'/bindings/ROLE/'+configrole,'PUT',{'conditions':{'field':'CREATOR','value':env.denied_username}})
    matrix('role_intersection',reader,[k for k in fixtures if 'reader' in k],fixtures)
    call(admin,POLICY+'/bindings/USER/'+env.denied_username,'PUT',{'conditions':{'field':'ORGANIZATION','value':'org-operations'}})
    matrix('user_deny_wins',reader,[k for k in fixtures if k.endswith('readereng')],fixtures)
    preview,_=call(admin,POLICY+':preview','POST',{'subject':env.denied_username,'organizationId':'org-operations','creatorId':env.denied_username,'ownerId':env.denied_username})
    check('preview_reads_persisted_bindings',preview['allowed'] is False and preview['reasonCode']=='USER_DENY')
    call(admin,POLICY+':preview','POST',{'subject':env.denied_username,'roleAllows':['TENANT']},400,name='preview_cannot_spoof_role_allow')
    policies,_=call(admin,BASE+'/data-scopes');audit,_=call(admin,POLICY+'/audit')
    check('policy_audit_tracks_actual_version',len(audit)>0 and any(x['version']==next(p['version'] for p in policies if p['resource']=='system-parameters') for x in audit))
    result['stage']='restart_persistence'
    env.stop(23081);env.start(23081,jar=candidate);reader=logged(env.denied_username,env.denied_password);admin=logged(env.username,env.password)
    matrix('restart_persisted_effective_scope',reader,[k for k in fixtures if k.endswith('readereng')],fixtures)
    result['stage']='complete'
    result['migrations']=env.sql("SELECT version,success FROM flyway_schema_history WHERE version IN ('15','19','29') ORDER BY installed_rank",env.schema).splitlines()
except Exception as error:
    result['errorType']=type(error).__name__
    if isinstance(error,AssertionError):result['safeAssertion']=str(error)
finally:
    (OUT/'http-observations.json').write_text(json.dumps(Client.observations,indent=2))
    if ui and ui.poll() is None:
        ui.terminate()
        try:ui.wait(timeout=10)
        except subprocess.TimeoutExpired:ui.kill();ui.wait()
    if env:
        result['exceptionClasses']=env.diagnostics;result['sensitiveLogDetected']=env.sensitive_log_detected
        try:env.close();result['cleanup']='VERIFIED'
        except Exception as cleanup:result['cleanupErrorType']=type(cleanup).__name__
    if 'source' in globals():
        changed=[p for p,h in source.items() if not (ROOT/p).exists() or hashlib.sha256((ROOT/p).read_bytes()).hexdigest()!=h];result['sourceChanged']=changed
    save()
print(json.dumps({'stage':result['stage'],'counts':result['counts'],'cleanup':result.get('cleanup'),'errorType':result.get('errorType')}))
raise SystemExit(0 if result['stage']=='complete' and result.get('browserExit')==0 and not result['counts']['failures'] and not result['counts']['errors'] and result.get('cleanup')=='VERIFIED' else 1)
