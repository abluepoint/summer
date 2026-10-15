"""Real MySQL/Redis/JVM/WebSocket acceptance. Credentials stay in process memory/stdin."""
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import uuid

from environment import Client as BaseClient, Environment, OUT, ROOT, SCRIPT, immutable_candidate

sys.path.insert(0, str(ROOT / 'scripts/acceptance/identity'))
from grants import bootstrap, create_role, unwrap

NODE = '/opt/homebrew/opt/node@22/bin/node'
RESULT = {'checks': [], 'businessSqlWrites': False, 'stage': 'setup'}

class Client(BaseClient):
    def request(self, port, *args, **kwargs):
        return super().request(19081 if port == 21081 else port, *args, **kwargs)

def record(name, passed, **facts):
    RESULT['checks'].append({'name': name, 'passed': bool(passed), **facts})
    print(name, bool(passed), flush=True)

def call(client, path, method='GET', body=None, headers=None, status=200, port=19081, bearer=True):
    actual, data, response = client.request(port, path, method, body, headers, bearer=bearer)
    assert actual == status, 'http_status_' + str(actual) + '_expected_' + str(status) + '_path_' + path
    return unwrap(data), response

class SocketDriver:
    def __init__(self):
        self.process = subprocess.Popen([NODE, str(SCRIPT / 'socket.mjs')], cwd=ROOT, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
        self.sequence = 0
    def request(self, op, name='reader', **data):
        self.sequence += 1
        self.process.stdin.write(json.dumps({'id': self.sequence, 'op': op, 'name': name, **data}) + '\n')
        self.process.stdin.flush()
        import select
        ready, _, _ = select.select([self.process.stdout], [], [], 30)
        if not ready: raise RuntimeError('socket_driver_timeout')
        reply = json.loads(self.process.stdout.readline())
        assert reply['id'] == self.sequence, 'socket_driver_response_id'
        return reply
    def close(self):
        if self.process.poll() is None:
            try: self.request('shutdown')
            finally:
                try: self.process.wait(timeout=5)
                except subprocess.TimeoutExpired: self.process.kill(); self.process.wait()

def cookie(client): return '; '.join(value.name + '=' + value.value for value in client.cookies)

env = None
driver = None
try:
    candidate = immutable_candidate()
    RESULT['candidateSha256'] = hashlib.sha256(candidate.read_bytes()).hexdigest()
    env = Environment(); env.setup(); env.extra['summer.sample.authorization.legacy-scope-compatibility'] = False
    from fixtures import prepare
    admin = prepare(env, candidate, Client, call)
    env.start(19082, jar=candidate)
    driver = SocketDriver()
    url = lambda port, tenant='ent-acme', after=0: f'ws://127.0.0.1:{port}/ws/v1/messages?enterpriseId={tenant}&after={after}'
    first = driver.request('connect', 'a', url=url(19081), cookie=cookie(admin))
    second = driver.request('connect', 'b', url=url(19082), bearer=admin.token)
    record('actual_cookie_and_bearer_socket_authentication', first.get('connected') and second.get('connected'))
    anonymous = driver.request('connect', 'anonymous', url=url(19081))
    tenant = driver.request('connect', 'tenant', url=url(19081, 'unrelated-enterprise'), bearer=admin.token)
    invalid_cursor = driver.request('connect', 'cursor', url=url(19081, after=-1), bearer=admin.token)
    record('anonymous_tenant_and_invalid_cursor_rejected', anonymous.get('status') in [401, 403] and tenant.get('status') == 403 and invalid_cursor.get('status') == 400, anonymousStatus=anonymous.get('status'), tenantStatus=tenant.get('status'), cursorStatus=invalid_cursor.get('status'))
    path = '/api/v1/enterprises/ent-acme/messages'
    driver.request('send', 'a', payload={'type': 'send', 'clientMessageId': 'ws-' + uuid.uuid4().hex, 'payload': {'text': 'real socket send'}})
    a = driver.request('observe', 'a', waitMs=800)
    b = driver.request('observe', 'b', waitMs=500)
    am = [row['message'] for row in a['events'] if row.get('type') == 'message']
    bm = [row['message'] for row in b['events'] if row.get('type') == 'message']
    ack = [row for row in a['events'] if row.get('type') == 'ack']
    record('websocket_send_ack_and_cross_node_delivery', len(ack) == 1 and len(bm) >= 1 and ack[0]['messageId'] == bm[0]['messageId'])
    record('one_delivery_per_message_per_node', len(am) == 1 and len(bm) == 1, senderDeliveries=len(am), otherNodeDeliveries=len(bm))
    if not bm: raise RuntimeError('cross_node_message_absent')
    message = bm[0]
    driver.request('send', 'b', payload={'type': 'ack', 'messageId': message['messageId']})
    observed = driver.request('observe', 'b', waitMs=200)
    persisted, _ = call(admin, path + '/' + message['messageId'])
    record('websocket_ack_is_persisted', persisted['status'] == 'ACKED' and any(row.get('type') == 'ack' and row.get('status') == 'ACKED' for row in observed['events']))
    duplicate, _ = call(admin, path, 'POST', {'clientMessageId': message['clientMessageId'], 'payload': {'text': 'ignored duplicate'}}, status=200, port=19082)
    record('duplicate_send_keeps_message_and_sequence', duplicate['duplicate'] and duplicate['messageId'] == message['messageId'] and duplicate['sequence'] == message['sequence'])
    def send(index):
        data, _ = call(admin, path, 'POST', {'clientMessageId': 'ordered-' + str(index) + '-' + uuid.uuid4().hex, 'payload': {'text': 'parallel order'}}, status=201, port=19081 + index % 2)
        return data['sequence']
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool: sequences = list(pool.map(send, range(12)))
    observed = driver.request('observe', 'b', waitMs=1500)
    received = [row['message']['sequence'] for row in observed['events'] if row.get('type') == 'message']
    record('cross_node_concurrent_messages_are_unique_and_ordered', len(set(sequences)) == 12 and len(received) == len(set(received)) and received == sorted(received), sentDistinctSequences=len(set(sequences)), receivedSequences=received)
    driver.request('close', 'b')
    missed, _ = call(admin, path, 'POST', {'clientMessageId': 'offline-' + uuid.uuid4().hex, 'payload': {'text': 'offline compensation'}}, status=201)
    driver.request('connect', 'resume', url=url(19082, after=max(sequences)), bearer=admin.token)
    resume = driver.request('observe', 'resume', waitMs=500)
    restored = [row['message'] for row in resume['events'] if row.get('type') == 'message']
    record('reconnect_replays_persisted_messages_after_cursor', [row['messageId'] for row in restored] == [missed['messageId']])
    driver.request('send', 'resume', raw='{bad json')
    invalid = driver.request('observe', 'resume', waitMs=200)
    record('invalid_socket_message_has_framework_error', any(row.get('type') == 'error' and row.get('code') == 'SUMMER_MESSAGE_INVALID' for row in invalid['events']))
    _, error, headers = admin.request(19081, path + '?after=-1')
    record('http_invalid_input_has_failure_envelope_and_request_id', isinstance(error, dict) and error.get('success') is False and bool(headers.get('X-Request-Id')))
    accepted = []
    for index in range(31):
        name = 'capacity-' + str(index)
        accepted.append(driver.request('connect', name, url=url(19081, after=missed['sequence']), bearer=admin.token).get('connected') is True)
    overflow = driver.request('connect', 'overflow', url=url(19081), bearer=admin.token)
    current, _ = call(admin, path + '/connections')
    record('actual_33rd_connection_closes_at_tenant_limit', all(accepted) and overflow.get('closeCode') == 1013 and current['connections'] == 32, acceptedAdditional=sum(accepted), closeCode=overflow.get('closeCode'), connections=current['connections'])
    for index in range(31): driver.request('close', 'capacity-' + str(index))
    binding_path = '/api/v1/enterprises/ent-acme/users/' + env.username + '/roles'
    binding, _ = call(admin, binding_path)
    call(admin, binding_path, 'PUT', {'roleIds': binding['roleIds'], 'deniedCapabilityCodes': ['enterprise.messages.write']}, {'If-Match': '"' + str(binding['version']) + '"', 'Idempotency-Key': uuid.uuid4().hex})
    writer = driver.request('connect', 'denied-writer', url=url(19082, after=missed['sequence']), bearer=admin.token)
    driver.request('send', 'denied-writer', payload={'type': 'send', 'clientMessageId': 'denied-' + uuid.uuid4().hex, 'payload': {'text': 'must not persist'}})
    denied = driver.request('observe', 'denied-writer', waitMs=500)
    record('persisted_write_revocation_closes_old_token_socket', writer.get('connected') and denied.get('closeCode') == 1008, closeCode=denied.get('closeCode'))
    env.stop(19082)
    env.extra['summer.sample.realtime.idle-timeout-ms'] = 2000
    env.start(19082, jar=candidate)
    idle = driver.request('connect', 'idle', url=url(19082, after=missed['sequence']), bearer=admin.token)
    expired = driver.request('observe', 'idle', waitMs=3500)
    record('idle_socket_times_out_without_client_heartbeat', idle.get('connected') and expired.get('closeCode') == 1001, closeCode=expired.get('closeCode'))
    fault_socket = driver.request('connect', 'fault', url=url(19081, after=missed['sequence']), bearer=admin.token)
    env.sql('RENAME TABLE summer_realtime_message TO summer_realtime_message_acceptance_fault', env.schema)
    RESULT['isolatedSchemaFaultDdl'] = True
    try:
        status, failure, headers = admin.request(19081, path)
        failed = driver.request('observe', 'fault', waitMs=17000)
        record('message_storage_fault_fails_http_and_closes_socket', fault_socket.get('connected') and status == 500 and failure.get('success') is False and bool(headers.get('X-Request-Id')) and failed.get('closeCode') == 1011, httpStatus=status, closeCode=failed.get('closeCode'))
    finally:
        env.sql('RENAME TABLE summer_realtime_message_acceptance_fault TO summer_realtime_message', env.schema)
    recovered, _ = call(admin, path)
    record('message_storage_fault_recovery_retains_durable_messages', recovered['latestSequence'] >= missed['sequence'])
    browser_session = Client().login(19081, env.username, env.password)
    sessions, _ = call(browser_session, '/api/v1/sessions', bearer=False)
    current = [session for session in sessions['items'] if session['current']]
    assert len(current) == 1, 'one_current_cookie_session'
    connected = driver.request('connect', 'revoked-cookie', url=url(19081, after=missed['sequence']), cookie=cookie(browser_session))
    revoked, _ = call(admin, '/api/v1/sessions/' + current[0]['sessionRef'], 'DELETE')
    driver.request('send', 'revoked-cookie', payload={'type': 'ping'})
    after_revoke = driver.request('observe', 'revoked-cookie', waitMs=500)
    record('revoked_cookie_session_cannot_ping_or_receive', connected.get('connected') and revoked['status'] == 'REVOKED' and after_revoke.get('closeCode') == 1008 and not any(event.get('type') in ['pong', 'message', 'heartbeat'] for event in after_revoke['events']), closeCode=after_revoke.get('closeCode'))
    RESULT['stage'] = 'complete'
except Exception as error:
    RESULT['failureType'] = type(error).__name__
    if isinstance(error, AssertionError): RESULT['assertion'] = str(error)
finally:
    if driver: driver.close()
    if env:
        try: env.close(); RESULT['cleanup'] = 'VERIFIED'
        except Exception as error: RESULT['cleanup'] = 'FAILED'; RESULT['cleanupFailureType'] = type(error).__name__
    RESULT['passed'] = RESULT['stage'] == 'complete' and all(row['passed'] for row in RESULT['checks']) and RESULT.get('cleanup') == 'VERIFIED'
    RESULT['counts'] = {'tests': len(RESULT['checks']), 'failures': sum(not row['passed'] for row in RESULT['checks']), 'errors': int(RESULT['stage'] != 'complete'), 'skipped': 0}
    (OUT / 'result.json').write_text(json.dumps(RESULT, indent=2) + '\n')
    print(json.dumps({'stage': RESULT['stage'], 'counts': RESULT['counts'], 'cleanup': RESULT.get('cleanup')}), flush=True)
raise SystemExit(0 if RESULT['passed'] else 1)
