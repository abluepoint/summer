import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import ts from 'typescript';
import React from 'react';
import Reconciler from 'react-reconciler';

function load(file, dependencies, globals = {}) {
  const exports = {};
  const code = ts.transpileModule(readFileSync(new URL(file, import.meta.url), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020, jsx: ts.JsxEmit.React, esModuleInterop: true },
  }).outputText;
  vm.runInNewContext(code, { exports, ...globals, require(name) {
    assert.ok(name in dependencies, `unexpected dependency: ${name}`);
    return dependencies[name];
  } });
  return exports;
}

function transport() {
  let timerId = 0;
  const intervals = new Map(), timeouts = new Map(), sockets = [], events = [], states = [];
  class Socket {
    static OPEN = 1;
    readyState = 0;
    sent = [];
    closeCalls = 0;
    constructor(url) { this.url = url; sockets.push(this); }
    send(payload) { this.sent.push(JSON.parse(payload)); }
    // Browser close events arrive asynchronously; retain the queued callback.
    close() { this.readyState = 3; this.closeCalls += 1; this.queuedClose = this.onclose; }
    opened() { this.readyState = Socket.OPEN; this.onopen?.({}); }
    ended(code = 1006) { this.readyState = 3; this.onclose?.({ code }); }
  }
  const api = load('../src/pages/messages/api.ts', { axios: {}, '../customer/contract.mjs': {} }, {
    WebSocket: Socket,
    window: { location: { protocol: 'http:', host: 'localhost' },
      setInterval(fn) { intervals.set(++timerId, fn); return timerId; },
      clearInterval(id) { intervals.delete(id); },
      setTimeout(fn, delay) { timeouts.set(++timerId, { fn, delay }); return timerId; },
      clearTimeout(id) { timeouts.delete(id); },
    },
  });
  const client = api.connectRealtime('ent-test', 4, event => events.push(event), state => states.push(state));
  return { client, sockets, intervals, timeouts, events, states,
    retry() { const [id, timer] = timeouts.entries().next().value; timeouts.delete(id); timer.fn(); },
  };
}

test('disposing an open socket ignores queued callbacks and stops all work', () => {
  const f = transport(), first = f.sockets[0];
  first.opened();
  const lateOpen = first.onopen, lateMessage = first.onmessage, lateClose = first.onclose, lateError = first.onerror;
  const states = [...f.states], eventCount = f.events.length;
  f.client.close(); f.client.close();
  first.queuedClose?.({ code: 1000 });
  lateClose({ code: 1006 }); lateOpen({}); lateMessage({ data: '{"type":"ack"}' }); lateError({});
  assert.deepEqual(f.states, states, 'cleanup must not notify an unmounted subscriber');
  assert.equal(f.events.length, eventCount);
  assert.equal(f.intervals.size, 0);
  assert.equal(f.timeouts.size, 0);
  assert.equal(first.closeCalls, 1, 'cleanup is idempotent');
  assert.equal(f.client.send({ type: 'ping' }), false);
});

test('disposing during handshake or reconnect cancels callbacks and pending retry', () => {
  const connecting = transport(), first = connecting.sockets[0], lateOpen = first.onopen;
  connecting.client.close(); lateOpen({}); first.queuedClose?.({ code: 1000 });
  assert.deepEqual(connecting.states, ['CONNECTING']);
  assert.equal(connecting.intervals.size, 0);

  const f = transport();
  f.sockets[0].opened(); f.sockets[0].ended();
  assert.equal(f.timeouts.size, 1);
  const queuedRetry = [...f.timeouts.values()][0].fn;
  f.client.close();
  assert.equal(f.timeouts.size, 0, 'cleanup must remove the retry timer');
  queuedRetry();
  assert.equal(f.sockets.length, 1);
});

test('live reconnect preserves cursor and heartbeat; stale sockets cannot affect the replacement', () => {
  const f = transport(), first = f.sockets[0];
  first.opened();
  first.onmessage({ data: '{"type":"message","message":{"sequence":9}}' });
  const stale = { open: first.onopen, message: first.onmessage, close: first.onclose, error: first.onerror };
  first.ended();
  assert.equal(f.intervals.size, 0);
  assert.equal([...f.timeouts.values()][0].delay, 250);
  f.retry();
  const second = f.sockets[1]; second.opened();
  assert.match(second.url, /after=9$/);
  assert.deepEqual(f.states, ['CONNECTING', 'OPEN', 'RECONNECTING', 'OPEN']);
  const stateCount = f.states.length, eventCount = f.events.length;
  stale.open({}); stale.message({ data: '{"type":"ack"}' }); stale.close({ code: 1006 }); stale.error({});
  assert.equal(f.states.length, stateCount);
  assert.equal(f.events.length, eventCount);
  assert.equal(f.intervals.size, 1);
  assert.equal(f.timeouts.size, 0);
  assert.equal(second.closeCalls, 0);
  [...f.intervals.values()][0]();
  assert.equal(second.sent[0].type, 'ping');
  assert.equal(f.client.send({ type: 'ack' }), true);
  second.ended(1008);
  assert.equal(f.events.at(-1).code, 'SUMMER_ACCESS_DENIED');
  assert.equal(f.states.at(-1), 'CLOSED');
  assert.equal(f.timeouts.size, 0);
  assert.equal(f.intervals.size, 0);
  f.client.close();
});

function pageFixture() {
  let disposed = false;
  const attempts = [], pending = [], sockets = [], listeners = new Set();
  const guardedReact = { ...React, useState(initial) {
    const [value, set] = React.useState(initial);
    return [value, next => { if (disposed) attempts.push(next); set(next); }];
  } };
  const api = {};
  for (const name of ['listMessages', 'acknowledgeMessage'])
    api[name] = (...args) => new Promise((resolve, reject) => pending.push({ name, args, resolve, reject }));
  api.connectRealtime = (enterpriseId, after, onEvent, onState) => {
    const socket = { onEvent, onState, closed: false, close() { this.closed = true; } };
    sockets.push(socket); onState('OPEN'); return socket;
  };
  const widgets = { Alert: 'alert', Button: 'button', Card: 'card', Input: 'input', Select: 'select', Space: 'space', Table: 'table', Tag: 'tag', Typography: { Text: 'p' } };
  const noop = () => {};
  const page = load('../src/pages/messages/index.tsx', {
    react: guardedReact, '@arco-design/web-react': widgets,
    '@/utils/useLocale': { __esModule: true, default: () => ({ 'messages.refresh': 'refresh' }) },
    '@/pages/data-scopes/useActiveEnterprise': { __esModule: true, default: () => ({ enterpriseId: 'ent-test', options: [], change: noop, setDirty: noop }) },
    '@/auth/oidc': { getAuthenticatedUser: () => Promise.resolve({}), oidcManager: { events: {
      addUserLoaded(fn) { listeners.add(fn); }, removeUserLoaded(fn) { listeners.delete(fn); },
      addUserUnloaded(fn) { listeners.add(fn); }, removeUserUnloaded(fn) { listeners.delete(fn); },
    } } }, '@/auth/businessCapabilities': { hasEnterpriseCapability: () => true }, './api': api,
  });
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
  renderer.updateContainer(React.createElement(page.default), root, null, noop);
  return { pending, sockets, attempts, findAll, listeners,
    flush: async () => { for (let i = 0; i < 3; i++) { renderer.flushPassiveEffects(); await new Promise(setImmediate); } },
    close() { renderer.updateContainer(null, root, null, noop); renderer.flushPassiveEffects(); disposed = true; },
  };
}
const message = { messageId: 'm1', sequence: 1, status: 'SENT', payload: { text: 'test' } };

test('mounted page still applies refresh, acknowledgement and connection state', async () => {
  const f = pageFixture();
  try {
    await f.flush(); f.pending[0].resolve({ items: [message], latestSequence: 1 }); await f.flush();
    f.findAll('card')[0].props.extra.props.children.find(child => child.type === 'button').props.onClick();
    const second = { ...message, messageId: 'm2', sequence: 2 };
    f.pending[1].resolve({ items: [second], latestSequence: 2 }); await f.flush();
    assert.equal(f.findAll('table')[0].props.data.length, 2);
    f.findAll('table')[0].props.columns.at(-1).render(null, message).props.onClick();
    f.pending[2].resolve({ ...message, status: 'ACKED' }); await f.flush();
    assert.equal(f.findAll('table')[0].props.data[0].status, 'ACKED');
    f.sockets[0].onState('RECONNECTING'); await f.flush();
    assert.equal(f.findAll('card')[0].props.extra.props.children[0].props.children, 'RECONNECTING');
  } finally { f.close(); }
});

test('late initial load cannot open a socket after page unmount', async () => {
  const f = pageFixture(); await f.flush();
  f.close(); f.pending[0].resolve({ items: [message], latestSequence: 1 }); await f.flush();
  assert.equal(f.sockets.length, 0);
  assert.equal(f.attempts.length, 0);
  assert.equal(f.listeners.size, 0);
});

for (const failed of [false, true]) test(`late refresh, acknowledgement and socket callbacks are ignored after unmount (${failed ? 'failure' : 'success'})`, async () => {
  const f = pageFixture();
  try {
    await f.flush(); f.pending[0].resolve({ items: [message], latestSequence: 1 }); await f.flush();
    const card = f.findAll('card')[0];
    const refresh = card.props.extra.props.children.find(child => child.type === 'button');
    refresh.props.onClick();
    f.findAll('table')[0].props.columns.at(-1).render(null, message).props.onClick();
    f.close();
    assert.equal(f.sockets[0].closed, true);
    f.sockets[0].onState('CLOSED');
    f.sockets[0].onEvent({ type: 'message', message });
    for (const p of f.pending.slice(1)) {
      if (failed) p.reject({ message: 'synthetic failure' });
      else p.resolve(p.name === 'listMessages' ? { items: [message], latestSequence: 1 } : { ...message, status: 'ACKED' });
    }
    await f.flush();
    assert.equal(f.attempts.length, 0, 'async callbacks must not attempt any state update after unmount');
    assert.equal(f.listeners.size, 0);
  } finally { f.close(); }
});
