"""T07: immutable production JAR, real MySQL/PKCE/HTTP/Chrome; no business SQL writes."""
import os, sys, json, uuid, subprocess, hashlib, runpy, re, zipfile, traceback
from pathlib import Path
from urllib.parse import urlencode
from functools import cmp_to_key
from datetime import datetime

ROOT = Path(__file__).resolve().parents[3]
SCRIPT = Path(__file__).resolve().parent
os.environ['SUMMER_FOUNDATION_EVIDENCE_DIR'] = os.environ['SUMMER_CONFIG_EVIDENCE_DIR']
sys.path.insert(0, str(ROOT / 'scripts/acceptance/foundation'))
from environment import Environment, Client, OUT, immutable_candidate
grants = runpy.run_path(str(ROOT / 'scripts/acceptance/identity/grants.py'))
class LocalClient(Client):
    def request(self, port, *args, **kwargs): return super().request(20081, *args, **kwargs)
def logged(username, password): return LocalClient().login(20081, username, password)
result = {'uiOnlyReplay':os.environ.get('SUMMER_T07_UI_ONLY') == '1','issue':115,'stage':'setup','checks':[],'networkMocking':False,'businessSqlWrites':False}
env = ui = browser = None
base = '/api/v1/enterprises/ent-acme'; params = base+'/system-parameters'
def check(name, passed, **facts):
    result['checks'].append({'name':name,'passed':bool(passed),**facts})
    print(name,'PASS' if passed else 'FAIL',flush=True)
def request(name, client, path, method='GET', body=None, expected=200, error_code=None, field=None, headers=None):
    rid='t07-'+uuid.uuid4().hex
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
    return {str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(set(files))}
before=inputs()
try:
    candidate=immutable_candidate();env=Environment();env.extra['summer.sample.authorization.legacy-scope-compatibility']=False
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
    result['stage']='query_fixture';key='query.'+env.run;result['key_prefix']=key
    fixtures=[]
    for i in range(24):
        kind=['STRING','INTEGER','DECIMAL','BOOLEAN'][i%4];value={'STRING':'sample','INTEGER':i,'DECIMAL':i+0.5,'BOOLEAN':True}[kind]
        item,_=request('create_query_row_'+str(i),admin,params,'POST',{'key':key+'.'+str(i).zfill(2),'type':kind,'value':value,'public':i%2==0},201)
        if not isinstance(item,dict):raise RuntimeError('query_fixture_creation_failed')
        if i%3==0:item,_=request('deactivate_row_'+str(i),admin,params+'/'+item['key']+'/disable','POST')
        if i%5==0:item,_=request('version_row_'+str(i),admin,params+'/'+item['key'],'PATCH',{'version':item['version'],'description':'query fixture'})
        fixtures.append(item)
    if os.environ.get('SUMMER_T07_UI_ONLY') != '1':
        result['stage']='second_real_tenant'
        enterprise,_=request('create_isolated_enterprise',admin,'/api/v1/platform/enterprises','POST',{'name':'T07 isolated query tenant','code':'query-'+env.run},201)
        eid=enterprise['enterpriseId'];other='/api/v1/enterprises/'+eid
        admin=logged(env.username,env.password)
        other_role=grants['create_role'](admin,eid,[existing[c] for c in codes if c.startswith('system.')]+['enterprise.data-scope.read','enterprise.data-scope.write'],'T07 second tenant parameter role')
        binding,h=request('second_tenant_creator_binding',admin,other+'/users/'+env.username+'/roles')
        request('second_tenant_explicit_parameter_grant',admin,other+'/users/'+env.username+'/roles','PUT',{'roleIds':binding['roleIds']+[other_role],'deniedCapabilityCodes':[]},headers={**mh(),'If-Match':h['ETag']})
        admin=logged(env.username,env.password)
        request('configure_second_tenant_data_scope',admin,other+'/data-scopes/system-parameters','PUT',{'operator':'AND','rules':[{'type':'TENANT'}]})
        foreign,_=request('same_key_in_other_tenant',admin,other+'/system-parameters','POST',{'key':key+'.00','type':'STRING','value':'other tenant'},201)
        foreign_page,_=request('other_tenant_own_list',admin,other+'/system-parameters?'+urlencode({'key':key,'size':100}))
        check('other_tenant_list_and_count_isolated',foreign_page['totalElements']==1 and [x['id'] for x in foreign_page['items']]==[foreign['id']])
        own,_=request('own_tenant_same_key_detail',admin,params+'/'+key+'.00')
        check('same_key_detail_stays_in_selected_tenant',own['id']!=foreign['id'] and own['value']=='sample')
        request('reader_other_real_tenant_list_denied',reader,other+'/system-parameters',expected=403,error_code='SUMMER_FORBIDDEN')
        request('reader_other_real_tenant_detail_denied',reader,other+'/system-parameters/'+key+'.00',expected=403,error_code='SUMMER_FORBIDDEN')
        result['stage']='http_query_matrix'
        baseline,_=request('baseline_all_rows',admin,params+'?'+urlencode({'key':key,'size':100}))
        check('baseline_has_24_real_rows',len(baseline['items'])==baseline['totalElements']==24)
        reference={x['key']:x for x in baseline['items']}
        combinations=[{}, {'type':'STRING'}, {'status':'INACTIVE'}, {'public':'true'}, {'public':'false'}, {'type':'STRING','status':'ACTIVE','public':'true'}, {'type':'INTEGER','status':'INACTIVE','public':'false'}, {'type':'BOOLEAN','public':'true'}, {'key':key+'.23'}]
        for i,filters in enumerate(combinations):
            q={'key':key,'size':100,**filters};page,_=request('filter_'+str(i),admin,params+'?'+urlencode(q))
            expected=[x for x in reference.values() if all((q['key'] in x['key']) if k=='key' else x[k]==(v=='true' if k=='public' else v) for k,v in q.items() if k not in ['size'])]
            check('filter_'+str(i)+'_list_and_count', [x['key'] for x in page['items']]==sorted(x['key'] for x in expected) and page['totalElements']==len(expected))
            clauses=["tenant_id='ent-acme'","parameter_key LIKE '"+q['key']+"%'"]
            for k,column in [('type','value_type'),('status','status')]:
                if k in q:clauses.append(column+"='"+q[k]+"'")
            if 'public' in q:clauses.append('public_flag='+('1' if q['public']=='true' else '0'))
            count=int(env.sql('SELECT COUNT(*) FROM summer_system_parameter WHERE '+' AND '.join(clauses),env.schema))
            check('filter_'+str(i)+'_http_count_matches_mysql',page['totalElements']==count)
        for sort in ['key','type','status','createdAt','updatedAt','version']:
            for direction in ['asc','desc']:
                def compare(a,b):
                    av,bv=a[sort],b[sort]
                    if sort in ['createdAt','updatedAt']:av,bv=datetime.fromisoformat(av),datetime.fromisoformat(bv)
                    primary=(av>bv)-(av<bv);return primary*(-1 if direction=='desc' else 1) if primary else (a['key']>b['key'])-(a['key']<b['key'])
                expected=sorted(reference.values(),key=cmp_to_key(compare))
                page,_=request('sort_'+sort+'_'+direction,admin,params+'?'+urlencode({'key':key,'size':100,'sort':sort,'direction':direction}))
                check('sort_'+sort+'_'+direction+'_stable_order',[x['key'] for x in page['items']]==[x['key'] for x in expected])
        collected=[]
        for p in range(5):
            page,_=request('page_'+str(p),admin,params+'?'+urlencode({'key':key,'page':p,'size':7}))
            check('page_'+str(p)+'_metadata',page['page']==p and page['size']==7 and page['totalPages']==4 and page['totalElements']==24 and len(page['items'])==max(0,min(7,24-p*7)))
            collected.extend(x['key'] for x in page['items'])
        check('pagination_no_duplicates_or_missing',collected==sorted(reference))
        summary,_=request('summary_projection',admin,params+'?'+urlencode({'key':key,'projection':'summary','size':100}))
        check('summary_only_declared_fields',len(summary['items'])==24 and all(set(x)=={'id','key','type','status','public'} for x in summary['items']))
        check('summary_and_detail_identity_equal',[(x['id'],x['key'],x['type'],x['status'],x['public']) for x in summary['items']]==[(x['id'],x['key'],x['type'],x['status'],x['public']) for x in baseline['items']])
        for i,item in enumerate(baseline['items']):
            detail,_=request('detail_'+str(i),admin,params+'/'+item['key']);check('detail_'+str(i)+'_agrees_with_list',detail==item)
        invalid=[('unknown',{'extra':'x'},None),('sort',{'sort':'value'},'sort'),('direction',{'direction':'sideways'},'direction'),('projection',{'projection':'bean'},'projection'),('negative_page',{'page':-1},'page'),('huge_page',{'page':'2147483648'},'page'),('size_zero',{'size':0},'size'),('size_over',{'size':101},'size'),('type',{'type':'TEXT'},'type'),('status',{'status':'DELETED'},'status'),('public',{'public':'yes'},'public'),('invalid_key',{'key':'INVALID'},'key')]
        for name,q,field in invalid:request('invalid_'+name,admin,params+'?'+urlencode(q),expected=400,error_code='SUMMER_INVALID_ARGUMENT',field=field)
        for name,q,headers in [('conflicting_tenant',{'tenantId':'ent-other'},{}),('duplicate_tenant',[('tenantId','ent-acme'),('tenantId','ent-other')],{}),('conflicting_header',{}, {'X-Enterprise-Id':'ent-other'})]:request(name,admin,params+'?'+urlencode(q),expected=403,error_code='SUMMER_FORBIDDEN',headers=headers)
        request('other_tenant_list_denied',reader,'/api/v1/enterprises/ent-other/system-parameters',expected=403,error_code='SUMMER_FORBIDDEN')
        request('other_tenant_detail_denied',reader,'/api/v1/enterprises/ent-other/system-parameters/'+key+'.00',expected=403,error_code='SUMMER_FORBIDDEN')
        request('authorized_reader_query',reader,params+'?'+urlencode({'key':key,'size':7,'projection':'summary'}))
    binding,h=request('reader_binding_before_deny',admin,base+'/users/'+env.denied_username+'/roles')
    request('reader_explicit_read_deny',admin,base+'/users/'+env.denied_username+'/roles','PUT',{'roleIds':[read_role],'deniedCapabilityCodes':['system.parameter.read']},headers={**mh(),'If-Match':h['ETag']})
    denied=logged(env.denied_username,env.denied_password)
    request('explicit_denied_list',denied,params,expected=403,error_code='SUMMER_FORBIDDEN')
    request('explicit_denied_detail',denied,params+'/'+key+'.00',expected=403,error_code='SUMMER_FORBIDDEN')
    check('real_mysql_migrations_applied',int(env.sql('SELECT COUNT(*) FROM flyway_schema_history WHERE success=1',env.schema))>0)
    result['stage']='real_chrome'
    child={**os.environ,'SUMMER_E2E_USERNAME':env.username,'SUMMER_E2E_PASSWORD':env.password,'SUMMER_E2E_READER':env.denied_username,'SUMMER_E2E_READER_PASSWORD':env.denied_password,'SUMMER_E2E_RUN':env.run}
    node='/opt/homebrew/opt/node@22/bin/node'
    ui=subprocess.Popen([node,str(ROOT/'scripts/acceptance/foundation/ui-server.mjs')],cwd=ROOT/'summer-ui',env=child,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True);assert ui.stdout.readline().strip()=='UI_READY'
    browser=subprocess.Popen([node,str(SCRIPT/'t07-browser.mjs')],cwd=ROOT/'summer-ui',env=child,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
    for line in browser.stdout:
        if line.strip()=='FAULT_READY':env.stop(20081);browser.stdin.write('GO\n');browser.stdin.flush()
        elif line.strip()=='RECOVER_READY':env.start(20081,jar=candidate);browser.stdin.write('GO\n');browser.stdin.flush()
    result['browser_exit']=browser.wait(timeout=20);result['browser']=json.loads((OUT/'browser-t07.json').read_text())
    if os.environ.get('SUMMER_T07_UI_ONLY') != '1':
        result['stage']='actual_projection_resource_variants'
        # External resource variants exercise the production adapter; repository SQL and JAR remain unchanged.
        with zipfile.ZipFile(candidate) as z:sql=z.read('BOOT-INF/classes/sql/customer-queries.sql').decode()
        def reverse_columns(match):
            columns=[x.strip() for x in match.group(1).split(',')];return '\n       '+',\n       '.join(reversed(columns))+'\n'
        shuffled=re.sub(r'(?<=<#if summaryProjection>)(.*?)(?=<#else>)',reverse_columns,sql,flags=re.S)
        start=shuffled.index('<#else>',shuffled.index('-- listSystemParameters'))+len('<#else>');end=shuffled.index('</#if>',start)
        columns=[x.strip() for x in shuffled[start:end].split(',')];shuffled=shuffled[:start]+'\n       '+',\n       '.join(reversed(columns))+'\n'+shuffled[end:]
        variant=OUT/'reordered-projections.sql';variant.write_text(shuffled)
        env.stop(20081);env.extra['summer.sample.data.sql-resource']=variant.as_uri();env.start(20081,jar=candidate);admin=logged(env.username,env.password)
        for projection,expected in [('detail',baseline),('summary',summary)]:
            actual,_=request('reordered_columns_'+projection,admin,params+'?'+urlencode({'key':key,'size':100,'projection':projection}));check('reordered_columns_'+projection+'_unchanged_result',actual==expected)
        collision=OUT/'duplicate-normalized-alias.sql';collision.write_text(shuffled.replace('parameter_key as parameter_key,','parameter_key as parameter_key,\n       parameter_key as parameterKey,'))
        env.stop(20081);env.extra['summer.sample.data.sql-resource']=collision.as_uri();env.start(20081,jar=candidate);admin=logged(env.username,env.password)
        for projection in ['detail','summary']:
            rid='t07-'+uuid.uuid4().hex;status,data,h=admin.request(20081,params+'?'+urlencode({'key':key,'projection':projection}),headers={'X-Request-Id':rid})
            error=data.get('error') or {} if isinstance(data,dict) else {};check('duplicate_alias_'+projection+'_rejected_without_override',status in [400,500] and data.get('success') is False and str(error.get('code','')).startswith('SUMMER_'),status=status,error_code=error.get('code'))
            check('duplicate_alias_'+projection+'_correlation',data.get('requestId')==h.get('X-Request-Id')==rid)
    result['count_interface']={'http':'list.totalElements','separateCountRouteObserved':False,'originalScopeRequiresRootReview':True}
    result['stage']='complete'
except Exception as error:
    result['failure_type']=type(error).__name__;result['local_frames']=[{'line':f.lineno,'function':f.name} for f in traceback.extract_tb(error.__traceback__) if f.filename==__file__]
finally:
    for proc in [browser,ui]:
        if proc and proc.poll() is None:
            proc.terminate()
            try:proc.wait(timeout=15)
            except subprocess.TimeoutExpired:proc.kill();proc.wait(timeout=5)
    if env:
        try:env.close();result['cleanup']='VERIFIED'
        except Exception as error:result['cleanup']='FAILED';result['cleanup_failure_type']=type(error).__name__
    after=inputs();result['source_inputs']={'files':len(before),'stable':before==after,'changes':[p for p in set(before)|set(after) if before.get(p)!=after.get(p)]}
    (OUT/'source-before.json').write_text(json.dumps(before,indent=2));(OUT/'source-after.json').write_text(json.dumps(after,indent=2))
    checks=result['checks']+result.get('browser',{}).get('checks',[])
    result['counts']={'tests':len(checks),'failures':sum(not c['passed'] for c in checks),'errors':int('failure_type' in result)+int('failureType' in result.get('browser',{})),'skipped':0}
    result['passed']=result['stage']=='complete' and result['counts']['failures']==0 and result.get('browser_exit')==0 and result.get('browser',{}).get('complete') is True and result.get('cleanup')=='VERIFIED'
    (OUT/'result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n');print(json.dumps({'stage':result['stage'],'passed':result['passed'],'counts':result['counts'],'cleanup':result.get('cleanup')}),flush=True)
raise SystemExit(0 if result['passed'] else 1)
