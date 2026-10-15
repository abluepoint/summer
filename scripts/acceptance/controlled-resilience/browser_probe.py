"""T32 actual PKCE/Chrome diagnostics; independent from root's native run.py."""
import hashlib
import json
import os
from pathlib import Path
import runpy
import selectors
import socket
import shutil
import subprocess
import time
import traceback
import uuid

from browser_environment import Client, Environment, OUT, ROOT, immutable_candidate

SCRIPT = Path(__file__).resolve().parent
NODE = '/opt/homebrew/opt/node@22/bin/node'
BASE = '/api/v1/enterprises/ent-acme'
PARAMETERS = BASE + '/system-parameters'
DIAG = '/internal/v1/diagnostics/resilience'
result = {'issue': 140, 'checks': [], 'networkMocking': False, 'businessSqlWrites': False,
          'syntheticIdentity': False, 'commands': []}
env = ui = browser = None
paused = False


class LocalClient(Client):
    def request(self, port, *args, **kwargs):
        return super().request(28081 if port == 21081 else port, *args, **kwargs)


def save():
    result['counts'] = {'tests': len(result['checks']),
                        'failures': sum(not c['passed'] for c in result['checks']),
                        'errors': int('errorType' in result), 'skipped': 0}
    tmp = OUT / 'browser-probe-result.tmp'
    tmp.write_text(json.dumps(result, indent=2) + '\n')
    tmp.replace(OUT / 'browser-probe-result.json')


def check(name, passed, **facts):
    result['checks'].append({'name': name, 'passed': bool(passed), **facts})
    save()
    print(name, 'PASS' if passed else 'FAIL', flush=True)


def call(name, path, method='GET', body=None, expected=200, headers=None):
    rid = 't32ui-' + uuid.uuid4().hex
    code, response, h = admin.request(28081, path, method, body,
                                     headers={'X-Request-Id': rid, **(headers or {})})
    error = (response.get('error') or {}) if isinstance(response, dict) else {}
    valid = code == expected and isinstance(response, dict)
    valid = valid and response.get('success') == (code < 400)
    valid = valid and response.get('requestId') == h.get('X-Request-Id') == rid
    if code >= 400:
        valid = valid and str(error.get('code', '')).startswith('SUMMER_') and response.get('data') is None
    check(name, valid, httpStatus=code, expected=expected, requestId=rid, code=error.get('code'))
    if not valid:
        raise AssertionError('invalid_response_contract')
    return response.get('data')


def mutation(version=None):
    return {'Idempotency-Key': uuid.uuid4().hex,
            **({'If-Match': '"' + str(version) + '"'} if version is not None else {})}


def login():
    c = LocalClient().login(28081, env.username, env.password)
    env.additional_secrets.append(c.token)
    c.cookies.clear()
    return c


def redis(*args):
    # Direct RESP control requires an actual server acknowledgment; no client response is fabricated.
    payloads = [str(arg).encode() for arg in args]
    wire = b'*' + str(len(payloads)).encode() + b'\r\n' + b''.join(
        b'$' + str(len(arg)).encode() + b'\r\n' + arg + b'\r\n' for arg in payloads)
    with socket.create_connection(('127.0.0.1', env.redis_port), timeout=5) as connection:
        connection.settimeout(5)
        connection.sendall(wire)
        with connection.makefile('rb') as reader:
            if reader.readline().strip() not in [b'+OK', b'+PONG']:
                raise RuntimeError('redis_control_not_acknowledged')


def assignment(denies):
    current = call('read_assignment_version', BASE + '/users/' + env.username + '/roles')
    call('change_actual_diagnostics_permission', BASE + '/users/' + env.username + '/roles', 'PUT',
         {'roleIds': [bootstrap_role, read_role], 'deniedCapabilityCodes': denies},
         headers=mutation(current['version']))


def snapshot():
    return call('actual_diagnostics_snapshot', DIAG)


def stop(process):
    if process is not None and process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=20)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=10)


def main():
    global env, ui, browser, admin, bootstrap_role, read_role, paused
    if os.environ.get('SUMMER_RESILIENCE_BROWSER_EVIDENCE_DIR') is None:
        raise RuntimeError('fresh_evidence_directory_required')
    if (OUT / 'browser-probe-result.json').exists():
        raise RuntimeError('evidence_directory_already_used')
    candidate = immutable_candidate()
    result['candidateSha256'] = hashlib.sha256(candidate.read_bytes()).hexdigest()
    source_manifest = candidate.with_name('candidate-inputs.json')
    inputs = json.loads(source_manifest.read_text())
    backend = 'summer-sample/src/main/java/com/abluepoint/summer/sample/resilience/'
    expected = {p: digest for p, digest in inputs.items() if p.startswith(backend)}
    if len(expected) < 7 or any(hashlib.sha256((ROOT / p).read_bytes()).hexdigest() != digest for p, digest in expected.items()):
        raise RuntimeError('candidate_resilience_source_mismatch')
    (OUT / 'candidate-source-match.json').write_text(json.dumps({'manifest': str(source_manifest), 'matchedBackendInputs': len(expected)}, indent=2) + '\n')
    archive = OUT / 'executed-scripts'
    archive.mkdir()
    for name in ['browser.mjs', 'ui-server.mjs', 'browser_probe.py', 'browser_environment.py']:
        shutil.copy2(SCRIPT / name, archive / name)
    relevant = list((ROOT / 'summer-sample/src/main/java/com/abluepoint/summer/sample/resilience').glob('*.java'))
    relevant += list((ROOT / 'summer-ui/src/pages/diagnostics/resilience').glob('*'))
    before = {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in relevant if p.is_file()}
    (OUT / 'inputs-before.json').write_text(json.dumps(before, indent=2) + '\n')
    ui_before = {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in (ROOT / 'summer-ui/src').rglob('*') if p.is_file()}
    (OUT / 'ui-inputs-before.json').write_text(json.dumps(ui_before, indent=2) + '\n')
    result['stage'] = 'environment'
    env = Environment()
    env.setup()
    env.extra = {'summer.sample.resilience.timeout': '400ms', 'summer.sample.resilience.max-attempts': 2,
                 'summer.sample.resilience.circuit-window': 2, 'summer.sample.resilience.circuit-open-wait': '30s',
                 'summer.sample.resilience.bulkhead': 1, 'summer.sample.resilience.fallback-ttl': '60s',
                 'summer.sample.resilience.rate-limit': 1, 'summer.sample.resilience.rate-limit-period': '1s'}
    env.start(28081, jar=candidate)
    result['stage'] = 'authorized_fixtures'
    admin = login()
    grants = runpy.run_path(str(ROOT / 'scripts/acceptance/authorized-configuration/grants.py'))
    bootstrap_role = grants['bootstrap'](admin, env.username)
    admin = login()
    catalog = call('capability_catalog', BASE + '/capabilities/catalog')
    ids = {c['code']: c['capabilityId'] for c in catalog['capabilities']}
    wanted = ['system.parameter.read', 'system.parameter.create', 'system.parameter.publish',
              'menu.diagnostics.resilience.read']
    for code in wanted:
        if code not in ids:
            capability = call('register_' + code, BASE + '/capabilities', 'POST',
                              {'code': code, 'resource': code.rsplit('.', 1)[0],
                               'action': code.rsplit('.', 1)[1], 'riskLevel': 'HIGH',
                               'scope': 'ENTERPRISE' if code.startswith('menu.') else 'PLATFORM'},
                              201, mutation())
            ids[code] = capability['capabilityId']
    read_role = grants['create_role'](admin, 'ent-acme', [ids[c] for c in wanted], 'T32 real UI metrics')
    assignment([])
    admin = login()
    call('actual_tenant_scope', BASE + '/data-scopes/system-parameters', 'PUT', {'conditions': {'field': 'TENANT'}})
    key = 'resilience.ui.' + env.run
    for suffix in ['', '.cold0', '.cold1', '.cold2', '.cold3', '.cold4']:
        call('create_parameter' + suffix, PARAMETERS, 'POST',
             {'key': key + suffix, 'type': 'STRING', 'value': 'T32 actual diagnostic value', 'public': True}, 201)
    path = PARAMETERS + '/' + key
    result['stage'] = 'actual_metric_generation'
    warm = call('warm_actual_parameter', path)
    rate_rejected = False
    for i in range(5):
        code, data, _ = admin.request(28081, path + '.cold' + str(i))
        if code == 503:
            rate_rejected = str((data.get('error') or {}).get('code', '')).startswith('SUMMER_')
            break
        if code != 200:
            raise AssertionError('unexpected_cold_read_status')
    check('actual_completed_reads_exhaust_rate_permits', rate_rejected,
          configuredLimit=1, period='1s', noConcurrentReadDuringRateProbe=True)
    if not rate_rejected:
        raise AssertionError('rate_fault_not_formed')
    time.sleep(1.2)
    call('rewarm_before_redis_latency', path)
    redis('CLIENT', 'PAUSE', 7000, 'ALL')
    pause_until = time.monotonic() + 7
    paused = True
    try:
        for i in range(2):
            value = call('real_latency_safe_read_' + str(i), path)
            check('fallback_preserves_verified_value_' + str(i), value == warm)
            time.sleep(4.5)
    finally:
        # Finite server-side fault expiry does not depend on an unpause command being processed.
        time.sleep(max(0, pause_until - time.monotonic()) + .2)
        redis('PING')
        paused = False
        check('finite_actual_redis_pause_expired_and_ping_recovered', True, pauseMilliseconds=7000)
    time.sleep(2.2)
    initial = snapshot()
    metric = next(v for v in initial['operations'] if v['name'] == 'system-parameters.read')
    check('real_nonzero_fault_counters_ready_for_ui', metric['timedOut'] > 0 and metric['fallbacks'] > 0
          and metric['rejected'] > 0 and isinstance(metric['retryAttempts'], int) and metric['retryAttempts'] >= 0
          and metric['circuitBreaker'] == 'OPEN',
          operations=initial['operations'])
    if not result['checks'][-1]['passed']:
        raise AssertionError('expected_metric_state_not_formed')
    result['stage'] = 'ui_start'
    ui_env = os.environ.copy()
    ui_env['SUMMER_RESILIENCE_BROWSER_VITE_CACHE'] = str(OUT / 'vite-cache')
    ui = subprocess.Popen([NODE, str(SCRIPT / 'ui-server.mjs')], cwd=ROOT / 'summer-ui',
                          env=ui_env, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                          stderr=subprocess.DEVNULL, text=True)
    selector = selectors.DefaultSelector()
    selector.register(ui.stdout, selectors.EVENT_READ)
    ready = False
    for _ in range(60):
        if ui.poll() is not None:
            raise RuntimeError('ui_start_exit')
        if selector.select(1) and ui.stdout.readline().strip() == 'UI_READY':
            ready = True
            break
    selector.close()
    if not ready:
        raise RuntimeError('ui_start_timeout')
    result['stage'] = 'actual_chrome'
    browser_env = os.environ.copy()
    browser_env['SUMMER_E2E_USERNAME'] = env.username
    browser_env['SUMMER_E2E_PASSWORD'] = env.password
    browser = subprocess.Popen([NODE, str(SCRIPT / 'browser.mjs')], cwd=ROOT / 'summer-ui', env=browser_env,
                               stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
    selector = selectors.DefaultSelector()
    selector.register(browser.stdout, selectors.EVENT_READ)
    deadline = time.monotonic() + 240
    while time.monotonic() < deadline:
        if browser.poll() is not None:
            break
        if not selector.select(1):
            continue
        command = browser.stdout.readline().strip()
        if not command:
            continue
        if command not in ['RECOVER_READS', 'DENY_DIAGNOSTICS', 'RESTORE_DIAGNOSTICS']:
            raise RuntimeError('unexpected_browser_command')
        result['commands'].append(command)
        if command == 'RECOVER_READS':
            time.sleep(30.2)
            for i in range(10):
                value = call('real_dependency_recovered_' + str(i), path)
                check('recovered_value_is_current_' + str(i), value == warm)
                time.sleep(1.1)
            recovered = snapshot()
            check('actual_recovery_closes_circuit', next(v for v in recovered['operations']
                  if v['name'] == 'system-parameters.read')['circuitBreaker'] == 'CLOSED')
        else:
            assignment(['platform.capabilities.read'] if command == 'DENY_DIAGNOSTICS' else [])
        browser.stdin.write(json.dumps({'ok': True}) + '\n')
        browser.stdin.flush()
        save()
    selector.close()
    try:
        result['browserExitCode'] = browser.wait(timeout=20)
    except subprocess.TimeoutExpired:
        raise RuntimeError('browser_process_exit_timeout')
    receipt = json.loads((OUT / 'browser-result.json').read_text())
    result['browserCounts'] = receipt['counts']
    if result['browserExitCode'] or not receipt['complete'] or receipt['counts']['failures'] or receipt['counts']['errors']:
        raise AssertionError('browser_incomplete_or_failed')
    after = {p: hashlib.sha256((ROOT / p).read_bytes()).hexdigest() for p in before}
    result['changedRelevantInputs'] = [p for p, digest in before.items() if after[p] != digest]
    check('related_execution_inputs_stable', not result['changedRelevantInputs'])
    result['uiInputCount'] = len(ui_before)
    result['changedUiInputsOutsideRelatedScope'] = [p for p, digest in ui_before.items() if p not in before and hashlib.sha256((ROOT / p).read_bytes()).hexdigest() != digest]
    result['complete'] = True


if __name__ == '__main__':
    try:
        main()
    except Exception as failure:
        result['errorType'] = type(failure).__name__
        result['localFrames'] = [{'file': Path(f.filename).name, 'line': f.lineno} for f in traceback.extract_tb(failure.__traceback__) if str(ROOT) in f.filename]
        # Exception messages, raw headers and authentication values are never persisted.
    finally:
        stop(browser)
        stop(ui)
        if env is not None:
            if paused:
                try:
                    redis('PING')
                except Exception:
                    result['redisRecoveryPingError'] = True
            result['sensitiveLogDetected'] = env.sensitive_log_detected
            result['exceptionClasses'] = env.diagnostics
            try:
                env.close()
                result['cleanup'] = 'VERIFIED'
            except Exception as failure:
                result['cleanup'] = type(failure).__name__
                result.setdefault('errorType', 'CleanupError')
        (OUT / 'http-metadata.json').write_text(json.dumps(Client.observations, indent=2) + '\n')
        save()
    raise SystemExit(0 if result.get('complete') and not result['counts']['failures']
                     and not result['counts']['errors'] and result.get('cleanup') == 'VERIFIED' else 1)
