import test from 'node:test';
import assert from 'node:assert/strict';
import {
  DEFAULT_ROUTE,
  DRAFT_KEY,
  safeReturnTo,
  saveCustomerDraft,
  consumeCustomerDraft,
  readCustomerDraft,
  LOGIN_START_KEY,
  UNAUTHORIZED_RESTART_KEY,
  claimLoginStart,
  completeLoginStart,
  claimUnauthorizedRestart,
  completeAuthenticatedRequest,
  clearRecoveryState,
  isBusinessApi,
  classifyBusinessFailure,
} from '../src/auth/recovery.mjs';

function memoryStorage() {
  const values = new Map();
  return {
    setItem(key, value) {
      values.set(key, value);
    },
    getItem(key) {
      return values.get(key) ?? null;
    },
    removeItem(key) {
      values.delete(key);
    },
    values,
  };
}

test('only same-origin application routes can be restored', () => {
  assert.equal(
    safeReturnTo('/customer/page?sort=asc#edit'),
    '/customer/page?sort=asc#edit'
  );
  for (const path of [
    'https://attacker.invalid',
    '//attacker.invalid',
    '/\\attacker.invalid',
    '/login',
    '/oauth2/callback',
  ]) {
    assert.equal(safeReturnTo(path), DEFAULT_ROUTE);
  }
});

test('draft recovery is single-use and stores only Customer fields', () => {
  const storage = memoryStorage();
  saveCustomerDraft(storage, {
    username: 'bluepoint',
    email: 'contact@example.test',
    displayName: 'Bluepoint',
    active: true,
    password: 'sensitive',
    access_token: 'sensitive',
    id_token: 'sensitive',
    code_verifier: 'sensitive',
  });
  assert.deepEqual(JSON.parse(storage.values.get(DRAFT_KEY)), {
    username: 'bluepoint',
    email: 'contact@example.test',
    displayName: 'Bluepoint',
    active: true,
  });
  assert.equal(readCustomerDraft(storage).username, 'bluepoint');
  assert.equal(readCustomerDraft(storage).username, 'bluepoint');
  assert.deepEqual(consumeCustomerDraft(storage), {
    username: 'bluepoint',
    email: 'contact@example.test',
    displayName: 'Bluepoint',
    active: true,
  });
  assert.equal(consumeCustomerDraft(storage), undefined);
});

test('invalid interrupted draft is discarded', () => {
  const storage = memoryStorage();
  storage.setItem(DRAFT_KEY, '{');
  assert.equal(consumeCustomerDraft(storage), undefined);
  assert.equal(storage.getItem(DRAFT_KEY), null);
});

test('edit recovery persists only target, fields and original values', () => {
  const storage = memoryStorage();
  saveCustomerDraft(storage, {
    username: 'alice',
    email: 'new@example.test',
    displayName: 'New',
    editUsername: 'alice',
    originalEmail: 'old@example.test',
    originalDisplayName: 'Old',
    access_token: 'sensitive',
    password: 'sensitive',
  });
  assert.deepEqual(
    Object.keys(readCustomerDraft(storage)).sort(),
    [
      'displayName',
      'editUsername',
      'email',
      'originalDisplayName',
      'originalEmail',
      'username',
    ].sort()
  );
});

test('missing or expired token starts one login until callback completes', () => {
  const storage = memoryStorage();
  assert.equal(claimLoginStart(storage), true);
  assert.equal(storage.getItem(LOGIN_START_KEY), '1');
  assert.equal(claimLoginStart(storage), false);
  completeLoginStart(storage);
  assert.equal(claimLoginStart(storage), true);
});

test('401 restart is single-use until a successful business response', () => {
  const storage = memoryStorage();
  assert.equal(claimUnauthorizedRestart(storage), true);
  assert.equal(claimUnauthorizedRestart(storage), false);
  assert.equal(storage.getItem(UNAUTHORIZED_RESTART_KEY), '1');
  completeLoginStart(storage);
  assert.equal(claimUnauthorizedRestart(storage), false);
  completeAuthenticatedRequest(storage);
  assert.equal(claimUnauthorizedRestart(storage), true);
  clearRecoveryState(storage);
  assert.equal(storage.getItem(UNAUTHORIZED_RESTART_KEY), null);
});

test('business API failures distinguish 401, 403, and network errors', () => {
  assert.equal(isBusinessApi('/api/customers'), true);
  assert.equal(isBusinessApi('/api/customers?size=20'), true);
  assert.equal(isBusinessApi('/oauth2/token'), false);
  assert.equal(isBusinessApi('/apiary'), false);
  assert.equal(classifyBusinessFailure(401, true), 'unauthorized');
  assert.equal(classifyBusinessFailure(403, true), 'forbidden');
  assert.equal(classifyBusinessFailure(undefined, false), 'network');
  assert.equal(classifyBusinessFailure(409, true), 'other');
});
