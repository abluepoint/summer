import json
import os
import subprocess
import sys
import time
import urllib.request
from environment import Client as BaseClient, Environment, ROOT, OUT, SCRIPT, immutable_candidate
sys.path.insert(0, str(ROOT / 'scripts/acceptance/identity'))
from grants import unwrap
from fixtures import prepare

class Client(BaseClient):
    def request(self, port, *args, **kwargs): return super().request(19081 if port == 21081 else port, *args, **kwargs)
def call(client, path, method='GET', body=None, headers=None, status=200, **kwargs):
    actual, data, response = client.request(19081, path, method, body, headers, **kwargs)
    assert actual == status, 'http_status_' + str(actual) + '_expected_' + str(status)
    return unwrap(data), response

env = None; ui = None; browser = None
result = {'stage': 'setup', 'businessSqlWrites': False}
try:
    candidate = immutable_candidate()
    env = Environment(); env.setup(); env.extra['summer.sample.authorization.legacy-scope-compatibility'] = False
    admin = prepare(env, candidate, Client, call)
    call(admin, '/api/users', 'POST', {'username': env.denied_username, 'password': env.denied_password, 'role': 'USER'})
    runtime = os.environ.copy(); runtime['SUMMER_E2E_USERNAME'] = env.username; runtime['SUMMER_E2E_PASSWORD'] = env.password
    runtime['SUMMER_E2E_READER'] = env.denied_username; runtime['SUMMER_E2E_READER_PASSWORD'] = env.denied_password
    runtime['SUMMER_REALTIME_EVIDENCE_DIR'] = str(OUT); runtime['REALTIME_VITE_CACHE'] = str(OUT / 'vite-cache')
    ui = subprocess.Popen(['/opt/homebrew/opt/node@22/bin/node', str(SCRIPT / 'ui-server.mjs')], cwd=ROOT / 'summer-ui', env=runtime, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
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
    browser.wait(timeout=180)
    result['browserExitCode'] = browser.returncode
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
        try: env.close(); result['cleanup'] = 'VERIFIED'
        except Exception as error: result['cleanup'] = 'FAILED'; result['cleanupFailureType'] = type(error).__name__
    result['passed'] = result['stage'] == 'complete' and result.get('browserExitCode') == 0 and result.get('cleanup') == 'VERIFIED'
    (OUT / 'browser-probe-result.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result), flush=True)
raise SystemExit(0 if result['passed'] else 1)
