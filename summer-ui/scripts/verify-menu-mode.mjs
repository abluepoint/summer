import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import ts from 'typescript';

const source = fs.readFileSync(
  new URL('../src/pages/menu-manifest/api.ts', import.meta.url),
  'utf8'
);
const code = ts.transpileModule(source, {
  compilerOptions: {
    module: ts.ModuleKind.CommonJS,
    target: ts.ScriptTarget.ES2020,
  },
}).outputText;
const calls = [];
let mode = 'LEGACY';
const axios = Object.fromEntries(
  ['get', 'patch', 'put'].map((method) => [
    method,
    async (path, ...args) => {
      calls.push({ method, path, args });
      return {
        status: 200,
        data: {
          data: path.endsWith('/authorization/navigation')
            ? { engineMode: mode, items: [] }
            : { items: [], manifestVersion: 1 },
        },
      };
    },
  ])
);
const exports = {};
vm.runInNewContext(code, {
  exports,
  require(name) {
    if (name === 'axios') return { default: axios };
    if (name === '@/pages/customer/contract.mjs')
      return { readEnvelope: (value) => value.data };
    throw new Error(`Unexpected menu API dependency: ${name}`);
  },
});

for (const [engine, tenantRead, tenantWrite, platform] of [
  [
    'LEGACY',
    '/menu-manifest/management',
    '/menu-manifest',
    '/api/v1/platform/menu-manifest',
  ],
  [
    'MANAGED',
    '/authorization/menu-layout',
    '/authorization/menu-layout',
    '/api/v1/platform/authorization/menu-manifest',
  ],
]) {
  mode = engine;
  await exports.getManagedManifest('tenant/a');
  assert.equal(
    calls.at(-1).path,
    `/api/v1/enterprises/tenant%2Fa${tenantRead}`
  );
  await exports.saveOverride(
    'tenant/a',
    'nested/menu',
    { sortOrder: 7 },
    2,
    'test-key'
  );
  const saved = calls.at(-1);
  assert.equal(saved.path, `/api/v1/enterprises/tenant%2Fa${tenantWrite}`);
  assert.equal(saved.args[1].params.menuId, 'nested/menu');
  assert.equal(saved.args[1].headers['If-Match'], '"2"');
  assert.equal(saved.args[1].headers['Idempotency-Key'], 'test-key');
  await exports.getPlatformManifest();
  assert.equal(calls.at(-1).path, platform);
  await exports.savePlatformManifest(
    { manifestVersion: 3, items: [] },
    2,
    'platform-key'
  );
  assert.equal(calls.at(-1).path, platform);
}
mode = 'UNKNOWN';
const writes = calls.filter((call) => call.method !== 'get').length;
await assert.rejects(
  exports.saveOverride(
    'tenant/a',
    'nested/menu',
    { sortOrder: 23 },
    2,
    'denied'
  ),
  (error) => error.status === 503
);
await assert.rejects(
  exports.savePlatformManifest({ manifestVersion: 3, items: [] }, 2, 'denied'),
  (error) => error.status === 503
);
assert.equal(calls.filter((call) => call.method !== 'get').length, writes);
console.log(
  'Menu API preserves explicit LEGACY routes, MANAGED routes, versions and failure-closed unknown modes'
);
