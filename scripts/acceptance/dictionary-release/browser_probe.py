"""Actual T11 form; fixture mutations use only authenticated HTTP APIs."""
import hashlib
import json
import os
import selectors
import subprocess
import sys
import time
import urllib.request
import uuid
from environment import Client as BaseClient, Environment, ROOT, OUT, SCRIPT, immutable_candidate
sys.path.insert(0, str(ROOT / 'scripts/acceptance/identity'))
from grants import bootstrap, create_role, bind, unwrap

class Client(BaseClient):
    def request(self, port, *args, **kwargs): return super().request(24081 if port == 21081 else port, *args, **kwargs)

def call(client, path, method='GET', body=None, expected=200):
    status, data, headers = client.request(24081, path, method, body, headers={'Idempotency-Key': uuid.uuid4().hex})
    assert status == expected, 'http_status_' + str(status) + '_expected_' + str(expected)
    return unwrap(data)

def ui_inputs():
    return {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((ROOT / 'summer-ui/src').rglob('*')) if p.is_file()}

def command(version, target=None):
    result = {'expectedVersion': version, 'idempotencyKey': uuid.uuid4().hex}
    if target is not None: result['targetVersion'] = target
    return result

result = {'issue': 119, 'stage': 'setup', 'businessSqlWrites': False, 'networkMocking': False, 'commands': []}
env = ui = browser = None
before = ui_inputs()
try:
    candidate = immutable_candidate(); env = Environment(); env.setup(); env.start(24081, jar=candidate)
    admin = Client().login(24081, env.username, env.password)
    initial_role = bootstrap(admin, env.username); admin = Client().login(24081, env.username, env.password)
    base = '/api/v1/enterprises/ent-acme'
    known = {row['code']: row['capabilityId'] for row in call(admin, base + '/capabilities/catalog')['capabilities']}
    codes = ['platform.dictionary.' + action for action in ['read', 'create', 'update', 'publish', 'disable', 'rollback', 'write']] + ['menu.dictionary.form.read']
    for code in codes:
        if code not in known:
            known[code] = call(admin, base + '/capabilities', 'POST', {'code': code, 'resource': code.rsplit('.', 1)[0], 'action': code.rsplit('.', 1)[1], 'riskLevel': 'HIGH', 'scope': 'PLATFORM' if code.startswith('platform.') else 'ENTERPRISE'}, 201)['capabilityId']
    role = create_role(admin, 'ent-acme', [known[code] for code in codes], 'T11 actual UI administration')
    bind(admin, 'ent-acme', env.username, [initial_role, role]); admin = Client().login(24081, env.username, env.password)
    call(admin, '/api/users', 'POST', {'username': env.denied_username, 'password': env.denied_password, 'role': 'USER'})
    call(admin, base + '/members', 'POST', {'subject': env.denied_username, 'displayName': 'T11 actual reader', 'email': 'dictionary-reader@example.test'}, 201)
    reader_role = create_role(admin, 'ent-acme', [known['platform.dictionary.read'], known['menu.dictionary.form.read']], 'T11 actual UI read only')
    bind(admin, 'ent-acme', env.denied_username, [reader_role])
    path = base + '/dictionaries/customer-segment'
    call(admin, base + '/dictionaries', 'POST', {'code': 'customer-segment', 'name': 'T11 实际字典', 'published': False, 'publicReadable': True, 'enabled': True})
    for value, label in [('alpha', '发布一甲'), ('bravo', '发布一乙')]:
        call(admin, path + '/items', 'POST', {'value': value, 'label': label, 'sortOrder': 0, 'enabled': True})
    call(admin, path + '/publish', 'POST', command(0))
    runtime = dict(os.environ, SUMMER_E2E_USERNAME=env.username, SUMMER_E2E_PASSWORD=env.password, SUMMER_E2E_READER=env.denied_username, SUMMER_E2E_READER_PASSWORD=env.denied_password, SUMMER_DICTIONARY_RELEASE_EVIDENCE_DIR=str(OUT), DICTIONARY_RELEASE_VITE_CACHE=str(OUT / 'vite-cache'))
    ui = subprocess.Popen(['/opt/homebrew/opt/node@22/bin/node', str(SCRIPT / 'ui-server.mjs')], cwd=ROOT / 'summer-ui', env=runtime, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    for _ in range(180):
        if ui.poll() is not None: raise RuntimeError('ui_startup_exit')
        try:
            with urllib.request.urlopen('http://127.0.0.1:24573/login', timeout=1) as response:
                if response.status == 200: break
        except Exception: pass
        time.sleep(.25)
    else: raise RuntimeError('ui_startup_timeout')
    result['stage'] = 'browser'
    browser = subprocess.Popen(['/opt/homebrew/opt/node@22/bin/node', str(SCRIPT / 'browser.mjs')], cwd=ROOT / 'summer-ui', env=runtime, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
    selector = selectors.DefaultSelector(); selector.register(browser.stdout, selectors.EVENT_READ)
    deadline = time.monotonic() + 240
    while selector.get_map():
        if time.monotonic() >= deadline: raise TimeoutError('browser_deadline')
        for key, _ in selector.select(timeout=.5):
            line = key.fileobj.readline()
            if not line:
                selector.unregister(key.fileobj); continue
            action = line.strip()
            if action == 'DRAFT_EDIT':
                call(admin, path + '/items/alpha', 'PATCH', {'label': '发布二甲', 'sortOrder': 0, 'enabled': True})
                call(admin, path + '/items', 'POST', {'value': 'charlie', 'label': '发布二丙', 'sortOrder': 1, 'enabled': True})
            elif action == 'PUBLISH': call(admin, path + '/publish', 'POST', command(1))
            elif action == 'DISABLE': call(admin, path + '/disable', 'POST', command(2))
            elif action == 'ROLLBACK': call(admin, path + '/rollback', 'POST', command(2, 1))
            else: raise RuntimeError('unexpected_browser_command')
            result['commands'].append(action); browser.stdin.write('GO\n'); browser.stdin.flush()
    selector.close(); browser.wait(timeout=10); result['browserExitCode'] = browser.returncode
    final = call(admin, '/public/enterprises/ent-acme/dictionaries/customer-segment')
    result['restoredPublishedVersion'] = final['version']; result['restoredLabels'] = [x['label'] for x in final['items']]
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
    receipt = OUT / 'browser-result.json'
    observed = json.loads(receipt.read_text()) if receipt.exists() else {}
    result['passed'] = result['stage'] == 'complete' and result.get('browserExitCode') == 0 and observed.get('complete') is True and all(x['passed'] for x in observed.get('checks', [])) and bool(observed.get('checks')) and result.get('cleanup') == 'VERIFIED' and result.get('restoredPublishedVersion') == 1 and result.get('restoredLabels') == ['发布一甲', '发布一乙'] and not result['changedUiInputs'] and not result.get('sensitiveLogDetected')
    (OUT / 'ui-inputs-before.json').write_text(json.dumps(before, indent=2) + '\n')
    (OUT / 'browser-probe-result.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(result, ensure_ascii=False), flush=True)
raise SystemExit(0 if result['passed'] else 1)
