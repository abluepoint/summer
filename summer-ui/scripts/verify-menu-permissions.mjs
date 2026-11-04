import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import ts from 'typescript';
const entry = fs.readFileSync(new URL('../src/main.tsx', import.meta.url), 'utf8');
assert.equal(entry.includes("generatePermission('admin')"), false, 'backend failures must not grant administrator UI permissions');
const source = fs.readFileSync(new URL('../src/menu/permissions.ts', import.meta.url), 'utf8');
const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText;
const exports = {};
vm.runInNewContext(code, { exports });
const load = exports.loadMenuPermissions;
const manifest = (items) => ({ manifestVersion: 1, items });
const plain = (value) => JSON.parse(JSON.stringify(value));
let reads = 0;
const visible = manifest([{ menuId: 'customer/list', capabilityCode: 'customer.read', actions: ['read'] }]);
const ordinary = await load(async () => { throw new Error('403'); }, async () => { reads++; return visible; });
assert.equal(reads, 1, 'platform sync denial must not skip user grants');
assert.deepEqual(plain(ordinary), { 'customer/list': ['read'], customer: ['read'] });
assert.deepEqual(plain(await load(async () => {}, async () => manifest([]))), {}, 'empty grants stay empty');
assert.deepEqual(plain(await load(async () => {}, async () => { throw new Error('503'); })), {}, 'read failure must fail closed');
assert.deepEqual(plain(await load(async () => { throw new Error('403'); }, async () => { throw new Error('403'); })), {}, 'two failures never create administrator grants');
const both = await load(async () => {}, async () => manifest([
  { menuId: 'customer/read', capabilityCode: 'customer.read', actions: ['read'] },
  { menuId: 'customer/edit', capabilityCode: 'customer.write', actions: ['write'] },
]));
assert.deepEqual(plain(both.customer), ['read', 'write'], 'allowed actions on the same resource are combined');
function evaluateModule(path, dependencies = {}) {
  const source = fs.readFileSync(new URL(path, import.meta.url), 'utf8');
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText;
  const result = {};
  vm.runInNewContext(code, { exports: result, require(name) {
    assert.ok(name in dependencies, `unexpected runtime dependency: ${name}`);
    return dependencies[name];
  } });
  return result;
}
const authentication = evaluateModule('../src/utils/authentication.ts');
const menu = evaluateModule('../src/menu/manifest.ts');
const access = evaluateModule('../src/menu/access.ts', { react: { createContext: () => ({}) } });
const routes = evaluateModule('../src/routes.ts', {
  '@/utils/authentication': authentication,
  '@/menu/manifest': menu,
  '@/menu/access': access,
  react: {},
});
assert.equal(routes.menuManifest.manifestVersion, 8, 'changed metadata must have a new version');
const bootstrapCodes = {
  'diagnostics/release': 'internal.release.read',
  'plugins/customer-insights': 'enterprise.plugins.customer-insights.read',
  roles: 'enterprise.roles.read',
  'interface-resources': 'platform.interface-resources.read',
  'auth-users': 'enterprise.members.read',
  organization: 'enterprise.organization.read',
  audit: 'enterprise.audit.read',
  'diagnostics/redis': 'platform.capabilities.read',
  'diagnostics/runtime': 'platform.capabilities.read',
};
for (const [key, code] of Object.entries(bootstrapCodes)) {
  const route = routes.routes.flatMap((item) => [item, ...(item.children || [])]).find((item) => item.key === key);
  const item = routes.menuManifest.items.find((item) => item.menuId === key);
  assert.equal(item.capabilityCode, code);
  const permissions = await load(async () => {}, async () => manifest([{ ...item, actions: ['read'] }]));
  assert.equal(authentication.default(route, permissions), true, `${key} must be navigable with its backend grant`);
  assert.equal(authentication.default(route, {}), false, `${key} must remain hidden without a grant`);
}
console.log('Menu permissions: 5 failure/action regressions and 8 navigation contracts passed');
