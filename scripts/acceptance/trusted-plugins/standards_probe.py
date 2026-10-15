"""Targeted standards repair check against a root-built candidate, real MySQL and PKCE."""
import hashlib
import json
import os
import shutil
import sys
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'scripts/acceptance/trusted-plugins'))
from environment import Environment, Client, OUT, immutable_candidate
from grants import bootstrap, create_role

PL = '/api/v1/platform/plugins'
ITEM = PL + '/customer-insights'
B = '/api/v1/enterprises/ent-acme'
PERMISSION = 'GET:/api/v1/platform/plugins'
result = {'stage': 'preflight', 'checks': [], 'realHttp': True, 'realMySql': True,
          'realPkce': True, 'legacyCompatibility': False, 'networkMocking': False}
env = None


def save():
    result['counts'] = {'tests': len(result['checks']),
                        'failures': sum(not check['passed'] for check in result['checks']),
                        'errors': int('errorType' in result), 'skipped': 0}
    temporary = OUT / 'result.tmp'
    temporary.write_text(json.dumps(result, indent=2))
    temporary.replace(OUT / 'result.json')


def check(name, passed, **facts):
    result['checks'].append({'name': name, 'passed': bool(passed), **facts})
    save()
    print(name, 'PASS' if passed else 'FAIL', flush=True)


def request(path, method='GET', body=None, expected=200, headers=None):
    status, envelope, response_headers = admin.request(21081, path, method, body,
            headers={'X-Request-Id': 'standards-' + uuid.uuid4().hex, **(headers or {})})
    if status != expected:
        raise AssertionError('setup_http_' + str(status) + '_expected_' + str(expected))
    return envelope.get('data'), response_headers


def binding(denied):
    path = B + '/users/' + env.username + '/roles'
    _, headers = request(path)
    request(path, 'PUT', {'roleIds': [boot, role], 'deniedCapabilityCodes': denied},
            headers={'If-Match': headers['ETag'], 'Idempotency-Key': uuid.uuid4().hex})


def correlated_decision(label, supplied, denied):
    headers = {} if supplied is None else {'X-Request-Id': supplied}
    status, envelope, response_headers = admin.request(21081, PL, headers=headers)
    correlation = response_headers.get('X-Request-Id')
    rows, _ = request(B + '/authorization-audit/' + env.username)
    matching = [row for row in rows if row.get('permissionCode') == PERMISSION]
    audit = matching[-1] if matching else {}
    expected_id = 'correlation_id__sample' if supplied else correlation
    generated = bool(correlation) and (supplied is not None or str(uuid.UUID(correlation)) == correlation)
    code = (envelope.get('error') or {}).get('code')
    passed = (status == (403 if denied else 200) and generated
              and correlation == expected_id == envelope.get('requestId') == audit.get('requestId')
              and audit.get('decision') == ('DENY' if denied else 'ALLOW')
              and (not denied or (code == 'SUMMER_FORBIDDEN' and envelope.get('data') is None)))
    check(label, passed, httpStatus=status, requestId=correlation,
          auditRequestId=audit.get('requestId'), auditDecision=audit.get('decision'), code=code)


def error_request(name, path, body, expected_status, expected_code):
    correlation = 'standards-' + uuid.uuid4().hex
    status, envelope, headers = admin.request(21081, path, 'POST', body,
                                            headers={'X-Request-Id': correlation})
    code = (envelope.get('error') or {}).get('code') if isinstance(envelope, dict) else None
    check(name, status == expected_status and code == expected_code and envelope.get('success') is False
          and envelope.get('data') is None and correlation == headers.get('X-Request-Id') == envelope.get('requestId'),
          httpStatus=status, code=code, requestId=headers.get('X-Request-Id'))


try:
    candidate = immutable_candidate()
    result['candidateSha256'] = hashlib.sha256(candidate.read_bytes()).hexdigest()
    frozen = {path: hashlib.sha256((ROOT / path).read_bytes()).hexdigest() for path in [
        'summer-sample/src/main/java/com/abluepoint/summer/sample/authorization/AuthorizationRequestInterceptor.java',
        'summer-sample/src/main/java/com/abluepoint/summer/sample/plugin/PluginController.java',
        'summer-sample/src/main/java/com/abluepoint/summer/sample/plugin/PluginErrorCode.java']}
    candidate_manifest = Path(os.environ['SUMMER_STANDARDS_CANDIDATE_INPUTS']).resolve()
    inputs = json.loads(candidate_manifest.read_text())
    if any(inputs.get(path) != sha or hashlib.sha256((ROOT / path).read_bytes()).hexdigest() != sha
           for path, sha in frozen.items()):
        raise RuntimeError('candidate_source_manifest_mismatch')
    result['mainInputsMatchCandidate'] = True
    result['candidateInputManifest'] = str(candidate_manifest)
    archive = OUT / 'executed-scripts'
    archive.mkdir(exist_ok=False)
    for source in [Path(__file__), ROOT / 'scripts/acceptance/trusted-plugins/environment.py',
                   ROOT / 'scripts/acceptance/trusted-plugins/grants.py']:
        shutil.copy2(source, archive / source.name)
    env = Environment()
    env.setup()
    env.start(21081, jar=candidate)
    admin = Client().login(21081, env.username, env.password)
    boot = bootstrap(admin, env.username)
    admin = Client().login(21081, env.username, env.password)
    ids = []
    for code in ['platform.plugins.read', 'platform.plugins.write']:
        capability, _ = request(B + '/capabilities', 'POST', {
            'code': code, 'resource': 'plugins', 'action': code.rsplit('.', 1)[1],
            'scope': 'PLATFORM', 'riskLevel': 'HIGH'}, 201,
            {'Idempotency-Key': uuid.uuid4().hex})
        ids.append(capability['capabilityId'])
    methods = [PERMISSION, 'POST:/api/v1/platform/plugins',
               'POST:/api/v1/platform/plugins/{pluginId}:enable',
               'POST:/api/v1/platform/plugins/{pluginId}:upgrade']
    role = create_role(admin, 'ent-acme', ids + methods, 'Standards focused plugin operator')
    binding([])
    admin = Client().login(21081, env.username, env.password)
    result['stage'] = 'correlation'
    correlated_decision('allow_without_request_header', None, False)
    correlated_decision('allow_with_normalized_request_header', 'correlation id:/sample', False)
    binding([PERMISSION])
    correlated_decision('deny_existing_token_without_request_header', None, True)
    correlated_decision('deny_existing_token_with_normalized_request_header', 'correlation id:/sample', True)
    binding([])
    result['stage'] = 'plugin_errors'
    error_request('invalid_plugin_uses_sample_namespace', PL,
                  {'pluginId': 'INVALID', 'version': '', 'artifactDigest': 'sha256:customer-insights-v1'},
                  400, 'SAMPLE_PLUGIN_INVALID')
    error_request('untrusted_plugin_uses_sample_namespace', PL,
                  {'pluginId': 'customer-insights', 'version': '1.0.0', 'artifactDigest': 'sha256:untrusted'},
                  409, 'SAMPLE_PLUGIN_UNTRUSTED')
    request(PL, 'POST', {'pluginId': 'customer-insights', 'version': '9.0.0',
                        'artifactDigest': 'sha256:customer-insights-v1'}, 201)
    error_request('incompatible_plugin_uses_sample_namespace', ITEM + ':enable', None,
                  409, 'SAMPLE_PLUGIN_INCOMPATIBLE')
    env.sql("CREATE TRIGGER standards_plugin_failure BEFORE UPDATE ON summer_plugin FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='controlled plugin write failure'", env.schema)
    try:
        error_request('unexpected_plugin_failure_keeps_framework_namespace', ITEM + ':upgrade',
                      {'version': '1.0.0', 'artifactDigest': 'sha256:customer-insights-v1'},
                      500, 'SUMMER_INTERNAL_ERROR')
    finally:
        env.sql('DROP TRIGGER standards_plugin_failure', env.schema)
    recovered, _ = request(ITEM + ':upgrade', 'POST',
                           {'version': '1.0.0', 'artifactDigest': 'sha256:customer-insights-v1'})
    check('same_node_recovers_after_controlled_write_failure', recovered['version'] == '1.0.0')
    result['stage'] = 'complete'
except Exception as failure:
    result['errorType'] = type(failure).__name__
    if isinstance(failure, AssertionError):
        result['safeAssertion'] = str(failure)
finally:
    if env is not None:
        try:
            env.close()
            result['cleanup'] = 'VERIFIED'
        except Exception as failure:
            result['cleanupErrorType'] = type(failure).__name__
        result['sensitiveLogDetected'] = env.sensitive_log_detected
    result['mainInputsStable'] = 'frozen' in globals() and all(
        hashlib.sha256((ROOT / path).read_bytes()).hexdigest() == sha for path, sha in frozen.items())
    save()

print(json.dumps({key: result.get(key) for key in ['stage', 'counts', 'cleanup', 'errorType',
                                                'mainInputsStable', 'sensitiveLogDetected']}))
sys.exit(0 if result['stage'] == 'complete' and result['counts']['failures'] == 0
         and result['counts']['errors'] == 0 and result.get('cleanup') == 'VERIFIED'
         and result['mainInputsStable'] and not result.get('sensitiveLogDetected') else 1)
