"""T27: actual retry/timeout history on a pinned JAR and disposable infrastructure.

Reuses T26 provisioning. Credentials remain in memory; business setup uses HTTP.
Set SUMMER_TASK_CONFIGURATION_EVIDENCE_DIR, _CANDIDATE and _CANDIDATE_SHA256.
"""
import hashlib
import json
from pathlib import Path
import re
import sys
import time
import uuid

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'scripts/acceptance/task-configuration'))
from environment import Client as BaseClient, Environment, OUT, immutable_candidate
sys.path.insert(0, str(ROOT / 'scripts/acceptance/identity'))
from grants import bootstrap, create_role, bind, unwrap

BASE = '/api/v1/enterprises/ent-acme'
TASKS = BASE + '/tasks'
result = {'issue': 135, 'stage': 'setup', 'checks': [], 'networkMocking': False,
          'businessSqlWrites': False}
env = None


class Client(BaseClient):
    def request(self, port, *args, **kwargs):
        reply = super().request(26081 if port == 21081 else port, *args, **kwargs)
        (OUT / 'http-metadata.json').write_text(json.dumps(self.observations, indent=2))
        return reply


def check(name, condition):
    result['checks'].append({'name': name, 'passed': bool(condition)})
    print(name, bool(condition), flush=True)
    assert condition, name


def call(client, path, method='GET', body=None, expected=200, headers=None):
    rid = 't27-' + uuid.uuid4().hex
    status, data, response = client.request(26081, path, method, body, headers={
        'X-Request-Id': rid, 'Idempotency-Key': uuid.uuid4().hex, **(headers or {})})
    assert status == expected, 'unexpected_http_status_' + str(status)
    assert isinstance(data, dict) and data.get('requestId') == response.get('X-Request-Id') == rid, 'correlation'
    if status >= 400:
        assert data.get('success') is False and data['error']['code'].startswith('SUMMER_')
        return data
    return unwrap(data)


def login():
    return Client().login(26081, env.username, env.password)


def terminal(client, task_id):
    deadline = time.monotonic() + 35
    while time.monotonic() < deadline:
        rows = call(client, TASKS + '/' + task_id + '/executions')['items']
        if rows and rows[0]['status'] in ['SUCCESS', 'FAILED']:
            return rows[0]
        time.sleep(.25)
    raise RuntimeError('execution_terminal_timeout')


try:
    candidate = immutable_candidate()
    result['candidateSha256'] = hashlib.sha256(candidate.read_bytes()).hexdigest()
    own_inputs = [Path(__file__).resolve(), ROOT / 'scripts/acceptance/task-configuration/environment.py',
                  ROOT / 'scripts/acceptance/identity/grants.py']
    inputs = {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in own_inputs}
    (OUT / 'script-inputs.json').write_text(json.dumps(inputs, indent=2))
    env = Environment()
    env.setup()
    env.start(26081, jar=candidate)
    admin = login()
    base_role = bootstrap(admin, env.username)
    admin = login()
    catalog = call(admin, BASE + '/capabilities/catalog')['capabilities']
    known = {row['code']: row['capabilityId'] for row in catalog}
    codes = ['enterprise.tasks.read', 'enterprise.tasks.write']
    for code in codes:
        if code not in known:
            known[code] = call(admin, BASE + '/capabilities', 'POST', {
                'code': code, 'resource': 'enterprise.tasks', 'action': code.rsplit('.', 1)[1],
                'riskLevel': 'HIGH', 'scope': 'ENTERPRISE'}, 201)['capabilityId']
    route = '/api/v1/enterprises/{enterpriseId}/tasks'
    routes = ['GET:' + route, 'GET:' + route + '/{taskId}/executions',
              'POST:' + route, 'POST:' + route + '/{taskId}:trigger']
    role = create_role(admin, 'ent-acme', [known.get(code, code) for code in codes + routes], 'T27 retry verification')
    bind(admin, 'ent-acme', env.username, [base_role, role])
    admin = login()
    result['stage'] = 'retry_and_timeout'
    payload = {'name': 'T27-retry-' + env.run, 'taskType': 'system.fail',
               'cronExpression': '0 0 0 1 1 ? 2099', 'parameters': {}, 'maxRetries': 2, 'timeoutMs': 1000}
    task = call(admin, TASKS, 'POST', payload, 201)
    call(admin, TASKS + '/' + task['taskId'] + ':trigger', 'POST', expected=202)
    failure = terminal(admin, task['taskId'])
    check('configured_two_retries_produce_exactly_three_attempts', failure['status'] == 'FAILED'
          and failure['attempt'] == 3 and len(failure['attempts']) == 3)
    check('each_failed_attempt_has_persistent_result_timestamps_and_owner',
          [row['attempt'] for row in failure['attempts']] == [1, 2, 3]
          and all(row['status'] == 'FAILED' and row['failureCode'] == 'TASK_HANDLER_FAILED'
                  and row['startedAt'] and row['finishedAt'] and row['nodeId'] for row in failure['attempts'])
          and failure['failureCode'] == 'TASK_HANDLER_FAILED' and bool(failure['finishedAt']))
    slow = call(admin, TASKS, 'POST', {**payload, 'name': 'T27-timeout-' + env.run,
                'taskType': 'system.sleep', 'parameters': {'millis': 3000}, 'maxRetries': 0}, 201)
    call(admin, TASKS + '/' + slow['taskId'] + ':trigger', 'POST', expected=202)
    timeout = terminal(admin, slow['taskId'])
    check('real_handler_timeout_has_final_failed_state_and_single_attempt',
          timeout['status'] == 'FAILED' and timeout['failureCode'] == 'TASK_TIMEOUT'
          and timeout['attempt'] == 1 and len(timeout['attempts']) == 1
          and timeout['attempts'][0]['failureCode'] == 'TASK_TIMEOUT' and bool(timeout['finishedAt']))
    histories = {task['taskId']: failure, slow['taskId']: timeout}
    (OUT / 'execution-history.json').write_text(json.dumps(histories, indent=2))
    env.stop(26081)
    env.start(26081, jar=candidate)
    admin = login()
    retained = {task_id: call(admin, TASKS + '/' + task_id + '/executions')['items'][0] for task_id in histories}
    check('retry_and_timeout_histories_survive_actual_jvm_restart', retained == histories)
    time.sleep(2)
    check('terminal_histories_remain_stable_without_extra_retry',
          all(call(admin, TASKS + '/' + task_id + '/executions')['items'] == [history]
              for task_id, history in histories.items()))
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
    result['passed'] = result['stage'] == 'complete' and result.get('cleanup') == 'VERIFIED' and not result.get('sensitiveLogDetected')
    (OUT / 'runtime-result.json').write_text(json.dumps(result, ensure_ascii=False, indent=2))
    print(json.dumps({key: value for key, value in result.items() if key not in ['checks']}), flush=True)
    sys.exit(0 if result['passed'] else 1)
