"""Pinned production JAR, real PKCE/HTTP/MySQL/Chrome. No business SQL writes."""
import os, sys, json, uuid, subprocess, hashlib, runpy, re
from pathlib import Path
ROOT = Path(__file__).resolve().parents[3]
SCRIPT = Path(__file__).resolve().parent
os.environ['SUMMER_FOUNDATION_EVIDENCE_DIR'] = os.environ['SUMMER_CONFIG_EVIDENCE_DIR']
sys.path.insert(0, str(ROOT / 'scripts/acceptance/foundation'))
from environment import Environment, Client, OUT, immutable_candidate
grants = runpy.run_path(str(ROOT / 'scripts/acceptance/identity/grants.py'))
class LocalClient(Client):
    def request(self, port, *args, **kwargs): return super().request(20081, *args, **kwargs)
def logged(username, password): return LocalClient().login(20081, username, password)
result = {'issue': 114, 'stage': 'setup', 'checks': [], 'networkMocking': False, 'businessSqlWrites': False}
env = ui = browser = None
base = '/api/v1/enterprises/ent-acme'; params = base + '/dictionaries'
def check(name, passed, **facts):
    result['checks'].append({'name': name, 'passed': bool(passed), **facts})
    print(name, 'PASS' if passed else 'FAIL', flush=True)
def request(name, client, path, method='GET', body=None, expected=200, error_code=None, field=None, headers=None):
    rid = 't06-' + uuid.uuid4().hex
    status, data, response = client.request(20081, path, method, body, headers={'X-Request-Id': rid, **(headers or {})})
    check(name, status == expected, status=status, expected=expected)
    if isinstance(data, dict):
        check(name + '_correlation', data.get('requestId') == response.get('X-Request-Id') == rid)
    if error_code:
        error = data.get('error', {}) if isinstance(data, dict) else {}
        check(name + '_stable_error', error.get('code') == error_code, error_code=error.get('code'))
        if field: check(name + '_field_violation', any(v.get('field') == field for v in error.get('violations', [])))
    return data.get('data') if isinstance(data, dict) else None, response
def mutation_headers(): return {'Idempotency-Key': uuid.uuid4().hex}
def ui_inputs():
    return {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((ROOT/'summer-ui/src').rglob('*')) if p.is_file()}
before = ui_inputs()
try:
    candidate = immutable_candidate(); env = Environment(); env.extra['summer.sample.authorization.legacy-scope-compatibility'] = False
    env.setup(); env.start(20081, jar=candidate)
    result['candidate_sha256'] = hashlib.sha256(candidate.read_bytes()).hexdigest()
    result['stage'] = 'real_authorization_bootstrap'
    admin = logged(env.username, env.password); bootstrap_role = grants['bootstrap'](admin, env.username)
    admin = logged(env.username, env.password)
    catalog, _ = request('capability_catalog_real_api', admin, base+'/capabilities/catalog')
    codes = ['platform.dictionary.'+action for action in ['read','create','update','publish','disable','rollback','write']] + ['menu.dictionary.form.read']
    existing = {c['code']: c['capabilityId'] for c in catalog['capabilities']}; ids = []
    for code in codes:
        if code not in existing:
            created, _ = request('register_'+code, admin, base+'/capabilities', 'POST', {'code':code,'resource':code.rsplit('.',1)[0],'action':code.rsplit('.',1)[1],'riskLevel':'HIGH','scope':'PLATFORM' if code.startswith('platform.') else 'ENTERPRISE'}, 201, headers=mutation_headers())
            existing[code] = created['capabilityId']
        ids.append(existing[code])
    role = grants['create_role'](admin, 'ent-acme', ids, 'T06 actual parameter administration')
    assignment, h = request('read_admin_binding', admin, base+'/users/'+env.username+'/roles')
    request('extend_admin_binding_real_api', admin, base+'/users/'+env.username+'/roles', 'PUT', {'roleIds':[bootstrap_role,role],'deniedCapabilityCodes':[]}, headers={**mutation_headers(),'If-Match':h['ETag']})
    admin = logged(env.username, env.password)
    request('create_reader_identity', admin, '/api/users', 'POST', {'username':env.denied_username,'password':env.denied_password,'role':'USER'})
    request('create_reader_membership', admin, base+'/members', 'POST', {'subject':env.denied_username,'displayName':'T06 acceptance reader','email':'t06-reader@example.test'}, 201, headers=mutation_headers())
    read_role = grants['create_role'](admin, 'ent-acme', [existing['platform.dictionary.read'],existing['menu.dictionary.form.read']], 'T06 actual read-only role')
    grants['bind'](admin, 'ent-acme', env.denied_username, [read_role]); reader = logged(env.denied_username, env.denied_password)
    check('all_business_authorization_via_real_endpoints', True)
    result['ui_only_replay']=os.environ.get('SUMMER_T06_UI_ONLY')=='1'
    if not result['ui_only_replay']:
        result['stage']='dictionary_http'
        code='acceptance-'+env.run; target=params+'/'+code; public='/public/enterprises/ent-acme/dictionaries/'+code
        anonymous=LocalClient()
        created,_=request('create_dictionary_type',admin,params,'POST',{'code':code,'name':'验收字典','published':False,'publicReadable':True,'enabled':True})
        check('created_type_flags',created['code']==code and not created['published'] and created['publicReadable'] and created['enabled'])
        request('duplicate_dictionary_type',admin,params,'POST',{'code':code,'name':'重复'},409,'SUMMER_DICTIONARY_CONFLICT')
        request('invalid_type_code_contract',admin,params,'POST',{'code':'INVALID','name':'无效'},400,'SUMMER_INVALID_ARGUMENT','code')
        request('missing_type_name_contract',admin,params,'POST',{'code':code+'-missing'},400,'SUMMER_INVALID_ARGUMENT','name')
        request('unpublished_dictionary_not_public',anonymous,public,expected=404,error_code='SUMMER_NOT_FOUND')
        for value,label,order,enabled in [('alpha','初版甲',30,True),('bravo','初版乙',10,True),('charlie','已停用',20,False)]:
            item,_=request('create_item_'+value,admin,target+'/items','POST',{'value':value,'label':label,'sortOrder':order,'enabled':enabled})
            check(value+'_item_fields',item['value']==value and item['sortOrder']==order and item['enabled']==enabled)
        for name,payload,field in [('value',{'value':'INVALID','label':'无效','sortOrder':0},'value'),('label',{'value':'invalid-label','label':'','sortOrder':0},'item'),('order',{'value':'invalid-order','label':'无效','sortOrder':-1},'item')]:
            request('invalid_item_'+name,admin,target+'/items','POST',payload,400,'SUMMER_DICTIONARY_INVALID',field)
        request('duplicate_item_conflict',admin,target+'/items','POST',{'value':'alpha','label':'重复','sortOrder':0},409,'SUMMER_DICTIONARY_CONFLICT')
        request('publish_basic_dictionary_state',admin,target,'PATCH',{'name':'验收字典新版','published':True})
        visible,_=request('anonymous_reads_published_public_dictionary',anonymous,public)
        check('public_items_sorted_and_disabled_item_filtered',[x['value'] for x in visible['items']]==['bravo','alpha'])
        request('edit_and_sort_item',admin,target+'/items/alpha','PATCH',{'label':'更新甲','sortOrder':0})
        visible,_=request('public_reload_after_item_update',anonymous,public)
        check('updated_label_sort_and_version_visible',visible['items'][0]['value']=='alpha' and visible['items'][0]['label']=='更新甲')
        for flag in ['publicReadable','published','enabled']:
            request('turn_off_'+flag,admin,target,'PATCH',{'name':'验收字典新版',flag:False})
            request(flag+'_off_has_unified_not_found',anonymous,public,expected=404,error_code='SUMMER_NOT_FOUND')
            request('restore_'+flag,admin,target,'PATCH',{'name':'验收字典新版',flag:True})
        request('disable_enabled_item',admin,target+'/items/alpha','PATCH',{'label':'更新甲','sortOrder':0,'enabled':False})
        visible,_=request('read_after_item_disabled',anonymous,public)
        check('disabled_items_unavailable_to_form',[x['value'] for x in visible['items']]==['bravo'])
        request('enable_item_again',admin,target+'/items/alpha','PATCH',{'label':'更新甲','sortOrder':0,'enabled':True})
        request('read_only_management_query',reader,params)
        request('read_only_mutation_denied',reader,target+'/items','POST',{'value':'denied','label':'拒绝','sortOrder':0},403,'SUMMER_FORBIDDEN')
        request('anonymous_admin_api_denied',anonymous,params,expected=401)
        rows,_=request('dictionary_audit_query',admin,'/api/audit-events?resource=dictionary:'+code+'&size=100')
        check('all_dictionary_mutation_categories_audited',{'DICTIONARY_CREATED','DICTIONARY_UPDATED','DICTIONARY_ITEM_CREATED','DICTIONARY_ITEM_UPDATED'}.issubset({x['action'] for x in rows['items']}))
        check('audit_has_no_item_values',all(x.get('detail') in ({},None) for x in rows['items']))
        check('actual_mysql_dictionary_rows_and_migrations',int(env.sql('SELECT COUNT(*) FROM summer_dictionary_type',env.schema))==1 and int(env.sql('SELECT COUNT(*) FROM summer_dictionary_item',env.schema))==3 and int(env.sql('SELECT COUNT(*) FROM flyway_schema_history WHERE success=1',env.schema))>0)
        result['stage']='restart';env.stop(20081);env.start(20081,jar=candidate);admin=logged(env.username,env.password)
        restored,_=request('dictionary_read_after_restart',anonymous,public)
        check('dictionary_items_persist_after_restart',[x['value'] for x in restored['items']]==['alpha','bravo'] and restored['items'][0]['label']=='更新甲')
    form_target=params+'/customer-segment'
    request('create_real_form_dictionary',admin,params,'POST',{'code':'customer-segment','name':'真实表单验收','published':True,'publicReadable':True,'enabled':True})
    request('create_real_form_option',admin,form_target+'/items','POST',{'value':'acceptance-option','label':'验收选项初版','sortOrder':0,'enabled':True})
    request('create_second_form_option',admin,form_target+'/items','POST',{'value':'fallback-option','label':'备用选项','sortOrder':10,'enabled':True})
    result['stage']='browser'
    child={**os.environ,'SUMMER_E2E_USERNAME':env.username,'SUMMER_E2E_PASSWORD':env.password,'SUMMER_E2E_READER':env.denied_username,'SUMMER_E2E_READER_PASSWORD':env.denied_password,'SUMMER_E2E_RUN':env.run}
    node='/opt/homebrew/opt/node@22/bin/node'
    ui=subprocess.Popen([node,str(ROOT/'scripts/acceptance/foundation/ui-server.mjs')],cwd=ROOT/'summer-ui',env=child,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
    assert ui.stdout.readline().strip()=='UI_READY'
    browser=subprocess.Popen([node,str(SCRIPT/'t06-browser.mjs')],cwd=ROOT/'summer-ui',env=child,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
    for line in browser.stdout:
        command=line.strip()
        if command=='DICT_UPDATE_READY':request('chrome_external_option_update',admin,form_target+'/items/acceptance-option','PATCH',{'label':'验收选项新版','sortOrder':0})
        elif command=='DICT_DISABLE_READY':request('chrome_external_option_disable',admin,form_target+'/items/acceptance-option','PATCH',{'label':'验收选项新版','sortOrder':0,'enabled':False})
        elif command=='DICT_TYPE_DISABLE_READY':request('chrome_external_type_disable',admin,form_target,'PATCH',{'name':'真实表单验收','enabled':False})
        elif command=='DICT_TYPE_RESTORE_READY':request('chrome_external_type_restore',admin,form_target,'PATCH',{'name':'真实表单验收','enabled':True})
        elif command=='FAULT_READY':env.stop(20081)
        elif command=='RECOVER_READY':env.start(20081,jar=candidate)
        elif command=='DENY_READY':
            binding,h=request('reader_binding_for_explicit_deny',admin,base+'/users/'+env.denied_username+'/roles')
            request('explicit_deny_reader_real_api',admin,base+'/users/'+env.denied_username+'/roles','PUT',{'roleIds':[read_role],'deniedCapabilityCodes':['platform.dictionary.read']},headers={**mutation_headers(),'If-Match':h['ETag']})
            denied=logged(env.denied_username,env.denied_password)
            request('authenticated_public_read_denied',denied,'/public/enterprises/ent-acme/dictionaries/customer-segment',expected=403,error_code='SUMMER_FORBIDDEN')
        else:continue
        browser.stdin.write('GO\n');browser.stdin.flush()
    result['browser_exit']=browser.wait(timeout=20);result['browser']=json.loads((OUT/'browser-t06.json').read_text());result['stage']='complete'
except Exception as error:
    result['failure_type']=type(error).__name__
    if isinstance(error,AssertionError):result['assertion_class']='prerequisite_failed'
finally:
    for proc in [browser,ui]:
        if proc and proc.poll() is None:
            proc.terminate()
            try:proc.wait(timeout=15)
            except subprocess.TimeoutExpired:proc.kill();proc.wait(timeout=5)
    if env:
        try:env.close();result['cleanup']='VERIFIED'
        except Exception as error:result['cleanup']='FAILED';result['cleanup_failure_type']=type(error).__name__
    after=ui_inputs();result['ui_inputs']={'files':len(before),'stable':before==after,'changes':[p for p in set(before)|set(after) if before.get(p)!=after.get(p)]}
    (OUT/'ui-input-before.json').write_text(json.dumps(before,indent=2));(OUT/'ui-input-after.json').write_text(json.dumps(after,indent=2))
    checks=result['checks']+result.get('browser',{}).get('checks',[])
    result['counts']={'tests':len(checks),'failures':sum(not c['passed'] for c in checks),'errors':int('failure_type' in result)+int('failureType' in result.get('browser',{})),'skipped':0}
    result['passed']=result['stage']=='complete' and result['counts']['failures']==0 and result.get('browser_exit')==0 and result.get('browser',{}).get('complete') is True and result.get('cleanup')=='VERIFIED'
    (OUT/'result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({'stage':result['stage'],'passed':result['passed'],'counts':result['counts'],'cleanup':result.get('cleanup')}),flush=True)
raise SystemExit(0 if result['passed'] else 1)
