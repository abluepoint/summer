import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import ts from 'typescript';
import * as recovery from '../src/auth/recovery.mjs';

function loadAuth() {
  const values = new Map();
  const storage = {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: (key) => values.delete(key),
  };
  const calls = [];
  let redirect = async () => {};
  class UserManager {
    events = {};
    async getUser() { return null; }
    async removeUser() { calls.push('removeUser'); }
    async signinRedirect(options) { calls.push(options); await redirect(); }
  }
  const source = readFileSync(new URL('../src/auth/oidc.ts', import.meta.url), 'utf8');
  const compiled = ts.transpileModule(source.replaceAll('import.meta.env', '({})'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const module = { exports: {} };
  vm.runInNewContext(compiled, {
    exports: module.exports,
    require: (name) => name === './recovery.mjs' ? recovery : {
      UserManager, InMemoryWebStorage: class {}, WebStorageStateStore: class {},
    },
    window: { sessionStorage: storage, location: {
      origin: 'http://127.0.0.1:5173', pathname: '/messages', search: '?after=7', hash: '#history',
    } },
    crypto: { getRandomValues: (bytes) => bytes.fill(1) },
  });
  return { auth: module.exports, storage, calls, setRedirect: (value) => { redirect = value; } };
}

test('explicit sign in recovers the same route from both exhausted automatic guards', async () => {
  const { auth, storage, calls } = loadAuth();
  recovery.claimLoginStart(storage);
  recovery.claimUnauthorizedRestart(storage);
  auth.registerCustomerDraftProvider(() => ({ displayName: 'unsaved draft', password: 'excluded' }));
  assert.equal(await auth.startLoginForUnavailable('missing'), false);
  assert.equal(typeof auth.restartLoginManually, 'function', 'blocked authentication needs an explicit recovery action');
  assert.equal(await auth.restartLoginManually(), true);
  assert.equal(auth.isAuthRedirecting(), true);
  assert.equal(calls.filter((call) => typeof call === 'object').length, 1);
  assert.equal(calls[1].state.returnTo, '/messages?after=7#history');
  assert.equal(storage.getItem(recovery.UNAUTHORIZED_RESTART_KEY), null);
  assert.equal(storage.getItem(recovery.LOGIN_START_KEY), '1');
  assert.deepEqual(recovery.readCustomerDraft(storage), { displayName: 'unsaved draft' });
  assert.equal(await auth.startLoginForUnavailable('missing'), false);
});

test('manual login form resets guards without discarding an in-flight PKCE state or draft', () => {
  const { auth, storage } = loadAuth();
  recovery.claimLoginStart(storage);
  recovery.claimUnauthorizedRestart(storage);
  storage.setItem('oidc.pending-state', 'preserved');
  recovery.saveCustomerDraft(storage, { displayName: 'draft' });
  assert.equal(typeof auth.prepareManualLogin, 'function');
  auth.prepareManualLogin();
  assert.equal(storage.getItem(recovery.LOGIN_START_KEY), null);
  assert.equal(storage.getItem(recovery.UNAUTHORIZED_RESTART_KEY), null);
  assert.equal(storage.getItem('oidc.pending-state'), 'preserved');
  assert.equal(recovery.readCustomerDraft(storage).displayName, 'draft');
});

test('failed manual redirect can be retried and concurrent clicks start only one redirect', async () => {
  const { auth, calls, storage, setRedirect } = loadAuth();
  assert.equal(typeof auth.restartLoginManually, 'function');
  setRedirect(async () => { throw new Error('offline'); });
  await assert.rejects(auth.restartLoginManually(), /offline/);
  assert.equal(auth.isAuthRedirecting(), false);
  assert.equal(storage.getItem(recovery.LOGIN_START_KEY), null);
  let finish;
  setRedirect(() => new Promise((resolve) => { finish = resolve; }));
  const first = auth.restartLoginManually();
  assert.equal(await auth.restartLoginManually(), false);
  await Promise.resolve();
  finish();
  assert.equal(await first, true);
  assert.equal(calls.filter((call) => typeof call === 'object').length, 2);
});
