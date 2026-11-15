// Public selection/subscription contract; synthetic identity and confirmation UI.
// No browser, backend, or real authentication is exercised by these tests.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import test from 'node:test';
import ts from 'typescript';
import React from 'react';
import Reconciler from 'react-reconciler';

function fixture(initial, deferredInitial, batch = (fn) => fn()) {
  let currentUser = initial, firstRead = true;
  const loaded = new Set(), unloaded = new Set();
  const confirmations = [];
  const exports = {};
  const source = readFileSync(new URL('../src/pages/data-scopes/useActiveEnterprise.ts', import.meta.url), 'utf8');
  const code = ts.transpileModule(source, { compilerOptions: {
    module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020,
  } }).outputText;
  vm.runInNewContext(code, {
    exports, atob, Map, Set, Symbol,
    require(name) {
      if (name === 'react') return React;
      if (name === 'react-dom') return { unstable_batchedUpdates: batch };
      if (name === '@arco-design/web-react') return { Modal: { confirm: (options) => confirmations.push(options) } };
      if (name === '@/auth/oidc') return {
        getAuthenticatedUser: () => {
          const pending = firstRead && deferredInitial; firstRead = false;
          return pending || Promise.resolve(currentUser);
        },
        oidcManager: { events: {
          addUserLoaded: (fn) => loaded.add(fn), removeUserLoaded: (fn) => loaded.delete(fn),
          addUserUnloaded: (fn) => unloaded.add(fn), removeUserUnloaded: (fn) => unloaded.delete(fn),
        } },
      };
      throw new Error(`Unexpected dependency: ${name}`);
    },
  });
  return {
    selection: exports.activeEnterpriseSelection, confirmations,
    load: (user) => { currentUser = user; loaded.forEach((fn) => fn(user)); },
    unload: () => { currentUser = null; unloaded.forEach((fn) => fn()); },
    getUser: () => Promise.resolve(currentUser),
    useEnterprise: exports.default,
    listenerCount: () => loaded.size + unloaded.size,
  };
}
const user = (memberships = ['company-a', 'company-b'], extra = {}, subject = 'component-user') => ({
  profile: { sub: subject, enterprise_memberships: memberships.map((enterpriseId) => ({ enterpriseId, status: 'ACTIVE' })), ...extra },
  access_token: 'opaque-component-fixture', expired: false,
});

// Real React 17, its installed host reconciler, and the actual shell controller.
// Host widgets and the routed-page output are controlled render boundaries: this
// tests shell lifecycle/transport races, not Arco pixels, routing, or real OIDC.
function shellFixture(identity, { retryLogin = async () => true } = {}) {
  let batch = (fn) => fn();
  let pageTenant;
  const f = fixture(identity, undefined, (fn) => batch(fn)), reads = [], focus = new Set(), commits = [], intervals = new Map();
  const menuContext = React.createContext([]);
  const redux = { state: { userInfo: { permissions: {} } }, dispatch(action) { this.state = { ...this.state, ...action.payload }; }, getState() { return this.state; } };
  const permissionsSource = readFileSync(new URL('../src/menu/permissions.ts', import.meta.url), 'utf8');
  const permissionExports = {};
  vm.runInNewContext(ts.transpileModule(permissionsSource, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText, { exports: permissionExports });
  function RoutedPage() {
    const tenant = f.useEnterprise(), menu = React.useContext(menuContext);
    pageTenant = tenant;
    return React.createElement('routed-page', { enterpriseId: tenant.enterpriseId, menu });
  }
  const mainPath = process.env.SUMMER_ENTERPRISE_MAIN_SOURCE || new URL('../src/main.tsx', import.meta.url);
  const main = readFileSync(mainPath, 'utf8');
  const ast = ts.createSourceFile('main.tsx', main, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  const controller = ast.statements.find((statement) => ts.isFunctionDeclaration(statement) && statement.name?.text === 'AuthenticatedLayout');
  assert.ok(controller, 'Shell controller must be present');
  const compiled = ts.transpileModule(controller.getText(ast) + '\nexports.Component = AuthenticatedLayout;', { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020, jsx: ts.JsxEmit.React } }).outputText;
  const refreshExports = {};
  vm.runInNewContext(ts.transpileModule(readFileSync(new URL('../src/menu/refresh.ts', import.meta.url), 'utf8'), { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText, { exports: refreshExports });
  const menuRefresh = new Set();
  const shellExports = {};
  vm.runInNewContext(compiled, {
    MENU_REFRESH_EVENT: refreshExports.MENU_REFRESH_EVENT,
    exports: shellExports, React, useEffect: React.useEffect, useState: React.useState,
    useActiveEnterprise: f.useEnterprise, activeEnterpriseSelection: f.selection,
    getAuthenticatedUser: f.getUser, authenticationStatus: async () => 'missing',
    startLoginForUnavailable: async () => false, isAuthRedirecting: () => false,
    restartLoginManually: retryLogin, Button: 'button',
    consumeAuthNotice: () => null, authText: (key) => key, useLocale: () => ({}),
    store: redux, menuManifest: { manifestVersion: 1, items: [] },
    loadMenuPermissions: permissionExports.loadMenuPermissions,
    syncMenuManifest: async () => {},
    getManagedMenuManifest: async () => ({manifestVersion: 0,items: []}),
    getVisibleMenuManifest: (enterpriseId) => new Promise((resolve, reject) => reads.push({ enterpriseId, resolve, reject })),
    // Operation display hints are an independent transport boundary; this shell fixture
    // supplies no grants and keeps its original tenant/navigation lifecycle assertions.
    useInterfaceOperations: () => ({}),
    InterfaceOperationHints: React.createContext({ actions: {} }),
    PageLayout: RoutedPage, MenuAccessContext: menuContext,
    Spin: 'spinner', AppLoading: 'app-loading', Alert: 'alert', Card: 'card', Select: 'enterprise-select', Message: { info() {} },
    window: { setInterval: (fn, delay) => { const id = Symbol(); intervals.set(id, { fn, delay }); return id; }, clearInterval: id => intervals.delete(id), addEventListener: (type, fn) => { if (type === 'focus') focus.add(fn); if (type === refreshExports.MENU_REFRESH_EVENT) menuRefresh.add(fn); }, removeEventListener: (type, fn) => { if (type === 'focus') focus.delete(fn); if (type === refreshExports.MENU_REFRESH_EVENT) menuRefresh.delete(fn); } },
    document: { visibilityState: 'visible', addEventListener() {}, removeEventListener() {} },
  });
  const append = (parent, child) => { parent.children.push(child); };
  const remove = (parent, child) => { parent.children.splice(parent.children.indexOf(child), 1); };
  const renderer = Reconciler({
    now: Date.now, supportsMutation: true, isPrimaryRenderer: true,
    getRootHostContext: () => ({}), getChildHostContext: (context) => context,
    getPublicInstance: (instance) => instance, prepareForCommit: () => null,
    resetAfterCommit() { commits.push(find('routed-page')?.props); },
    shouldSetTextContent: () => false, createInstance: (type, props) => ({ type, props, children: [] }),
    createTextInstance: (text) => ({ type: 'text', text, children: [] }),
    appendInitialChild: append, appendChild: append, appendChildToContainer: append,
    removeChild: remove, removeChildFromContainer: remove,
    prepareUpdate: () => true, commitUpdate: (instance, payload, type, oldProps, props) => { instance.props = props; },
    commitTextUpdate: (instance, oldText, text) => { instance.text = text; },
    finalizeInitialChildren: () => false, clearContainer: (container) => { container.children = []; },
    scheduleTimeout: setTimeout, cancelTimeout: clearTimeout, noTimeout: -1,
  });
  const container = { children: [] }, root = renderer.createContainer(container, 0, false, null);
  const find = (type, node = container) => node.type === type ? node : node.children.map((child) => find(type, child)).find(Boolean);
  batch = renderer.batchedUpdates;
  renderer.updateContainer(React.createElement(shellExports.Component), root, null, () => {});
  return { ...f, reads, redux, find, commits, pageTenant: () => pageTenant, focus: () => focus.forEach((fn) => fn()), invalidate: (contextVersion) => menuRefresh.forEach(fn => fn({ detail: { contextVersion } })),
    elapse: (ms) => { for (const { fn, delay } of intervals.values()) for (let elapsed = delay; elapsed <= ms; elapsed += delay) fn(); },
    flush: async () => { for (let i = 0; i < 4; i++) { renderer.flushPassiveEffects(); await tick(); } },
    close: () => { renderer.updateContainer(null, root, null, () => {}); renderer.flushPassiveEffects(); },
  };
}
const manifest = (id) => ({ manifestVersion: 1, items: [{ menuId: id, enabled: true, actions: ['read'] }] });

test('startup keeps one loading surface until menus settle and does not return to it on background reads', async () => {
  const f = shellFixture(user(['company-a']));
  try {
    assert.ok(f.find('app-loading'), 'Identity bootstrap must use the same surface as the callback');
    await f.flush();
    assert.equal(f.reads.length, 1);
    assert.ok(f.find('app-loading'), 'Menu bootstrap must retain the startup surface');
    assert.equal(f.find('routed-page'), undefined);
    f.reads[0].resolve(manifest('messages')); await f.flush();
    assert.equal(f.find('app-loading'), undefined);
    const page = f.find('routed-page');
    f.focus(); await f.flush();
    assert.equal(f.find('routed-page'), page, 'Background permission refresh must retain the mounted page');
    assert.equal(f.find('app-loading'), undefined);
    f.reads[1].resolve(manifest('messages')); await f.flush();
    assert.equal(f.find('routed-page'), page);
  } finally { f.close(); }
});

test('the authenticated shell offers explicit enterprise selection before any tenant menu is available', async () => {
  const f = shellFixture(user());
  try {
    await f.flush();
    const selector = f.find('enterprise-select');
    assert.ok(selector, 'Multi-enterprise users must be able to choose in the shell before routed pages mount');
    assert.equal(f.reads.length, 0);
    assert.equal(f.find('routed-page'), undefined);
    selector.props.onChange('company-b'); await f.flush();
    assert.equal(f.reads[0].enterpriseId, 'company-b');
    f.reads[0].resolve(manifest('statistics')); await f.flush();
    assert.equal(f.find('routed-page').props.enterpriseId, 'company-b');
  } finally { f.close(); }
});

test('a tenant switch removes the old routed page in the same render and rejects a late refresh before effect cleanup', async () => {
  const f = shellFixture(user(['company-a', 'company-b'], { active_enterprise_id: 'company-a' }));
  try {
    await f.flush(); f.reads[0].resolve(manifest('company-a-page')); await f.flush();
    assert.equal(f.find('routed-page').props.enterpriseId, 'company-a');
    f.focus(); await f.flush(); assert.equal(f.reads.length, 2);
    const beforeSwitch = f.commits.length;
    f.selection.change('company-b');
    assert.ok(f.commits.slice(beforeSwitch).every((page) => !page), 'No intermediate commit may expose the old tenant menu with the new selection');
    assert.equal(f.find('routed-page'), undefined, 'Old business UI must disappear before passive effects run');
    f.reads[1].resolve(manifest('late-company-a-page'));
    await f.flush();
    const b = f.reads.find((request) => request.enterpriseId === 'company-b');
    assert.ok(b); b.reject(new Error('Synthetic denied enterprise menu')); await f.flush();
    assert.equal(f.find('routed-page').props.enterpriseId, 'company-b');
    assert.equal(f.find('routed-page').props.menu.length, 0);
    assert.deepEqual(Object.keys(f.redux.getState().userInfo.permissions), []);
  } finally { f.close(); }
});

test('refreshing organization identity in the same enterprise invalidates the old page and pending menu', async () => {
  const f = shellFixture(user(['company-a']));
  try {
    await f.flush(); f.reads[0].resolve(manifest('old-organization-page')); await f.flush();
    f.focus(); await f.flush();
    f.load(user(['company-a'], { organization_context: { enterpriseId: 'company-a', organizationId: 'new-assigned-organization' } }));
    assert.equal(f.find('routed-page'), undefined);
    f.reads[1].resolve(manifest('late-old-organization-page')); await f.flush();
    assert.equal(f.reads.length, 3);
    f.reads[2].resolve(manifest('new-organization-page')); await f.flush();
    assert.equal(f.find('routed-page').props.menu[0].menuId, 'new-organization-page');
  } finally { f.close(); }
});
const tick = () => new Promise((done) => setImmediate(done));

test('a multi-enterprise user explicitly selects once and all subscribers receive the same enterprise', async () => {
  const f = fixture(user());
  assert.ok(f.selection, 'The authenticated shell and pages need a shared enterprise selection contract');
  const shell = [], page = [];
  const stopShell = f.selection.subscribe(() => shell.push(f.selection.getSnapshot().enterpriseId));
  const stopPage = f.selection.subscribe(() => page.push(f.selection.getSnapshot().enterpriseId));
  await tick();
  assert.equal(f.selection.getSnapshot().ready, true);
  assert.equal(f.selection.getSnapshot().enterpriseId, '', 'Two memberships must not silently select the first');
  f.selection.change('company-b');
  assert.equal(shell.at(-1), 'company-b');
  assert.equal(page.at(-1), 'company-b');
  assert.equal(f.selection.getSnapshot().enterpriseId, 'company-b');
  stopPage(); stopShell();
});

test('inactive or unknown enterprises cannot be selected and a sole active member is unambiguous', async () => {
  const f = fixture(user(['company-a'], { enterprise_memberships: [
    { enterpriseId: 'company-a', status: 'ACTIVE' },
    { enterpriseId: 'company-disabled', status: 'DISABLED' },
  ] }));
  const stop = f.selection.subscribe(() => {}); await tick();
  assert.equal(f.selection.getSnapshot().enterpriseId, 'company-a');
  f.selection.change('company-disabled'); f.selection.change('unknown-company');
  assert.equal(f.selection.getSnapshot().enterpriseId, 'company-a');
  stop();
});

test('shell switching respects every page guard and unmount only releases its own guard', async () => {
  const f = fixture(user()); const stop = f.selection.subscribe(() => {}); await tick();
  f.selection.change('company-a');
  const editing = Symbol('editing-page'), saving = Symbol('saving-page');
  f.selection.setGuard(editing, { dirty: true });
  f.selection.setGuard(saving, { busy: true });
  f.selection.change('company-b');
  assert.equal(f.confirmations.length, 0, 'A save blocks switching even through the shell');
  assert.equal(f.selection.getSnapshot().enterpriseId, 'company-a');
  f.selection.releaseGuard(editing);
  assert.equal(f.selection.getSnapshot().busy, true, 'Unmounting another page cannot release a save');
  f.selection.releaseGuard(saving);
  f.selection.setGuard(editing, { dirty: true });
  f.selection.change('company-b');
  assert.equal(f.confirmations.length, 1);
  assert.equal(f.selection.getSnapshot().enterpriseId, 'company-a', 'Pending/cancelled confirmation preserves the enterprise');
  f.confirmations[0].onOk();
  assert.equal(f.selection.getSnapshot().enterpriseId, 'company-b');
  f.selection.releaseGuard(editing); stop();
});

test('identity refresh and logout invalidate old selection and delayed confirmations', async () => {
  const f = fixture(user()); const stop = f.selection.subscribe(() => {}); await tick();
  f.selection.change('company-a'); const version = f.selection.getSnapshot().contextVersion;
  f.selection.setGuard(Symbol(), { dirty: true }); f.selection.change('company-b');
  f.load(user(['company-b'], { active_enterprise_id: 'company-b' }));
  assert.equal(f.selection.getSnapshot().enterpriseId, 'company-b');
  assert.ok(f.selection.getSnapshot().contextVersion > version);
  f.unload(); f.confirmations[0].onOk();
  assert.equal(f.selection.getSnapshot().enterpriseId, '');
  assert.equal(f.selection.getSnapshot().options.length, 0);
  f.load(user(['company-a'], {}, 'different-component-user'));
  assert.equal(f.selection.getSnapshot().enterpriseId, 'company-a');
  stop(); assert.equal(f.listenerCount(), 0);
});

test('a delayed initial identity read cannot overwrite a newer signed-in identity event', async () => {
  let settle; const pending = new Promise((resolve) => { settle = resolve; });
  const f = fixture(null, pending); const stop = f.selection.subscribe(() => {});
  f.load(user(['company-b'])); settle(user(['company-a'])); await tick();
  assert.equal(f.selection.getSnapshot().enterpriseId, 'company-b'); stop();
});

test('unmounted hook consumers release only their own guard and ignore delayed save callbacks', async () => {
  const f = shellFixture(user(['company-a']));
  const keep = f.selection.subscribe(() => {}), other = Symbol('another-consumer');
  try {
    await f.flush(); f.reads[0].resolve(manifest('company-a-page')); await f.flush();
    const page = f.pageTenant(); page.setBusy(true);
    f.selection.setGuard(other, { dirty: true });
    assert.equal(f.selection.getSnapshot().busy, true);
    f.close();
    assert.equal(f.selection.getSnapshot().busy, false);
    assert.equal(f.selection.getSnapshot().dirty, true);
    page.setBusy(true); page.setDirty(false);
    assert.equal(f.selection.getSnapshot().busy, false, 'Late callbacks cannot recreate an unmounted page guard');
    assert.equal(f.selection.getSnapshot().dirty, true);
  } finally { f.selection.releaseGuard(other); keep(); f.close(); }
});


test('an exhausted login cycle renders a usable manual retry and keeps it usable after failure', async () => {
  let attempts = 0;
  const f = shellFixture(null, { retryLogin: async () => { attempts++; throw new Error('offline'); } });
  try {
    await f.flush();
    assert.equal(attempts, 0, 'exhaustion must not create an automatic redirect loop');
    let retry = f.find('alert')?.props.action;
    assert.equal(retry?.type, 'button', 'the blocked shell must offer a sign-in action');
    await retry.props.onClick();
    await f.flush();
    assert.equal(attempts, 1);
    retry = f.find('alert')?.props.action;
    assert.equal(retry?.type, 'button', 'a failed redirect must not strand the shell on a spinner');
    assert.equal(retry.props.loading, false);
    await retry.props.onClick();
    assert.equal(attempts, 2, 'each explicit click may retry once');
  } finally { f.close(); }
});


test('an idle authenticated shell never polls the menu manifest, while focus still refreshes it', async () => {
  const f = shellFixture(user(['company-a']));
  try {
    await f.flush();
    assert.equal(f.reads.length, 1);
    f.reads[0].resolve(manifest('messages')); await f.flush();
    const page = f.find('routed-page');
    f.elapse(60000); await f.flush();
    assert.equal(f.reads.length, 1, 'Elapsed time alone must not request the menu manifest');
    f.focus(); await f.flush();
    assert.equal(f.reads.length, 2, 'Returning to the app must still refresh effective permissions');
    f.reads[1].resolve(manifest('messages')); await f.flush();
    assert.equal(f.find('routed-page'), page);
    f.elapse(60000); await f.flush();
    assert.equal(f.reads.length, 2, 'An event refresh must not start a polling loop');
  } finally { f.close(); }
});

test('saved menu facts refresh only the matching current context and retain unchanged page identity', async () => {
  const f = shellFixture(user(['company-a']));
  try {
    await f.flush();f.reads[0].resolve(manifest('messages'));await f.flush();
    const page=f.find('routed-page'),version=f.selection.getSnapshot().contextVersion;
    f.invalidate(version-1);await f.flush();assert.equal(f.reads.length,1);
    f.invalidate(version);await f.flush();assert.equal(f.reads.length,2);
    f.reads[1].resolve(manifest('messages'));await f.flush();assert.equal(f.find('routed-page'),page);
    f.invalidate(version);await f.flush();f.reads[2].reject(new Error('503'));await f.flush();
    assert.deepEqual(JSON.parse(JSON.stringify(f.find('routed-page').props.menu)),[],'failed current refresh clears old qualifications');
  } finally { f.close(); }
});
