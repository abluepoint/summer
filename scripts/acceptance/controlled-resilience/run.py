"""T32: real MySQL/Redis/JVM reads, actual Redis latency, no fabricated responses."""
import concurrent.futures
import hashlib
import json
import os
import runpy
import shutil
import subprocess
import sys
import time
import uuid
from pathlib import Path

SCRIPT = Path(__file__).resolve().parent
ROOT = SCRIPT.parents[2]
sys.path.insert(0, str(ROOT/'scripts/acceptance/task-configuration'))
from environment import Client, Environment, OUT, immutable_candidate

B = '/api/v1/enterprises/ent-acme'
P = B+'/system-parameters'
D = B+'/dictionaries'
DIAG = '/internal/v1/diagnostics/resilience'
PHASE = os.environ.get('SUMMER_T32_PHASE', 'full')
if PHASE not in {'full', 'circuit'}: raise ValueError('unsupported_phase')
result = {'phase': PHASE, 'issue': 140, 'checks': [], 'networkMocking': False, 'businessSqlWrites': False}
env = None
paused = trigger = False

class LocalClient(Client):
    def request(self, port, *args, **kwargs):
        return super().request(26081 if port == 21081 else port, *args, **kwargs)

def save():
    result['counts'] = {'tests': len(result['checks']),
                        'failures': sum(not x['passed'] for x in result['checks']),
                        'errors': int('errorType' in result), 'skipped': 0}
    tmp = OUT/'result.tmp'
    tmp.write_text(json.dumps(result, indent=2)+'\n'); tmp.replace(OUT/'result.json')

def check(name, passed, **facts):
    result['checks'].append({'name': name, 'passed': bool(passed), **facts}); save()
    print(name, 'PASS' if passed else 'FAIL', flush=True)

def mh(version=None):
    return {'Idempotency-Key': uuid.uuid4().hex,
            **({'If-Match': '"'+str(version)+'"'} if version is not None else {})}

def call(name, client, path, method='GET', body=None, expected=200, port=26081, headers=None):
    rid = 't32-'+uuid.uuid4().hex
    status, data, response = client.request(port, path, method, body,
                                           headers={'X-Request-Id': rid, **(headers or {})})
    code = (data.get('error') or {}).get('code') if isinstance(data, dict) else None
    valid = status == expected and isinstance(data, dict) and data.get('success') == (status < 400)
    valid = valid and data.get('requestId') == response.get('X-Request-Id') == rid
    if status >= 400: valid = valid and str(code).startswith('SUMMER_') and data.get('data') is None
    check(name, valid, status=status, expected=expected, code=code, requestId=rid)
    if status != expected: raise AssertionError('unexpected_status_'+str(status))
    return data.get('data'), response

def login(user, password, port=26081):
    c = LocalClient().login(port, user, password); env.additional_secrets.append(c.token)
    c.cookies.clear()  # Bearer authorization must not depend on a Redis session during latency faults.
    return c

def redis_command(*args):
    subprocess.run(['docker', 'exec', env.container, 'redis-cli', *map(str, args)],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=25)

def pause(milliseconds):
    global paused
    redis_command('CLIENT', 'PAUSE', milliseconds, 'ALL'); paused = True

def recover():
    global paused
    if paused: redis_command('CLIENT', 'UNPAUSE'); paused = False

def ddl(statement):
    assert env.created_schema and statement.startswith(('CREATE TRIGGER ', 'DROP TRIGGER '))
    args = ['kubectl', '--context', 'docker-desktop', 'exec', '-i', '-n', 'local-base',
            'mysql-0', '--', 'sh', '-c',
            'IFS= read -r MYSQL_PWD; export MYSQL_PWD; exec mysql -uroot --batch --skip-column-names']
    p = subprocess.run(args, input=env.mysql+'\nUSE `'+env.schema+'`;\n'+statement+';\n',
                       text=True, capture_output=True)
    if p.returncode: raise RuntimeError('owned_schema_fault_ddl_failed')

def operations(port=26081):
    data, _ = call('diagnostics_'+str(port), admin, DIAG, port=port)
    return {x['name']: x for x in data['operations']}

def bind_reader(denied):
    current, _ = call('reader_assignment_version', admin, B+'/users/'+env.denied_username+'/roles')
    call('reader_assignment_changed', admin, B+'/users/'+env.denied_username+'/roles', 'PUT',
         {'roleIds': [operator], 'deniedCapabilityCodes': denied}, headers=mh(current['version']))

try:
    candidate = immutable_candidate(); result['candidateSha256'] = hashlib.sha256(candidate.read_bytes()).hexdigest()
    archive = OUT/'executed-scripts'; archive.mkdir()
    for p in SCRIPT.iterdir():
        if p.is_file(): shutil.copy2(p, archive/p.name)
    relevant = [p for p in (ROOT/'summer-sample/src/main/java/com/abluepoint/summer/sample/resilience').glob('*.java')]
    relevant += [ROOT/'summer-ui/src/main.tsx', ROOT/'summer-ui/src/menu/api.ts']
    before = {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in relevant}
    (OUT/'inputs-before.json').write_text(json.dumps(before, indent=2)+'\n')
    env = Environment(); env.setup()
    env.extra = {'summer.sample.resilience.timeout': '400ms',
                 'summer.sample.resilience.fallback-ttl': '30s',
                 'summer.sample.resilience.max-attempts': 1,
                 'summer.sample.resilience.circuit-window': 4,
                 'summer.sample.resilience.circuit-open-wait': '1m' if PHASE == 'circuit' else '2s',
                 'summer.sample.resilience.bulkhead': 1}
    env.start(26081, jar=candidate); admin = login(env.username, env.password)
    grants = runpy.run_path(str(ROOT/'scripts/acceptance/authorized-configuration/grants.py'))
    boot = grants['bootstrap'](admin, env.username); admin = login(env.username, env.password)
    catalog, _ = call('real_capability_catalog', admin, B+'/capabilities/catalog')
    ids = {c['code']: c['capabilityId'] for c in catalog['capabilities']}
    wanted = [prefix+'.'+action for prefix in ['system.parameter', 'platform.dictionary']
              for action in ['read', 'create', 'update', 'publish', 'disable', 'rollback']]
    wanted += ['menu.diagnostics.resilience.read']
    for code in wanted:
        if code not in ids:
            cap, _ = call('register_'+code, admin, B+'/capabilities', 'POST',
                          {'code': code, 'resource': code.rsplit('.', 1)[0],
                           'action': code.rsplit('.', 1)[1], 'riskLevel': 'HIGH',
                           'scope': 'ENTERPRISE' if code.startswith('menu.') else 'PLATFORM'},
                          201, headers=mh())
            ids[code] = cap['capabilityId']
    operator = grants['create_role'](admin, 'ent-acme', [ids[c] for c in wanted], 'T32 controlled reader')
    current, _ = call('admin_assignment_version', admin, B+'/users/'+env.username+'/roles')
    call('admin_configuration_grants', admin, B+'/users/'+env.username+'/roles', 'PUT',
         {'roleIds': [boot, operator], 'deniedCapabilityCodes': []}, headers=mh(current['version']))
    admin = login(env.username, env.password)
    call('reader_identity', admin, '/api/users', 'POST',
         {'username': env.denied_username, 'password': env.denied_password, 'role': 'USER'})
    call('reader_membership', admin, B+'/members', 'POST',
         {'subject': env.denied_username, 'displayName': 'T32 reader', 'email': 't32@example.test'}, 201, headers=mh())
    grants['bind'](admin, 'ent-acme', env.denied_username, [operator])
    reader = login(env.denied_username, env.denied_password)
    for resource in ['system-parameters', 'dictionaries']:
        call('tenant_scope_'+resource, admin, B+'/data-scopes/'+resource, 'PUT', {'conditions': {'field': 'TENANT'}})
    key = 'resilience.'+env.run
    for suffix in ['', '.cold']:
        call('parameter_fixture'+suffix, admin, P, 'POST',
             {'key': key+suffix, 'type': 'STRING', 'value': 'T32 verified value'+suffix, 'public': True}, 201)
    code = 'resilience-'+env.run
    call('dictionary_fixture', admin, D, 'POST', {'code': code, 'name': 'T32 dictionary', 'publicReadable': True})
    call('dictionary_item_fixture', admin, D+'/'+code+'/items', 'POST',
         {'value': 'alpha', 'label': 'T32 published', 'sortOrder': 0, 'enabled': True})
    call('dictionary_published', admin, D+'/'+code+'/publish', 'POST',
         {'expectedVersion': 0, 'idempotencyKey': uuid.uuid4().hex})
    path = P+'/'+key
    dictionary = '/public/enterprises/ent-acme/dictionaries/'+code
    result['stage'] = 'timeout_and_circuit'
    warm, _ = call('parameter_warmed', reader, path)
    published, _ = call('dictionary_warmed', reader, dictionary)
    pause(2500)
    started = time.monotonic(); fallback, _ = call('actual_redis_latency_verified_parameter_fallback', reader, path)
    check('fallback_is_current_value_within_timeout', fallback == warm and time.monotonic()-started < 2,
          measuredSeconds=round(time.monotonic()-started, 3))
    recover(); time.sleep(.4)
    call('refresh_warm_value_before_sustained_failure', reader, path)
    pause(60000)
    for i in range(4):
        call('sustained_failure_read_'+str(i), reader, path)
        time.sleep(2.1)  # Let the 2s Redis command finish; do not confuse bulkhead rejection with timeout.
    metric = operations()['system-parameters.read']
    check('circuit_opens_and_reports_actual_timeout_and_fallback',
          metric['circuitBreaker'] in ({'OPEN'} if PHASE == 'circuit' else {'OPEN', 'HALF_OPEN'}) and metric['timedOut'] >= 2 and metric['fallbacks'] >= 2,
          circuit=metric['circuitBreaker'], timedOut=metric['timedOut'], fallbacks=metric['fallbacks'])
    if PHASE == 'circuit':
        check('actual_retry_counter_is_queryable', isinstance(metric.get('retryAttempts'), int) and metric['retryAttempts'] >= 0,
              retryAttempts=metric.get('retryAttempts'))
        call('parameter_version_changed_during_real_latency', admin, path, 'PATCH',
             {'version': warm['version'], 'value': 'T32 newer authoritative version'})
        call('old_parameter_fallback_rejected_after_version_change', reader, path, expected=503)
        call('dictionary_draft_changed_during_real_latency', admin, D+'/'+code+'/items', 'POST',
             {'value': 'beta', 'label': 'T32 newer published', 'sortOrder': 1, 'enabled': True})
        call('dictionary_pointer_changed_during_real_latency', admin, D+'/'+code+'/publish', 'POST',
             {'expectedVersion': published['version'], 'idempotencyKey': uuid.uuid4().hex})
        call('old_dictionary_fallback_rejected_after_publication_change', reader, dictionary, expected=503)
        result['stage'] = 'complete'
    else:
        time.sleep(30.2)
        call('expired_fallback_fails_closed_during_real_latency', reader, path, expected=503)
        recover(); time.sleep(2.2)
        for i in range(10):
            current, _ = call('half_open_recovery_'+str(i), reader, path)
            check('recovery_returns_current_value_'+str(i), current == warm)
        metric = operations()['system-parameters.read']
        check('circuit_returns_closed_after_recovery', metric['circuitBreaker'] == 'CLOSED')
        result['stage'] = 'concurrent_isolation'
        # Refresh the independently guarded dictionary immediately before latency injection.
        call('dictionary_rewarmed', reader, dictionary)
        other = LocalClient(); other.token = reader.token
        before_rejected = metric['rejected']; pause(5000)
        with concurrent.futures.ThreadPoolExecutor(max_workers=1) as pool:
            occupied = pool.submit(other.request, 26081, path)
            time.sleep(.12)
            started = time.monotonic()
            call('cold_key_concurrent_bulkhead_rejected', reader, path+'.cold', expected=503)
            check('concurrent_rejection_is_bounded', time.monotonic()-started < 1)
            d, _ = call('dictionary_operation_is_independent', reader, dictionary)
            check('dictionary_verified_fallback_not_parameter_value', d == published)
            status, value, _ = occupied.result(timeout=8)
            check('occupied_parameter_read_is_verified', status == 200 and value.get('data') == warm)
        recover(); time.sleep(.4)
        metric = operations()['system-parameters.read']
        check('bulkhead_rejection_is_counted', metric['rejected'] > before_rejected)
        result['stage'] = 'rate_limit_and_permission'
        env.extra['summer.sample.resilience.rate-limit'] = 1
        env.extra['summer.sample.resilience.rate-limit-period'] = '4s'
        env.start(26082, jar=candidate)
        first, _ = call('rate_node_first_read', reader, path, port=26082)
        second, _ = call('rate_node_verified_fallback', reader, path, port=26082)
        check('rate_rejection_uses_only_same_verified_value', first == second)
        call('rate_rejected_other_key_has_no_fallback', reader, path+'.cold', expected=503, port=26082)
        metric = operations(26082)['system-parameters.read']
        check('rate_limit_rejection_and_fallback_observable', metric['rejected'] >= 2 and metric['fallbacks'] >= 1)
        bind_reader(['system.parameter.read'])
        call('old_bearer_denied_instead_of_fallback', reader, path, expected=403, port=26082)
        call('unverified_tenant_cannot_fallback', reader,
             '/api/v1/enterprises/not-a-member/system-parameters/'+key, expected=403, port=26082)
        bind_reader([])
        result['stage'] = 'write_fault'
        before_write, _ = call('before_write_fault', admin, path)
        ddl("CREATE TRIGGER t32_audit_fault BEFORE INSERT ON summer_audit_event FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='controlled acceptance audit fault'")
        trigger = True
        call('write_audit_fault_has_no_fake_success', admin, path, 'PATCH',
             {'version': before_write['version'], 'value': 'must not persist'}, expected=500)
        ddl('DROP TRIGGER t32_audit_fault'); trigger = False
        after_write, _ = call('after_write_fault', admin, path)
        check('write_fault_preserves_value_and_version', before_write == after_write)
        result['stage'] = 'diagnostics_permission'
        current, _ = call('admin_binding_before_diagnostics_deny', admin, B+'/users/'+env.username+'/roles')
        call('persist_diagnostics_deny', admin, B+'/users/'+env.username+'/roles', 'PUT',
             {'roleIds': [boot, operator], 'deniedCapabilityCodes': ['platform.capabilities.read']},
             headers=mh(current['version']))
        call('old_admin_jwt_diagnostics_denied', admin, DIAG, expected=403)
        current, _ = call('admin_binding_before_restore', admin, B+'/users/'+env.username+'/roles')
        call('restore_diagnostics_grant', admin, B+'/users/'+env.username+'/roles', 'PUT',
             {'roleIds': [boot, operator], 'deniedCapabilityCodes': []}, headers=mh(current['version']))
        operations(); result['stage'] = 'complete'
except Exception as error:
    result['errorType'] = type(error).__name__
    if str(error).startswith(('unexpected_status_', 'owned_schema_')): result['errorCode'] = str(error)
finally:
    if env:
        try:
            try:
                # The circuit-only phase ends during an intentional pause; destroy its owned
                # Redis instead of waiting for a recovery that is not part of this phase.
                if PHASE != 'circuit': recover()
            finally:
                try:
                    if trigger: ddl('DROP TRIGGER t32_audit_fault')
                finally:
                    env.close(); result['cleanup'] = 'VERIFIED'
        except Exception as error: result['cleanupError'] = type(error).__name__
        result['sensitiveLogDetected'] = env.sensitive_log_detected
    if 'before' in locals():
        result['changedInputs'] = [p for p, sha in before.items()
                                   if hashlib.sha256((ROOT/p).read_bytes()).hexdigest() != sha]
    result['httpObservations'] = Client.observations
    save(); result['passed'] = not result['counts']['failures'] and not result['counts']['errors'] and result.get('cleanup') == 'VERIFIED' and not result.get('sensitiveLogDetected') and not result.get('changedInputs')
    save(); print(json.dumps({k: result.get(k) for k in ['counts', 'stage', 'passed', 'cleanup', 'errorType']}), flush=True)
raise SystemExit(0 if result.get('passed') else 1)
