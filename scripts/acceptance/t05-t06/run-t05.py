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
result = {'issue': 113, 'stage': 'setup', 'checks': [], 'networkMocking': False, 'businessSqlWrites': False}
env = ui = browser = None
base = '/api/v1/enterprises/ent-acme'; params = base + '/system-parameters'
def check(name, passed, **facts):
    result['checks'].append({'name': name, 'passed': bool(passed), **facts})
    print(name, 'PASS' if passed else 'FAIL', flush=True)
def request(name, client, path, method='GET', body=None, expected=200, error_code=None, field=None, headers=None):
    rid = 't05-' + uuid.uuid4().hex
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
    codes = ['system.parameter.'+action for action in ['read','create','update','publish','disable','rollback','write']] + ['menu.systemParameters.read']
    existing = {c['code']: c['capabilityId'] for c in catalog['capabilities']}; ids = []
    for code in codes:
        if code not in existing:
            created, _ = request('register_'+code, admin, base+'/capabilities', 'POST', {'code':code,'resource':code.rsplit('.',1)[0],'action':code.rsplit('.',1)[1],'riskLevel':'HIGH','scope':'PLATFORM' if code.startswith('system.') else 'ENTERPRISE'}, 201, headers=mutation_headers())
            existing[code] = created['capabilityId']
        ids.append(existing[code])
    role = grants['create_role'](admin, 'ent-acme', ids, 'T05 actual parameter administration')
    assignment, h = request('read_admin_binding', admin, base+'/users/'+env.username+'/roles')
    request('extend_admin_binding_real_api', admin, base+'/users/'+env.username+'/roles', 'PUT', {'roleIds':[bootstrap_role,role],'deniedCapabilityCodes':[]}, headers={**mutation_headers(),'If-Match':h['ETag']})
    admin = logged(env.username, env.password)
    request('create_reader_identity', admin, '/api/users', 'POST', {'username':env.denied_username,'password':env.denied_password,'role':'USER'})
    request('create_reader_membership', admin, base+'/members', 'POST', {'subject':env.denied_username,'displayName':'T05 acceptance reader','email':'t05-reader@example.test'}, 201, headers=mutation_headers())
    read_role = grants['create_role'](admin, 'ent-acme', [existing['system.parameter.read'],existing['menu.systemParameters.read']], 'T05 actual read-only role')
    grants['bind'](admin, 'ent-acme', env.denied_username, [read_role]); reader = logged(env.denied_username, env.denied_password)
    check('all_business_authorization_via_real_endpoints', True)
    key = 'acceptance.'+env.run; result['key_prefix'] = key
    result['ui_only_replay'] = os.environ.get('SUMMER_T05_UI_ONLY') == '1'
    if not result['ui_only_replay']:
        result['stage'] = 'typed_http'
        valid = [('integer','INTEGER',25,{'required':True,'min':1,'max':100}),('decimal','DECIMAL',2.5,{'min':1,'max':10}),('boolean','BOOLEAN',True,{}),('string','STRING','AB',{'pattern':'^[A-Z]{2}$','required':True})]
        created = {}
        for suffix, kind, value, rules in valid:
            item, _ = request('create_typed_'+suffix, admin, params, 'POST', {'key':key+'.'+suffix,'type':kind,'value':value,'public':False,**rules},201)
            if not isinstance(item,dict): raise RuntimeError('typed_creation_failed')
            created[suffix] = item
            check(suffix+'_uuidv7_initial_version', uuid.UUID(item['id']).version==7 and item['version']==0 and item['status']=='ACTIVE')
        invalid = [('integer_fraction',{'type':'INTEGER','value':1.5},'value'),('boolean_text',{'type':'BOOLEAN','value':'true'},'value'),('required_null',{'type':'STRING','required':True,'value':None},'value'),('below_min',{'type':'INTEGER','value':0,'min':1},'value'),('above_max',{'type':'INTEGER','value':11,'max':10},'value'),('format',{'type':'STRING','value':'lower','pattern':'^[A-Z]+$'},'value'),('invalid_regex',{'type':'STRING','value':'AB','pattern':'['},'pattern'),('min_above_max',{'type':'INTEGER','value':5,'min':10,'max':1},'min'),('missing_type',{'value':'AB'},'type'),('invalid_key',{'key':'INVALID','type':'STRING','value':'AB'},'key')]
        for label, payload, field in invalid:
            request('invalid_'+label, admin, params, 'POST', {'key':key+'.invalid-'+label,**payload},400,'SUMMER_PARAMETER_INVALID',field)
        target = params+'/'+key+'.integer'
        request('duplicate_key_conflict', admin, params,'POST',{'key':key+'.integer','type':'INTEGER','value':1},409,'SUMMER_PARAMETER_CONFLICT')
        updated, _ = request('update_with_expected_version',admin,target,'PATCH',{'value':50,'version':0,'publicValue':True})
        check('stable_id_incremented_version',updated['id']==created['integer']['id'] and updated['version']==1 and updated['public'] is True)
        request('stale_version_conflict',admin,target,'PATCH',{'value':75,'version':0},409,'SUMMER_PARAMETER_VERSION_CONFLICT')
        request('missing_version_rejected',admin,target,'PATCH',{'value':75},409,'SUMMER_PARAMETER_VERSION_CONFLICT')
        disabled,_ = request('disable_parameter',admin,target+'/disable','POST');check('disabled_status',disabled['status']=='INACTIVE')
        enabled,_ = request('enable_parameter',admin,target+'/enable','POST');check('enabled_status',enabled['status']=='ACTIVE')
        request('read_only_list',reader,params)
        request('read_only_mutation_denied',reader,params,'POST',{'key':key+'.forbidden','type':'STRING','value':'AB'},403,'SUMMER_FORBIDDEN')
        request('anonymous_protected_api',LocalClient(),params,expected=401)
        request('invalid_query_page_size',admin,params+'?size=0',expected=400,error_code='SUMMER_INVALID_ARGUMENT',field='size')
        rows,_ = request('audit_query',admin,'/api/audit-events?resource=system-parameter:'+key+'.integer&size=100')
        actions={x['action'] for x in rows['items']};check('successful_writes_audited',{'SYSTEM_PARAMETER_CREATED','SYSTEM_PARAMETER_UPDATED'}.issubset(actions))
        check('audit_does_not_include_configuration_values',all(not any(k in json.dumps(x).lower() for k in ['value_json','"value":']) for x in rows['items']))
        check('actual_mysql_rows_and_migrations',int(env.sql('SELECT COUNT(*) FROM summer_system_parameter',env.schema))==4 and int(env.sql('SELECT COUNT(*) FROM flyway_schema_history WHERE success=1',env.schema))>0)
        result['stage']='restart'; env.stop(20081); env.start(20081,jar=candidate);admin=logged(env.username,env.password)
        restored,_=request('parameter_after_restart',admin,target);check('typed_id_value_version_persist_after_restart',restored['id']==enabled['id'] and restored['value']==50 and restored['version']==enabled['version'])
    result['stage']='browser'
    child={**os.environ,'SUMMER_E2E_USERNAME':env.username,'SUMMER_E2E_PASSWORD':env.password,'SUMMER_E2E_READER':env.denied_username,'SUMMER_E2E_READER_PASSWORD':env.denied_password,'SUMMER_E2E_RUN':env.run}
    node='/opt/homebrew/opt/node@22/bin/node'
    ui=subprocess.Popen([node,str(ROOT/'scripts/acceptance/foundation/ui-server.mjs')],cwd=ROOT/'summer-ui',env=child,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
    assert ui.stdout.readline().strip()=='UI_READY'
    browser=subprocess.Popen([node,str(SCRIPT/'t05-browser.mjs')],cwd=ROOT/'summer-ui',env=child,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
    for line in browser.stdout:
        if line.strip()=='CONFLICT_READY':
            ui_key=params+'/'+key+'.ui';current,_=request('concurrent_read_actual_api',admin,ui_key)
            request('concurrent_change_actual_api',admin,ui_key,'PATCH',{'value':40,'version':current['version']});browser.stdin.write('GO\n');browser.stdin.flush()
        elif line.strip()=='FAULT_READY': env.stop(20081);browser.stdin.write('GO\n');browser.stdin.flush()
        elif line.strip()=='RECOVER_READY': env.start(20081,jar=candidate);browser.stdin.write('GO\n');browser.stdin.flush()
    result['browser_exit']=browser.wait(timeout=20)
    observed=json.loads((OUT/'browser-t05.json').read_text());result['browser']=observed
    result['stage']='complete'
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
    (OUT/'ui-input-before.json').write_text(json.dumps(before,indent=2))
    (OUT/'ui-input-after.json').write_text(json.dumps(after,indent=2))
    checks=result['checks']+result.get('browser',{}).get('checks',[])
    result['counts']={'tests':len(checks),'failures':sum(not c['passed'] for c in checks),'errors':int('failure_type' in result)+int('failureType' in result.get('browser',{})),'skipped':0}
    result['passed']=result['stage']=='complete' and result['counts']['failures']==0 and result.get('browser_exit')==0 and result.get('browser',{}).get('complete') is True and result.get('cleanup')=='VERIFIED'
    (OUT/'result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({'stage':result['stage'],'passed':result['passed'],'counts':result['counts'],'cleanup':result.get('cleanup')}),flush=True)
raise SystemExit(0 if result['passed'] else 1)
