"""T10 actual MySQL, two JVMs and isolated Redis. No business SQL mutations."""
import base64
import concurrent.futures
import hashlib
import json
import os
import subprocess
import sys
import time
import uuid
from pathlib import Path

from environment import Client as BaseClient, Environment, OUT, ROOT, immutable_candidate
sys.path.insert(0, str(ROOT / 'scripts/acceptance/identity'))
from grants import bootstrap, create_role, bind, unwrap

RESULT = {'issue': 118, 'stage': 'setup', 'checks': [], 'businessSqlWrites': False, 'networkMocking': False}
BASE = '/api/v1/enterprises/ent-acme'
PARAMS = BASE + '/system-parameters'
env = None

class Client(BaseClient):
    def request(self, port, *args, **kwargs):
        return super().request(19081 if port == 21081 else port, *args, **kwargs)

def record(name, passed, **facts):
    RESULT['checks'].append({'name': name, 'passed': bool(passed), **facts})
    print(name, bool(passed), flush=True)

def call(client, path, method='GET', body=None, expected=200, port=19081, code=None):
    rid = 't10-' + uuid.uuid4().hex
    status, data, headers = client.request(port, path, method, body, headers={'X-Request-Id': rid, 'Idempotency-Key': uuid.uuid4().hex})
    assert status == expected, 'http_status_' + str(status) + '_expected_' + str(expected)
    assert isinstance(data, dict) and data.get('requestId') == headers.get('X-Request-Id') == rid, 'correlation_contract'
    if expected >= 400:
        assert data.get('success') is False and data.get('error', {}).get('code', '').startswith('SUMMER_'), 'framework_error_contract'
        if code: assert data['error']['code'] == code, 'specific_error_code'
        return data, headers
    return unwrap(data), headers

def logged():
    return Client().login(19081, env.username, env.password)

def redis(*args):
    return subprocess.check_output(['docker', 'exec', env.container, 'redis-cli', '-n', '15', '--raw', *args], text=True).strip()

def cache_version():
    key = 'summer:cache:version:' + base64.urlsafe_b64encode(b'ent-acme:system-parameter').decode().rstrip('=')
    return int(redis('GET', key) or 0)

def await_delivery(client, key):
    deadline = time.monotonic() + 20
    while time.monotonic() < deadline:
        page, _ = call(client, BASE + '/system-parameter-events?key=' + key)
        if page['items'] and all(row['status'] == 'PUBLISHED' for row in page['items']): return page['items']
        time.sleep(.3)
    raise RuntimeError('event_delivery_not_published')

def command(revision, version):
    return {'revision': revision, 'expectedVersion': version, 'idempotencyKey': uuid.uuid4().hex}

try:
    candidate = immutable_candidate()
    RESULT['candidateSha256'] = hashlib.sha256(candidate.read_bytes()).hexdigest()
    env = Environment()
    env.extra.update({'summer.sample.parameter-events.scheduling-enabled': True, 'summer.sample.parameter-events.poll-interval-ms': 300})
    env.setup(); env.start(19081, jar=candidate)
    admin = logged(); initial_role = bootstrap(admin, env.username); admin = logged()
    catalog, _ = call(admin, BASE + '/capabilities/catalog')
    known = {row['code']: row['capabilityId'] for row in catalog['capabilities']}
    codes = ['system.parameter.' + action for action in ['read', 'create', 'update', 'publish', 'disable', 'rollback', 'write']] + ['menu.systemParameters.read']
    for code in codes:
        if code not in known:
            value, _ = call(admin, BASE + '/capabilities', 'POST', {'code': code, 'resource': code.rsplit('.', 1)[0], 'action': code.rsplit('.', 1)[1], 'riskLevel': 'HIGH', 'scope': 'PLATFORM' if code.startswith('system.') else 'ENTERPRISE'}, 201)
            known[code] = value['capabilityId']
    role = create_role(admin, 'ent-acme', [known[code] for code in codes], 'T10 actual release administration')
    bind(admin, 'ent-acme', env.username, [initial_role, role]); admin = logged()
    env.start(19082, jar=candidate)
    call(admin, '/api/users', 'POST', {'username': env.denied_username, 'password': env.denied_password, 'role': 'USER'})
    call(admin, BASE + '/members', 'POST', {'subject': env.denied_username, 'displayName': 'T10 actual reader', 'email': 'release-reader@example.test'}, 201)
    reader_role = create_role(admin, 'ent-acme', [known['system.parameter.read'], known['menu.systemParameters.read']], 'T10 read only')
    bind(admin, 'ent-acme', env.denied_username, [reader_role])
    reader = Client().login(19081, env.denied_username, env.denied_password)
    RESULT['stage'] = 'actual_release_protocol'
    key = 'acceptance.release.' + env.run; target = PARAMS + '/' + key
    created, _ = call(admin, PARAMS, 'POST', {'key': key, 'type': 'INTEGER', 'value': 10, 'required': True, 'min': 0, 'max': 100, 'public': True}, 201)
    initial_version = created['version']
    baseline, _ = call(admin, target + '/versions')
    record('initial_published_history_exists', any(row['revision'] == 0 and row['state'] == 'PUBLISHED' and row['value'] == 10 for row in baseline['items']))
    for port in [19081, 19082]:
        value, _ = call(admin, target, port=port); assert value['value'] == 10
    await_delivery(admin, key); cached_before = cache_version()
    record('both_nodes_prime_actual_cache', bool(redis('--scan', '--pattern', 'summer:cache:entry:*')))
    draft, _ = call(admin, target + '/draft', 'POST', {'value': 20, 'baseVersion': initial_version, 'idempotencyKey': uuid.uuid4().hex}, 201)
    record('draft_does_not_change_active_version', draft['state'] == 'DRAFT' and draft['version'] == initial_version)
    values = [call(admin, target, port=p)[0]['value'] for p in [19081, 19082]]
    public, _ = call(admin, '/public/enterprises/ent-acme/system-parameters/' + key)
    record('draft_does_not_affect_runtime_or_public_read', values == [10, 10] and public['value'] == 10 and cache_version() == cached_before)
    published, _ = call(admin, target + '/publish', 'POST', command(draft['revision'], initial_version))
    events = await_delivery(admin, key)
    values = [call(admin, target, port=p)[0]['value'] for p in [19081, 19082]]
    record('publish_changes_both_nodes_and_invalidates_cache', published['state'] == 'PUBLISHED' and values == [20, 20] and cache_version() > cached_before)
    version = published['version']; before = cache_version()
    rolled, _ = call(admin, target + '/rollback', 'POST', command(0, version), port=19082)
    await_delivery(admin, key)
    values = [call(admin, target, port=p)[0]['value'] for p in [19081, 19082]]
    record('old_published_revision_rolls_back_across_nodes', rolled['state'] == 'ROLLED_BACK' and values == [10, 10] and rolled['version'] > version and cache_version() > before)
    version = rolled['version']; before = cache_version()
    inactive, _ = call(admin, target + '/deactivate', 'POST', command(None, version))
    await_delivery(admin, key)
    for port in [19081, 19082]: call(admin, '/public/enterprises/ent-acme/system-parameters/' + key, expected=404, port=port, code='SUMMER_NOT_FOUND')
    record('deactivation_invalidates_cache_and_public_access', inactive['state'] == 'INACTIVE' and cache_version() > before)
    restored, _ = call(admin, target + '/rollback', 'POST', command(0, inactive['version']))
    await_delivery(admin, key)
    record('rollback_restores_active_public_value', call(admin, '/public/enterprises/ent-acme/system-parameters/' + key)[0]['value'] == 10)
    next_draft, _ = call(admin, target + '/draft', 'POST', {'value': 30, 'baseVersion': restored['version'], 'idempotencyKey': uuid.uuid4().hex}, 201)
    payloads = [command(next_draft['revision'], restored['version']) for _ in range(2)]
    def publish_race(item):
        port, payload = item
        client = logged()
        rid = 't10-race-' + uuid.uuid4().hex
        status, body, headers = client.request(port, target + '/publish', 'POST', payload, headers={'X-Request-Id': rid})
        return {'status': status, 'correlation': isinstance(body, dict) and body.get('requestId') == headers.get('X-Request-Id') == rid, 'code': body.get('error', {}).get('code') if isinstance(body.get('error'), dict) else None}
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        race = list(pool.map(publish_race, zip([19081, 19082], payloads)))
    record('same_version_publish_has_one_winner', sorted(row['status'] for row in race) == [200, 409] and all(row['correlation'] for row in race), responses=race)
    winner, _ = call(admin, target)
    record('concurrent_publish_increments_version_once', winner['value'] == 30 and winner['version'] == restored['version'] + 1)
    call(admin, target + '/draft', 'POST', {'value': 'invalid', 'baseVersion': winner['version'], 'idempotencyKey': uuid.uuid4().hex}, 400, code='SUMMER_PARAMETER_INVALID')
    record('invalid_draft_has_unified_error_and_request_id', True)
    call(reader, target + '/publish', 'POST', command(next_draft['revision'], winner['version']), 403)
    call(Client(), target + '/versions', expected=401)
    record('read_only_and_anonymous_release_access_denied', True)
    call(admin, target + '/publish', 'POST', command(next_draft['revision'], restored['version']), 409, code='SUMMER_PARAMETER_VERSION_CONFLICT')
    record('stale_publish_rejected_without_mutation', call(admin, target)[0]['version'] == winner['version'])
    history, _ = call(admin, target + '/versions'); events = await_delivery(admin, key)
    record('history_exposes_draft_publish_rollback_and_inactive', {'DRAFT', 'PUBLISHED', 'ROLLED_BACK', 'INACTIVE'} <= {row['state'] for row in history['items']} and history['currentVersion'] == winner['version'])
    event_types = {row['eventType'] for row in events}
    record('release_events_are_published_without_parameter_values', {'SYSTEM_PARAMETER_PUBLISHED', 'SYSTEM_PARAMETER_ROLLED_BACK', 'SYSTEM_PARAMETER_DISABLED'} <= event_types and all('value' not in row['event'] for row in events))
    audits, _ = call(admin, '/api/audit-events?resource=system-parameter:' + key + '&size=100')
    actions = {row['action'] for row in audits['items']}
    record('publish_disable_rollback_and_draft_audited', {'SYSTEM_PARAMETER_DRAFT_SAVED', 'SYSTEM_PARAMETER_PUBLISHED', 'SYSTEM_PARAMETER_ROLLED_BACK', 'SYSTEM_PARAMETER_DISABLED'} <= actions)
    record('production_migrations_and_durable_history_exist', int(env.sql('SELECT COUNT(*) FROM flyway_schema_history WHERE success=1', env.schema)) > 0 and int(env.sql('SELECT COUNT(*) FROM summer_system_parameter_version', env.schema)) == len(history['items']))
    env.stop(19082); env.start(19082, jar=candidate)
    persistent, _ = call(admin, target, port=19082); persistent_history, _ = call(admin, target + '/versions', port=19082)
    record('jvm_restart_retains_effective_version_and_history', persistent['id'] == winner['id'] and persistent['value'] == 30 and persistent['version'] == winner['version'] and len(persistent_history['items']) == len(history['items']))
    RESULT['key'] = key; RESULT['stage'] = 'complete'
except Exception as error:
    RESULT.update({'stage': 'failed', 'failureType': type(error).__name__})
    if isinstance(error, AssertionError): RESULT['safeAssertion'] = str(error)[:160]
finally:
    if env:
        RESULT['sensitiveLogDetected'] = env.sensitive_log_detected
        try: env.close(); RESULT['cleanup'] = 'VERIFIED'
        except Exception as error: RESULT.update({'cleanup': 'FAILED', 'cleanupFailureType': type(error).__name__})
    RESULT['counts'] = {'tests': len(RESULT['checks']), 'failures': sum(not row['passed'] for row in RESULT['checks']), 'errors': int(RESULT['stage'] != 'complete'), 'skipped': 0}
    RESULT['passed'] = RESULT['stage'] == 'complete' and not RESULT['counts']['failures'] and RESULT.get('cleanup') == 'VERIFIED' and not RESULT.get('sensitiveLogDetected')
    (OUT / 'result.json').write_text(json.dumps(RESULT, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({key: RESULT[key] for key in ['stage', 'counts', 'cleanup', 'passed'] if key in RESULT}), flush=True)
sys.exit(0 if RESULT['passed'] else 1)
