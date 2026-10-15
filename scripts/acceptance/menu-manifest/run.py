"""T16 real management API, MySQL, two JVMs and Chrome acceptance. No business SQL fixtures."""
import concurrent.futures,copy,hashlib,json,os,secrets,subprocess,threading,urllib.parse
from pathlib import Path
from environment import Environment,Client,OUT,ROOT,immutable_candidate
from grants import BASE,bootstrap,headers,data

SCRIPT=Path(__file__).resolve().parent
PLATFORM='/api/v1/platform/menu-manifest';VISIBLE=BASE+'/menu-manifest';MANAGED=VISIBLE+'/management'
result={'status':'RUNNING','tests':0,'failures':0,'errors':0,'skipped':0,'checks':[]};env=None;ui=None;browser=None;stage='setup'
def persist():
    tmp=OUT/'result.json.tmp';tmp.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n');tmp.replace(OUT/'result.json')
def check(name,condition,**evidence):
    result['checks'].append({'name':name,'status':'passed' if condition else 'failed',**evidence});result['tests']+=1;result['failures']+=not condition;persist();print(('passed ' if condition else 'RED ')+name,flush=True)
def call(name,client,path,method='GET',body=None,expected=200,port=23081,h=None,critical=False):
    status,payload,response=client.request(port,path,method,body,headers=h)
    code=(payload.get('error') or {}).get('code') if isinstance(payload,dict) else None
    check(name,status==expected,status=status,expected=expected,code=code,requestId=payload.get('requestId') if isinstance(payload,dict) else response.get('X-Request-Id'))
    if critical and status!=expected:raise RuntimeError('required_step_failed')
    return payload,response

def fresh(name,password,port=23081):
    client=Client().login(port,name,password);env.additional_secrets.append(client.token);return client

def read(client,path=MANAGED,port=23081):
    status,body,_=client.request(port,path);assert status==200,'required_read_failed';return data(body)

def override(client,menu,patch,version=None,port=23081):
    if version is None:version=next(i for i in read(client,MANAGED,port)['items'] if i['menuId']==menu)['version']
    return client.request(port,VISIBLE+'?'+urllib.parse.urlencode({'menuId':menu}),'PATCH',patch,headers=headers(version))

try:
    candidate=immutable_candidate();result['candidate_sha256']=hashlib.sha256(candidate.read_bytes()).hexdigest()
    subprocess.run(['/opt/homebrew/opt/node@22/bin/node',str(SCRIPT/'export-manifest.mjs')],cwd=ROOT/'summer-ui',env={**os.environ,'MENU_MANIFEST_EXPORT':str(OUT/'actual-route-manifest.json')},check=True)
    manifest=json.loads((OUT/'actual-route-manifest.json').read_text())
    allowed={'menuId','parentMenuId','path','titleKey','iconKey','sortOrder','capabilityCode'}
    check('actual_route_manifest_contains_only_safe_metadata',len(manifest['items'])>0 and all(set(i)<=allowed for i in manifest['items']),items=len(manifest['items']),manifestVersion=manifest['manifestVersion'])
    env=Environment();env.setup();env.start(23081,jar=candidate)
    admin=fresh(env.username,env.password);bootstrap(admin,env.username);admin=fresh(env.username,env.password)
    check('real_pkce_and_management_api_bootstrap',True)
    migrations=env.sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version='14' AND success=1",env.schema)
    check('real_mysql_menu_migration',migrations=='1')
    body,response=call('sync_actual_frontend_route_manifest',admin,PLATFORM,'PUT',manifest,h=headers(),critical=True)
    check('manifest_envelope_etag_and_request_correlation',body['success'] is True and response.get('ETag')=='"5"' and response.get('X-Request-Id')==body['requestId'])
    check('backend_manifest_has_no_component_or_expression_fields',all(set(i)<=allowed|{'enabled','actions','version'} for i in data(body)['items']))
    key=headers();body,_=call('same_version_same_content_is_idempotent',admin,PLATFORM,'PUT',manifest,h=key)
    call('same_key_same_content_replay',admin,PLATFORM,'PUT',manifest,h=key)
    modified=copy.deepcopy(manifest);modified['items'][0]['titleKey']='menu.changed'
    call('same_version_changed_content_rejected',admin,PLATFORM,'PUT',modified,expected=409,h=headers())
    call('idempotency_key_changed_content_rejected',admin,PLATFORM,'PUT',modified,expected=409,h=key)
    older=copy.deepcopy(manifest);older['manifestVersion']=4
    call('older_manifest_version_rejected',admin,PLATFORM,'PUT',older,expected=409,h=headers())
    for name,mutate in [('negative_sort',lambda m:m['items'][0].update(sortOrder=-1)),('unknown_parent',lambda m:m['items'][0].update(parentMenuId='missing-parent')),('self_parent',lambda m:m['items'][0].update(parentMenuId=m['items'][0]['menuId'])),('executable_path',lambda m:m['items'][0].update(path='/${expression}'))]:
        bad=copy.deepcopy(manifest);bad['manifestVersion']=6;mutate(bad)
        call('invalid_manifest_'+name,admin,PLATFORM,'PUT',bad,expected=400,h=headers())
    reader_name='t16-reader';reader_password=secrets.token_urlsafe(24);env.additional_secrets.append(reader_password)
    call('reader_identity_created',admin,'/api/users','POST',{'username':reader_name,'password':reader_password,'role':'USER'},critical=True)
    call('reader_membership_created',admin,BASE+'/members','POST',{'subject':reader_name,'displayName':'T16 reader','email':'t16-reader@example.test'},expected=201,h=headers(),critical=True)
    role_body={'name':'T16 read only','code':'t16-reader-role','capabilityIds':['enterprise.menu.read','menu.menuManifest.read','menu.securityKeys.read','GET:/api/customers','GET:/api/customers/{username}']}
    body,_=call('reader_role_created',admin,BASE+'/roles','POST',role_body,expected=201,h=headers(),critical=True);reader_role=data(body)
    call('reader_grants_assigned_via_api',admin,BASE+'/users/'+reader_name+'/roles','PUT',{'roleIds':[reader_role['roleId']],'deniedCapabilityCodes':[]},h=headers(1),critical=True)
    reader=fresh(reader_name,reader_password)
    body,_=call('reader_visible_manifest_allowed',reader,VISIBLE,critical=True)
    check('visible_capability_action_matches_read',any(i['menuId']=='security-keys' and i['actions']==['read'] for i in data(body)['items']))
    call('reader_cannot_manage_menu',reader,MANAGED,expected=403)
    call('reader_cannot_publish_manifest',reader,PLATFORM,'PUT',manifest,expected=403,h=headers())
    call('reader_cannot_override_menu',reader,VISIBLE+'?menuId=security-keys','PATCH',{'enabled':False},expected=403,h=headers(1))
    call('anonymous_manifest_denied',Client(),VISIBLE,expected=401)
    call('cross_tenant_manifest_denied',reader,'/api/v1/enterprises/ent-other/menu-manifest',expected=403)
    call('read_only_user_actual_business_read_allowed',reader,'/api/customers')
    call('read_only_user_actual_business_write_denied',reader,'/api/customers','POST',{'username':'t16-denied-create','email':'t16-denied@example.test','displayName':'Denied'},expected=403)
    status,body,_=override(admin,'security-keys',{'enabled':False,'sortOrder':77,'parentMenuId':'roles','capabilityCode':'menu.menuManifest.read'})
    check('admin_configures_enable_parent_sort_capability',status==200 and all(data(body).get(k)==v for k,v in {'enabled':False,'sortOrder':77,'parentMenuId':'roles','capabilityCode':'menu.menuManifest.read'}.items()))
    check('disabled_menu_absent_from_visible_manifest',all(i['menuId']!='security-keys' for i in read(reader,VISIBLE)['items']))
    saved=data(body);call('stale_override_version_rejected',admin,VISIBLE+'?menuId=security-keys','PATCH',{'sortOrder':88},expected=412,h=headers(99))
    status,body,_=override(admin,'security-keys',{'enabled':True,'parentMenuId':'','capabilityCode':'menu.securityKeys.read'})
    check('admin_restores_root_menu',status==200 and data(body)['parentMenuId'] is None)
    check('restored_menu_visible_again',any(i['menuId']=='security-keys' for i in read(reader,VISIBLE)['items']))
    call('invalid_override_parent_rejected',admin,VISIBLE+'?menuId=security-keys','PATCH',{'parentMenuId':'missing-parent'},expected=400,h=headers(data(body)['version']))
    current=next(i for i in read(admin)['items'] if i['menuId']=='security-keys')
    env.start(23082,jar=candidate);admin2=fresh(env.username,env.password,23082)
    check('second_node_reads_same_manifest_and_override',read(admin,MANAGED)==read(admin2,MANAGED,23082))
    barrier=threading.Barrier(2)
    def contend(client,port,order):
        barrier.wait(timeout=10);status,body,response=override(client,'security-keys',{'sortOrder':order},current['version'],port)
        return {'node':port,'submitted_version':current['version'],'status':status,'returned_version':data(body)['version'] if status==200 else None,'code':(body.get('error') or {}).get('code'),'requestId':body.get('requestId'),'sortOrder':order}
    with concurrent.futures.ThreadPoolExecutor(2) as pool:
        futures=[pool.submit(contend,admin,23081,101),pool.submit(contend,admin2,23082,102)];writes=[f.result() for f in futures]
    result['concurrent_override']=writes
    check('cross_node_override_version_allows_exactly_one_writer',sorted(i['status'] for i in writes)==[200,412],writes=writes)
    snapshot=read(admin);env.stop(23082);env.start(23082,jar=candidate);admin2=fresh(env.username,env.password,23082)
    check('menu_metadata_and_override_survive_node_restart',read(admin2,MANAGED,23082)==snapshot)
    # Prepare a visible managed menu for actual admin UI, preserving read-only user's rights.
    override(admin,'security-keys',{'enabled':True,'parentMenuId':'','sortOrder':33,'capabilityCode':'menu.securityKeys.read'})
    stage='browser';runenv={**os.environ,'SUMMER_E2E_USERNAME':env.username,'SUMMER_E2E_PASSWORD':env.password,'MENU_READER_USERNAME':reader_name,'MENU_READER_PASSWORD':reader_password,'MENU_OUTPUT':str(OUT),'MENU_MANIFEST_VITE_CACHE':str(OUT/('vite-cache-'+env.run))}
    ui=subprocess.Popen(['/opt/homebrew/opt/node@22/bin/node',str(SCRIPT/'ui-server.mjs')],cwd=ROOT/'summer-ui',env=runenv,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True);assert ui.stdout.readline().strip()=='UI_READY'
    browser=subprocess.Popen(['/opt/homebrew/opt/node@22/bin/node',str(SCRIPT/'browser.mjs')],cwd=ROOT/'summer-ui',env=runenv,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
    for line in browser.stdout:
        if not line.startswith('CONTROL '):continue
        action=line.strip()[8:];reply={}
        if action=='reader_write':
            client=fresh(reader_name,reader_password);status,body,_=client.request(23081,'/api/customers','POST',{'username':'t16-denied-create','email':'t16-denied@example.test','displayName':'Denied'});reply={'status':status,'code':(body.get('error') or {}).get('code'),'requestId':body.get('requestId')}
        elif action=='disable_customer':
            status,_,_=override(admin,'customer/drawer',{'enabled':False});client=fresh(reader_name,reader_password);visible=read(client,VISIBLE);reply={'status':status,'visible':any(i['menuId']=='customer/drawer' for i in visible['items'])}
        elif action=='stop':env.stop(23081)
        elif action=='restart':env.start(23081,jar=candidate)
        else:raise RuntimeError('unknown_browser_control')
        browser.stdin.write(json.dumps(reply)+'\n');browser.stdin.flush()
    browser.wait(timeout=30)
    if not (OUT/'browser-result.json').exists():raise RuntimeError('browser_receipt_missing')
    result['browser']=json.loads((OUT/'browser-result.json').read_text());result['browser_exit_code']=browser.returncode
    check('runtime_logs_no_known_secret_values',not env.sensitive_log_detected)
    result['status']='INCOMPLETE' if result['browser'].get('errors') or result['browser'].get('status')=='INCOMPLETE' else ('RED' if result['failures'] or result['browser']['failures'] or browser.returncode else 'PASSED')
except Exception as error:result.update(status='INCOMPLETE',failed_stage=stage,error_type=type(error).__name__);result['errors']+=1
finally:
    for process in [browser,ui]:
        if process and process.poll() is None:
            process.terminate()
            try:process.wait(timeout=15)
            except subprocess.TimeoutExpired:process.kill();process.wait()
    if env:
        try:env.close();result['cleanup']='VERIFIED'
        except Exception as error:result.update(cleanup='FAILED',cleanup_error=type(error).__name__);result['errors']+=1
    result['http_observations']=Client.observations;persist()
    print(json.dumps({k:result.get(k) for k in ['status','tests','failures','errors','failed_stage','cleanup']}),flush=True)
raise SystemExit(0 if result['status']=='PASSED' and result.get('cleanup')=='VERIFIED' else 1)
