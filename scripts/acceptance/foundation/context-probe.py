"""T04 real API + browser replay with a persisted ACTIVE member/organization assignment."""
import json, os, subprocess
from environment import Environment, Client, OUT, ROOT, SCRIPT, immutable_candidate
from strict_fixtures import Fixtures, literal

env = None
ui = None
result = {'checks': []}

def check(name, condition, **facts):
    result['checks'].append({'name': name, 'passed': bool(condition), **facts})
    (OUT / 'organization-audit-context-probe.json').write_text(json.dumps(result, indent=2))

try:
    candidate = immutable_candidate()
    env = Environment()
    env.setup()
    env.start(20081, jar=candidate)
    fixture = Fixtures(env).seed()
    for capability in ('enterprise.organization.read', 'enterprise.organization.write',
                       'enterprise.organizations.read', 'enterprise.organizations.write'):
        fixture.grant(capability)
    fixture.route('GET', '/api/v1/enterprises/{enterpriseId}/org-tree')
    fixture.route('PUT', '/api/v1/enterprises/{enterpriseId}/context')
    fixture.route('PUT', '/api/v1/enterprises/{enterpriseId}/members/{memberId}/org-assignments')
    admin = Client().login(20081, env.username, env.password)
    name = 'foundation' + env.run
    path = '/api/customers/' + name
    status, _, _ = admin.request(20081, '/api/customers', 'POST',
                                {'username': name, 'email': name + '@example.test', 'displayName': 'Before context'})
    assert status == 200
    status, events, _ = admin.request(20081, '/api/audit-events?resource=customer:' + name)
    check('unselected_context_is_null', status == 200 and events['data']['items'][0]['organizationId'] is None)
    member_id = env.sql("SELECT member_id FROM summer_enterprise_member WHERE enterprise_id='ent-acme' AND status='ACTIVE' AND subject=" + literal(env.username), env.schema)
    assert member_id and '\n' not in member_id, 'one_active_persisted_member_required'
    status, tree, headers = admin.request(20081, '/api/v1/enterprises/ent-acme/org-tree')
    assert status == 200
    status, _, _ = admin.request(20081, '/api/v1/enterprises/ent-acme/members/' + member_id + '/org-assignments', 'PUT',
                                {'organizationIds': ['org-engineering'], 'primaryOrganizationId': 'org-engineering'},
                                {'If-Match': headers['ETag'], 'Idempotency-Key': 'context-assignment-' + env.run})
    assert status == 200, 'real_assignment_status_' + str(status)
    status, _, _ = admin.request(20081, '/api/v1/enterprises/ent-acme/context', 'PUT', {'organizationId': 'org-engineering'})
    assert status == 200, 'real_context_status_' + str(status)
    # Re-create after selecting context so every required lifecycle action has the selected org.
    assert admin.request(20081, path, 'DELETE')[0] == 204
    writes = [('POST', '/api/customers', {'username': name, 'email': name + '@example.test', 'displayName': 'Selected context'}),
              ('PATCH', path, {'displayName': 'Edited context'}), ('PATCH', path, {'active': False}),
              ('PATCH', path, {'active': True})]
    expected_requests = set()
    for index, (method, endpoint, body) in enumerate(writes):
        request_id = 'context-' + env.run + '-' + str(index)
        expected_requests.add(request_id)
        assert admin.request(20081, endpoint, method, body, {'X-Request-Id': request_id})[0] == 200
    for index in range(25):
        assert admin.request(20081, path, 'PATCH', {'displayName': 'Pagination ' + str(index)})[0] == 200
    request_id = 'context-' + env.run + '-delete'
    expected_requests.add(request_id)
    assert admin.request(20081, path, 'DELETE', headers={'X-Request-Id': request_id})[0] == 204
    status, events, _ = admin.request(20081, '/api/audit-events?resource=customer:' + name + '&size=100')
    required = [event for event in events['data']['items'] if event['requestId'] in expected_requests]
    check('five_actions_preserve_selected_organization', len(required) == 5 and all(event['organizationId'] == 'org-engineering' for event in required),
          actual_count=len(required), observed_organizations=[event['organizationId'] for event in required])
    check('principal_tenant_requestid_match', len(required) == 5 and all(event['principal'] == env.username and event['tenantId'] == 'ent-acme' for event in required))
    selected_event = required[0]
    for method in ('PATCH', 'DELETE'):
        status, body, headers = admin.request(20081, '/api/audit-events/' + selected_event['eventId'], method,
                                             {'result': 'FAILURE'} if method == 'PATCH' else None)
        check('audit_' + method.lower() + '_405', status == 405 and body['error']['code'].startswith('SUMMER_')
              and body['requestId'] == headers.get('X-Request-Id') and 'GET' in headers.get('Allow', ''), http_status=status)
    status, body, _ = admin.request(20081, '/api/audit-events/' + selected_event['eventId'])
    check('unsupported_methods_did_not_mutate_event', status == 200 and body['data']['result'] == 'SUCCESS')
    child = os.environ.copy()
    child.update({'SUMMER_E2E_USERNAME': env.username, 'SUMMER_E2E_PASSWORD': env.password,
                  'SUMMER_E2E_RUN': env.run, 'SUMMER_E2E_EXPECTED_ORG': 'org-engineering'})
    node = os.environ.get('SUMMER_FOUNDATION_NODE', '/opt/homebrew/opt/node@22/bin/node')
    ui = subprocess.Popen([node, str(SCRIPT / 'ui-server.mjs')], cwd=ROOT / 'summer-ui', env=child,
                          stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
    assert ui.stdout.readline().strip() == 'UI_READY'
    browser = subprocess.run([node, str(SCRIPT / 'browser.mjs'), 'audit'], cwd=ROOT / 'summer-ui', env=child,
                             capture_output=True, text=True, timeout=160)
    check('selected_context_audit_browser', browser.returncode == 0)
except Exception as error:
    result['failure_type'] = type(error).__name__
    if isinstance(error, AssertionError):
        result['safe_assertion'] = str(error)
finally:
    if ui and ui.poll() is None:
        ui.terminate()
        try:
            ui.wait(timeout=10)
        except subprocess.TimeoutExpired:
            ui.kill()
            ui.wait()
    if env:
        env.close()
        result['cleanup'] = 'VERIFIED'
    (OUT / 'organization-audit-context-probe.json').write_text(json.dumps(result, indent=2))
print(json.dumps(result))
raise SystemExit(0 if len(result['checks']) == 7 and all(check['passed'] for check in result['checks']) and result.get('cleanup') == 'VERIFIED' else 1)
