import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import ts from 'typescript';
import React from 'react';
import Reconciler from 'react-reconciler';

function fixture() {
  const pending = [], exports = {};
  class ApiError extends Error { constructor(status) { super('synthetic failure'); this.status = status; } }
  const api = { InterfaceResourceApiError: ApiError };
  for (const name of ['listInterfaceResources', 'listInterfaceResourceHistory', 'getInterfaceResourceRevision', 'compareInterfaceResourceRevisions'])
    api[name] = (...args) => new Promise((resolve, reject) => pending.push({ name, args, resolve, reject }));
  const widgets = { Alert: 'alert', Button: 'button', Card: 'card', Modal: 'modal', Select: 'select', Space: 'space', Table: 'table', Tag: 'tag', Typography: { Paragraph: 'p', Title: 'h5' } };
  const code = ts.transpileModule(readFileSync(new URL('../src/pages/interface-resources/index.tsx', import.meta.url), 'utf8'), { compilerOptions: {
    module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020, jsx: ts.JsxEmit.React, esModuleInterop: true,
  } }).outputText;
  vm.runInNewContext(code, { exports, require(name) {
    if (name === 'react') return React;
    if (name === '@arco-design/web-react') return widgets;
    if (name === '@/components/AppLoading') return { __esModule: true, default: 'app-loading' };
    if (name === '@/utils/useLocale') return { __esModule: true, default: () => ({}) };
    if (name === './api') return api;
    if (name === './locale') return {};
    throw new Error(name);
  } });
  const append = (p, c) => p.children.push(c), remove = (p, c) => p.children.splice(p.children.indexOf(c), 1);
  const insert = (p, c, before) => p.children.splice(p.children.indexOf(before), 0, c);
  const renderer = Reconciler({
    now: Date.now, supportsMutation: true, isPrimaryRenderer: true,
    getRootHostContext: () => ({}), getChildHostContext: c => c, getPublicInstance: i => i,
    prepareForCommit: () => null, resetAfterCommit() {}, shouldSetTextContent: () => false,
    createInstance: (type, props) => ({ type, props, children: [] }), createTextInstance: text => ({ type: 'text', text, children: [] }),
    appendInitialChild: append, appendChild: append, appendChildToContainer: append,
    insertBefore: insert, insertInContainerBefore: insert,
    removeChild: remove, removeChildFromContainer: remove, prepareUpdate: () => true,
    commitUpdate: (i, _, type, old, props) => { i.props = props; }, commitTextUpdate: (i, old, text) => { i.text = text; },
    finalizeInitialChildren: () => false, clearContainer: c => { c.children = []; },
    scheduleTimeout: setTimeout, cancelTimeout: clearTimeout, noTimeout: -1,
  });
  const container = { children: [] }, root = renderer.createContainer(container, 0, false, null);
  const findAll = (type, node = container) => [...(node.type === type ? [node] : []), ...node.children.flatMap(c => findAll(type, c))];
  renderer.updateContainer(React.createElement(exports.default), root, null, () => {});
  return { pending, findAll, ApiError,
    currentTable: () => findAll('table').find(t => typeof t.props.rowKey === 'function'),
    flush: async () => { for (let i = 0; i < 4; i++) { renderer.flushPassiveEffects(); await new Promise(setImmediate); } },
    close() { renderer.updateContainer(null, root, null, () => {}); renderer.flushPassiveEffects(); },
  };
}
const page = (n = 0, revision = 7) => ({ items: [{ normalizedPath: `/page-${n}` }], page: n, size: 10, total: 31, revision, complete: true, issues: [] });

test('initial catalog requests one page and renders without waiting for history', async () => {
  const f = fixture();
  try {
    await f.flush();
    const read = f.pending.find(p => p.name === 'listInterfaceResources');
    assert.deepEqual(read.args.slice(0, 2), [0, 10]);
    read.resolve(page()); await f.flush();
    assert.equal(f.currentTable().props.data[0].normalizedPath, '/page-0');
    assert.equal(f.currentTable().props.pagination.total, 31);
    f.currentTable().props.pagination.onChange(2, 10); await f.flush();
    const next = f.pending.filter(p => p.name === 'listInterfaceResources').at(-1);
    assert.deepEqual(next.args.slice(0, 2), [1, 10]);
    assert.equal(next.args[2].revision, 7);
    assert.equal(f.pending.filter(p => p.name === 'listInterfaceResourceHistory').length, 1);
    next.resolve(page(1)); await f.flush();
    assert.equal(f.currentTable().props.pagination.current, 2);
    f.currentTable().props.pagination.onChange(1, 20); await f.flush();
    const resized = f.pending.filter(p => p.name === 'listInterfaceResources').at(-1);
    assert.deepEqual(resized.args.slice(0, 2), [0, 20]);
    assert.equal(resized.args[2].revision, 7);
    resized.resolve({ ...page(), size: 20 }); await f.flush();
    assert.equal(f.currentTable().props.pagination.pageSize, 20);

  } finally { f.close(); }
});

test('rapid page changes reject late data and authorization failure clears protected rows', async () => {
  const f = fixture();
  try {
    await f.flush(); f.pending.find(p => p.name === 'listInterfaceResources').resolve(page()); await f.flush();
    f.currentTable().props.pagination.onChange(2, 10);
    f.currentTable().props.pagination.onChange(3, 10); await f.flush();
    const reads = f.pending.filter(p => p.name === 'listInterfaceResources');
    reads[2].resolve(page(2)); await f.flush(); reads[1].resolve(page(1)); await f.flush();
    assert.equal(f.currentTable().props.data[0].normalizedPath, '/page-2');
    f.currentTable().props.pagination.onChange(4, 10); await f.flush();
    f.pending.at(-1).reject(new f.ApiError(403)); await f.flush();
    assert.equal(f.currentTable(), undefined);
    f.pending.find(p => p.name === 'listInterfaceResourceHistory').resolve({ items: [], total: 0, page: 0, size: 20 });
    await f.flush(); assert.equal(f.findAll('table').length, 0);
  } finally { f.close(); }
});

test('snapshot details request pages and a late page cannot reopen a closed dialog', async () => {
  const f = fixture();
  try {
    await f.flush();
    f.pending.find(p => p.name === 'listInterfaceResources').resolve(page());
    f.pending.find(p => p.name === 'listInterfaceResourceHistory').resolve({ items: [{ revision: 6 }], total: 1, page: 0, size: 20 });
    await f.flush();
    const history = f.findAll('table').find(t => t.props.rowKey === 'revision');
    history.props.columns.at(-1).render(null, { revision: 6 }).props.onClick();
    await f.flush();
    const initial = f.pending.find(p => p.name === 'getInterfaceResourceRevision');
    assert.deepEqual(initial.args, [6, 0, 10]);
    initial.resolve(page(0, 6)); await f.flush();
    assert.equal(f.findAll('modal')[0].props.visible, true);
    f.findAll('table').at(-1).props.pagination.onChange(2, 10); await f.flush();
    const next = f.pending.filter(p => p.name === 'getInterfaceResourceRevision').at(-1);
    assert.deepEqual(next.args, [6, 1, 10]);
    f.findAll('modal')[0].props.onCancel();
    next.resolve(page(1, 6)); await f.flush();
    assert.equal(f.findAll('modal')[0].props.visible, false);
  } finally { f.close(); }
});

for (const outcome of ['resolve', 'reject']) {
  test(`refresh clears an invalidated snapshot load and ignores its late ${outcome}`, async () => {
    const f = fixture();
    try {
      await f.flush();
      f.pending.find(p => p.name === 'listInterfaceResources').resolve(page());
      f.pending.find(p => p.name === 'listInterfaceResourceHistory').resolve({ items: [{ revision: 6 }], total: 1, page: 0, size: 20 });
      await f.flush();
      const view = () => f.findAll('table').find(t => t.props.rowKey === 'revision')
        .props.columns.at(-1).render(null, { revision: 6 });
      view().props.onClick(); await f.flush();
      const stale = f.pending.find(p => p.name === 'getInterfaceResourceRevision');
      assert.equal(view().props.loading, true);

      f.findAll('card')[0].props.extra.props.onClick(); await f.flush();
      f.pending.filter(p => p.name === 'listInterfaceResources').at(-1).resolve(page(0, 8));
      f.pending.filter(p => p.name === 'listInterfaceResourceHistory').at(-1).resolve({ items: [{ revision: 6 }], total: 1, page: 0, size: 20 });
      await f.flush();
      assert.equal(view().props.loading, false);
      assert.equal(f.findAll('modal')[0].props.visible, false);

      view().props.onClick(); await f.flush();
      const retry = f.pending.filter(p => p.name === 'getInterfaceResourceRevision').at(-1);
      assert.notEqual(retry, stale);
      if (outcome === 'resolve') stale.resolve(page(0, 6));
      else stale.reject(new f.ApiError(403));
      await f.flush();
      assert.equal(view().props.loading, true);
      assert.ok(f.currentTable());
      assert.equal(f.findAll('modal')[0].props.visible, false);

      retry.resolve(page(0, 6)); await f.flush();
      assert.equal(view().props.loading, false);
      assert.equal(f.findAll('modal')[0].props.visible, true);
    } finally { f.close(); }
  });
}
