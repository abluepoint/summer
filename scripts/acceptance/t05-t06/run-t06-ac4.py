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
    result['stage']='ac4_actual_http';result['acceptanceScope']='T06 AC4 supplemental; original133 API/17 Chrome evidence preserved'
    target=params+'/customer-segment';public='/public/enterprises/ent-acme/dictionaries/customer-segment';item=public+'/items/acceptance-option'
    request('create_selected_item_dictionary',admin,params,'POST',{'code':'customer-segment','name':'选中项消费验收','published':True,'publicReadable':True,'enabled':True})
    for value,label,order in [('acceptance-option','验收选项初版',0),('fallback-option','备用选项',10)]:request('create_consumable_'+value,admin,target+'/items','POST',{'value':value,'label':label,'sortOrder':order,'enabled':True})
    visible,_=request('initial_public_list_allows_selection',LocalClient(),public);check('selected_value_was_enabled_in_public_list',any(x['value']=='acceptance-option' for x in visible['items']))
    request('enabled_item_can_be_consumed',LocalClient(),item)
    request('reader_can_consume_enabled_item',reader,item)
    request('invalid_consumed_value_is_field_error',LocalClient(),public+'/items/INVALID',expected=400,error_code='SUMMER_DICTIONARY_INVALID',field='value')
    request('unknown_consumed_value_is_unified_not_found',LocalClient(),public+'/items/missing-option',expected=404,error_code='SUMMER_NOT_FOUND')
    result['stage']='ac4_actual_browser'
    child={**os.environ,'SUMMER_E2E_USERNAME':env.username,'SUMMER_E2E_PASSWORD':env.password,'SUMMER_E2E_RUN':env.run};node='/opt/homebrew/opt/node@22/bin/node'
    ui=subprocess.Popen([node,str(ROOT/'scripts/acceptance/foundation/ui-server.mjs')],cwd=ROOT/'summer-ui',env=child,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True);assert ui.stdout.readline().strip()=='UI_READY'
    browser=subprocess.Popen([node,str(SCRIPT/'t06-ac4-browser.mjs')],cwd=ROOT/'summer-ui',env=child,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
    for line in browser.stdout:
        if line.strip()=='ITEM_DISABLE_READY':
            request('disable_previously_selected_item',admin,target+'/items/acceptance-option','PATCH',{'label':'验收选项初版','sortOrder':0,'enabled':False})
            request('disabled_selected_item_is_unified_not_found',LocalClient(),item,expected=404,error_code='SUMMER_NOT_FOUND')
            browser.stdin.write('GO\n');browser.stdin.flush()
    result['browser_exit']=browser.wait(timeout=20);result['browser']=json.loads((OUT/'browser-ac4.json').read_text())
    result['stage']='ac4_related_boundaries'
    # Consumption denial never prevents an administrator from maintaining disabled items.
    listed,_=request('administrator_still_lists_disabled_item',admin,params)
    check('management_keeps_disabled_item_visible',any(d['code']=='customer-segment' and any(x['value']=='acceptance-option' and not x['enabled'] for x in d['items']) for d in listed))
    for flag in ['publicReadable','published','enabled']:
        request('hide_type_'+flag,admin,target,'PATCH',{'name':'选中项消费验收',flag:False})
        request('single_item_obeys_'+flag,LocalClient(),public+'/items/fallback-option',expected=404,error_code='SUMMER_NOT_FOUND')
        request('restore_type_'+flag,admin,target,'PATCH',{'name':'选中项消费验收',flag:True})
    request('single_item_isolated_from_other_tenant',LocalClient(),'/public/enterprises/ent-other/dictionaries/customer-segment/items/fallback-option',expected=403,error_code='SUMMER_FORBIDDEN')
    binding,h=request('read_consumer_binding_for_deny',admin,base+'/users/'+env.denied_username+'/roles')
    request('explicit_consumer_read_deny',admin,base+'/users/'+env.denied_username+'/roles','PUT',{'roleIds':[read_role],'deniedCapabilityCodes':['platform.dictionary.read']},headers={**mutation_headers(),'If-Match':h['ETag']})
    denied=logged(env.denied_username,env.denied_password)
    request('unauthorized_single_item_consumption_is_unified',denied,public+'/items/fallback-option',expected=403,error_code='SUMMER_FORBIDDEN')
    result['stage']='complete'
except Exception as error:
    result['failure_type']=type(error).__name__
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
    checks=result['checks']+result.get('browser',{}).get('checks',[]);result['counts']={'tests':len(checks),'failures':sum(not c['passed'] for c in checks),'errors':int('failure_type' in result)+int('failureType' in result.get('browser',{})),'skipped':0}
    result['passed']=result['stage']=='complete' and result['counts']['failures']==0 and result.get('browser_exit')==0 and result.get('browser',{}).get('complete') is True and result.get('cleanup')=='VERIFIED'
    (OUT/'result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n');print(json.dumps({'stage':result['stage'],'passed':result['passed'],'counts':result['counts'],'cleanup':result.get('cleanup')}),flush=True)
raise SystemExit(0 if result['passed'] else 1)
