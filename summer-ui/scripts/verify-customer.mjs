import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import ts from 'typescript';
import axios from 'axios';

// Execute the same TypeScript runtime modules used by Vite, with only HTTP at
// the system boundary replaced in individual tests.
const modules = new Map();
function loadModule(url) {
  if (modules.has(url.href)) return modules.get(url.href);
  const exports = {};
  modules.set(url.href, exports);
  const source = fs.readFileSync(url, 'utf8');
  const code = ts.transpileModule(source, { compilerOptions: {
    module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020,
  } }).outputText;
  vm.runInThisContext(`(function(exports, require) { ${code}\n})`)(exports, (name) => {
    if (name === 'axios') return axios;
    const dependency = name.startsWith('@/')
      ? new URL(`../src/${name.slice(2)}.ts`, import.meta.url)
      : new URL(`${name}.ts`, url);
    return loadModule(dependency);
  });
  return exports;
}
const { readEnvelope } = loadModule(new URL('../src/api/envelope.ts', import.meta.url));
const { readFailure, SummerApiError } = loadModule(new URL('../src/api/errors.ts', import.meta.url));
const { businessData } = loadModule(new URL('../src/api/client.ts', import.meta.url));
import {
  readListState,
  listSearch,
  detailPath,
  detailSource,
  detailUsername,
  canConfirmDeletion,
  pageAfterDeletion,
} from '../src/pages/customer/contract.mjs';

test('list URL restores valid pagination, filter and sort and rejects invalid values', () => {
  assert.deepEqual(
    readListState('?page=2&size=50&active=false&sort=email&direction=desc'),
    {
      page: 2,
      size: 50,
      active: 'false',
      sort: 'email',
      direction: 'desc',
    }
  );
  assert.deepEqual(
    readListState('?page=-1&size=500&active=maybe&sort=id&direction=down'),
    {
      page: 0,
      size: 20,
      active: '',
      sort: 'username',
      direction: 'asc',
    }
  );
  assert.equal(
    listSearch({
      page: 2,
      size: 50,
      active: 'false',
      sort: 'email',
      direction: 'desc',
    }),
    '?page=2&size=50&active=false&sort=email&direction=desc'
  );
});

test('deletion needs exact username and falls back only when the page is gone', () => {
  assert.equal(canConfirmDeletion('alice', 'alice'), true);
  for (const typed of ['Alice', 'alice ', ' alice', 'ali', ''])
    assert.equal(canConfirmDeletion(typed, 'alice'), false);
  assert.equal(pageAfterDeletion(2, 10, 20), 1);
  assert.equal(pageAfterDeletion(2, 10, 21), 2);
  assert.equal(pageAfterDeletion(0, 10, 0), 0);
});

test('Customer detail links use username and preserve list state', () => {
  assert.equal(
    detailPath('drawer', 'alice_1', '?page=2'),
    '/customer/detail/alice_1?page=2'
  );
  assert.equal(detailUsername('/customer/detail/alice_1', 'drawer'), 'alice_1');
  assert.equal(detailUsername('/customer/detail/summer.demo', 'page'), 'summer.demo');
  assert.equal(detailPath('page', 'summer.demo'), '/customer/detail/summer.demo');
  assert.equal(detailUsername('/customer/drawer', 'drawer'), null);
  assert.equal(detailUsername('/customer/detail/not%2Fvalid', 'drawer'), null);
  assert.equal(detailUsername('/customer/detail/Abc', 'drawer'), null);
  assert.equal(detailUsername('/customer/detail/abc-', 'drawer'), null);
  assert.deepEqual(detailSource('/customer/modal?page=2#list'), {
    mode: 'modal',
    url: '/customer/modal?page=2#list',
  });
  assert.equal(detailSource('//outside.example/customer/modal'), null);
  assert.equal(detailSource('/customer/modal/other'), null);
});

test('business envelopes return data and retain request metadata on failure', () => {
  assert.deepEqual(
    readEnvelope(
      { success: true, data: { username: 'alice' }, requestId: 'req-1' },
      200
    ),
    { username: 'alice' }
  );
  assert.deepEqual(
    readFailure(
      {
        success: false,
        error: {
          code: 'CUSTOMER_NOT_FOUND',
          message: 'Missing',
          violations: [],
        },
        requestId: 'req-2',
        timestamp: '2026-09-30T00:00:00Z',
      },
      404
    ),
    {
      status: 404,
      code: 'CUSTOMER_NOT_FOUND',
      message: 'Missing',
      violations: [],
      requestId: 'req-2',
      timestamp: '2026-09-30T00:00:00Z',
    }
  );
  for (const status of [401, 403, 404, 500]) {
    assert.equal(readFailure(undefined, status, 'header-id').status, status);
    assert.equal(
      readFailure(undefined, status, 'header-id').requestId,
      'header-id'
    );
  }
  assert.equal(readFailure({ error: null }, 500).status, 500);
});


test('shared business requests preserve response metadata, empty responses and typed failures', async () => {
  assert.deepEqual(await businessData(async () => ({ status: 201, data: { success: true, data: { saved: true } } })), { saved: true });
  assert.equal(await businessData(async () => ({ status: 204, data: '' })), undefined);
  for (const status of [200, 400, 401, 403, 404, 409, 500]) {
    await assert.rejects(businessData(async () => ({ status, headers: { 'x-request-id': 'header-request' }, data: { success: false, error: { code: 'APP_FAILURE', message: 'Failed', violations: [{ field: 'name', message: 'Required' }] }, timestamp: '2026-10-28T00:00:00Z' } })), (error) => {
      assert.ok(error instanceof SummerApiError);
      assert.equal(error.status, status);
      assert.equal(error.code, 'APP_FAILURE');
      assert.equal(error.requestId, 'header-request');
      assert.equal(error.timestamp, '2026-10-28T00:00:00Z');
      assert.deepEqual(error.violations, [{ field: 'name', message: 'Required' }]);
      return true;
    });
  }
  await assert.rejects(businessData(async () => ({ status: 503, data: { success: true, data: 'must fail' } })), SummerApiError);
  await assert.rejects(businessData(async () => ({ status: 200, data: '' })), SummerApiError);
  let writes = 0;
  await assert.rejects(businessData(async () => {
    writes += 1;
    throw { isAxiosError: true, response: { status: 401, headers: { get: () => 'recovery-request' } } };
  }), (error) => error instanceof SummerApiError && error.status === 401 && error.requestId === 'recovery-request');
  assert.equal(writes, 1, 'authentication failures never replay the business write');
  await assert.rejects(businessData(async () => { throw { isAxiosError: true }; }), (error) => error instanceof SummerApiError && error.status === 0);
});


test('business consumers share error status and request metadata at their HTTP entry points', async (t) => {
  const cases = [
    ['menu/api', 'getVisibleMenuManifest', ['ent-test']],
    ['pages/audit/api', 'listAuditEvents', [{ page: 0, size: 20 }]],
    ['pages/auth-users/api', 'listUsers', []],
    ['pages/authorization-events/api', 'listAuthorizationChangeEvents', ['ent-test']],
    ['pages/customer/api', 'listCustomers', [{ page: 0, size: 20, active: '', sort: 'username', direction: 'asc' }]],
    ['pages/data-scopes/api', 'listDataScopes', ['ent-test']],
    ['pages/diagnostics/release/api', 'getReleaseReadiness', []],
    ['pages/diagnostics/resilience/api', 'getResilienceDiagnostics', []],
    ['pages/diagnostics/runtime/api', 'getRuntimeDiagnostics', []],
    ['pages/interface-resources/api', 'listInterfaceResources', []],
    ['pages/menu-manifest/api', 'getManagedManifest', ['ent-test']],
    ['pages/messages/api', 'listMessages', ['ent-test']],
    ['pages/oauth-clients/api', 'listOAuthClients', []],
    ['pages/organization/api', 'getOrganizationTree', ['ent-test']],
    ['pages/plugins/api', 'listPlugins', []],
    ['pages/roles/api', 'listRoles', ['ent-test']],
    ['pages/security-keys/api', 'listSecurityKeys', []],
    ['pages/sessions/api', 'listSessions', []],
    ['pages/statistics/api', 'getStatistics', ['ent-test']],
    ['pages/system-parameters/api', 'listParameters', [{ page: 0, size: 20 }]],
    ['pages/tasks/api', 'listTasks', ['ent-test']],
  ];
  const originalAdapter = axios.defaults.adapter;
  try {
    for (const [file, name, args] of cases) {
      await t.test(file, async () => {
        const api = loadModule(new URL(`../src/${file}.ts`, import.meta.url));
        for (const status of [200, 403]) {
          axios.defaults.adapter = async (config) => {
            const response = { config, status, headers: { 'x-request-id': 'consumer-request', 'x-trace-id': '1234567890abcdef1234567890abcdef' }, data: { success: false, error: { code: 'APP_FORBIDDEN', message: 'Denied' } } };
            if (status === 403) throw { isAxiosError: true, response };
            return response;
          };
          await assert.rejects(api[name](...args), (error) => {
            assert.equal(error.status, status);
            assert.equal(error.requestId, 'consumer-request');
            if (file === 'pages/diagnostics/runtime/api' && status === 403) assert.equal(error.traceId, '1234567890abcdef1234567890abcdef');
            return true;
          });
        }
        const data = file === 'pages/auth-users/api' || file === 'pages/data-scopes/api' || file === 'pages/roles/api'
          ? [] : file === 'pages/diagnostics/release/api'
            ? { status: 'READY', checks: api.checkNames.map(name => ({ name, passed: true, detail: 'Ready' })) }
            : { items: [] };
        axios.defaults.adapter = async (config) => ({ config, status: 200, headers: { 'x-request-id': 'success-request' }, data: { success: true, data, timestamp: '2026-10-28T00:00:00Z' } });
        const value = await api[name](...args);
        if (file === 'pages/diagnostics/release/api') {
          assert.equal(value.status, 'READY');
          assert.equal(value.requestId, 'success-request');
        } else {
          assert.deepEqual(value, file === 'pages/oauth-clients/api' || file === 'pages/security-keys/api' ? [] : data);
        }
      });
    }
  } finally {
    axios.defaults.adapter = originalAdapter;
  }
});

test('resource transfer bytes and organization concurrency metadata keep their own contracts', async () => {
  const audit = loadModule(new URL('../src/pages/audit/api.ts', import.meta.url));
  const organization = loadModule(new URL('../src/pages/organization/api.ts', import.meta.url));
  const customer = loadModule(new URL('../src/pages/customer/api.ts', import.meta.url));
  const originalAdapter = axios.defaults.adapter;
  try {
    for (const [url, raw] of [
      ['/.well-known/openid-configuration', { issuer: 'https://identity.example' }],
      ['/oauth2/token', { token_type: 'Bearer', expires_in: 900 }],
      ['/csrf', { parameterName: '_csrf', headerName: 'X-CSRF-TOKEN' }],
      ['/login', 'login response'],
    ]) {
      axios.defaults.adapter = async (config) => ({ config, status: 200, headers: {}, data: raw });
      assert.deepEqual((await axios.get(url)).data, raw, 'shared business adaptation leaves protocol responses raw');
    }
    const bytes = new Blob(['csv contents'], { type: 'text/csv' });
    axios.defaults.adapter = async (config) => ({ config, status: 200, headers: {}, data: bytes });
    assert.equal(await audit.exportAuditEvents({}), bytes);
    axios.defaults.adapter = async (config) => ({ config, status: 200, headers: { etag: '"v3"' }, data: { success: true, data: { id: 'unit-1' } } });
    assert.deepEqual(await organization.getOrganizationDetail('ent-test', 'unit-1'), { unit: { id: 'unit-1' }, etag: '"v3"' });
    axios.defaults.adapter = async (config) => ({ config, status: 204, headers: {}, data: '' });
    assert.equal(await customer.deleteCustomer('alice'), undefined);
    assert.equal(await organization.deleteOrganization('ent-test', 'unit-1', '"v3"', 'delete-key'), undefined);
  } finally {
    axios.defaults.adapter = originalAdapter;
  }
});
