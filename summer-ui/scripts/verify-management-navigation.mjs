import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import ts from 'typescript';
function module(path, deps = {}) {
  const exports = {};
  vm.runInNewContext(
    ts.transpileModule(
      fs.readFileSync(new URL(path, import.meta.url), 'utf8'),
      {
        compilerOptions: {
          module: ts.ModuleKind.CommonJS,
          target: ts.ScriptTarget.ES2020,
        },
      }
    ).outputText,
    {
      exports,
      require(name) {
        assert.ok(name in deps, name);
        return deps[name];
      },
    }
  );
  return exports;
}
const mapping = module('../src/menu/managementNavigation.ts');
const row = (menuId, extra = {}) => ({
  menuId,
  path: '/' + menuId,
  titleKey: menuId,
  sortOrder: 1,
  enabled: true,
  pageEligible: true,
  navOnly: false,
  actions: [],
  version: 1,
  ...extra,
});
function fixture({
  tenant = [row('roles')],
  platform = [row('roles')],
  denied = false,
  clientCode = 'web',
} = {}) {
  const calls = [];
  const api = module('../src/menu/api.ts', {
    './managementNavigation': mapping,
    './client': {menuClientCode: clientCode},
    '@/pages/customer/contract.mjs': {
      readEnvelope: (x) => x,
      readFailure: (x) => x,
    },
    axios: {
      default: {
        isAxiosError: () => false,
        get: async (path, config) => {
          assert.equal(config.params.clientCode, clientCode);
          calls.push(path);
          const isPlatform = path.includes('/platform/');
          if (isPlatform && denied) throw Error('denied');
          return {
            status: 200,
            data: {
              engineMode: 'MANAGED',
              manifestVersion: 6,
              items: isPlatform ? platform : tenant,
            },
          };
        },
      },
    },
  });
  return { api, calls };
}
const plain = (x) => JSON.parse(JSON.stringify(x));
test('one identity can reach distinct platform and tenant entries from independently qualified grants', async () => {
  const { api, calls } = fixture();
  const view = await api.getVisibleMenuManifest('tenant/a');
  assert.deepEqual(
    plain(view.items.map((x) => [x.menuId, x.path, x.titleKey])),
    [
      ['roles', '/roles', 'roles'],
      ['platform/roles', '/platform/roles', 'roles'],
    ]
  );
  assert.deepEqual(calls, [
    '/api/v1/enterprises/tenant%2Fa/authorization/navigation',
    '/api/v1/platform/authorization/navigation',
  ]);
});
test('tenant grant cannot create platform entry when platform authorization fails', async () => {
  const { api } = fixture({
    tenant: [row('roles'), row('platform/permissions')],
    denied: true,
  });
  const view = await api.getVisibleMenuManifest('tenant-a');
  assert.deepEqual(plain(view.items.map((x) => x.menuId)), ['roles']);
});
test('platform grants never create tenant entry or inherit enterprise qualification', async () => {
  const { api } = fixture({
    platform: [
      row('roles', { navOnly: true, pageEligible: false }),
      row('permissions'),
    ],
  });
  const view = await api.getManagedMenuManifest();
  assert.deepEqual(plain(view.items.map((x) => x.menuId)), [
    'platform/roles',
    'platform/permissions',
  ]);
  assert.equal(view.items[0].pageEligible, false);
  assert.equal(view.items[0].navOnly, true);
});
test('platform revocation clears old platform entry on the next navigation read', async () => {
  let allowed = true;
  const platform = [row('roles')];
  const { api } = fixture({ platform });
  assert.equal((await api.getVisibleMenuManifest('tenant-a')).items.length, 2);
  platform.length = 0;
  assert.deepEqual(
    plain(
      (await api.getVisibleMenuManifest('tenant-a')).items.map((x) => x.menuId)
    ),
    ['roles']
  );
});
test('disabled entries and parent relationships retain their access semantics', () => {
  const result = mapping.managementNavigation(
    [row('roles', { enabled: false }), row('child', { parentMenuId: 'roles' })],
    'PLATFORM', 'web'
  );
  assert.equal(result[0].enabled, false);
  assert.equal(result[1].parentMenuId, 'platform/roles');
});

const route = (key, extra = {}) => ({ key, name: key, exact: true, ...extra });
const flatten = (routes) =>
  routes.flatMap((item) => [item, ...flatten(item.children || [])]);
test('platform entries carry their published ancestors into a tenant shell as directories', async () => {
  const {api} = fixture({tenant:[], platform:[
    row('folder'), row('platform-only-unrelated'), row('permissions',{parentMenuId:'folder',titleKey:'自定义权限'}),
  ]});
  const view = await api.getVisibleMenuManifest('tenant-a');
  assert.deepEqual(plain(view.items.map(x=>x.menuId).sort()), ['folder','platform/permissions']);
  const parent = view.items.find(x=>x.menuId==='folder');
  assert.equal(parent.pageEligible,false);
  assert.equal(parent.navOnly,true);
  assert.equal(view.items[0].parentMenuId,'folder');
  assert.equal(view.items[0].titleKey,'自定义权限');
});

test('registered pages use catalog-only folders, published sibling order and names', () => {
  const access = module('../src/menu/access.ts', {
    react: {createContext:()=>({})}, '@/auth/interfaceOperations': {},
  });
  const tree = access.resolveMenuRoutes([route('platform/permissions', {children:[route('unpublished-page')]}),route('interface-resources')], [
    row('folder',{titleKey:'业务入口'}), row('platform/permissions',{parentMenuId:'folder',sortOrder:10}),
    row('interface-resources',{parentMenuId:'folder',sortOrder:3}), row('unknown-leaf')
  ]);
  assert.deepEqual(plain(flatten(tree).map(x=>x.key)), ['folder','interface-resources','platform/permissions']);
  assert.equal(tree[0].navOnly,true);
  assert.equal(tree[0].name,'业务入口');
  assert.deepEqual(plain(mapping.menuAncestorKeys(tree,'platform/permissions')),['folder']);
});

test('tenant permission grants never restore a custom permission page or qualify platform management', async () => {
  const { api } = fixture({
    tenant: [
      row('permissions'),
      row('interface-resources', { parentMenuId: 'permissions' }),
    ],
    denied: true,
  });
  const view = await api.getVisibleMenuManifest('tenant-a');
  assert.deepEqual(plain(view.items.map((item) => item.menuId)), [
    'interface-resources',
  ]);
  assert.equal(view.items[0].parentMenuId, undefined);
});

test('retiring the tenant permission entry preserves disabled ancestors of its children', () => {
  const access = module('../src/menu/access.ts', {
    react: { createContext: () => ({}) },
    '@/auth/interfaceOperations': {},
  });
  for (const disabled of ['permissions', 'parent']) {
    const items = mapping.managementNavigation(
      [
        row('parent', { enabled: disabled !== 'parent' }),
        row('permissions', {
          parentMenuId: 'parent',
          enabled: disabled !== 'permissions',
        }),
        row('interface-resources', { parentMenuId: 'permissions' }),
      ],
      'ENTERPRISE', 'web'
    );
    const visible = access.resolveMenuRoutes(
      ['parent', 'permissions', 'interface-resources'].map((key) => route(key)),
      items
    );
    assert.equal(
      flatten(visible).some((item) => item.key === 'interface-resources'),
      false
    );
  }
});

test('web navigation uses platform menu management and web client assignments', async () => {
  const { api } = fixture({ tenant: [row('menu-manifest'), row('mobile-page', { clientCode: 'mobile' })],
    platform: [row('menu-manifest', { clientCode: 'web' })] });
  const view = await api.getVisibleMenuManifest('tenant-a');
  assert.deepEqual(plain(view.items.map(item => item.menuId)), ['platform/menu-manifest']);
});

test('a different frontend client requests and renders only that client', async () => {
  const {api} = fixture({clientCode:'tablet', tenant:[row('root'),row('tablet-page',{parentMenuId:'root',clientCode:'tablet'})]});
  const view = await api.getVisibleMenuManifest('tenant-a');
  assert.deepEqual(plain(view.items.map(x=>x.menuId)), ['tablet-page']);
  assert.equal(view.items[0].parentMenuId,undefined);
});

test('the published access control folder contains four platform entrances and keeps tenant roles separate', async () => {
  const folder = 'access-control';
  const pages = ['interface-resources', 'permissions', 'menu-manifest', 'platform/roles'];
  const {api} = fixture({tenant: [row('roles'), row('platform/roles')], platform: [
    row(folder, {navOnly:true, pageEligible:false}),
    ...pages.map((id, sortOrder) => row(id, {parentMenuId:folder, sortOrder})),
  ]});
  const view = await api.getVisibleMenuManifest('tenant-a');
  assert.deepEqual(plain(view.items.filter(x=>x.parentMenuId===folder).map(x=>x.path)), [
    '/interface-resources', '/platform/permissions', '/platform/menu-manifest', '/platform/roles',
  ]);
  assert.equal(view.items.filter(x=>x.menuId==='platform/roles').length,1);
  assert.equal(view.items.find(x=>x.menuId==='roles').parentMenuId,undefined);
});

test('dictionary management and groups require independent platform menu grants', async () => {
  const ids = ['dictionary/manage', 'dictionary/groups'];
  const tenant = ids.map((id) => row(id));
  const platform = ids.map((id) => row(id, { parentMenuId: 'dictionary' }));
  const { api } = fixture({ tenant, platform: [row('dictionary'), ...platform] });
  const visible = await api.getVisibleMenuManifest('tenant-a');
  for (const id of ids)
    assert.equal(visible.items.find((item) => item.menuId === id)?.parentMenuId, 'dictionary');
  const denied = fixture({ tenant, denied: true });
  assert.deepEqual(plain((await denied.api.getVisibleMenuManifest('tenant-a')).items), []);
});
