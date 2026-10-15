"""T02 scope: actual config-file/environment precedence and existing health/management UI."""
import json, os, subprocess, re
from environment import Environment, Client, OUT, ROOT, SCRIPT, immutable_candidate
from strict_fixtures import Fixtures

class ExternalConfigEnvironment(Environment):
    def properties(self, port, database=None):
        values = super().properties(port, database)
        for key in ('spring.datasource.url', 'spring.datasource.username', 'spring.datasource.password'):
            values.pop(key)
        if getattr(self, 'disable_migrations', False):
            values['spring.flyway.enabled'] = False
        return values

env = None
ui = None
saved = os.environ.copy()
result = {'checks': [], 'scope_gaps': []}
try:
    candidate = immutable_candidate()
    env = ExternalConfigEnvironment()
    env.setup()
    config = OUT / 'external-runtime.properties'
    config.write_text('spring.datasource.url=${FOUNDATION_FILE_DB_URL}\nspring.datasource.username=${FOUNDATION_FILE_DB_USER}\nspring.datasource.password=${FOUNDATION_FILE_DB_PASSWORD}\n')
    url = 'jdbc:mysql://127.0.0.1:3306/' + env.schema + '?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
    for key in list(os.environ):
        if key.startswith('SPRING_DATASOURCE_'):
            os.environ.pop(key)
    os.environ.update({'SPRING_CONFIG_ADDITIONAL_LOCATION': config.as_uri(), 'FOUNDATION_FILE_DB_URL': url,
                       'FOUNDATION_FILE_DB_USER': env.db_user, 'FOUNDATION_FILE_DB_PASSWORD': env.db_password})
    env.start(20081, jar=candidate)
    result['checks'].append({'name': 'external_file_overrides_packaged_config', 'passed': True})
    env.stop(20081)
    os.environ.update({'FOUNDATION_FILE_DB_URL': 'jdbc:mysql://127.0.0.1:1/unreachable_fixture',
                       'FOUNDATION_FILE_DB_USER': 'unusable-fixture-user', 'FOUNDATION_FILE_DB_PASSWORD': 'unusable-fixture-value',
                       'SPRING_DATASOURCE_URL': url, 'SPRING_DATASOURCE_USERNAME': env.db_user,
                       'SPRING_DATASOURCE_PASSWORD': env.db_password})
    env.start(20081, jar=candidate)
    result['checks'].append({'name': 'environment_overrides_external_file_url_user_password', 'passed': True})
    fixture = Fixtures(env).seed()
    admin = fixture.admin
    status, body, headers = admin.request(20081, '/internal/v1/diagnostics/runtime', headers={'X-Request-Id': 'scope-runtime-' + env.run})
    data = body.get('data', {}) if body else {}
    components = {item['name']: item['status'] for item in data.get('components', [])}
    encoded = json.dumps(data)
    result['runtime_api'] = {'http_status': status, 'components': components,
                             'safe_shape': sorted(data), 'migration_component_present': any('flyway' in k.lower() or 'migration' in k.lower() for k in components),
                             'secret_scan_passed': not any(value in encoded for value in (env.mysql, env.master, env.redis, env.password, env.db_password, env.db_user)),
                             'request_correlation': bool(body.get('requestId')) and body.get('requestId') == data.get('requestId') == headers.get('X-Request-Id')}
    result['checks'].append({'name': 'runtime_database_and_migrations_healthy_with_safe_shape', 'passed': status == 200 and components.get('db') == 'UP' and components.get('migrations') == 'UP' and sorted(data) == ['checkedAt', 'components', 'metrics', 'requestId'] and result['runtime_api']['secret_scan_passed'] and result['runtime_api']['request_correlation']})
    denied_status, _, _ = fixture.denied.request(20081, '/internal/v1/diagnostics/runtime')
    anonymous_status, _, _ = Client().request(20081, '/internal/v1/diagnostics/runtime')
    result['checks'].append({'name': 'unassigned_reader_runtime_api_forbidden', 'passed': denied_status == 403})
    result['checks'].append({'name': 'unauthenticated_runtime_api_rejected', 'passed': anonymous_status in (401, 403)})
    status, body, _ = admin.request(20081, '/internal/v1/release/readiness')
    data = body.get('data', {}) if body else {}
    result['release_readiness_api'] = {'http_status': status, 'fields': sorted(data),
                                       'migration_database_checks': [check for check in data.get('checks', []) if check.get('id', check.get('name')) in ('database', 'migrations')]}
    child = os.environ.copy()
    child.update({'SUMMER_E2E_USERNAME': env.username, 'SUMMER_E2E_PASSWORD': env.password, 'SUMMER_E2E_RUN': env.run})
    node = os.environ.get('SUMMER_FOUNDATION_NODE', '/opt/homebrew/opt/node@22/bin/node')
    ui = subprocess.Popen([node, str(SCRIPT / 'ui-server.mjs')], cwd=ROOT / 'summer-ui', env=child,
                          stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
    assert ui.stdout.readline().strip() == 'UI_READY'
    def browser(phase, browser_env):
        run = subprocess.run([node, str(SCRIPT / 'browser.mjs'), phase], cwd=ROOT / 'summer-ui', env=browser_env,
                             capture_output=True, text=True, timeout=100)
        result[phase + '_exit'] = run.returncode
        receipt = OUT / ('browser-' + phase + '.json')
        if not receipt.exists():
            # Keep only error classes and local source positions; browser logs can contain form values.
            result[phase + '_process_failure'] = {
                'error_classes': sorted(set(re.findall(r'\b(?:[A-Z][A-Za-z]*(?:Error|Exception)|ERR_[A-Z_]+)\b', run.stderr))),
                'local_frames': re.findall(r'(browser\.mjs:[0-9]+:[0-9]+)', run.stderr),
                'launch_failure': 'browserType.launch' in run.stderr,
                'page_creation_failure': 'browser.newPage' in run.stderr,
                'missing_receipt': True,
            }
            raise RuntimeError('browser receipt missing; safe process diagnostics recorded')
        observed = json.loads(receipt.read_text())
        return run.returncode == 0 and observed.get('passed') is True and observed.get('pageErrors') == 0
    result['checks'].append({'name': 'platform_capability_runtime_page_and_refresh', 'passed': browser('runtime', child)})
    reader = child.copy()
    reader.update({'SUMMER_E2E_USERNAME': env.denied_username, 'SUMMER_E2E_PASSWORD': env.denied_password})
    result['checks'].append({'name': 'unassigned_reader_runtime_page_forbidden', 'passed': browser('runtime-reader', reader)})
    # Mutate only this run's isolated Flyway checksum; restore before continuing.
    rank, checksum = [int(value) for value in env.sql('select installed_rank, checksum from flyway_schema_history where success=true and checksum is not null order by installed_rank limit 1', env.schema).split()]
    changed_checksum = checksum + 1 if checksum < 2147483647 else checksum - 1
    env.sql('update flyway_schema_history set checksum=' + str(changed_checksum) + ' where installed_rank=' + str(rank), env.schema)
    def migration_api_state(expected):
        status, body, response_headers = admin.request(20081, '/internal/v1/diagnostics/runtime', headers={'X-Request-Id': 'scope-migration-' + env.run})
        payload = body.get('data', {}) if body else {}
        parts = {item['name']: item['status'] for item in payload.get('components', [])}
        safe = json.dumps(body)
        return status == 200 and parts.get('db') == 'UP' and parts.get('migrations') == expected and body.get('requestId') == payload.get('requestId') == response_headers.get('X-Request-Id') and not any(secret in safe for secret in (env.mysql, env.master, env.redis, env.password, env.db_password, env.db_user))
    try:
        result['checks'].append({'name': 'actual_flyway_checksum_mismatch_reported_safely', 'passed': migration_api_state('VALIDATION_FAILED')})
        result['checks'].append({'name': 'actual_migration_failure_visible_on_page', 'passed': browser('runtime-validation-failed', child)})
    finally:
        env.sql('update flyway_schema_history set checksum=' + str(checksum) + ' where installed_rank=' + str(rank), env.schema)
    result['checks'].append({'name': 'actual_migration_recovery_after_checksum_restore', 'passed': migration_api_state('UP')})
    env.stop(20081)
    env.disable_migrations = True
    env.start(20081, jar=candidate)
    result['checks'].append({'name': 'flyway_disabled_reported_without_false_success', 'passed': migration_api_state('NOT_ENABLED')})
    result['checks'].append({'name': 'disabled_migrations_visible_on_page', 'passed': browser('runtime-disabled', child)})
    if not all(check['passed'] for check in result['checks']):
        result['scope_gaps'].append('One or more actual configuration/runtime management checks failed; inspect named checks.')

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
    os.environ.clear()
    os.environ.update(saved)
    if env:
        env.close()
        result['cleanup'] = 'VERIFIED'
    (OUT / 'scope-result.json').write_text(json.dumps(result, indent=2))
print(json.dumps(result))
raise SystemExit(0 if len(result['checks']) == 12 and all(c['passed'] for c in result['checks']) and not result.get('failure_type') and not result.get('scope_gaps') else 1)
