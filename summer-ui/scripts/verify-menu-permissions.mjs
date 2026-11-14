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
const access = evaluateModule('../src/menu/access.ts', {
  react: { createContext: () => ({}) },
  '@/auth/interfaceOperations': { useOperationHints: () => ({ actions: {} }) },
});
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

const directory = { menuId: 'roles', path: '/roles', enabled: true, navOnly: true, pageEligible: false, sortOrder: 1, actions: ['read'] };
const child = { menuId: 'permissions', path: '/permissions', parentMenuId: 'roles', enabled: true, pageEligible: true, sortOrder: 2, actions: [] };
const projected = access.resolveMenuRoutes([{ key: 'roles' }, { key: 'permissions' }, { key: 'sibling' }], [directory, child]);
assert.equal(projected[0].navOnly, true);
assert.deepEqual(plain(projected[0].children.map(x => x.key)), ['permissions']);
assert.deepEqual(plain(await load(async () => {}, async () => manifest([directory]))), {}, 'directory actions cannot grant a page');
assert.equal(access.customerRouteAllowed('/customer/page/arbitrary/path', [{ menuId: 'customer/page', enabled: true, actions: ['read'] }]), false);
assert.equal(access.customerRouteAllowed('/customer/detail/alice', [{ menuId: 'customer/page', enabled: true, pageEligible: true, actions: [] }]), true);
assert.equal(access.customerRouteAllowed('/customer/detail/alice', [{ menuId: 'customer/page', enabled: true, navOnly: true, actions: ['read'] }]), false);
const currentCustomerPage = [{ menuId: 'customer/page', enabled: true, pageEligible: true, actions: [] }];
assert.equal(access.customerRouteAllowed('/customer/page/create', currentCustomerPage, { engineMode: 'MANAGED', actions: { 'customer.create': true } }), true);
assert.equal(access.customerRouteAllowed('/customer/page/edit/alice', currentCustomerPage, { engineMode: 'MANAGED', actions: { 'customer.update': true } }), true);
assert.equal(access.customerRouteAllowed('/customer/page/create', currentCustomerPage, { engineMode: 'MANAGED', actions: { 'customer.update': true } }), false);
assert.equal(access.customerRouteAllowed('/customer/page/edit/alice', currentCustomerPage, { engineMode: 'MANAGED', actions: { 'customer.create': true } }), false);
assert.equal(access.customerRouteAllowed('/customer/page/create', [{ ...currentCustomerPage[0], actions: ['create'] }], { engineMode: 'MANAGED', actions: {} }), false, 'old MENU actions cannot authorize a managed API operation');
assert.equal(access.customerRouteAllowed('/customer/page/create', [{ ...currentCustomerPage[0], navOnly: true }], { engineMode: 'MANAGED', actions: { 'customer.create': true } }), false, 'INTERFACE hints cannot grant a missing page');
assert.equal(access.customerRouteAllowed('/customer/page/create', currentCustomerPage, { actions: { 'customer.create': true } }), false, 'unknown mode fails closed');
const contract = await import('../src/pages/customer/contract.mjs');
let calls = [], modeResponse;
const fakeAxios = { get: async url => { calls.push(url); if (modeResponse instanceof Error) throw modeResponse; return { status: 200, data: { success: true, data: calls.length === 1 ? modeResponse : { manifestVersion: 1, items: [] } } }; }, isAxiosError: () => false };
const navigationApi = evaluateModule('../src/menu/api.ts', { axios: { default: fakeAxios }, '@/pages/customer/contract.mjs': contract });
modeResponse = { engineMode: 'LEGACY', items: [] };
await navigationApi.getVisibleMenuManifest('ent-acme');
assert.equal(calls.length, 2);assert.equal(calls[1], '/api/v1/enterprises/ent-acme/menu-manifest');
calls = [];modeResponse = { engineMode: 'MANAGED', items: [] };
await navigationApi.getVisibleMenuManifest('ent-acme');assert.equal(calls.length, 1);
for (const failure of [new Error('403'), new Error('503'), { engineMode: 'UNKNOWN', items: [] }]) {
  calls = [];modeResponse = failure;
  await assert.rejects(() => navigationApi.getVisibleMenuManifest('ent-acme'));assert.equal(calls.length, 1, 'managed/unknown failures never fall back to legacy facts');
}
console.log('Menu navigation: directory, explicit-owner, engine-mode and fail-closed contracts passed');
const managedEditorApi = evaluateModule('../src/pages/menu-manifest/api.ts', { axios: { default: fakeAxios }, '@/pages/customer/contract.mjs': contract });
calls = [];modeResponse = { engineMode: 'LEGACY', items: [] };
await assert.rejects(() => managedEditorApi.getManagedManifest('ent-acme'), { status: 409, code: 'DOMAIN_NOT_MANAGED' });
assert.equal(calls.length, 1, 'explicit legacy mode rejects without reading management data');
assert.equal(calls[0], '/api/v1/enterprises/ent-acme/authorization/navigation');
calls = [];modeResponse = { engineMode: 'MANAGED', items: [] };
await managedEditorApi.getManagedManifest('ent-acme');assert.equal(calls[1], '/api/v1/enterprises/ent-acme/authorization/menu-layout');
calls = [];modeResponse = new Error('503');
await assert.rejects(() => managedEditorApi.getManagedManifest('ent-acme'));assert.equal(calls.length, 1);
console.log('Menu editor requires MANAGED and never falls back on LEGACY or dependency failure');
