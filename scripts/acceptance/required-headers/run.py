"""Actual missing-header response regression, using T26 disposable infrastructure."""
import hashlib
import json
from pathlib import Path
import sys
import uuid

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'scripts/acceptance/task-configuration'))
from environment import Client as BaseClient, Environment, OUT, immutable_candidate
sys.path.insert(0, str(ROOT / 'scripts/acceptance/identity'))
from grants import bootstrap

result = {'stage': 'setup', 'checks': [], 'networkMocking': False, 'businessSqlWrites': False}
env = None


class Client(BaseClient):
    def request(self, port, *args, **kwargs):
        return super().request(26081 if port == 21081 else port, *args, **kwargs)


try:
    candidate = immutable_candidate()
    result['candidateSha256'] = hashlib.sha256(candidate.read_bytes()).hexdigest()
    env = Environment()
    env.setup()
    env.start(26081, jar=candidate)
    admin = Client().login(26081, env.username, env.password)
    bootstrap(admin, env.username)
    admin = Client().login(26081, env.username, env.password)
    status, _, _ = admin.request(26081, '/api/users', 'POST', {
        'username': env.denied_username, 'password': env.denied_password, 'role': 'USER'})
    assert status == 200, 'identity_setup_failed'
    result['stage'] = 'missing_required_header'
    body = {'subject': env.denied_username, 'displayName': 'Required header regression', 'email': 'header-reader@example.test'}
    rid = 'missing-header-' + uuid.uuid4().hex
    status, reply, headers = admin.request(26081, '/api/v1/enterprises/ent-acme/members', 'POST', body,
                                           headers={'X-Request-Id': rid})
    facts = {'status': status, 'code': (reply.get('error') or {}).get('code'),
             'correlationMatches': reply.get('requestId') == headers.get('X-Request-Id') == rid,
             'fields': [v.get('field') for v in (reply.get('error') or {}).get('violations', [])]}
    result['missingHeaderResponse'] = facts
    passed = status == 400 and facts['code'] == 'SUMMER_INVALID_ARGUMENT' and facts['correlationMatches'] and 'Idempotency-Key' in facts['fields']
    result['checks'].append({'name': 'missing_idempotency_header_is_correlated_bad_request', 'passed': passed})
    assert passed, 'missing_header_contract_failed'
    status, reply, headers = admin.request(26081, '/api/v1/enterprises/ent-acme/members', 'POST', body,
                                           headers={'X-Request-Id': rid, 'Idempotency-Key': uuid.uuid4().hex})
    passed = status == 201 and reply.get('requestId') == headers.get('X-Request-Id') == rid
    result['checks'].append({'name': 'same_valid_request_with_required_header_is_created', 'passed': passed})
    assert passed, 'present_header_request_failed'
    result['stage'] = 'complete'
except Exception as error:
    result['failureType'] = type(error).__name__
finally:
    if env:
        result['sensitiveLogDetected'] = env.sensitive_log_detected
        try:
            env.close()
            result['cleanup'] = 'VERIFIED'
        except Exception as error:
            result['cleanup'] = 'FAILED'
            result['cleanupFailureType'] = type(error).__name__
    result['counts'] = {'tests': len(result['checks']), 'failures': sum(not c['passed'] for c in result['checks']),
                        'errors': int('failureType' in result), 'skipped': 0}
    result['passed'] = result['stage'] == 'complete' and result.get('cleanup') == 'VERIFIED' and not result.get('sensitiveLogDetected')
    (OUT / 'header-result.json').write_text(json.dumps(result, indent=2))
    print(json.dumps(result), flush=True)
    sys.exit(0 if result['passed'] else 1)
