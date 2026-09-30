import test from 'node:test';
import assert from 'node:assert/strict';
import {
  DEFAULT_ROUTE,
  DRAFT_KEY,
  safeReturnTo,
  saveCustomerDraft,
  consumeCustomerDraft,
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
