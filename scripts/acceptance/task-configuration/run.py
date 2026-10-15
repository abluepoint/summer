"""T26 real HTTP grants and task lifecycle, then actual PKCE Chrome.

Uses a pinned application JAR and disposable MySQL/Redis infrastructure.
Only infrastructure DDL and read-only evidence SQL are allowed.
"""
import hashlib
import json
import os
import re
from pathlib import Path
import selectors
import subprocess
import sys
import time
import uuid

from environment import Client as BaseClient, Environment, OUT, ROOT, SCRIPT, immutable_candidate

sys.path.insert(0, str(ROOT / 'scripts/acceptance/identity'))
from grants import bootstrap, create_role, bind, unwrap

BASE = '/api/v1/enterprises/ent-acme'
TASKS = BASE + '/tasks'
result = {'issue': 134, 'stage': 'setup', 'checks': [], 'networkMocking': False, 'businessSqlWrites': False}
env = None
ui = None


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
    rid = 't26-' + uuid.uuid4().hex
    status, data, response = client.request(26081, path, method, body, headers={'X-Request-Id': rid, 'Idempotency-Key': uuid.uuid4().hex, **(headers or {})})
    assert status == expected, 'unexpected_http_status_' + str(status)
    assert isinstance(data, dict) and data.get('requestId') == response.get('X-Request-Id') == rid, 'correlation'
    if status >= 400:
        assert data.get('success') is False and data['error']['code'].startswith('SUMMER_')
        return data
    return unwrap(data)


def logged():
    return Client().login(26081, env.username, env.password)


def ui_inputs():
    files = list((ROOT / 'summer-ui/src').rglob('*')) + list((ROOT / 'summer-ui/config').rglob('*'))
    files += [ROOT / 'summer-ui/package.json', ROOT / 'summer-ui/package-lock.json']
    return {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in files if p.is_file()}


try:
    candidate = immutable_candidate()
    result['candidateSha256'] = hashlib.sha256(candidate.read_bytes()).hexdigest()
    manifest = {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in SCRIPT.iterdir() if p.suffix in ['.py', '.mjs']}
    (OUT / 'script-inputs.json').write_text(json.dumps(manifest, indent=2))
    env = Environment()
    env.setup()
    env.start(26081, jar=candidate)
    admin = logged()
    bootstrap_role = bootstrap(admin, env.username)
    admin = logged()
    catalog = call(admin, BASE + '/capabilities/catalog')['capabilities']
    known = {row['code']: row['capabilityId'] for row in catalog}
    codes = ['enterprise.tasks.read', 'enterprise.tasks.write']
    for code in codes:
        if code not in known:
            known[code] = call(admin, BASE + '/capabilities', 'POST', {
                'code': code, 'resource': 'enterprise.tasks', 'action': code.rsplit('.', 1)[1],
                'riskLevel': 'HIGH', 'scope': 'ENTERPRISE'}, 201)['capabilityId']
    route = '/api/v1/enterprises/{enterpriseId}/tasks'
    read_routes = ['GET:' + route, 'GET:' + route + '/types', 'GET:' + route + '/{taskId}/executions', 'GET:' + route + '/{taskId}/events']
    write_routes = ['POST:' + route, 'PATCH:' + route + '/{taskId}'] + ['POST:' + route + '/{taskId}:' + action for action in ['pause', 'resume', 'trigger']]
    writer = create_role(admin, 'ent-acme', [known.get(code, code) for code in codes + read_routes + write_routes], 'T26 task administrator')
    bind(admin, 'ent-acme', env.username, [bootstrap_role, writer])
    admin = logged()
    call(admin, '/api/users', 'POST', {'username': env.denied_username, 'password': env.denied_password, 'role': 'USER'})
    call(admin, BASE + '/members', 'POST', {'subject': env.denied_username, 'displayName': 'T26 reader', 'email': 'task-reader@example.test'}, 201)
    reader_role = create_role(admin, 'ent-acme', [known.get(code, code) for code in ['enterprise.tasks.read'] + read_routes], 'T26 task reader')
    bind(admin, 'ent-acme', env.denied_username, [reader_role])
    reader = Client().login(26081, env.denied_username, env.denied_password)
    check('roles_and_membership_configured_by_real_http', len(call(reader, TASKS)['items']) == 0)
    result['stage'] = 'task_lifecycle'
    types = call(admin, TASKS + '/types')
    check('only_registered_trusted_task_types_exposed', {'system.sleep', 'system.fail'} <= {row['taskType'] for row in types})
    payload = {'name': 'T26-' + env.run, 'taskType': 'system.sleep', 'cronExpression': '0 0 0 1 1 ? 2099',
               'parameters': {'millis': 1000}, 'maxRetries': 0, 'timeoutMs': 10000}
    call(admin, TASKS, 'POST', {**payload, 'cronExpression': 'invalid'}, 400)
    call(admin, TASKS, 'POST', {**payload, 'taskType': 'arbitrary.untrusted'}, 400)
    check('invalid_cron_and_unregistered_code_rejected', True)
    call(reader, TASKS, 'POST', payload, 403)
    check('read_only_identity_cannot_create', True)
    task = call(admin, TASKS, 'POST', payload, 201)
    path = TASKS + '/' + task['taskId']
    check('configuration_created_with_real_plan_and_parameters', task['parameters'] == {'millis': 1000} and task['status'] == 'ACTIVE')
    updated = call(admin, path, 'PATCH', {'name': payload['name'] + '-edited', 'parameters': {'millis': 1500}}, headers={'If-Match': '"' + str(task['version']) + '"'})
    call(admin, path, 'PATCH', {'name': payload['name']}, 412, {'If-Match': '"' + str(task['version']) + '"'})
    check('versioned_edit_and_stale_conflict', updated['parameters'] == {'millis': 1500})
    paused = call(admin, path + ':pause', 'POST', headers={'If-Match': '"' + str(updated['version']) + '"'})
    call(admin, path + ':trigger', 'POST', expected=409, headers={'Idempotency-Key': uuid.uuid4().hex})
    resumed = call(admin, path + ':resume', 'POST', headers={'If-Match': '"' + str(paused['version']) + '"'})
    check('pause_resume_and_paused_execution_rejected', paused['status'] == 'PAUSED' and resumed['status'] == 'ACTIVE')
    call(reader, path + ':trigger', 'POST', expected=403, headers={'Idempotency-Key': uuid.uuid4().hex})
    call(reader, path + ':pause', 'POST', expected=403, headers={'If-Match': '"' + str(resumed['version']) + '"'})
    check('read_only_identity_cannot_trigger_or_change_status', True)
    call(admin, '/api/v1/enterprises/ent-other/tasks', expected=403)
    check('cross_tenant_access_rejected', True)
    call(admin, path + ':trigger', 'POST', expected=202, headers={'Idempotency-Key': uuid.uuid4().hex})
    deadline = time.monotonic() + 25
    while time.monotonic() < deadline:
        history = call(admin, path + '/executions')['items']
        if history and history[0]['status'] == 'SUCCESS':
            break
        time.sleep(.25)
    check('real_quartz_execution_has_persistent_success_history', bool(history) and history[0]['status'] == 'SUCCESS' and bool(history[0]['finishedAt']))
    audit = env.sql("SELECT COUNT(DISTINCT action) FROM summer_audit_event WHERE resource='task:" + task['taskId'] + "' AND action IN ('TASK_CREATED','TASK_UPDATED','TASK_PAUSED','TASK_RESUMED','TASK_TRIGGERED') AND result='SUCCESS'", env.schema)
    check('all_five_configuration_and_execution_actions_audited', audit == '5')
    tables = env.sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='" + env.schema + "' AND (table_name LIKE 'QRTZ_%' OR table_name LIKE 'qrtz_%')", env.schema)
    check('production_quartz_mysql_tables_exist', int(tables) >= 10)
    env.stop(26081)
    env.start(26081, jar=candidate)
    admin = logged()
    retained = call(admin, TASKS)['items']
    check('definitions_and_execution_history_survive_jvm_restart', any(row['taskId'] == task['taskId'] and row['parameters'] == {'millis': 1500} for row in retained) and call(admin, path + '/executions')['items'][0]['status'] == 'SUCCESS')
    result['stage'] = 'actual_chrome'
    before = ui_inputs()
    (OUT / 'ui-inputs-before.json').write_text(json.dumps(before, indent=2))
    runtime = dict(os.environ, SUMMER_E2E_ORIGIN='http://127.0.0.1:26573', SUMMER_E2E_USERNAME=env.username,
                   SUMMER_E2E_PASSWORD=env.password, SUMMER_E2E_ENTERPRISE_ID='ent-acme', SUMMER_E2E_ISOLATED_SCHEMA=env.schema,
                   SUMMER_E2E_READER=env.denied_username, SUMMER_E2E_READER_PASSWORD=env.denied_password,
                   TASK_CONFIGURATION_VITE_CACHE=str(OUT / 'vite-cache'))
    ui = subprocess.Popen(['/opt/homebrew/opt/node@22/bin/node', str(SCRIPT / 'ui-server.mjs')], cwd=ROOT / 'summer-ui', env=runtime, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
    with selectors.DefaultSelector() as selector:
        selector.register(ui.stdout, selectors.EVENT_READ)
        assert selector.select(timeout=45), 'ui_startup_timeout'
        assert ui.stdout.readline().strip() == 'UI_READY'
    browser = subprocess.run(['/opt/homebrew/opt/node@22/bin/node', str(SCRIPT / 'browser.mjs')], cwd=ROOT / 'summer-ui', env=runtime, capture_output=True, text=True, timeout=240)
    report = json.loads((OUT / 'result.json').read_text())
    (OUT / 'browser-result.json').write_text(json.dumps(report, indent=2))
    result['browser'] = report
    result['browserExit'] = browser.returncode
    result['uiInputCount'] = len(before)
    after = ui_inputs()
    result['changedUiInputs'] = [p for p in set(before) | set(after) if before.get(p) != after.get(p)]
    assert browser.returncode == 0 and report['tests'] == 7 and not report['failures'] and not report['errors']
    assert not result['changedUiInputs'], 'ui_inputs_changed_during_execution'
    assert all(hashlib.sha256((ROOT / p).read_bytes()).hexdigest() == h for p, h in manifest.items()), 'script_inputs_changed'
    result['stage'] = 'complete'
except Exception as error:
    result['failureType'] = type(error).__name__
    if re.fullmatch(r'[A-Za-z_]+(?:_[0-9]+)?', str(error)):
        result['failureCode'] = str(error)
finally:
    if ui:
        ui.terminate()
        try:
            ui.wait(timeout=8)
        except subprocess.TimeoutExpired:
            ui.kill()
            ui.wait(timeout=8)
    if env:
        result['sensitiveLogDetected'] = env.sensitive_log_detected
        result['diagnostics'] = env.diagnostics
        try:
            env.close()
            result['cleanup'] = 'VERIFIED'
        except Exception as error:
            result['cleanup'] = 'FAILED'
            result['cleanupFailureType'] = type(error).__name__
    result['counts'] = {'tests': len(result['checks']), 'failures': sum(not row['passed'] for row in result['checks']), 'errors': int('failureType' in result), 'skipped': 0}
    result['passed'] = result['stage'] == 'complete' and result.get('cleanup') == 'VERIFIED' and not result.get('sensitiveLogDetected')
    (OUT / 'runtime-result.json').write_text(json.dumps(result, ensure_ascii=False, indent=2))
    print(json.dumps({key: value for key, value in result.items() if key not in ['checks', 'browser']}), flush=True)
    sys.exit(0 if result['passed'] else 1)
