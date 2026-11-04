import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import ts from 'typescript';
import { webcrypto } from 'node:crypto';
import test from 'node:test';
const code = ts.transpileModule(
  fs.readFileSync(
    new URL('../src/pages/roles/api.ts', import.meta.url),
    'utf8'
  ),
  {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020,
    },
  }
).outputText;
function fixture(reply = () => ({})) {
  const calls = [],
    exports = {};
  vm.runInNewContext(code, {
    exports,
    crypto: webcrypto,
    require: (name) => {
      assert.equal(name, '../permissions/api');
      return {
        call: (...args) => {
          calls.push(args);
          return Promise.resolve(reply(args));
        },
      };
    },
  });
  return { api: exports, calls };
}
test('create and replace persist concrete permission IDs in the selected domain', async () => {
  const { api, calls } = fixture();
  await api.createRole('/api/v1/enterprises/tenant-a', 'Orders', 'orders', [
    'permission-1',
  ]);
  await api.saveRolePermissions(
    '/api/v1/platform',
    { roleId: 'r1', version: 7 },
    []
  );
  assert.equal(calls[0][0], '/api/v1/enterprises/tenant-a');
  assert.equal(
    JSON.stringify(calls[0][3]),
    JSON.stringify({
      name: 'Orders',
      code: 'orders',
      permissionIds: ['permission-1'],
    })
  );
  assert.match(calls[0][5], /^ui-roles-/);
  assert.equal(calls[1][0], '/api/v1/platform');
  assert.equal(calls[1][1], '/roles/r1/permissions');
  assert.equal(calls[1][4], 7);
  assert.equal(JSON.stringify(calls[1][3]), '{"permissionIds":[]}');
});
test('user binding accepts only role IDs and supports revoking every role at version zero', async () => {
  const { api, calls } = fixture();
  await api.assignUserRoles('/api/v1/enterprises/tenant-a', 'name/a', [], 0);
  assert.equal(calls[0][1], '/users/name%2Fa/roles');
  assert.equal(JSON.stringify(calls[0][3]), '{"roleIds":[]}');
  assert.equal(calls[0][4], 0);
});
test('selection loads the entire paged permission catalogue', async () => {
  const { api, calls } = fixture(([, path]) =>
    path.includes('page=1')
      ? {
          total: 201,
          items: Array.from({ length: 200 }, (_, i) => ({
            permissionId: String(i),
          })),
        }
      : { total: 201, items: [{ permissionId: 'last' }] }
  );
  const permissions = await api.listPermissionChoices(
    '/api/v1/enterprises/tenant-b'
  );
  assert.equal(permissions.length, 201);
  assert.equal(permissions[200].permissionId, 'last');
  assert.equal(calls.length, 2);
});

test('role selection loads every page instead of truncating at the first page', async () => {
  const { api, calls } = fixture(([, path]) =>
    path.includes('page=1')
      ? {
          total: 201,
          items: Array.from({ length: 200 }, (_, i) => ({ roleId: String(i) })),
        }
      : { total: 201, items: [{ roleId: 'last' }] }
  );
  const roles = await api.listRoles('/api/v1/enterprises/tenant-c');
  assert.equal(roles.length, 201);
  assert.equal(roles[200].roleId, 'last');
  assert.equal(calls.length, 2);
});

test('enterprise ID platform and the platform domain use distinct explicit API bases', async () => {
  const { api, calls } = fixture();
  await api.createRole(
    '/api/v1/enterprises/platform',
    'Enterprise',
    'enterprise',
    []
  );
  await api.createRole('/api/v1/platform', 'Platform', 'platform', []);
  assert.equal(calls[0][0], '/api/v1/enterprises/platform');
  assert.equal(calls[1][0], '/api/v1/platform');
});
