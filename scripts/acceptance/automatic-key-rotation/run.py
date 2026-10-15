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


try:
    candidate = immutable_candidate()
    result['candidateSha256'] = hashlib.sha256(candidate.read_bytes()).hexdigest()
    inputs = {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest()
              for p in [Path(__file__).resolve(), SCRIPT / 'CountedTaskConfiguration.java']}
    (OUT / 'script-inputs.json').write_text(json.dumps(inputs, indent=2))
    fixture = build_fixture(candidate)
    env = Environment()
    env.setup()
    env.start(26081, jar=candidate, acceptance_fixture=fixture)
    admin = login()
    base_role = bootstrap(admin, env.username)
    admin = login()
    role = create_role(admin, 'ent-acme', ['security.key.read', 'security.key.rotate', 'menu.securityKeys.read',
        'GET:/api/security/keys', 'POST:/api/security/keys:rotate',
        'POST:/api/acceptance/automatic-rotation:due', 'POST:/api/acceptance/automatic-rotation:wake'], 'T28 rotation verifier')
    bind(admin, 'ent-acme', env.username, [base_role, role])
    admin = login()
    initial = metadata(admin)
    check('default_schedule_is_ninety_days', initial['rotation']['periodDays'] == 90
          and initial['rotation']['status'] == 'SCHEDULED' and initial['rotation']['failureCount'] == 0)
    active = next(row for row in initial['items'] if row['status'] == 'ACTIVE')
    job = env.sql("SELECT COUNT(*) FROM QRTZ_JOB_DETAILS WHERE JOB_NAME='automatic-key-rotation' AND JOB_GROUP='summer-security' AND IS_NONCONCURRENT=1", env.schema)
    check('real_persistent_quartz_job_disallows_concurrent_execution', job == '1')
    call(admin, '/api/acceptance/automatic-rotation:due', 'POST')
    rotated = wait_status(admin, 'SUCCEEDED')
    next_active = next(row for row in rotated['items'] if row['status'] == 'ACTIVE')
    check('actual_quartz_job_rotates_due_key', rotated['rotation']['status'] == 'SUCCEEDED'
          and next_active['kid'] != active['kid'] and next_active['version'] == active['version'] + 1)
    check('old_key_remains_in_grace_with_retention_deadline',
          any(row['kid'] == active['kid'] and row['status'] == 'GRACE' and row['graceUntil'] for row in rotated['items']))
    call(admin, '/api/acceptance/automatic-rotation:wake', 'POST')
    time.sleep(2)
    check('duplicate_job_wake_before_next_period_does_not_rotate_again', metadata(admin) == rotated)
    result['stage'] = 'actual_insert_fault'
    env.sql("CREATE TRIGGER t28_fail_insert BEFORE INSERT ON summer_security_keys FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='T28 controlled insert failure'", env.schema)
    call(admin, '/api/acceptance/automatic-rotation:due', 'POST')
    failed = wait_status(admin, 'FAILED', rotated['rotation']['lastAttemptAt'])
    check('failed_automatic_rotation_persists_failed_state_and_retry', failed['rotation']['status'] == 'FAILED'
          and failed['rotation']['failureCount'] == 1 and bool(failed['rotation']['lastError']))
    check('failed_automatic_rotation_preserves_active_key',
          [row for row in failed['items'] if row['status'] == 'ACTIVE'] == [next_active])
    env.sql('DROP TRIGGER t28_fail_insert', env.schema)
    (OUT / 'rotation-statuses.json').write_text(json.dumps({'initial': initial, 'rotated': rotated, 'failed': failed}, indent=2))
    assert all(hashlib.sha256((ROOT / p).read_bytes()).hexdigest() == h for p, h in inputs.items()), 'script_inputs_changed'
    result['stage'] = 'complete'
except Exception as error:
    result['failureType'] = type(error).__name__
    if re.fullmatch(r'[A-Za-z_]+(?:_[0-9]+)?', str(error)):
        result['failureCode'] = str(error)
finally:
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
    print(json.dumps({key: value for key, value in result.items() if key not in ['checks', 'diagnostics']}), flush=True)
    sys.exit(0 if result['passed'] else 1)
