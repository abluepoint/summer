"""T11 real MySQL, two JVMs and isolated Redis; no business SQL mutations."""
import base64
import concurrent.futures
import hashlib
import json
import subprocess
import sys
import uuid
from environment import Client as BaseClient, Environment, OUT, ROOT, immutable_candidate

sys.path.insert(0, str(ROOT / 'scripts/acceptance/identity'))
from grants import bootstrap, create_role, bind, unwrap

RESULT = {'issue': 119, 'stage': 'setup', 'checks': [], 'networkMocking': False, 'businessSqlWrites': False}
BASE = '/api/v1/enterprises/ent-acme'
DICTIONARIES = BASE + '/dictionaries'
env = None

class Client(BaseClient):
    def request(self, port, *args, **kwargs):
        return super().request(24081 if port == 21081 else port, *args, **kwargs)

def check(name, passed, **facts):
    RESULT['checks'].append({'name': name, 'passed': bool(passed), **facts})
    print(name, bool(passed), flush=True)

def call(client, path, method='GET', body=None, expected=200, port=24081):
    rid = 't11-' + uuid.uuid4().hex
    status, data, headers = client.request(port, path, method, body, headers={'X-Request-Id': rid, 'Idempotency-Key': uuid.uuid4().hex})
    if status != expected:
        RESULT['unexpectedResponse'] = {'stage': RESULT['stage'], 'method': method, 'status': status, 'expected': expected,
                                        'code': (data.get('error') or {}).get('code') if isinstance(data, dict) else None}
        raise AssertionError('unexpected_http_status')
    assert isinstance(data, dict) and data.get('requestId') == headers.get('X-Request-Id') == rid, 'correlation_contract'
    if expected >= 400:
        assert data.get('success') is False and (data.get('error') or {}).get('code', '').startswith('SUMMER_'), 'failure_contract'
        return data
    return unwrap(data)

def logged():
    return Client().login(24081, env.username, env.password)

def redis(*args):
    return subprocess.check_output(['docker', 'exec', env.container, 'redis-cli', '-n', '15', '--raw', *args], text=True).strip()

def cache_version():
    key = 'summer:cache:version:' + base64.urlsafe_b64encode(b'ent-acme:dictionary').decode().rstrip('=')
    return int(redis('GET', key) or 0)

def command(expected, target=None):
    value = {'expectedVersion': expected, 'idempotencyKey': uuid.uuid4().hex}
    if target is not None:
        value['targetVersion'] = target
    return value

try:
    candidate = immutable_candidate()
    RESULT['candidateSha256'] = hashlib.sha256(candidate.read_bytes()).hexdigest()
    env = Environment(); env.setup(); env.start(24081, jar=candidate)
    admin = logged(); bootstrap_role = bootstrap(admin, env.username); admin = logged()
    known = {row['code']: row['capabilityId'] for row in call(admin, BASE + '/capabilities/catalog')['capabilities']}
    codes = ['platform.dictionary.' + action for action in ['read', 'create', 'update', 'publish', 'disable', 'rollback', 'write']] + ['menu.dictionary.form.read']
    for code in codes:
        if code not in known:
            known[code] = call(admin, BASE + '/capabilities', 'POST', {
                'code': code, 'resource': code.rsplit('.', 1)[0], 'action': code.rsplit('.', 1)[1],
                'riskLevel': 'HIGH', 'scope': 'PLATFORM' if code.startswith('platform.') else 'ENTERPRISE'}, 201)['capabilityId']
    role = create_role(admin, 'ent-acme', [known[code] for code in codes], 'T11 actual release administration')
    bind(admin, 'ent-acme', env.username, [bootstrap_role, role]); admin = logged()
    call(admin, '/api/users', 'POST', {'username': env.denied_username, 'password': env.denied_password, 'role': 'USER'})
    call(admin, BASE + '/members', 'POST', {'subject': env.denied_username, 'displayName': 'T11 reader', 'email': 'dictionary-reader@example.test'}, 201)
    read_role = create_role(admin, 'ent-acme', [known['platform.dictionary.read'], known['menu.dictionary.form.read']], 'T11 read only')
    bind(admin, 'ent-acme', env.denied_username, [read_role])
    reader = Client().login(24081, env.denied_username, env.denied_password)
    env.start(24082, jar=candidate)
    RESULT['stage'] = 'dictionary_release'
    code = 'acceptance-release-' + env.run
    path = DICTIONARIES + '/' + code
    public = '/public/enterprises/ent-acme/dictionaries/' + code
    anonymous = Client()
    call(admin, DICTIONARIES, 'POST', {'code': code, 'name': 'T11 验收字典', 'published': False, 'publicReadable': True, 'enabled': True})
    for value, label in [('alpha', '版本一甲'), ('bravo', '版本一乙')]:
        call(admin, path + '/items', 'POST', {'value': value, 'label': label, 'sortOrder': 0, 'enabled': True})
    call(anonymous, public, expected=404)
    check('unpublished_draft_is_not_public', True)
    first_command = command(0)
    first = call(admin, path + '/publish', 'POST', first_command)
    check('first_snapshot_published_version_one', first['version'] == 1 and first['status'] == 'PUBLISHED')
    for port in [24081, 24082]:
        value = call(anonymous, public, port=port)
        assert value['version'] == 1 and {row['label'] for row in value['items']} == {'版本一甲', '版本一乙'}
    check('both_nodes_read_immutable_first_snapshot', True)
    first_cache = cache_version()
    check('public_read_primes_real_redis_cache', bool(redis('--scan', '--pattern', 'summer:cache:entry:*')))
    call(admin, path + '/items/alpha', 'PATCH', {'label': '版本二甲', 'sortOrder': 0, 'enabled': True})
    call(admin, path + '/items', 'POST', {'value': 'charlie', 'label': '版本二丙', 'sortOrder': 1, 'enabled': True})
    values = [call(anonymous, public, port=p) for p in [24081, 24082]]
    check('unpublished_item_edits_do_not_change_selected_snapshot', all(v['version'] == 1 and len(v['items']) == 2 and v['items'][0]['label'] == '版本一甲' for v in values))
    replay = call(admin, path + '/publish', 'POST', first_command)
    check('publish_idempotent_retry_keeps_version', replay['version'] == 1 and len(call(admin, path + '/versions')) == 1)
    second = call(admin, path + '/publish', 'POST', command(1))
    values = [call(anonymous, public, port=p) for p in [24081, 24082]]
    check('publish_updates_both_nodes_and_invalidates_cache', second['version'] == 2 and cache_version() > first_cache and all(v['version'] == 2 and len(v['items']) == 3 and v['items'][0]['label'] == '版本二甲' for v in values))
    call(admin, path + '/publish', 'POST', command(1), 409)
    check('stale_publication_rejected_with_correlated_error', True)
    before_disable = cache_version()
    disabled = call(admin, path + '/disable', 'POST', command(2))
    for port in [24081, 24082]:
        call(anonymous, public, expected=404, port=port)
    check('disable_removes_snapshot_from_both_nodes', disabled['status'] == 'DISABLED' and cache_version() > before_disable)
    call(admin, path + '/disable', 'POST', command(2), 409)
    check('repeat_disable_invalid_transition_rejected', True)
    rollback = call(admin, path + '/rollback', 'POST', command(2, 1))
    values = [call(anonymous, public, port=p) for p in [24081, 24082]]
    check('rollback_restores_immutable_previous_options', rollback['version'] == 1 and all(v['version'] == 1 and len(v['items']) == 2 and v['items'][0]['label'] == '版本一甲' for v in values))
    call(admin, path + '/rollback', 'POST', command(1, 999), 409)
    call(admin, path + '/rollback', 'POST', command(1, 1), 409)
    check('missing_and_current_rollback_targets_rejected', True)
    call(reader, path + '/publish', 'POST', command(1), 403)
    call(reader, path + '/disable', 'POST', command(1), 403)
    call(reader, path + '/rollback', 'POST', command(1, 2), 403)
    check('reader_cannot_publish_disable_or_rollback', True)
    audit = env.sql("SELECT COUNT(DISTINCT action) FROM summer_audit_event WHERE resource='dictionary:" + code + "' AND action IN ('DICTIONARY_PUBLISHED','DICTIONARY_DISABLED','DICTIONARY_ROLLED_BACK') AND result='SUCCESS'", env.schema)
    event_count = env.sql("SELECT COUNT(*) FROM summer_dictionary_outbox WHERE dictionary_code='" + code + "'", env.schema)
    payload_leaks = env.sql("SELECT COUNT(*) FROM summer_dictionary_outbox WHERE dictionary_code='" + code + "' AND (payload_json LIKE '%版本%' OR payload_json LIKE '%items%')", env.schema)
    check('all_lifecycle_actions_audited_and_outboxed_without_values', audit == '3' and event_count == '4' and payload_leaks == '0')
    RESULT['stage'] = 'concurrent_publication'
    clients = [logged(), logged()]
    def race(index):
        body = command(1)
        status, data, headers = clients[index].request(24081 + index, path + '/publish', 'POST', body, headers={'Idempotency-Key': uuid.uuid4().hex})
        return status
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        outcomes = sorted(pool.map(race, [0, 1]))
    check('two_jvm_same_expected_version_has_one_conflict', outcomes == [200, 409], statuses=outcomes)
    history = call(admin, path + '/versions')
    check('only_winner_created_new_snapshot', len(history) == 3)
    env.stop(24081); env.start(24081, jar=candidate)
    admin = logged()
    check('snapshot_history_persists_after_jvm_restart', len(call(admin, path + '/versions')) == len(history))
    failed_migrations = env.sql('SELECT COUNT(*) FROM flyway_schema_history WHERE success=0', env.schema)
    check('production_mysql_migrations_succeeded', failed_migrations == '0')
    RESULT['stage'] = 'complete'
except Exception as error:
    RESULT['failureType'] = type(error).__name__
finally:
    if env:
        RESULT['sensitiveLogDetected'] = env.sensitive_log_detected
        try:
            env.close(); RESULT['cleanup'] = 'VERIFIED'
        except Exception as error:
            RESULT.update({'cleanup': 'FAILED', 'cleanupFailureType': type(error).__name__})
    checks = RESULT['checks']
    RESULT['counts'] = {'tests': len(checks), 'failures': sum(not c['passed'] for c in checks), 'errors': int('failureType' in RESULT), 'skipped': 0}
    RESULT['passed'] = RESULT['stage'] == 'complete' and not RESULT['counts']['failures'] and not RESULT['counts']['errors'] and RESULT.get('cleanup') == 'VERIFIED' and not RESULT.get('sensitiveLogDetected')
    (OUT / 'result.json').write_text(json.dumps(RESULT, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'stage': RESULT['stage'], 'counts': RESULT['counts'], 'cleanup': RESULT.get('cleanup'), 'passed': RESULT['passed']}), flush=True)
raise SystemExit(0 if RESULT['passed'] else 1)
