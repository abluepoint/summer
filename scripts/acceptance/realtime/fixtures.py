import uuid
from grants import bootstrap, create_role

def prepare(env, candidate, Client, call):
    env.start(19081, jar=candidate)
    admin = Client().login(19081, env.username, env.password)
    base_role = bootstrap(admin, env.username)
    admin = Client().login(19081, env.username, env.password)
    catalog, _ = call(admin, '/api/v1/enterprises/ent-acme/capabilities/catalog')
    known = {row['code']: row['capabilityId'] for row in catalog['capabilities']}
    codes = ['enterprise.messages.read', 'enterprise.messages.write', 'GET:/api/v1/enterprises/{enterpriseId}/messages', 'POST:/api/v1/enterprises/{enterpriseId}/messages', 'GET:/api/v1/enterprises/{enterpriseId}/messages/{messageId}', 'POST:/api/v1/enterprises/{enterpriseId}/messages/{messageId}:ack', 'GET:/api/v1/enterprises/{enterpriseId}/messages/connections', 'GET:/api/v1/sessions', 'DELETE:/api/v1/sessions/{reference}']
    ids = []
    for code in codes:
        if code not in known:
            created, _ = call(admin, '/api/v1/enterprises/ent-acme/capabilities', 'POST', {'code': code, 'resource': 'messages', 'action': 'read', 'scope': 'ENTERPRISE', 'riskLevel': 'LOW'}, {'Idempotency-Key': uuid.uuid4().hex}, 201)
            known[code] = created['capabilityId']
        ids.append(known[code])
    role = create_role(admin, 'ent-acme', ids, 'T30 live messages')
    assignment, _ = call(admin, '/api/v1/enterprises/ent-acme/users/' + env.username + '/roles')
    call(admin, '/api/v1/enterprises/ent-acme/users/' + env.username + '/roles', 'PUT', {'roleIds': [base_role, role], 'deniedCapabilityCodes': []}, {'If-Match': '"' + str(assignment['version']) + '"', 'Idempotency-Key': uuid.uuid4().hex})
    admin = Client().login(19081, env.username, env.password)
    return admin
