"""T08 native delivery audit. Dedicated MySQL schema/Redis; all business writes use HTTP."""
import hashlib, json, os, runpy, socket, subprocess, sys, time, uuid
from pathlib import Path
from urllib.parse import urlencode

from environment import Environment, Client, OUT, ROOT, immutable_candidate

PORT = 20081
STREAM = 'summer:system-parameter-events'
GROUP = 'summer-system-parameter-consumers-t08-20081'
BASE = '/api/v1/enterprises/ent-acme'
PARAMS = BASE + '/system-parameters'
EVENTS = BASE + '/system-parameter-events'
grants = runpy.run_path(str(ROOT / 'scripts/acceptance/identity/grants.py'))
RECOVERY_ONLY = os.environ.get('SUMMER_T08_RECOVERY_ONLY') == '1'
result = {'recoveryOnly':RECOVERY_ONLY, 'issue':116, 'checks':[], 'networkMocking':False, 'businessSqlWrites':False,
          'faultInjection':'DDL rename only in freshly provisioned acceptance schema', 'stage':'setup'}
env = ui = browser = None
renamed = set()

class LocalClient(Client):
    def request(self, port, *args, **kwargs): return super().request(PORT, *args, **kwargs)

def check(name, passed, **facts):
    result['checks'].append({'name':name, 'passed':bool(passed), **facts})
    print(name, 'PASS' if passed else 'FAIL', flush=True)

def request(name, client, path, method='GET', body=None, expected=200, headers=None):
    rid = 't08-' + uuid.uuid4().hex
    status, data, h = client.request(PORT, path, method, body, headers={'X-Request-Id':rid, **(headers or {})})
    check(name, status == expected, status=status, expected=expected)
    if isinstance(data, dict):
        check(name+'_correlation', data.get('requestId') == h.get('X-Request-Id') == rid
              and data.get('success') == (status < 400))
        if status >= 400:
            check(name+'_summer_error', str((data.get('error') or {}).get('code','')).startswith('SUMMER_'))
    return data.get('data') if isinstance(data, dict) else None, h

def wait_until(predicate, seconds=8):
    deadline=time.monotonic()+seconds
    while time.monotonic()<deadline:
        if predicate(): return True
        time.sleep(.25)
    return bool(predicate())

def redis(*args):
    def encode(parts):
        return b'*'+str(len(parts)).encode()+b'\r\n'+b''.join(b'$'+str(len(str(a).encode())).encode()+b'\r\n'+str(a).encode()+b'\r\n' for a in parts)
    def read(f):
        line=f.readline().rstrip(b'\r\n'); kind=line[:1]; rest=line[1:]
        if kind==b'+': return rest.decode()
        if kind==b'-': raise RuntimeError('isolated_redis_command_failed')
        if kind==b':': return int(rest)
        if kind==b'$':
            size=int(rest)
            if size<0:return None
            value=f.read(size);f.read(2);return value.decode()
        if kind==b'*':return [read(f) for _ in range(int(rest))]
        raise RuntimeError('isolated_redis_protocol_error')
    with socket.create_connection(('127.0.0.1',env.redis_port),timeout=3) as connection:
        connection.settimeout(4);connection.sendall(encode(['SELECT',15])+encode(args));f=connection.makefile('rb')
        assert read(f)=='OK';return read(f)

def fault(table, restore=False):
    assert env.created_schema and table in {'summer_system_parameter_outbox','summer_system_parameter_event_receipt'}
    source=table+'_t08_fault' if restore else table;target=table if restore else table+'_t08_fault'
    # Infrastructure DDL only, intentionally separate from the read-only/business-write guard.
    args=['kubectl','--context','docker-desktop','exec','-i','-n','local-base','mysql-0','--','sh','-c',
          'IFS= read -r MYSQL_PWD; export MYSQL_PWD; exec mysql -uroot --batch --skip-column-names']
    p=subprocess.run(args,input=env.mysql+'\nRENAME TABLE `'+env.schema+'`.`'+source+'` TO `'+env.schema+'`.`'+target+'`;\n',text=True,capture_output=True)
    if p.returncode:raise RuntimeError('own_schema_fault_ddl_failed')
    if restore:renamed.remove(table)
    else:renamed.add(table)

def receipt(event_id):
    return env.sql("SELECT COUNT(*) FROM summer_system_parameter_event_receipt WHERE event_id='"+event_id+"'",env.schema)=='1'

def event_for(key):
    return env.sql("SELECT event_id FROM summer_system_parameter_outbox WHERE parameter_key='"+key+"'",env.schema)

def published(event_id):
    return env.sql("SELECT status FROM summer_system_parameter_outbox WHERE event_id='"+event_id+"'",env.schema)=='PUBLISHED'

def stream_id(event_id):
    for record_id,values in redis('XRANGE',STREAM,'-','+'):
        if dict(zip(values[::2],values[1::2])).get('eventId')==event_id:return record_id
    raise RuntimeError('native_event_stream_record_absent')

def pending_record(record_id):
    return redis('XPENDING',STREAM,GROUP,record_id,record_id,1)

def create(name):
    key='event.'+env.run+'.'+name
    request('create_'+name,admin,PARAMS,'POST',{'key':key,'type':'STRING','value':canary,'public':False},201)
    event_id=event_for(key);assert event_id
    check(name+'_outbox_committed_with_parameter',bool(event_id))
    check(name+'_published',wait_until(lambda:published(event_id)))
    return key,event_id

def mh():return {'Idempotency-Key':uuid.uuid4().hex}

try:
    candidate=immutable_candidate();result['candidate_sha256']=hashlib.sha256(candidate.read_bytes()).hexdigest()
    env=Environment();env.extra.update({'summer.sample.parameter-events.scheduling-enabled':True,
                                      'summer.sample.parameter-events.poll-interval-ms':250})
    canary='t08-sensitive-'+uuid.uuid4().hex;env.additional_secrets.append(canary)
    env.setup();env.start(PORT,jar=candidate)
    result['stage']='real_authorization'
    admin=LocalClient().login(PORT,env.username,env.password)
    bootstrap_role=grants['bootstrap'](admin,env.username);admin=LocalClient().login(PORT,env.username,env.password)
    catalog,_=request('capability_catalog',admin,BASE+'/capabilities/catalog')
    existing={c['code']:c['capabilityId'] for c in catalog['capabilities']}
    codes=['system.parameter.'+a for a in ['read','create','update','publish','disable','rollback','write']]+['menu.systemParameters.read']
    for code in codes:
        if code not in existing:
            created,_=request('register_'+code,admin,BASE+'/capabilities','POST',{'code':code,'resource':code.rsplit('.',1)[0],'action':code.rsplit('.',1)[1],'riskLevel':'HIGH','scope':'PLATFORM' if code.startswith('system.') else 'ENTERPRISE'},201,mh())
            existing[code]=created['capabilityId']
    role=grants['create_role'](admin,'ent-acme',[existing[c] for c in codes],'T08 event administrator')
    binding,h=request('admin_binding',admin,BASE+'/users/'+env.username+'/roles')
    request('grant_admin',admin,BASE+'/users/'+env.username+'/roles','PUT',{'roleIds':[bootstrap_role,role],'deniedCapabilityCodes':[]},headers={**mh(),'If-Match':h['ETag']})
    admin=LocalClient().login(PORT,env.username,env.password)
    request('create_reader',admin,'/api/users','POST',{'username':env.denied_username,'password':env.denied_password,'role':'USER'})
    request('reader_membership',admin,BASE+'/members','POST',{'subject':env.denied_username,'displayName':'T08 event reader','email':'t08@example.test'},201,mh())
    reader_role=grants['create_role'](admin,'ent-acme',[existing['system.parameter.read'],existing['menu.systemParameters.read']],'T08 event reader')
    grants['bind'](admin,'ent-acme',env.denied_username,[reader_role]);reader=LocalClient().login(PORT,env.denied_username,env.denied_password)
    check('real_flyway_event_migrations',env.sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('10','11') AND success=1",env.schema)=='2')
    result['stage']='cold_stream_first_delivery'
    result['initialStreamState']={'exists':redis('EXISTS',STREAM),'length':redis('XLEN',STREAM)}
    key,first=create('first')
    check('cold_stream_first_event_consumed',wait_until(lambda:receipt(first)),eventId=first)
    result['stage']='native_consumer_failure_recovery'
    key,second=create('second');check('subsequent_event_consumed',wait_until(lambda:receipt(second)))
    fault('summer_system_parameter_event_receipt')
    try:
        key,failed=create('receipt-fault')
        check('consumer_failure_is_pending',wait_until(lambda:redis('XPENDING',STREAM,GROUP)[0]>0))
    finally:fault('summer_system_parameter_event_receipt',True)
    check('consumer_pending_recovers_after_database_restore',wait_until(lambda:receipt(failed)),eventId=failed)
    env.stop(PORT);env.start(PORT,jar=candidate)
    check('consumer_pending_recovers_after_jvm_restart',wait_until(lambda:receipt(failed)),eventId=failed)
    check('pending_queue_drained_after_recovery',redis('XPENDING',STREAM,GROUP)[0]==0)
    pending=redis('XREADGROUP','GROUP',GROUP,'t08-20081','COUNT',100,'STREAMS',STREAM,'0')
    entries=pending[0][1] if pending else []
    pending_bodies=[dict(zip(entry[1][::2],entry[1][1::2])) for entry in entries]
    result['pendingEventReadableAfterRecovery']=any(x.get('eventId')==failed for x in pending_bodies)
    failed_record_id=stream_id(failed)
    stored_values=redis('XRANGE',STREAM,failed_record_id,failed_record_id)[0][1]
    stored_event=dict(zip(stored_values[::2],stored_values[1::2]))
    check('recovery_event_metadata_valid',stored_event.get('version','').isdigit() and set(stored_event)=={'eventId','tenantId','parameterKey','eventType','version','requestId'})
    result['recoveryPendingCount']=redis('XPENDING',STREAM,GROUP)[0]
    result['stage']='native_orphan_claim_and_joining_node'
    fault('summer_system_parameter_event_receipt')
    try:
        _,orphan=create('orphan-consumer')
        orphan_record=stream_id(orphan)
        check('orphan_fixture_is_real_native_pending',wait_until(lambda:bool(pending_record(orphan_record))))
        env.stop(PORT)
    finally:fault('summer_system_parameter_event_receipt',True)
    redis('XCLAIM',STREAM,GROUP,'retired-process',0,orphan_record,'IDLE',31000)
    orphan_state=pending_record(orphan_record)
    check('orphan_fixture_has_retired_owner_and_expired_idle',bool(orphan_state) and orphan_state[0][1]=='retired-process' and orphan_state[0][2]>=30000)
    env.start(PORT,jar=candidate)
    check('native_listener_claims_retired_consumer_pending',wait_until(lambda:receipt(orphan)),eventId=orphan)
    check('claimed_orphan_is_acknowledged',not pending_record(orphan_record))
    fault('summer_system_parameter_event_receipt')
    try:
        _,joining=create('joining-node')
        joining_record=stream_id(joining)
        check('joining_fixture_is_real_native_pending',wait_until(lambda:bool(pending_record(joining_record))))
        env.stop(PORT)
    finally:fault('summer_system_parameter_event_receipt',True)
    env.start(20082,jar=candidate)
    check('joining_node_consumes_unprocessed_stream_backlog',wait_until(lambda:receipt(joining)),eventId=joining)
    env.stop(20082);env.start(PORT,jar=candidate)
    if not RECOVERY_ONLY:
        result['stage']='atomic_rollback'
        rollback_key='event.'+env.run+'.rollback'
        stream_before=redis('XLEN',STREAM)
        fault('summer_system_parameter_outbox')
        try:request('create_outbox_database_failure',admin,PARAMS,'POST',{'key':rollback_key,'type':'STRING','value':canary,'public':False},500)
        finally:fault('summer_system_parameter_outbox',True)
        check('failed_outbox_rolls_back_parameter',env.sql("SELECT COUNT(*) FROM summer_system_parameter WHERE parameter_key='"+rollback_key+"'",env.schema)=='0')
        check('rolled_back_write_has_no_outbox',event_for(rollback_key)=='')
        check('rolled_back_write_has_no_stream_event',redis('XLEN',STREAM)==stream_before)
        result['stage']='metadata_permissions_and_duplicate'
        events,_=request('admin_event_details',admin,EVENTS+'?'+urlencode({'key':'event.'+env.run+'.first'}))
        check('event_body_metadata_only',all(set(e['event'])=={'eventId','tenantId','parameterKey','eventType','version','requestId'} for e in events['items']))
        check('event_details_exclude_sensitive_canary',canary not in json.dumps(events))
        request('reader_event_details',reader,EVENTS)
        request('reader_cannot_retry',reader,EVENTS+'/'+second+'/retry','POST',expected=403)
        request('missing_event_retry',admin,EVENTS+'/'+str(uuid.uuid4())+'/retry','POST',expected=404)
        before=redis('XLEN',STREAM)
        request('admin_duplicate_retry',admin,EVENTS+'/'+second+'/retry','POST')
        check('retry_republishes_same_event',wait_until(lambda:redis('XLEN',STREAM)>before))
        check('duplicate_has_single_receipt',receipt(second))
        request('reader_cannot_access_other_tenant',reader,'/api/v1/enterprises/ent-other/system-parameter-events',expected=403)
        result['stage']='native_publisher_retry_dead_letter'
        redis('ACL','SETUSER','default','-xadd')
        try:
            dead_key='event.'+env.run+'.publisher-fault'
            request('write_commits_during_redis_publish_fault',admin,PARAMS,'POST',{'key':dead_key,'type':'STRING','value':canary,'public':False},201)
            dead=event_for(dead_key)
            check('publisher_fault_keeps_committed_outbox',bool(dead))
            check('publisher_fault_reaches_three_attempt_dead_letter',wait_until(lambda:env.sql("SELECT CONCAT(status,':',attempts) FROM summer_system_parameter_outbox WHERE event_id='"+dead+"'",env.schema)=='DEAD_LETTER:3',40))
            detail,_=request('dead_letter_observable',admin,EVENTS+'?'+urlencode({'key':dead_key}))
            check('dead_letter_has_safe_error_and_attempts',len(detail['items'])==1 and detail['items'][0]['lastError']=='redis_delivery_failed' and detail['items'][0]['attempts']==3)
            check('dead_letter_excludes_sensitive_canary',canary not in json.dumps(detail))
        finally:redis('ACL','SETUSER','default','+xadd')
        result['stage']='real_chrome'
        child={**os.environ,'SUMMER_FOUNDATION_EVIDENCE_DIR':str(OUT),'SUMMER_E2E_USERNAME':env.username,'SUMMER_E2E_PASSWORD':env.password,
               'SUMMER_E2E_READER':env.denied_username,'SUMMER_E2E_READER_PASSWORD':env.denied_password,'SUMMER_E2E_EVENT_KEY':dead_key,'SUMMER_E2E_EVENT_ID':dead}
        node='/opt/homebrew/opt/node@22/bin/node'
        ui=subprocess.Popen([node,str(ROOT/'scripts/acceptance/foundation/ui-server.mjs')],cwd=ROOT/'summer-ui',env=child,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
        assert ui.stdout.readline().strip()=='UI_READY'
        browser=subprocess.Popen([node,str(Path(__file__).parent/'t08-browser.mjs')],cwd=ROOT/'summer-ui',env=child,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
        for line in browser.stdout:
            marker=line.strip()
            if marker=='ADMIN_RETRY_READY':
                check('browser_retry_republishes_dead_letter',wait_until(lambda:published(dead)))
                check('browser_retry_consumed',wait_until(lambda:receipt(dead)))
            elif marker=='FAULT_READY':fault('summer_system_parameter_outbox')
            elif marker=='RECOVER_READY':fault('summer_system_parameter_outbox',True)
            elif marker=='READ_DENY_READY':
                binding,h=request('reader_binding_before_deny',admin,BASE+'/users/'+env.denied_username+'/roles')
                request('reader_explicit_read_deny',admin,BASE+'/users/'+env.denied_username+'/roles','PUT',{'roleIds':[reader_role],'deniedCapabilityCodes':['system.parameter.read']},headers={**mh(),'If-Match':h['ETag']})
            else:
                if marker.endswith((' PASS',' FAIL')):print(marker,flush=True)
                continue
            browser.stdin.write('GO\n');browser.stdin.flush()
        result['browserExit']=browser.wait(timeout=20)
        result['browser']=json.loads((OUT/'browser-t08.json').read_text())
    check('no_sensitive_values_in_runtime_logs',not env.sensitive_log_detected)
    result['stage']='complete'
except Exception as error:
    result['fatal_class']=type(error).__name__;print('fatal',type(error).__name__,flush=True)
finally:
    for process in [browser,ui]:
        if process and process.poll() is None:
            process.terminate()
            try:process.wait(timeout=15)
            except subprocess.TimeoutExpired:process.kill();process.wait()
    if env:
        for table in list(renamed):fault(table,True)
        env.close()
    checks=result['checks']+result.get('browser',{}).get('checks',[])
    result['counts']={'tests':len(checks),'failures':sum(not c['passed'] for c in checks),'errors':int('fatal_class' in result)+int('failureType' in result.get('browser',{})),'skipped':0}
    result['passed']=result['stage']=='complete' and result['counts']['failures']==0 and result['counts']['errors']==0 and (RECOVERY_ONLY or (result.get('browserExit')==0 and result.get('browser',{}).get('complete') is True))
    (OUT/'result.json').write_text(json.dumps(result,indent=2))
    print('counts',json.dumps(result['counts']),flush=True)
sys.exit(1 if result['counts']['failures'] or result['counts']['errors'] else 0)
