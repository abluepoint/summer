"""Narrow statistics error-contract check: root candidate, real MySQL and strict PKCE."""
import hashlib
import json
import os
import runpy
import shutil
import sys
import uuid
from pathlib import Path
from urllib.parse import urlencode

from environment import Client as BaseClient, Environment, OUT, ROOT, immutable_candidate

PORT = 20081
BASE = '/api/v1/enterprises/ent-acme'
STATS = BASE + '/statistics'
ROUTE = 'GET:/api/v1/enterprises/{enterpriseId}/statistics'
result = {'issue': 142, 'stage': 'preflight', 'checks': [], 'realHttp': True,
          'realMySql': True, 'realPkce': True, 'legacyCompatibility': False,
          'networkMocking': False, 'businessSqlWrites': False}
env = None
before = {}


class Client(BaseClient):
    def request(self, port, *args, **kwargs):
        return super().request(PORT, *args, **kwargs)


def save():
    result['counts'] = {'tests': len(result['checks']),
                        'failures': sum(not item['passed'] for item in result['checks']),
                        'errors': int('errorType' in result) + int('cleanupErrorType' in result),
                        'skipped': 0}
    temporary = OUT / 'result.tmp'
    temporary.write_text(json.dumps(result, indent=2) + '\n')
    temporary.replace(OUT / 'result.json')


def call(path, method='GET', body=None, expected=200, headers=None):
    status, body, response_headers = admin.request(PORT, path, method, body,
        headers={'X-Request-Id': 'statistics-setup-' + uuid.uuid4().hex,
                 'Idempotency-Key': uuid.uuid4().hex, **(headers or {})})
    if status != expected:
        raise AssertionError('setup_http_' + str(status) + '_expected_' + str(expected))
    return body['data'], response_headers


def verify(name, parameters, expected_status=400, supplied_id='generated'):
    # "generated" is this script's sentinel for omitting the request header.
    headers = {} if supplied_id == 'generated' else {'X-Request-Id': supplied_id}
    status, body, response_headers = admin.request(PORT, STATS + '?' + urlencode(parameters), headers=headers)
    request_id = response_headers.get('X-Request-Id')
    generated = False
    if supplied_id == 'generated':
        try:
            generated = str(uuid.UUID(request_id)) == request_id
        except (TypeError, ValueError, AttributeError):
            pass
    expected_id = 'statistics_invalid__window' if supplied_id == 'statistics invalid:/window' else supplied_id
    same_id = bool(request_id) and isinstance(body, dict) and body.get('requestId') == request_id
    valid_id = generated if supplied_id == 'generated' else request_id == expected_id
    code = (body.get('error') or {}).get('code') if isinstance(body, dict) else None
    envelope_valid = isinstance(body, dict) and body.get('success') == (expected_status < 400)
    contract = (code == 'SUMMER_INVALID_ARGUMENT' and body.get('data') is None) if expected_status == 400 else code is None and body.get('data') is not None
    passed = status == expected_status and same_id and valid_id and envelope_valid and contract
    result['checks'].append({'name': name, 'passed': bool(passed), 'httpStatus': status,
                             'code': code, 'requestId': request_id, 'sameRequestId': same_id,
                             'requestIdMatchesContract': valid_id})
    save()
    print(name, 'PASS' if passed else 'FAIL', flush=True)


try:
    candidate = immutable_candidate()
    result['candidateSha256'] = hashlib.sha256(candidate.read_bytes()).hexdigest()
    manifest = Path(os.environ['SUMMER_T34_STANDARDS_CANDIDATE_INPUTS']).resolve()
    inputs = json.loads(manifest.read_text())
    tracked = [*sorted((ROOT / 'summer-sample/src/main/java/com/abluepoint/summer/sample/statistics').glob('*.java')),
               Path(__file__).resolve(), Path(__file__).parent / 'environment.py',
               ROOT / 'scripts/acceptance/identity/grants.py']
    before = {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in tracked}
    if any(inputs.get(path) != digest for path, digest in before.items() if '/src/main/' in path):
        raise RuntimeError('candidate_statistics_source_mismatch')
    result['candidateInputManifest'] = str(manifest)
    result['mainInputsMatchCandidate'] = True
    (OUT / 'inputs-before.json').write_text(json.dumps(before, indent=2) + '\n')
    archive = OUT / 'executed-scripts'
    archive.mkdir(exist_ok=False)
    for p in tracked:
        if p.suffix == '.py':
            shutil.copy2(p, archive / p.name)
    grants = runpy.run_path(str(ROOT / 'scripts/acceptance/identity/grants.py'))
    env = Environment()
    env.setup()
    env.start(PORT, jar=candidate)
    admin = Client().login(PORT, env.username, env.password)
    env.additional_secrets.append(admin.token)
    boot = grants['bootstrap'](admin, env.username)
    admin = Client().login(PORT, env.username, env.password)
    env.additional_secrets.append(admin.token)
    catalog, _ = call(BASE + '/capabilities/catalog')
    known = {item['code']: item['capabilityId'] for item in catalog['capabilities']}
    if 'enterprise.statistics.read' not in known:
        capability, _ = call(BASE + '/capabilities', 'POST',
            {'code': 'enterprise.statistics.read', 'resource': 'enterprise.statistics',
             'action': 'read', 'riskLevel': 'LOW', 'scope': 'ENTERPRISE'}, expected=201)
        known['enterprise.statistics.read'] = capability['capabilityId']
    role = grants['create_role'](admin, 'ent-acme', [known['enterprise.statistics.read'], ROUTE],
                                 'Statistics standards regression')
    _, headers = call(BASE + '/users/' + env.username + '/roles')
    call(BASE + '/users/' + env.username + '/roles', 'PUT',
         {'roleIds': [boot, role], 'deniedCapabilityCodes': []}, headers={'If-Match': headers['ETag']})
    admin = Client().login(PORT, env.username, env.password)
    env.additional_secrets.append(admin.token)
    result['stage'] = 'targeted_contract'
    verify('reversed_window_uses_common_invalid_argument',
           {'from': '2026-01-02T00:00:00Z', 'to': '2026-01-01T00:00:00Z'})
    verify('overlong_window_uses_common_invalid_argument',
           {'from': '2024-01-01T00:00:00Z', 'to': '2026-01-01T00:00:00Z'},
           supplied_id='statistics invalid:/window')
    verify('invalid_iso_date_uses_common_invalid_argument',
           {'from': 'not-an-instant', 'to': '2026-01-01T00:00:00Z'}, supplied_id='statistics-date')
    verify('unknown_parameter_stays_field_contract_error', {'unexpected': '1'}, supplied_id='statistics-unknown')
    verify('overlong_date_stays_field_contract_error', {'from': 'x' * 65}, supplied_id='statistics-length')
    verify('exact_366_day_window_is_accepted',
           {'from': '2024-01-01T00:00:00Z', 'to': '2025-01-01T00:00:00Z'},
           expected_status=200, supplied_id='statistics-boundary')
    result['stage'] = 'complete'
except Exception as error:
    result['errorType'] = type(error).__name__
    print('error_type', result['errorType'], flush=True)
finally:
    if env:
        result['sensitiveLogDetected'] = env.sensitive_log_detected
        try:
            env.close()
            result['cleanup'] = 'VERIFIED'
        except Exception as cleanup_error:
            result['cleanupErrorType'] = type(cleanup_error).__name__
            result['cleanup'] = 'FAILED'
    result['changedInputs'] = [path for path, digest in before.items()
                              if hashlib.sha256((ROOT / path).read_bytes()).hexdigest() != digest]
    result['passed'] = (result['stage'] == 'complete' and not result.get('errorType')
                        and not result.get('cleanupErrorType') and not result.get('changedInputs')
                        and not result.get('sensitiveLogDetected') and result.get('cleanup') == 'VERIFIED'
                        and all(item['passed'] for item in result['checks']) and len(result['checks']) == 6)
    save()
    print(json.dumps({'passed': result['passed'], 'counts': result['counts'],
                      'cleanup': result.get('cleanup')}), flush=True)
raise SystemExit(0 if result['passed'] else 1)
