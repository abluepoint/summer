"""T28 actual Quartz rotation and failure-state probe, pinned production JAR.

A separate test-only plugin advances due time and wakes the real Quartz job.
Key creation, rotation, protocol endpoints and state outcomes remain production code.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import uuid
import zipfile
import selectors

ROOT = Path(__file__).resolve().parents[3]
SCRIPT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / 'scripts/acceptance/task-configuration'))
from environment import Client as BaseClient, Environment as BaseEnvironment, OUT, JAVA, immutable_candidate
sys.path.insert(0, str(ROOT / 'scripts/acceptance/identity'))
from grants import bootstrap, create_role, bind, unwrap

result = {'issue': 136, 'stage': 'setup', 'checks': [], 'networkMocking': False,
          'timeFixture': 'next_rotation_at only; actual production Quartz job', 'productionJarUnmodified': True}
env = None


class Client(BaseClient):
    def request(self, port, *args, **kwargs):
        reply = super().request(26081 if port == 21081 else port, *args, **kwargs)
        (OUT / 'http-metadata.json').write_text(json.dumps(self.observations, indent=2))
        return reply


class Environment(BaseEnvironment):
    def sql(self, statement, database=None):
        if statement.startswith(('CREATE TRIGGER t28_', 'DROP TRIGGER t28_')):
            args = ['kubectl', '--context', 'docker-desktop', 'exec', '-i', '-n', 'local-base',
                    'mysql-0', '--', 'sh', '-c', 'IFS= read -r MYSQL_PWD; export MYSQL_PWD; exec mysql -uroot --batch --skip-column-names']
            body = self.mysql + '\nUSE `' + self.schema + '`;\n' + statement + '\n'
            reply = subprocess.run(args, input=body, text=True, capture_output=True)
            if reply.returncode:
                raise RuntimeError('fault_fixture_ddl_failed')
            return reply.stdout.strip()
        return super().sql(statement, database)


def check(name, condition):
    result['checks'].append({'name': name, 'passed': bool(condition)})
    print(name, bool(condition), flush=True)


def call(client, path, method='GET', body=None, expected=200):
    rid = 't28-' + uuid.uuid4().hex
    status, data, response = client.request(26081, path, method, body,
        headers={'X-Request-Id': rid, 'Idempotency-Key': uuid.uuid4().hex})
    assert status == expected, 'unexpected_http_status_' + str(status)
    assert isinstance(data, dict) and data.get('requestId') == response.get('X-Request-Id') == rid, 'correlation'
    if status >= 400:
        assert data.get('success') is False and data['error']['code'].startswith('SUMMER_')
        return data
    return unwrap(data)


def login():
    return Client().login(26081, env.username, env.password)


def metadata(client):
    return call(client, '/api/security/keys')


def wait_status(client, expected, previous_attempt=None):
    deadline = time.monotonic() + 18
    current = None
    while time.monotonic() < deadline:
        current = metadata(client)
        if current['rotation']['status'] == expected and current['rotation']['lastAttemptAt'] != previous_attempt:
            return current
        time.sleep(.3)
    return current


def build_fixture(candidate):
    libs, classes = OUT / 'fixture-libs', OUT / 'fixture-classes'
    libs.mkdir(exist_ok=False)
    classes.mkdir(exist_ok=False)
    with zipfile.ZipFile(candidate) as archive:
        for item in archive.namelist():
            if item.startswith('BOOT-INF/lib/') and item.endswith('.jar'):
                (libs / Path(item).name).write_bytes(archive.read(item))
    source = SCRIPT / 'CountedTaskConfiguration.java'
    compiled = subprocess.run([str(JAVA.parent / 'javac'), '-proc:none', '-cp', str(libs / '*'),
                               '-d', str(classes), str(source)], capture_output=True)
    if compiled.returncode:
        raise RuntimeError('acceptance_fixture_compile_failed')
    fixture = OUT / 'rotation-time-fixture.jar'
    subprocess.run([str(JAVA.parent / 'jar'), 'cf', str(fixture), '-C', str(classes), '.'],
                   check=True, capture_output=True)
    (OUT / 'fixture-build.json').write_text(json.dumps({'testOnly': True,
        'sourceSha256': hashlib.sha256(source.read_bytes()).hexdigest(),
        'fixtureSha256': hashlib.sha256(fixture.read_bytes()).hexdigest()}, indent=2))
    return fixture


ui = None


def browser(mode, username, password):
    runtime = dict(os.environ, SUMMER_ROTATION_MODE=mode, SUMMER_E2E_USERNAME=username,
                   SUMMER_E2E_PASSWORD=password)
    run = subprocess.run(['/opt/homebrew/opt/node@22/bin/node', str(SCRIPT / 'browser.mjs')],
                         cwd=ROOT / 'summer-ui', env=runtime, capture_output=True, timeout=150)
    report = json.loads((OUT / ('browser-' + mode + '.json')).read_text())
    result.setdefault('browsers', []).append({'exitCode': run.returncode, **report})
    assert run.returncode == 0 and report['passed'], 'browser_failed_' + mode


def verify(token, jwks):
    proof = subprocess.run(['/opt/homebrew/opt/node@22/bin/node', str(SCRIPT.parent / 'keys/crypto.mjs')],
        input=json.dumps({'token': token, 'jwks': jwks}), text=True, capture_output=True, check=True)
    return json.loads(proof.stdout).get('signatureValid') is True


try:
    candidate = immutable_candidate()
    result['candidateSha256'] = hashlib.sha256(candidate.read_bytes()).hexdigest()
    inputs = {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in
              [Path(__file__).resolve(), SCRIPT / 'CountedTaskConfiguration.java', SCRIPT / 'browser.mjs']}
    (OUT / 'script-inputs.json').write_text(json.dumps(inputs, indent=2))
    fixture = build_fixture(candidate)
    env = Environment()
    env.extra = {'summer.sample.security.key-rotation.period-days': 7,
                 'summer.sample.security.key-rotation.retention-days': 1}
    env.setup()
    env.start(26081, jar=candidate, acceptance_fixture=fixture)
    admin = login()
    base_role = bootstrap(admin, env.username)
    admin = login()
    role = create_role(admin, 'ent-acme', ['security.key.read', 'security.key.rotate', 'menu.securityKeys.read',
        'menu.audit.read', 'GET:/api/security/keys', 'POST:/api/security/keys:rotate',
        'POST:/api/acceptance/automatic-rotation:due', 'POST:/api/acceptance/automatic-rotation:wake'], 'T28 supplementary verifier')
    bind(admin, 'ent-acme', env.username, [base_role, role])
    admin = login()
    old_token = admin.token
    env.additional_secrets.append(old_token)
    initial = metadata(admin)
    active = next(row for row in initial['items'] if row['status'] == 'ACTIVE')
    check('configured_seven_day_period_and_one_day_retention_visible',
          initial['rotation']['periodDays'] == 7 and initial['rotation']['retentionDays'] == 1)
    env.start(26082, jar=candidate, acceptance_fixture=fixture)
    check('two_actual_quartz_cluster_members_registered',
          int(env.sql('SELECT COUNT(*) FROM QRTZ_SCHEDULER_STATE', env.schema)) == 2)
    call(admin, '/api/users', 'POST', {'username': env.denied_username, 'password': env.denied_password, 'role': 'USER'})
    reader = Client().login(26081, env.denied_username, env.denied_password)
    call(reader, '/api/security/keys', expected=403)
    check('ungranted_metadata_read_rejected_with_framework_error_and_correlation', True)
    env.sql("CREATE TRIGGER t28_fail_insert BEFORE INSERT ON summer_security_keys FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='T28 controlled insert failure'", env.schema)
    call(admin, '/api/acceptance/automatic-rotation:due', 'POST')
    failed = wait_status(admin, 'FAILED')
    from datetime import datetime, timezone
    retry_hours = (datetime.fromisoformat(failed['rotation']['nextRotationAt'].replace('Z', '+00:00')) - datetime.now(timezone.utc)).total_seconds() / 3600
    check('real_failure_has_one_hour_retry_backoff', failed['rotation']['status'] == 'FAILED'
          and failed['rotation']['failureCount'] == 1 and .9 < retry_hours <= 1)
    audit_path = '/api/audit-events?resource=security%3Aautomatic-key-rotation'
    failure_events = call(admin, audit_path)['items']
    check('failed_rotation_has_queryable_safe_persistent_audit',
          len(failure_events) == 1 and failure_events[0]['action'] == 'KEY_AUTOMATIC_ROTATION_FAILED'
          and failure_events[0]['result'] == 'FAILURE' and failure_events[0]['principal'] == 'system'
          and failure_events[0]['requestId'].startswith('automatic-key-rotation-')
          and failure_events[0]['detail'] == {'reason': 'SUMMER_KEY_ROTATION_FAILED'})
    paths = ['summer-ui/src/pages/security-keys/index.tsx', 'summer-ui/src/pages/security-keys/api.ts',
             'summer-ui/src/pages/audit/index.tsx', 'summer-ui/src/pages/audit/api.ts', 'summer-ui/src/menu/access.ts']
    ui_before = {p: hashlib.sha256((ROOT / p).read_bytes()).hexdigest() for p in paths}
    (OUT / 'ui-inputs.json').write_text(json.dumps(ui_before, indent=2))
    runtime = dict(os.environ, TASK_CONFIGURATION_VITE_CACHE=str(OUT / 'vite-cache'))
    ui = subprocess.Popen(['/opt/homebrew/opt/node@22/bin/node', str(SCRIPT.parent / 'task-configuration/ui-server.mjs')],
        cwd=ROOT / 'summer-ui', env=runtime, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
    with selectors.DefaultSelector() as selector:
        selector.register(ui.stdout, selectors.EVENT_READ)
        assert selector.select(timeout=45), 'ui_startup_timeout'
        assert ui.stdout.readline().strip() == 'UI_READY'
    result['stage'] = 'failed_schedule_chrome'
    browser('failed', env.username, env.password)
    env.sql('DROP TRIGGER t28_fail_insert', env.schema)
    call(admin, '/api/acceptance/automatic-rotation:due', 'POST')
    recovered = wait_status(admin, 'SUCCEEDED', failed['rotation']['lastAttemptAt'])
    new_active = next(row for row in recovered['items'] if row['status'] == 'ACTIVE')
    delta_days = (datetime.fromisoformat(recovered['rotation']['nextRotationAt'].replace('Z', '+00:00')) - datetime.now(timezone.utc)).total_seconds() / 86400
    check('fault_recovery_rotates_once_and_reschedules_configured_period',
          recovered['rotation']['status'] == 'SUCCEEDED' and recovered['rotation']['failureCount'] == 0
          and new_active['version'] == active['version'] + 1 and 6.9 < delta_days <= 7)
    status, jwks, _ = admin.request(26082, '/oauth2/jwks', bearer=False)
    fresh = Client().login(26082, env.username, env.password)
    env.additional_secrets.append(fresh.token)
    import base64
    header = fresh.token.split('.')[0]
    kid = json.loads(base64.urlsafe_b64decode(header + '=' * (-len(header) % 4)))['kid']
    check('new_node_signs_new_kid_while_old_public_key_still_verifies',
          status == 200 and kid == new_active['kid'] and verify(fresh.token, jwks) and verify(old_token, jwks))
    audit = call(admin, audit_path)['items']
    check('success_and_failure_records_are_both_queryable_after_recovery', len(audit) == 2
          and {row['action'] for row in audit} == {'KEY_AUTOMATIC_ROTATION_FAILED', 'KEY_AUTOMATIC_ROTATION_SUCCEEDED'}
          and len({row['requestId'] for row in audit}) == 2)
    result['stage'] = 'recovered_schedule_chrome'
    browser('recovered', env.username, env.password)
    browser('denied', env.denied_username, env.denied_password)
    env.stop(26081)
    env.start(26081, jar=candidate, acceptance_fixture=fixture)
    admin = login()
    check('automatic_status_and_audits_survive_real_jvm_restart', metadata(admin) == recovered
          and call(admin, audit_path)['items'] == audit)
    check('five_related_ui_inputs_unchanged', all(hashlib.sha256((ROOT / p).read_bytes()).hexdigest() == h for p, h in ui_before.items()))
    assert all(hashlib.sha256((ROOT / p).read_bytes()).hexdigest() == h for p, h in inputs.items()), 'script_inputs_changed'
    result['stage'] = 'complete'
except Exception as error:
    result['failureType'] = type(error).__name__
    if re.fullmatch(r'[A-Za-z_]+(?:_[0-9]+)?', str(error)):
        result['failureCode'] = str(error)
finally:
    if ui:
        ui.terminate()
        try:
            ui.wait(timeout=10)
        except subprocess.TimeoutExpired:
            ui.kill()
            ui.wait(timeout=5)
    if env:
        result['sensitiveLogDetected'] = env.sensitive_log_detected
        result['diagnostics'] = env.diagnostics
        try:
            env.close()
            result['cleanup'] = 'VERIFIED'
        except Exception as error:
            result['cleanup'] = 'FAILED'
            result['cleanupFailureType'] = type(error).__name__
    result['counts'] = {'tests': len(result['checks']), 'failures': sum(not row['passed'] for row in result['checks']),
                        'errors': int('failureType' in result), 'skipped': 0}
    result['passed'] = result['stage'] == 'complete' and not result['counts']['failures'] and result.get('cleanup') == 'VERIFIED' and not result.get('sensitiveLogDetected')
    (OUT / 'runtime-result.json').write_text(json.dumps(result, ensure_ascii=False, indent=2))
    print(json.dumps({key: value for key, value in result.items() if key not in ['checks', 'diagnostics', 'browsers']}), flush=True)
    sys.exit(0 if result['passed'] else 1)
