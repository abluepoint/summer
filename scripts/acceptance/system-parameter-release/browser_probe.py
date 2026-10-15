import hashlib
import json
import os
import subprocess
import sys
import time
import urllib.request
import uuid
from environment import Client as BaseClient, Environment, ROOT, OUT, SCRIPT, immutable_candidate
sys.path.insert(0, str(ROOT / 'scripts/acceptance/identity'))
from grants import bootstrap, create_role, bind, unwrap

class Client(BaseClient):
    def request(self, port, *args, **kwargs): return super().request(19081 if port == 21081 else port, *args, **kwargs)

def call(client, path, method='GET', body=None, expected=200):
    status, data, headers = client.request(19081, path, method, body, headers={'Idempotency-Key': uuid.uuid4().hex})
    assert status == expected, 'http_status_' + str(status) + '_expected_' + str(expected)
    return unwrap(data)

def ui_inputs():
    return {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((ROOT / 'summer-ui/src').rglob('*')) if p.is_file()}

result = {'stage': 'setup', 'businessSqlWrites': False}
env = ui = browser = None
before = ui_inputs()
try:
    candidate = immutable_candidate(); env = Environment(); env.setup(); env.start(19081, jar=candidate)
    admin = Client().login(19081, env.username, env.password)
    initial_role = bootstrap(admin, env.username); admin = Client().login(19081, env.username, env.password)
    base = '/api/v1/enterprises/ent-acme'
    known = {row['code']: row['capabilityId'] for row in call(admin, base + '/capabilities/catalog')['capabilities']}
    codes = ['system.parameter.' + action for action in ['read', 'create', 'update', 'publish', 'disable', 'rollback', 'write']] + ['menu.systemParameters.read']
    for code in codes:
        if code not in known:
            known[code] = call(admin, base + '/capabilities', 'POST', {'code': code, 'resource': code.rsplit('.', 1)[0], 'action': code.rsplit('.', 1)[1], 'riskLevel': 'HIGH', 'scope': 'PLATFORM' if code.startswith('system.') else 'ENTERPRISE'}, 201)['capabilityId']
    role = create_role(admin, 'ent-acme', [known[code] for code in codes], 'T10 actual UI administration')
    bind(admin, 'ent-acme', env.username, [initial_role, role]); admin = Client().login(19081, env.username, env.password)
    call(admin, '/api/users', 'POST', {'username': env.denied_username, 'password': env.denied_password, 'role': 'USER'})
    call(admin, base + '/members', 'POST', {'subject': env.denied_username, 'displayName': 'T10 actual reader', 'email': 'release-reader@example.test'}, 201)
    reader_role = create_role(admin, 'ent-acme', [known['system.parameter.read'], known['menu.systemParameters.read']], 'T10 actual UI read only')
    bind(admin, 'ent-acme', env.denied_username, [reader_role])
    key = 'acceptance.release.ui.' + env.run
    call(admin, base + '/system-parameters', 'POST', {'key': key, 'type': 'INTEGER', 'value': 10, 'required': True, 'min': 0, 'max': 100, 'public': True}, 201)
    runtime = dict(os.environ, SUMMER_E2E_USERNAME=env.username, SUMMER_E2E_PASSWORD=env.password, SUMMER_E2E_READER=env.denied_username, SUMMER_E2E_READER_PASSWORD=env.denied_password, SUMMER_E2E_PARAMETER_KEY=key, SUMMER_PARAMETER_RELEASE_EVIDENCE_DIR=str(OUT), REALTIME_VITE_CACHE=str(OUT / 'vite-cache'))
    ui = subprocess.Popen(['/opt/homebrew/opt/node@22/bin/node', str(ROOT / 'scripts/acceptance/realtime/ui-server.mjs')], cwd=ROOT / 'summer-ui', env=runtime, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    for _ in range(180):
        if ui.poll() is not None: raise RuntimeError('ui_startup_exit')
        try:
            with urllib.request.urlopen('http://127.0.0.1:19573/login', timeout=1) as response:
                if response.status == 200: break
        except Exception: pass
        time.sleep(.25)
    else: raise RuntimeError('ui_startup_timeout')
    result['stage'] = 'browser'
    browser = subprocess.Popen(['/opt/homebrew/opt/node@22/bin/node', str(SCRIPT / 'browser.mjs')], cwd=ROOT / 'summer-ui', env=runtime, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    browser.wait(timeout=240); result['browserExitCode'] = browser.returncode
    result['stage'] = 'post_browser_api_readback'
    final = call(admin, base + '/system-parameters/' + key)
    history = call(admin, base + '/system-parameters/' + key + '/versions')
    result['effectiveVersion'] = final['version']; result['active'] = final['status'] == 'ACTIVE'
    result['historyCount'] = len(history['items']); result['publishedValueRestored'] = final['value'] == 10
    result['stage'] = 'complete'
except Exception as error:
    result['failureType'] = type(error).__name__
finally:
    for process in [browser, ui]:
        if process and process.poll() is None:
            process.terminate()
            try: process.wait(timeout=8)
            except subprocess.TimeoutExpired: process.kill(); process.wait()
    if env:
        result['sensitiveLogDetected'] = env.sensitive_log_detected
        try: env.close(); result['cleanup'] = 'VERIFIED'
        except Exception as error: result.update({'cleanup': 'FAILED', 'cleanupFailureType': type(error).__name__})
    after = ui_inputs(); result['uiInputCount'] = len(before)
    result['changedUiInputs'] = [p for p in sorted(set(before) | set(after)) if before.get(p) != after.get(p)]
    result['passed'] = result['stage'] == 'complete' and result.get('browserExitCode') == 0 and result.get('cleanup') == 'VERIFIED' and result.get('effectiveVersion') == 4 and result.get('active') and result.get('publishedValueRestored') and not result['changedUiInputs'] and not result.get('sensitiveLogDetected')
    (OUT / 'ui-inputs-before.json').write_text(json.dumps(before, indent=2) + '\n')
    (OUT / 'browser-probe-result.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result), flush=True)
raise SystemExit(0 if result['passed'] else 1)
