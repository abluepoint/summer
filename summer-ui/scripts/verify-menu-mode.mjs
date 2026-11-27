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
    if (name === '@/menu/client') return {menuClientCode:'web'};
    if (name === 'axios') return { default: axios };
    if (name === '@/pages/customer/contract.mjs')
      return { readEnvelope: (value) => value.data };
    throw new Error(`Unexpected menu API dependency: ${name}`);
  },
});

for (const [engine, platform] of [
  ['LEGACY', '/api/v1/platform/menu-manifest'],
  ['MANAGED', '/api/v1/platform/authorization/menu-manifest'],
]) {
  mode = engine;
  await exports.getPlatformManifest();
  assert.equal(calls.at(-1).path, platform);
  await exports.savePlatformManifest(
    { manifestVersion: 3, items: [] },
    2,
    'platform-key'
  );
  assert.equal(calls.at(-1).path, platform);
  assert.equal(calls.at(-1).args[1].headers['If-Match'], '"2"');
  assert.equal(calls.at(-1).args[1].headers['Idempotency-Key'], 'platform-key');
}
mode = 'UNKNOWN';
const writes = calls.filter((call) => call.method !== 'get').length;
await assert.rejects(
  exports.savePlatformManifest({ manifestVersion: 3, items: [] }, 2, 'denied'),
  (error) => error.status === 503
);
assert.equal(calls.filter((call) => call.method !== 'get').length, writes);
console.log(
  'Menu API preserves explicit LEGACY routes, MANAGED routes, versions and failure-closed unknown modes'
);
