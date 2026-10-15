"""Narrow real HTTP supplement for the label-parser false positive in T33."""
import hashlib, json, re, runpy, uuid
from pathlib import Path
from environment import Environment, Client, OUT, ROOT, immutable_candidate

PORT = 20081
RUNTIME = '/internal/v1/diagnostics/runtime'
DENIED_PARTS = {'trace', 'traceid', 'request', 'requestid', 'user', 'userid', 'username',
                'principal', 'token', 'secret', 'password', 'url', 'uri', 'path', 'tenant', 'key'}
result = {'issue': 141, 'checks': [], 'networkMocking': False, 'businessSqlWrites': False}
env = None

def check(name, passed, **facts):
    result['checks'].append({'name': name, 'passed': bool(passed), **facts})
    print(name, 'PASS' if passed else 'FAIL', flush=True)

def forbidden(key):
    return bool(set(key.lower().split('_')) & DENIED_PARTS)

class LocalClient(Client):
    def request(self, port, *args, **kwargs):
        return super().request(PORT, *args, **kwargs)

frozen = json.loads((ROOT/'.git/summer4-closeout-20261015/frontend/t33-141/SOURCE_FROZEN.json').read_text())
before = {path: hashlib.sha256((ROOT/path).read_bytes()).hexdigest() for path in frozen}
(OUT/'inputs-before.json').write_text(json.dumps(before, indent=2)+'\n')
try:
    candidate = immutable_candidate()
    result['candidate_sha256'] = hashlib.sha256(candidate.read_bytes()).hexdigest()
    candidate_inputs = json.loads((candidate.parent/'candidate-inputs.json').read_text())
    assert before == frozen and all(candidate_inputs.get(path) == sha for path, sha in frozen.items())
    check('parser_accepts_security_names_and_rejects_sensitive_dimensions',
          not forbidden('spring_security_authentication_type') and
          all(forbidden(key) for key in ['uri', 'http_uri', 'trace_id', 'request_id', 'username', 'client_secret']))
    env = Environment(); env.setup(); env.start(PORT, jar=candidate)
    admin = LocalClient().login(PORT, env.username, env.password)
    env.additional_secrets.append(admin.token)
    runpy.run_path(str(ROOT/'scripts/acceptance/identity/grants.py'))['bootstrap'](admin, env.username)
    admin = LocalClient().login(PORT, env.username, env.password)
    env.additional_secrets.append(admin.token)
    rid = 't33-label-'+uuid.uuid4().hex; trace = uuid.uuid4().hex
    code, data, headers = admin.request(PORT, RUNTIME, headers={
        'X-Request-Id': rid, 'traceparent': '00-'+trace+'-'+uuid.uuid4().hex[:16]+'-01'})
    check('actual_correlated_request_generates_http_metrics', code == 200 and
          data.get('requestId') == rid and headers.get('X-Trace-Id') == trace)
    code, text, headers = admin.request(PORT, '/actuator/prometheus')
    check('actual_prometheus_raw_export', code == 200 and isinstance(text, str) and
          'http_server_requests_seconds_count' in text)
    assert isinstance(text, str)
    keys = {key for labels in re.findall(r'\{([^}]+)\}', text) for key in re.findall(r'(\w+)=', labels)}
    blocked = sorted(key for key in keys if forbidden(key))
    identity_matches = [name for name, value in {'username': env.username, 'reader': env.denied_username,
                        'requestId': rid, 'traceId': trace}.items() if value in text]
    check('metric_dimensions_have_no_request_or_identity_values', not blocked and not identity_matches,
          blockedLabelNames=blocked, identityMatchKinds=identity_matches)
    check('prometheus_export_excludes_credentials', not any(value and value in text for value in
          [env.password, env.denied_password, env.mysql, env.master, env.db_password, admin.token]))
    result['prometheusTagNames'] = sorted(keys)
    result['complete'] = True
except Exception as error:
    result['fatalClass'] = type(error).__name__
    print('fatal', type(error).__name__, flush=True)
finally:
    if env:
        env.close()
    result['changedInputs'] = [path for path, sha in before.items()
                               if hashlib.sha256((ROOT/path).read_bytes()).hexdigest() != sha]
    result['counts'] = {'tests': len(result['checks']),
                        'failures': sum(not check['passed'] for check in result['checks']),
                        'errors': int('fatalClass' in result), 'skipped': 0}
    result['passed'] = result.get('complete', False) and not result['changedInputs'] and \
                       not result['counts']['failures'] and not result['counts']['errors']
    (OUT/'result.json').write_text(json.dumps(result, indent=2)+'\n')
    print('counts', json.dumps(result['counts']), flush=True)
raise SystemExit(0 if result['passed'] else 1)
