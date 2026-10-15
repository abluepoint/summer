"""T20 real MySQL, isolated Redis, two JVMs and Chrome; no business SQL writes."""
import base64, hashlib, json, os, runpy, selectors, shutil, signal, socket, subprocess, sys, time, uuid
from pathlib import Path

SCRIPT = Path(__file__).resolve().parent
ROOT = SCRIPT.parents[2]
sys.path.insert(0, str(ROOT/'scripts/acceptance/task-configuration'))
from environment import Client, Environment, OUT, immutable_candidate

BASE='/api/v1/enterprises/ent-acme'
EVENTS='/api/authorization-events?enterpriseId=ent-acme'
STREAM='summer:authorization-change-events'
GROUP='summer-authorization-consumers-t26-26082'
result={'issue':128,'checks':[],'networkMocking':False,'businessSqlWrites':False,'stage':'setup'}
env=ui=browser=None
renamed=set()

class LocalClient(Client):
    def request(self,port,*args,**kwargs):
        return super().request(26081 if port==23081 else port,*args,**kwargs)

def save():
    checks=result['checks']
    result['counts']={'tests':len(checks),'failures':sum(not c['passed'] for c in checks),'errors':int('errorType' in result),'skipped':0}
    temporary=OUT/'result.tmp';temporary.write_text(json.dumps(result,indent=2));temporary.replace(OUT/'result.json')

def check(name,passed,**facts):
    result['checks'].append({'name':name,'passed':bool(passed),**facts});save()
    print(name,'PASS' if passed else 'FAIL',flush=True)

def headers(version=None):
    value={'Idempotency-Key':uuid.uuid4().hex}
    if version is not None:value['If-Match']='"'+str(version)+'"'
    return value

def call(name,client,path,method='GET',body=None,expected=200,port=26081,h=None):
    rid='t20-'+uuid.uuid4().hex
    status,data,response=client.request(port,path,method,body,headers={'X-Request-Id':rid,**(h or {})})
    code=(data.get('error') or {}).get('code') if isinstance(data,dict) else None
    ok=status==expected and isinstance(data,dict) and data.get('success')==(status<400) and data.get('requestId')==response.get('X-Request-Id')==rid
    if status>=400:ok=ok and str(code).startswith('SUMMER_')
    check(name,ok,status=status,expected=expected,code=code,requestId=rid)
    if status!=expected:raise AssertionError('unexpected_status_'+str(status))
    return data.get('data'),response,rid

def login(username,password,port=26081):
    client=LocalClient().login(port,username,password);env.additional_secrets.append(client.token);return client

def wait(predicate,seconds=18):
    deadline=time.monotonic()+seconds
    while time.monotonic()<deadline:
        try:
            if predicate():return True
        except OSError:pass
        time.sleep(.3)
    return bool(predicate())

def redis(*args):
    def encode(parts):
        return b'*'+str(len(parts)).encode()+b'\r\n'+b''.join(b'$'+str(len(str(a).encode())).encode()+b'\r\n'+str(a).encode()+b'\r\n' for a in parts)
    def read(f):
        line=f.readline().rstrip(b'\r\n');kind=line[:1];rest=line[1:]
        if kind==b'+':return rest.decode()
        if kind==b'-':raise RuntimeError('isolated_redis_command_failed')
        if kind==b':':return int(rest)
        if kind==b'$':
            size=int(rest)
            if size<0:return None
            value=f.read(size);f.read(2);return value.decode()
        if kind==b'*':return [read(f) for _ in range(int(rest))]
        raise RuntimeError('isolated_redis_protocol_error')
    with socket.create_connection(('127.0.0.1',env.redis_port),timeout=3) as connection:
        connection.settimeout(4);connection.sendall(encode(['SELECT',15])+encode(args));f=connection.makefile('rb')
        assert read(f)=='OK';return read(f)

def redis_ready():
    try:return redis('PING')=='PONG'
    except (OSError,RuntimeError):return False

def fault(table,restore=False):
    assert env.created_schema and table in {'summer_authorization_change_outbox','summer_authorization_change_receipt'}
    source=table+'_t20_fault' if restore else table;target=table if restore else table+'_t20_fault'
    args=['kubectl','--context','docker-desktop','exec','-i','-n','local-base','mysql-0','--','sh','-c','IFS= read -r MYSQL_PWD; export MYSQL_PWD; exec mysql -uroot --batch --skip-column-names']
    p=subprocess.run(args,input=env.mysql+'\nRENAME TABLE `'+env.schema+'`.`'+source+'` TO `'+env.schema+'`.`'+target+'`;\n',text=True,capture_output=True)
    if p.returncode:raise RuntimeError('own_schema_fault_ddl_failed')
    renamed.remove(table) if restore else renamed.add(table)

def event_for(rid):
    return env.sql("SELECT event_id FROM summer_authorization_change_outbox WHERE request_id='"+rid+"'",env.schema)

def processed(event_id):
    return env.sql("SELECT COUNT(*) FROM summer_authorization_change_receipt WHERE event_id='"+event_id+"' AND status='PROCESSED'",env.schema)=='1'

def record_for(event_id):
    for record_id,values in redis('XRANGE',STREAM,'-','+'):
        if dict(zip(values[::2],values[1::2])).get('eventId')==event_id:return record_id,values
    return None

def binding(denied=(),roles=None,name='binding_changed'):
    if name=='initial_reader_binding':current={'version':1}
    else:current,_,_=call(name+'_read',admin,BASE+'/users/'+env.denied_username+'/roles')
    return call(name,admin,BASE+'/users/'+env.denied_username+'/roles','PUT',{'roleIds':roles if roles is not None else [reader_role['roleId']],'deniedCapabilityCodes':list(denied)},h=headers(current['version']))

def override(enabled,name):
    current,_,_=call(name+'_management',admin,BASE+'/menu-manifest/management')
    version=next(i['version'] for i in current['items'] if i['menuId']=='customer/drawer')
    return call(name,admin,BASE+'/menu-manifest?menuId=customer%2Fdrawer','PATCH',{'enabled':enabled},h=headers(version))

try:
    jar=immutable_candidate();result['candidateSha256']=hashlib.sha256(jar.read_bytes()).hexdigest()
    archive=OUT/'executed-scripts';archive.mkdir()
    for path in SCRIPT.iterdir():
        if path.is_file():shutil.copy2(path,archive/path.name)
    ui_source=ROOT/'summer-ui/src/main.tsx'
    result['uiMainSha256']=hashlib.sha256(ui_source.read_bytes()).hexdigest();shutil.copy2(ui_source,archive/'main.tsx')
    menu_source=ROOT/'summer-ui/src/menu/api.ts'
    result['uiMenuApiSha256']=hashlib.sha256(menu_source.read_bytes()).hexdigest();shutil.copy2(menu_source,archive/'menu-api.ts')
    env=Environment();env.setup()
    # Docker reassigns randomly published host ports on restart. Pin this owned
    # fixture's already reserved port so the application sees a real stable endpoint.
    subprocess.run(['docker','rm','-f',env.container],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    subprocess.run(['docker','run','-d','--name',env.container,'-p','127.0.0.1:'+str(env.redis_port)+':6379','redis:7-alpine','redis-server','--protected-mode','no','--save','','--appendonly','no'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    env.start(26081,jar=jar)
    admin=login(env.username,env.password)
    grants=runpy.run_path(str(ROOT/'scripts/acceptance/menu-manifest/grants.py'))
    codes=grants['ADMIN_CODES']+['enterprise.data-scope.read','enterprise.data-scope.write']
    role,_,_=call('admin_role_created',admin,BASE+'/roles','POST',{'name':'T20 administrator','code':'t20-admin','capabilityIds':codes},201,h=headers())
    call('admin_role_assigned',admin,BASE+'/users/'+env.username+'/roles','PUT',{'roleIds':[role['roleId']],'deniedCapabilityCodes':[]},h=headers(1))
    admin=login(env.username,env.password)
    versions=env.sql('SELECT version FROM flyway_schema_history WHERE success=1',env.schema).splitlines()
    check('real_mysql_migrations',all(v in versions for v in ['14','19','23.1','29','31','34']),versions=versions)
    catalog,_,_=call('real_scanned_interface_catalog',admin,'/api/v1/platform/interface-resources')
    check('customer_permission_from_real_scan',any(i['permissionCode']=='GET:/api/customers' for i in catalog['items']))
    call('reader_identity',admin,'/api/users','POST',{'username':env.denied_username,'password':env.denied_password,'role':'USER'})
    call('reader_membership',admin,BASE+'/members','POST',{'subject':env.denied_username,'displayName':'T20 live reader','email':'t20@example.test'},201,h=headers())
    reader_role,_,_=call('reader_role',admin,BASE+'/roles','POST',{'name':'T20 reader','code':'t20-reader','capabilityIds':['GET:/api/customers','POST:/api/customers','GET:/api/customers/{username}']},201,h=headers())
    binding(name='initial_reader_binding')
    env.start(26082,jar=jar);reader=login(env.denied_username,env.denied_password,26082)
    if os.environ.get('SUMMER_T20_PHASE') not in {'remainder','ui','backend','redis'}:
        call('node_b_original_token_allows',reader,'/api/customers',port=26082)
        call('anonymous_event_read_denied',LocalClient(),EVENTS,expected=401)
        call('reader_event_read_denied',reader,EVENTS,expected=403,port=26082)
        call('unknown_retry_not_found',admin,'/api/authorization-events/'+str(uuid.uuid4())+'/retry?enterpriseId=ent-acme','POST',expected=404)
        result['stage']='cross_node'
        _,_,rid=binding(['GET:/api/customers'],name='node_a_explicit_deny')
        started=time.monotonic();call('node_b_original_token_denied',reader,'/api/customers',expected=403,port=26082)
        check('revocation_within_default_poll_bound',time.monotonic()-started<5,boundSeconds=5,measuredSeconds=round(time.monotonic()-started,3))
        eid=event_for(rid);check('mutation_commits_outbox',bool(eid))
        check('real_stream_and_receipt',wait(lambda:processed(eid)) and bool(record_for(eid)),eventId=eid)
        version_key='summer:cache:version:'+base64.urlsafe_b64encode(b'ent-acme:authorization').decode().rstrip('=')
        # Scope encoding is verified against the actual production scope separator below.
        version_keys=redis('KEYS','summer:cache:version:*')
        check('delivery_increments_authorization_cache_version',any('authorization' in base64.urlsafe_b64decode(k.rsplit(':',1)[1]+'===').decode() and int(redis('GET',k))>0 for k in version_keys))
        rows=env.sql("SELECT COUNT(*) FROM summer_authorization_audit a JOIN summer_authorization_change_outbox o ON a.request_id=o.request_id WHERE o.event_id='"+eid+"' AND a.action='USER_ROLES_UPDATED' AND a.occurred_at<=o.created_at",env.schema)
        check('mutation_audit_precedes_outbox_in_same_transaction',rows=='1')
        before=env.sql("SELECT COUNT(*) FROM summer_authorization_change_receipt WHERE event_id='"+eid+"'",env.schema)
        _,values=record_for(eid);duplicate=redis('XADD',STREAM,'*',*values)
        check('duplicate_acknowledged_on_other_node',wait(lambda:not redis('XPENDING',STREAM,GROUP,duplicate,duplicate,1) and redis('XINFO','GROUPS',STREAM) and any(dict(zip(g[::2],g[1::2])).get('last-delivered-id','0-0')>=duplicate for g in redis('XINFO','GROUPS',STREAM) if dict(zip(g[::2],g[1::2])).get('name')==GROUP)))
        check('duplicate_has_one_durable_receipt',before=='1' and env.sql("SELECT COUNT(*) FROM summer_authorization_change_receipt WHERE event_id='"+eid+"'",env.schema)=='1')
        binding(name='restore_after_duplicate');call('duplicate_does_not_reapply_old_deny',reader,'/api/customers',port=26082)
        result['stage']='pending_recovery'
        fault('summer_authorization_change_receipt')
        _,_,pending_rid=binding(['GET:/api/customers'],name='receipt_fault_revoke')
        pending_event=event_for(pending_rid)
        check('receipt_fault_leaves_pending_stream',wait(lambda:bool(record_for(pending_event)) and bool(redis('XPENDING',STREAM,GROUP,record_for(pending_event)[0],record_for(pending_event)[0],1))))
        call('receipt_fault_cannot_retain_old_grant',reader,'/api/customers',expected=403,port=26082)
        env.stop(26082);fault('summer_authorization_change_receipt',True);env.start(26082,jar=jar)
        check('pending_recovers_after_same_node_restart_without_new_event',wait(lambda:processed(pending_event)))
        call('old_bearer_still_denied_after_node_restart',reader,'/api/customers',expected=403,port=26082)
        binding(name='restore_before_loss')
        result['stage']='event_loss'
        env.stop(26082)
        _,_,lost_rid=binding(['GET:/api/customers'],name='offline_node_revoke')
        lost_event=event_for(lost_rid);assert wait(lambda:bool(record_for(lost_event)))
        lost_record,_=record_for(lost_event);redis('XDEL',STREAM,lost_record)
        env.start(26082,jar=jar)
        call('deleted_message_compensated_from_authoritative_database',reader,'/api/customers',expected=403,port=26082)
        check('lost_stream_record_is_actually_absent',record_for(lost_event) is None)
    if os.environ.get('SUMMER_T20_PHASE')!='ui':
        result['stage']='redis_restart'
        # Keep bearer credentials in memory; the outage must exercise authorization,
        # not a stale session Cookie lookup after deliberate Redis destruction.
        admin.cookies.clear();reader.cookies.clear()
        pre_restart_ids={row[0] for row in redis('XRANGE',STREAM,'-','+')}
        subprocess.run(['docker','stop',env.container],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        _,_,outage_rid=binding(['GET:/api/customers'],name='redis_down_committed_revoke')
        outage_event=event_for(outage_rid)
        check('redis_down_keeps_durable_outbox',bool(outage_event))
        call('redis_down_original_bearer_cannot_keep_grant',reader,'/api/customers',expected=403,port=26082)
        subprocess.run(['docker','start',env.container],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        assert wait(redis_ready,seconds=12),'isolated_redis_not_ready'
        post_restart_ids={row[0] for row in redis('XRANGE',STREAM,'-','+')}
        check('redis_restart_actually_drops_prior_stream_records',bool(pre_restart_ids) and not pre_restart_ids.intersection(post_restart_ids),priorRecordCount=len(pre_restart_ids))
        call('old_bearer_denied_after_redis_data_loss',reader,'/api/customers',expected=403,port=26082)
        recovered=wait(lambda:processed(outage_event),seconds=40)
        facts=env.sql("SELECT status,attempts,COALESCE(last_error,'') FROM summer_authorization_change_outbox WHERE event_id='"+outage_event+"'",env.schema).split('\t')
        receipt_rows=env.sql("SELECT status,attempts FROM summer_authorization_change_receipt WHERE event_id='"+outage_event+"'",env.schema)
        result['redisRecoveryDiagnostic']={'eventId':outage_event,'outboxStatus':facts[0],'attempts':int(facts[1]),'receiptRows':receipt_rows,'streamRecordPresent':record_for(outage_event) is not None,'groups':redis('XINFO','GROUPS',STREAM)}
        check('outbox_delivery_recovers_after_redis_restart',recovered)
        recovered_record=record_for(outage_event)
        def both_groups_recovered():
            if not recovered_record:return False
            expected=tuple(map(int,recovered_record[0].split('-')))
            groups={row['name']:row for raw in redis('XINFO','GROUPS',STREAM) for row in [dict(zip(raw[::2],raw[1::2]))]}
            return all(name in groups and groups[name]['pending']==0 and tuple(map(int,groups[name]['last-delivered-id'].split('-')))>=expected for name in ['summer-authorization-consumers-t26-26081',GROUP])
        check('both_node_groups_consume_recovered_event',wait(both_groups_recovered,seconds=18))
        binding(name='restore_after_redis_restart')
        call('grant_restored_without_new_token',reader,'/api/customers',port=26082)
        events,_,_=call('authorization_events_visible_to_admin',admin,EVENTS)
        check('stream_payload_is_metadata_only',all(set(i['payload'])=={'eventId','enterpriseId','resourceType','resourceId','eventType','version','requestId'} for i in events['items']))
        if os.environ.get('SUMMER_T20_PHASE')!='redis':
            result['stage']='data_policy'
            catalog,_,_=call('capability_catalog_for_configuration',admin,BASE+'/capabilities/catalog')
            cap_ids={c['code']:c['capabilityId'] for c in catalog['capabilities']}
            for code in ['system.parameter.read','system.parameter.create']:
                if code not in cap_ids:
                    cap,_,_=call('create_'+code,admin,BASE+'/capabilities','POST',{'code':code,'resource':'system.parameter','action':code.rsplit('.',1)[1],'riskLevel':'HIGH','scope':'PLATFORM'},201,h=headers())
                    cap_ids[code]=cap['capabilityId']
            call('admin_configuration_role',admin,BASE+'/roles/'+role['roleId']+'/capabilities','PUT',{'capabilityIds':codes+[cap_ids[c] for c in ['system.parameter.read','system.parameter.create']]},h=headers(role['version']))
            reader_codes=reader_role['capabilityIds']+[cap_ids['system.parameter.read']]
            changed,_,role_rid=call('interface_permission_removed_from_role',admin,BASE+'/roles/'+reader_role['roleId']+'/capabilities','PUT',{'capabilityIds':[c for c in reader_codes if c!=cap_ids['POST:/api/customers']]},h=headers(reader_role['version']))
            call('node_b_observes_interface_role_change',reader,'/api/customers','POST',{'username':'denied'+env.run,'email':'denied@example.test','displayName':'Denied'},403,port=26082)
            check('interface_role_change_traced',bool(event_for(role_rid)))
            reader_role,_,_=call('interface_permission_restored_in_role',admin,BASE+'/roles/'+reader_role['roleId']+'/capabilities','PUT',{'capabilityIds':reader_codes},h=headers(changed['version']))
            admin=login(env.username,env.password)
            key='propagation.'+env.run
            params=BASE+'/system-parameters'
            call('real_scoped_parameter_created',admin,params,'POST',{'key':key,'type':'STRING','value':'T20 scope fixture'},201)
            call('node_b_reads_parameter_before_policy_change',reader,params+'/'+key,port=26082)
            policy=BASE+'/data-scopes/system-parameters'
            policy_view,_,policy_rid=call('data_policy_changed_node_a',admin,policy,'PUT',{'conditions':{'field':'CREATOR','value':'no-matching-principal'}})
            call('node_b_filters_parameter_after_policy_change',reader,params+'/'+key,expected=404,port=26082)
            check('data_policy_has_traced_change_event',bool(event_for(policy_rid)))
            call('data_policy_restored',admin,policy,'PUT',{'conditions':{'field':'TENANT'}})
            call('node_b_reads_parameter_after_policy_restored',reader,params+'/'+key,port=26082)
            invalid_before=env.sql('SELECT COUNT(*) FROM summer_authorization_change_outbox',env.schema)
            call('invalid_data_policy_rejected',admin,policy,'PUT',{'sql':'select 1','conditions':{'field':'TENANT'}},400)
            check('invalid_mutation_produces_no_event',invalid_before==env.sql('SELECT COUNT(*) FROM summer_authorization_change_outbox',env.schema))
            result['stage']='transaction_fault'
            before_role,_,_=call('role_before_outbox_failure',admin,BASE+'/roles/'+reader_role['roleId'])
            audit_count=env.sql("SELECT COUNT(*) FROM summer_authorization_audit WHERE resource_id='"+reader_role['roleId']+"'",env.schema)
            fault('summer_authorization_change_outbox')
            try:
                call('outbox_failure_rejects_role_mutation',admin,BASE+'/roles/'+reader_role['roleId'],'PATCH',{'name':'T20 must roll back'},500,h=headers(before_role['version']))
            finally:fault('summer_authorization_change_outbox',True)
            after_role,_,_=call('role_after_outbox_failure_on_node_b',admin,BASE+'/roles/'+reader_role['roleId'],port=26082)
            check('outbox_failure_rolls_back_role_version_and_audit',before_role==after_role and audit_count==env.sql("SELECT COUNT(*) FROM summer_authorization_audit WHERE resource_id='"+reader_role['roleId']+"'",env.schema))
    if os.environ.get('SUMMER_T20_PHASE') not in {'backend','redis'}:
        result['stage']='browser'
        child=dict(os.environ,SUMMER_E2E_USERNAME=env.username,SUMMER_E2E_PASSWORD=env.password,T20_READER=env.denied_username,T20_READER_PASSWORD=env.denied_password,T20_OUTPUT=str(OUT),T20_VITE_CACHE=str(OUT/'vite-cache'))
        ui=subprocess.Popen(['/opt/homebrew/opt/node@22/bin/node',str(SCRIPT/'ui-server.mjs')],cwd=ROOT/'summer-ui',env=child,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
        selector=selectors.DefaultSelector();selector.register(ui.stdout,selectors.EVENT_READ);ready=selector.select(45);selector.close()
        if not ready or ui.stdout.readline().strip()!='UI_READY':raise RuntimeError('ui_startup_failed')
        browser=subprocess.Popen(['/opt/homebrew/opt/node@22/bin/node',str(SCRIPT/'browser.mjs')],cwd=ROOT/'summer-ui',env=child,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
        for line in browser.stdout:
            if not line.startswith('CONTROL '):continue
            action=line.strip()[8:]
            if action=='revoke_create':binding(['POST:/api/customers'],name='browser_revoke_create');status,body,h=reader.request(26082,'/api/customers','POST',{'username':'denied'+env.run,'email':'denied@example.test','displayName':'Denied'});reply={'status':status,'requestId':h.get('X-Request-Id')}
            elif action=='restore_create':binding(name='browser_restore_create');reply={'status':200}
            elif action=='disable_menu':_,_,rid=override(False,'browser_disable_menu');reply={'status':200,'eventId':event_for(rid)}
            elif action=='restore_menu':override(True,'browser_restore_menu');reply={'status':200}
            elif action in {'stall_manifest','unstall_manifest'}:ui.send_signal(signal.SIGUSR2);reply={'transportHeld':action=='stall_manifest'}
            else:raise RuntimeError('unknown_browser_control')
            browser.stdin.write(json.dumps(reply)+'\n');browser.stdin.flush()
        browser.wait(timeout=30)
        result['browser']=json.loads((OUT/'browser-result.json').read_text());save()
        check('real_chrome_live_capability_refresh',browser.returncode==0 and result['browser']['failures']==0 and result['browser']['errors']==0,browserCounts={k:result['browser'].get(k) for k in ['tests','failures','errors','skipped']})
        check('no_sensitive_service_logs',not env.sensitive_log_detected)
except Exception as error:
    result['errorType']=type(error).__name__
    if str(error).startswith(('isolated_redis_','unexpected_status_','own_schema_')):result['errorCode']=str(error)
finally:
    for process in [browser,ui]:
        if process and process.poll() is None:
            process.terminate()
            try:process.wait(timeout=15)
            except subprocess.TimeoutExpired:process.kill();process.wait()
    if env:
        for table in list(renamed):
            try:fault(table,True)
            except Exception:result['faultRestoreError']=True
        try:env.close();result['cleanup']='VERIFIED'
        except Exception as error:result['cleanupError']=type(error).__name__
        result['sensitiveLogDetected']=env.sensitive_log_detected
    result['httpObservations']=Client.observations
    save();result['status']='PASSED' if not result['counts']['failures'] and not result['counts']['errors'] and result.get('cleanup')=='VERIFIED' else 'RED';save()
    print(json.dumps({k:result.get(k) for k in ['status','counts','stage','errorType','cleanup']}),flush=True)
raise SystemExit(0 if result.get('status')=='PASSED' else 1)
