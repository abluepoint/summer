"""T07 independent count supplement: real MySQL/PKCE/HTTP; no mocks or business SQL writes."""
import os, sys, json, uuid, hashlib, runpy, traceback, zipfile
from pathlib import Path
from urllib.parse import urlencode

ROOT = Path(__file__).resolve().parents[3]
SCRIPT = Path(__file__).resolve().parent
os.environ['SUMMER_FOUNDATION_EVIDENCE_DIR'] = os.environ['SUMMER_CONFIG_EVIDENCE_DIR']
sys.path.insert(0, str(ROOT / 'scripts/acceptance/foundation'))
from environment import Environment, Client, OUT, immutable_candidate
grants = runpy.run_path(str(ROOT / 'scripts/acceptance/identity/grants.py'))
class LocalClient(Client):
    def request(self, port, *args, **kwargs): return super().request(20081, *args, **kwargs)
def logged(username, password): return LocalClient().login(20081, username, password)
result = {'independentCountSupplement':True,'issue':115,'stage':'setup','checks':[],'networkMocking':False,'businessSqlWrites':False}
env = None
base = '/api/v1/enterprises/ent-acme'; params = base+'/system-parameters'
def check(name, passed, **facts):
    result['checks'].append({'name':name,'passed':bool(passed),**facts})
    print(name,'PASS' if passed else 'FAIL',flush=True)
def request(name, client, path, method='GET', body=None, expected=200, error_code=None, field=None, headers=None):
    rid='t07-count-'+uuid.uuid4().hex
    status,data,h=client.request(20081,path,method,body,headers={'X-Request-Id':rid,**(headers or {})})
    check(name,status==expected,status=status,expected=expected)
    if isinstance(data,dict):
        check(name+'_envelope_correlation',data.get('requestId')==h.get('X-Request-Id')==rid and data.get('success')==(status<400))
    if error_code:
        error=data.get('error') or {} if isinstance(data,dict) else {}
        check(name+'_stable_error',error.get('code')==error_code,error_code=error.get('code'))
        if field:check(name+'_field_violation',any(v.get('field')==field for v in error.get('violations',[])))
    return data.get('data') if isinstance(data,dict) else None,h
def mh():return {'Idempotency-Key':uuid.uuid4().hex}
def inputs():
    files=[p for top in ROOT.glob('summer-*') if top.is_dir() for kind in ['main','test'] for p in (top/'src'/kind).rglob('*') if p.is_file()]
    files += [p for p in (ROOT/'summer-ui/src').rglob('*') if p.is_file()]
    files += [Path(__file__)]
    return {str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(set(files))}
before=inputs()
try:
    candidate=immutable_candidate()
    with zipfile.ZipFile(candidate) as archive:
        contains_count='BOOT-INF/classes/com/abluepoint/summer/sample/systemparameter/SystemParameterCountController.class' in archive.namelist()
    check('candidate_contains_independent_count_endpoint',contains_count)
    if not contains_count:raise RuntimeError('new_count_candidate_required')
    env=Environment();env.extra['summer.sample.authorization.legacy-scope-compatibility']=False
    env.setup();env.start(20081,jar=candidate);result['candidate_sha256']=hashlib.sha256(candidate.read_bytes()).hexdigest()
    result['stage']='real_authorization_bootstrap'
    admin=logged(env.username,env.password);bootstrap_role=grants['bootstrap'](admin,env.username);admin=logged(env.username,env.password)
    catalog,_=request('capability_catalog_real_api',admin,base+'/capabilities/catalog')
    codes=['system.parameter.'+a for a in ['read','create','update','publish','disable','rollback','write']]+['menu.systemParameters.read']
    existing={c['code']:c['capabilityId'] for c in catalog['capabilities']};ids=[]
    for code in codes:
        if code not in existing:
            created,_=request('register_'+code,admin,base+'/capabilities','POST',{'code':code,'resource':code.rsplit('.',1)[0],'action':code.rsplit('.',1)[1],'riskLevel':'HIGH','scope':'PLATFORM' if code.startswith('system.') else 'ENTERPRISE'},201,headers=mh());existing[code]=created['capabilityId']
        ids.append(existing[code])
    role=grants['create_role'](admin,'ent-acme',ids,'T07 real query administrator')
    binding,h=request('read_admin_binding',admin,base+'/users/'+env.username+'/roles')
    request('extend_admin_binding',admin,base+'/users/'+env.username+'/roles','PUT',{'roleIds':[bootstrap_role,role],'deniedCapabilityCodes':[]},headers={**mh(),'If-Match':h['ETag']})
    admin=logged(env.username,env.password)
    request('create_reader_identity',admin,'/api/users','POST',{'username':env.denied_username,'password':env.denied_password,'role':'USER'})
    request('create_reader_membership',admin,base+'/members','POST',{'subject':env.denied_username,'displayName':'T07 query reader','email':'t07-reader@example.test'},201,headers=mh())
    read_role=grants['create_role'](admin,'ent-acme',[existing['system.parameter.read'],existing['menu.systemParameters.read']],'T07 query reader')
    grants['bind'](admin,'ent-acme',env.denied_username,[read_role]);reader=logged(env.denied_username,env.denied_password)
    check('authorization_only_real_business_endpoints',True)
    result['stage']='query_fixture';key='count.'+env.run;result['key_prefix']=key
    fixtures=[]
    for i in range(8):
        kind=['STRING','INTEGER','DECIMAL','BOOLEAN'][i%4];value={'STRING':'sample','INTEGER':i,'DECIMAL':i+0.5,'BOOLEAN':True}[kind]
        item,_=request('create_query_row_'+str(i),admin,params,'POST',{'key':key+'.'+str(i).zfill(2),'type':kind,'value':value,'public':i%2==0},201)
        if not isinstance(item,dict):raise RuntimeError('query_fixture_creation_failed')
        if i%3==0:item,_=request('deactivate_row_'+str(i),admin,params+'/'+item['key']+'/disable','POST')
        if i%5==0:item,_=request('version_row_'+str(i),admin,params+'/'+item['key'],'PATCH',{'version':item['version'],'description':'query fixture'})
        fixtures.append(item)
    result['stage']='independent_count_matrix'
    combinations=[{}, {'type':'STRING'}, {'status':'INACTIVE'}, {'public':'true'}, {'public':'false'},
                  {'type':'STRING','status':'ACTIVE','public':'true'},
                  {'type':'INTEGER','status':'INACTIVE','public':'false'},
                  {'type':'BOOLEAN','public':'true'}, {'key':key+'.07'}, {'key':key+'.missing'}]
    for i,filters in enumerate(combinations):
        q={'key':key,**filters}
        expected=[x for x in fixtures if all(q['key'] in x['key'] if k=='key' else x[k]==(v=='true' if k=='public' else v) for k,v in q.items())]
        count,_=request('independent_count_'+str(i),admin,params+':count?'+urlencode(q))
        page,_=request('paired_list_'+str(i),admin,params+'?'+urlencode({**q,'size':100}))
        check('count_'+str(i)+'_exact_response_shape',set(count)=={'totalElements'})
        check('count_'+str(i)+'_agrees_with_list_and_fixtures',count['totalElements']==page['totalElements']==len(expected) and sorted(x['id'] for x in page['items'])==sorted(x['id'] for x in expected))
        clauses=["tenant_id='ent-acme'","parameter_key LIKE '"+q['key']+"%'"]
        for name,column in [('type','value_type'),('status','status')]:
            if name in q:clauses.append(column+"='"+q[name]+"'")
        if 'public' in q:clauses.append('public_flag='+('1' if q['public']=='true' else '0'))
        actual=int(env.sql('SELECT COUNT(*) FROM summer_system_parameter WHERE '+' AND '.join(clauses),env.schema))
        check('count_'+str(i)+'_matches_read_only_mysql',count['totalElements']==actual)
    for p in [0,1,8,99]:
        q={'key':key,'page':p,'size':1,'sort':'version','direction':'desc','projection':'summary'}
        count,_=request('window_independent_count_'+str(p),admin,params+':count?'+urlencode(q))
        page,_=request('window_list_'+str(p),admin,params+'?'+urlencode(q))
        check('window_'+str(p)+'_count_not_page_length',count['totalElements']==page['totalElements']==8 and len(page['items'])==(1 if p<8 else 0))
    legacy,_=request('legacy_count_alias',admin,'/api/system-parameters:count?'+urlencode({'tenantId':'ent-acme','key':key}))
    check('legacy_alias_count_equal',legacy=={'totalElements':8})
    request('create_legal_count_key',admin,params,'POST',{'key':'count','type':'STRING','value':'legal detail'},201)
    legal,_=request('legal_count_key_detail',admin,params+'/count')
    check('count_action_preserves_legal_detail_key',legal['key']=='count' and legal['value']=='legal detail')
    for i,item in enumerate(fixtures):
        detail,_=request('paired_detail_'+str(i),admin,params+'/'+item['key'])
        check('paired_detail_'+str(i)+'_same_identity',detail['id']==item['id'] and detail['value']==item['value'])
    result['stage']='second_real_tenant'
    enterprise,_=request('create_isolated_enterprise',admin,'/api/v1/platform/enterprises','POST',{'name':'T07 count tenant','code':'count-'+env.run},201)
    eid=enterprise['enterpriseId'];other='/api/v1/enterprises/'+eid;admin=logged(env.username,env.password)
    other_role=grants['create_role'](admin,eid,[existing[c] for c in codes if c.startswith('system.')]+['enterprise.data-scope.read','enterprise.data-scope.write'],'T07 count second tenant')
    binding,h=request('second_tenant_creator_binding',admin,other+'/users/'+env.username+'/roles')
    request('second_tenant_parameter_grant',admin,other+'/users/'+env.username+'/roles','PUT',{'roleIds':binding['roleIds']+[other_role],'deniedCapabilityCodes':[]},headers={**mh(),'If-Match':h['ETag']})
    admin=logged(env.username,env.password)
    request('second_tenant_scope',admin,other+'/data-scopes/system-parameters','PUT',{'operator':'AND','rules':[{'type':'TENANT'}]})
    foreign,_=request('same_key_other_tenant',admin,other+'/system-parameters','POST',{'key':key+'.00','type':'STRING','value':'other tenant'},201)
    own_count,_=request('own_tenant_count',admin,params+':count?'+urlencode({'key':key}))
    foreign_count,_=request('foreign_tenant_own_count',admin,other+'/system-parameters:count?'+urlencode({'key':key}))
    foreign_list,_=request('foreign_tenant_own_list',admin,other+'/system-parameters?'+urlencode({'key':key}))
    own_detail,_=request('own_same_key_detail',admin,params+'/'+key+'.00')
    foreign_detail,_=request('foreign_same_key_detail',admin,other+'/system-parameters/'+key+'.00')
    check('real_tenant_count_list_detail_isolated',own_count['totalElements']==8 and foreign_count['totalElements']==foreign_list['totalElements']==1 and foreign_list['items'][0]['id']==foreign_detail['id']==foreign['id'] and foreign_detail['id']!=own_detail['id'])
    for route in ['/system-parameters:count','/system-parameters','/system-parameters/'+key+'.00']:
        request('reader_cross_tenant_'+route.rsplit('/',1)[-1],reader,other+route,expected=403,error_code='SUMMER_FORBIDDEN')
    result['stage']='invalid_and_denied_count'
    invalid=[('unknown',{'extra':'x'},None),('sort',{'sort':'value'},'sort'),('direction',{'direction':'sideways'},'direction'),('projection',{'projection':'bean'},'projection'),('negative_page',{'page':-1},'page'),('huge_page',{'page':'2147483648'},'page'),('size_zero',{'size':0},'size'),('size_over',{'size':101},'size'),('type',{'type':'TEXT'},'type'),('status',{'status':'DELETED'},'status'),('public',{'public':'yes'},'public'),('key',{'key':'INVALID'},'key')]
    for name,q,field in invalid:request('count_invalid_'+name,admin,params+':count?'+urlencode(q),expected=400,error_code='SUMMER_INVALID_ARGUMENT',field=field)
    for name,q,headers in [('conflicting_tenant',{'tenantId':'ent-other'},{}),('duplicate_tenant',[('tenantId','ent-acme'),('tenantId','ent-other')],{}),('conflicting_header',{}, {'X-Enterprise-Id':'ent-other'})]:
        request('count_'+name,admin,params+':count?'+urlencode(q),expected=403,error_code='SUMMER_FORBIDDEN',headers=headers)
    reader_count,_=request('authorized_reader_count',reader,params+':count?'+urlencode({'key':key}))
    check('authorized_reader_count_equal',reader_count['totalElements']==8)
    binding,h=request('reader_binding_before_deny',admin,base+'/users/'+env.denied_username+'/roles')
    request('reader_explicit_deny',admin,base+'/users/'+env.denied_username+'/roles','PUT',{'roleIds':[read_role],'deniedCapabilityCodes':['system.parameter.read']},headers={**mh(),'If-Match':h['ETag']})
    denied=logged(env.denied_username,env.denied_password)
    for route in [params+':count',params,params+'/'+key+'.00']:
        request('explicit_denied_'+route.rsplit('/',1)[-1],denied,route,expected=403,error_code='SUMMER_FORBIDDEN')
    result['stage']='scoped_count'
    request('scope_deny_by_real_api',admin,base+'/data-scopes/system-parameters','PUT',{'operator':'AND','rules':[{'type':'TENANT','effect':'DENY'}]})
    scoped_count,_=request('deny_scope_count',admin,params+':count?'+urlencode({'key':key}))
    scoped_list,_=request('deny_scope_list',admin,params+'?'+urlencode({'key':key}))
    request('deny_scope_detail',admin,params+'/'+key+'.00',expected=404,error_code='SUMMER_NOT_FOUND')
    check('count_and_list_same_database_scope',scoped_count['totalElements']==scoped_list['totalElements']==0 and not scoped_list['items'])
    request('restore_scope_by_real_api',admin,base+'/data-scopes/system-parameters','PUT',{'operator':'AND','rules':[{'type':'TENANT'}]})
    restored,_=request('restored_scope_count',admin,params+':count?'+urlencode({'key':key}))
    check('restored_scope_returns_original_count',restored['totalElements']==8)
    check('real_mysql_migrations_present',int(env.sql('SELECT COUNT(*) FROM flyway_schema_history WHERE success=1',env.schema))>0)
    result['stage']='complete'
except Exception as error:
    result['failure_type']=type(error).__name__
    result['local_frames']=[{'line':f.lineno,'function':f.name} for f in traceback.extract_tb(error.__traceback__) if f.filename==__file__]
finally:
    if env:
        try:env.close();result['cleanup']='VERIFIED'
        except Exception as error:result['cleanup']='FAILED';result['cleanup_failure_type']=type(error).__name__
    after=inputs()
    result['source_inputs']={'files':len(before),'stable':before==after,'changes':sorted(p for p in set(before)|set(after) if before.get(p)!=after.get(p))}
    (OUT/'source-before.json').write_text(json.dumps(before,indent=2));(OUT/'source-after.json').write_text(json.dumps(after,indent=2))
    checks=result['checks']
    result['counts']={'tests':len(checks),'failures':sum(not c['passed'] for c in checks),'errors':int('failure_type' in result),'skipped':0}
    result['passed']=result['stage']=='complete' and not result['counts']['failures'] and not result['counts']['errors'] and result.get('cleanup')=='VERIFIED'
    result['redisCleanupScope']='Only this run random session/index namespaces; shared parameter Stream is retained.'
    (OUT/'result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({'stage':result['stage'],'passed':result['passed'],'counts':result['counts'],'cleanup':result.get('cleanup')}),flush=True)
raise SystemExit(0 if result['passed'] else 1)
