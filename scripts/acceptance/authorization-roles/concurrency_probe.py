"""Real two-node CAS regression with persisted mutation audit/outbox and restart checks."""
import concurrent.futures, json, secrets, threading
from environment import Environment, Client, OUT, immutable_candidate
from grants import BASE, bootstrap, headers, data

env = None
result = {'tests': 0, 'failures': 0, 'errors': 0, 'skipped': 0, 'checks': [], 'mode': 'real-mysql-two-node-api-bootstrap'}
def check(name, valid, **evidence):
    result['checks'].append({'name': name, 'status': 'passed' if valid else 'failed', **evidence})
    result['tests'] += 1
    result['failures'] += not valid

def read(client, path, port=23081):
    status, body, _ = client.request(port, path)
    if status != 200: raise RuntimeError('required_read_failed')
    return data(body)

def mutation_events(client, resource_type, resource_id, port=23081):
    return [r for r in read(client, '/api/authorization-events?enterpriseId=ent-acme&resourceType='+resource_type, port)['items'] if r['resourceId'] == resource_id]

try:
    candidate = immutable_candidate()
    env = Environment(); env.setup(); env.start(23081, jar=candidate)
    admin = Client().login(23081, env.username, env.password)
    bootstrap(admin, env.username)
    admin = Client().login(23081, env.username, env.password)
    env.additional_secrets.append(admin.token)
    status, body, _ = admin.request(23081, BASE+'/roles', 'POST', {'name': 'Concurrent role', 'code': 't15-concurrent-minimal', 'capabilityIds': []}, headers=headers())
    assert status == 201
    role = data(body)
    env.start(23082, jar=candidate)
    admin2 = Client().login(23082, env.username, env.password)
    env.additional_secrets.append(admin2.token)
    barrier = threading.Barrier(2)
    def update(client, port, name):
        request_headers = headers(role['version'])
        barrier.wait(timeout=10)
        status, body, response = client.request(port, BASE+'/roles/'+role['roleId'], 'PATCH', {'name': name}, headers=request_headers)
        return {'node': port, 'name': name, 'status': status, 'ifMatch': request_headers['If-Match'], 'returnedVersion': data(body).get('version') if status == 200 else None, 'code': (body.get('error') or {}).get('code'), 'requestId': body.get('requestId'), 'headerRequestId': response.get('X-Request-Id')}
    with concurrent.futures.ThreadPoolExecutor(2) as pool:
        futures = [pool.submit(update, admin, 23081, 'Writer A'), pool.submit(update, admin2, 23082, 'Writer B')]
        result['writes'] = [f.result() for f in futures]
    writes = result['writes']
    check('two_nodes_same_version_one_200_one_412', sorted(r['status'] for r in writes) == [200, 412])
    check('conflict_contract_and_request_correlation', all(r['requestId'] and r['requestId'] == r['headerRequestId'] for r in writes) and any(r['status'] == 412 and r['code'] == 'SUMMER_PRECONDITION_FAILED' for r in writes))
    winner = next(r for r in writes if r['status'] == 200)
    snapshot = read(admin, BASE+'/roles/'+role['roleId'])
    second = read(admin2, BASE+'/roles/'+role['roleId'], 23082)
    result['readback'] = {k: snapshot[k] for k in ['name', 'version']}
    check('both_nodes_read_only_winning_version', snapshot == second and snapshot['version'] == 2 and snapshot['name'] == winner['name'])
    mutations = [r for r in read(admin, BASE+'/authorization-audit/'+role['roleId']) if r['action'] == 'ROLE_UPDATED']
    result['mutation_audit'] = mutations
    check('one_persisted_mutation_audit_for_winner', len(mutations) == 1 and mutations[0]['requestId'] == winner['requestId'])
    changes = [r for r in read(admin, '/api/authorization-events?enterpriseId=ent-acme&resourceType=ROLE')['items'] if r['resourceId'] == role['roleId'] and r['eventType'] == 'ROLE_UPDATED']
    result['outbox'] = [{k: r[k] for k in ['eventId', 'resourceId', 'eventType', 'version', 'requestId', 'status']} for r in changes]
    check('one_persisted_outbox_event_for_winner', len(changes) == 1 and changes[0]['requestId'] == winner['requestId'] and changes[0]['version'] == 2)

    # updateRole claims the DB version before validating name. A real invalid HTTP
    # write therefore exercises rollback after the CAS, without injecting a fixture.
    invalid_headers = headers(2)
    invalid_status, invalid_body, _ = admin.request(23081, BASE+'/roles/'+role['roleId'], 'PATCH', {'name': '   '}, headers=invalid_headers)
    check('invalid_role_write_returns_domain_400', invalid_status == 400 and (invalid_body.get('error') or {}).get('code') == 'SUMMER_AUTHORIZATION_INVALID')
    invalid_id = invalid_body.get('requestId')
    after_invalid = read(admin, BASE+'/roles/'+role['roleId'])
    after_invalid_second = read(admin2, BASE+'/roles/'+role['roleId'], 23082)
    check('failed_transaction_rolls_back_claimed_version_on_both_nodes', after_invalid == snapshot and after_invalid_second == snapshot)
    check('failed_transaction_has_no_mutation_audit_or_outbox', not any(r['requestId'] == invalid_id for r in read(admin, BASE+'/authorization-audit/'+role['roleId'])) and not any(r['requestId'] == invalid_id for r in mutation_events(admin, 'ROLE', role['roleId'])))
    retry_headers = headers(2)
    retry_headers['Idempotency-Key'] = invalid_headers['Idempotency-Key']
    retry_status, retry_body, _ = admin.request(23081, BASE+'/roles/'+role['roleId'], 'PATCH', {'name': 'Committed retry'}, headers=retry_headers)
    check('rolled_back_key_can_commit_without_ghost_receipt', retry_status == 200 and data(retry_body).get('version') == 3 and data(retry_body).get('name') == 'Committed retry')
    result['rollback'] = {'invalid_status': invalid_status, 'invalid_requestId': invalid_id, 'version_after_invalid': after_invalid['version'], 'retry_status': retry_status, 'retry_requestId': retry_body.get('requestId'), 'version_after_retry': data(retry_body).get('version')}
    snapshot = read(admin, BASE+'/roles/'+role['roleId'])
    mutations = [r for r in read(admin, BASE+'/authorization-audit/'+role['roleId']) if r['action'] == 'ROLE_UPDATED']

    username = 't15-concurrent-member'
    password = secrets.token_urlsafe(24); env.additional_secrets.append(password)
    status, _, _ = admin.request(23081, '/api/users', 'POST', {'username': username, 'password': password, 'role': 'USER'})
    assert status == 200
    status, _, _ = admin.request(23081, BASE+'/members', 'POST', {'subject': username, 'displayName': 'T15 concurrency member', 'email': username+'@example.test'}, headers=headers())
    assert status == 201
    status, _, _ = admin.request(23081, BASE+'/users/'+username+'/roles', 'PUT', {'roleIds': [role['roleId']], 'deniedCapabilityCodes': []}, headers=headers(1))
    assert status == 200
    binding_barrier = threading.Barrier(2)
    def update_binding(client, port, denied):
        h = headers(1); binding_barrier.wait(timeout=10)
        status, body, response = client.request(port, BASE+'/users/'+username+'/roles', 'PUT', {'roleIds': [role['roleId']], 'deniedCapabilityCodes': [denied]}, headers=h)
        return {'node': port, 'status': status, 'submitted_version': 1, 'denied': denied, 'returned_version': data(body).get('version') if status == 200 else None, 'code': (body.get('error') or {}).get('code'), 'requestId': body.get('requestId'), 'headerRequestId': response.get('X-Request-Id')}
    with concurrent.futures.ThreadPoolExecutor(2) as pool:
        futures = [pool.submit(update_binding, admin, 23081, 'GET:/api/customers'), pool.submit(update_binding, admin2, 23082, 'POST:/api/customers')]
        binding_writes = [f.result() for f in futures]
    result['binding_writes'] = binding_writes
    check('two_nodes_binding_same_version_one_200_one_412', sorted(r['status'] for r in binding_writes) == [200, 412])
    check('binding_conflict_contract_and_correlation', all(r['requestId'] and r['requestId'] == r['headerRequestId'] for r in binding_writes) and any(r['status'] == 412 and r['code'] == 'SUMMER_PRECONDITION_FAILED' for r in binding_writes))
    binding_winner = next(r for r in binding_writes if r['status'] == 200)
    binding_snapshot = read(admin, BASE+'/users/'+username+'/roles')
    check('both_nodes_read_only_winning_binding', binding_snapshot == read(admin2, BASE+'/users/'+username+'/roles', 23082) and binding_snapshot['version'] == 2 and binding_snapshot['deniedCapabilityCodes'] == [binding_winner['denied']])
    winner_ids = {r['requestId'] for r in binding_writes}
    binding_audit = [r for r in read(admin, BASE+'/authorization-audit/'+username) if r['action'] == 'USER_ROLES_UPDATED' and r['requestId'] in winner_ids]
    binding_events = [r for r in mutation_events(admin, 'USER_BINDING', username) if r['requestId'] in winner_ids]
    result['binding_audit'] = binding_audit
    result['binding_outbox'] = [{k:r[k] for k in ['eventId', 'resourceId', 'eventType', 'version', 'requestId', 'status']} for r in binding_events]
    check('binding_only_winner_has_mutation_audit_and_outbox', len(binding_audit) == 1 and binding_audit[0]['requestId'] == binding_winner['requestId'] and len(binding_events) == 1 and binding_events[0]['requestId'] == binding_winner['requestId'] and binding_events[0]['version'] == 2)
    env.stop(23082); env.start(23082, jar=candidate)
    restarted = Client().login(23082, env.username, env.password)
    env.additional_secrets.append(restarted.token)
    restored = read(restarted, BASE+'/roles/'+role['roleId'], 23082)
    restored_audit = [r for r in read(restarted, BASE+'/authorization-audit/'+role['roleId'], 23082) if r['action'] == 'ROLE_UPDATED']
    check('winning_role_and_audit_survive_node_restart', restored == snapshot and restored_audit == mutations)
    restored_binding = read(restarted, BASE+'/users/'+username+'/roles', 23082)
    check('winning_binding_survives_node_restart', restored_binding == binding_snapshot)
    check('runtime_logs_no_known_secret_values', not env.sensitive_log_detected)
    result['status'] = 'RED' if result['failures'] else 'PASSED'
except Exception as error:
    result.update(status='ERROR', error_type=type(error).__name__); result['errors'] += 1
finally:
    if env:
        try: env.close(); result['cleanup'] = 'VERIFIED'
        except Exception as error: result.update(cleanup='FAILED', cleanup_error=type(error).__name__); result['errors'] += 1
        result['sensitive_log_detected'] = env.sensitive_log_detected
    (OUT/'concurrency-result.json').write_text(json.dumps(result, indent=2)+'\n')
    print(json.dumps({k: result.get(k) for k in ['status', 'tests', 'failures', 'errors', 'cleanup']}), flush=True)
raise SystemExit(0 if result.get('status') == 'PASSED' and result.get('cleanup') == 'VERIFIED' else 1)
