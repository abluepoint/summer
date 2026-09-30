import test from 'node:test';
import assert from 'node:assert/strict';
import {
  changedCustomerFields,
  customerDraft,
  customerPayload,
  editRecovery,
  isEditDirty,
  mapCustomerFailure,
  validateCustomerDraft,
} from '../src/pages/customer/form-contract.mjs';

test('create fields follow the current backend username, email and name rules', () => {
  assert.deepEqual(
    validateCustomerDraft({ username: 'ab', email: 'bad', displayName: ' ' }),
    {
      username: 'username',
      email: 'email',
      displayName: 'displayName',
    }
  );
  assert.deepEqual(
    validateCustomerDraft({
      username: 'abc',
      email: 'a@b.test',
      displayName: ' A ',
    }),
    {}
  );
  assert.deepEqual(
    validateCustomerDraft({
      username: 'a.b',
      email: 'a@b.test',
      displayName: 'A',
    }),
    { username: 'username' }
  );
  assert.deepEqual(
    validateCustomerDraft({
      username: 'abc',
      email: ' a@b.test ',
      displayName: 'A',
    }),
    { email: 'email' }
  );
  assert.deepEqual(
    validateCustomerDraft({
      username: 'abc-',
      email: 'a@b.test',
      displayName: 'A',
    }),
    {}
  );
  assert.deepEqual(
    customerPayload({
      username: 'abc',
      email: ' a@b.test ',
      displayName: ' A ',
    }),
    {
      username: 'abc',
      email: ' a@b.test ',
      displayName: 'A',
    }
  );
});

test('edit sends only changed mutable fields and never username or active', () => {
  const original = {
    username: 'alice',
    email: 'a@example.test',
    displayName: 'Alice',
  };
  assert.deepEqual(changedCustomerFields(original, original), {});
  assert.deepEqual(
    validateCustomerDraft({ ...original, email: ' a@example.test ' }),
    { email: 'email' }
  );
  assert.equal(isEditDirty(original, original), false);
  assert.equal(
    isEditDirty(original, { ...original, email: ' a@example.test ' }),
    true
  );
  assert.deepEqual(
    changedCustomerFields(original, { ...original, email: ' a@example.test ' }),
    { email: ' a@example.test ' }
  );
  assert.deepEqual(
    changedCustomerFields(original, {
      ...original,
      username: 'other',
      email: ' b@example.test ',
    }),
    { email: ' b@example.test ' }
  );
  assert.deepEqual(
    changedCustomerFields(original, {
      ...original,
      displayName: 'New name',
      active: false,
    }),
    { displayName: 'New name' }
  );
});

test('edit recovery requires a matching target and retains the baseline', () => {
  const saved = {
    username: 'alice',
    email: 'new@example.test',
    displayName: 'New',
    editUsername: 'alice',
    originalEmail: 'old@example.test',
    originalDisplayName: 'Old',
  };
  assert.deepEqual(editRecovery(saved), {
    original: {
      username: 'alice',
      email: 'old@example.test',
      displayName: 'Old',
    },
    draft: { username: 'alice', email: 'new@example.test', displayName: 'New' },
  });
  assert.equal(editRecovery({ ...saved, editUsername: 'other' }), null);
  assert.equal(editRecovery({ ...saved, originalEmail: undefined }), null);
});

test('draft recovery only keeps Customer form fields', () => {
  assert.deepEqual(
    customerDraft({
      username: 'abc',
      email: 'a@b.test',
      displayName: 'A',
      access_token: 'secret',
    }),
    {
      username: 'abc',
      email: 'a@b.test',
      displayName: 'A',
    }
  );
});

test('field violations map to fields and unknown violations stay visible', () => {
  assert.deepEqual(
    mapCustomerFailure({
      violations: [
        { field: 'email', message: 'Invalid email' },
        { field: '$', message: 'Invalid request' },
      ],
    }),
    { fields: { email: 'Invalid email' }, other: ['Invalid request'] }
  );
  assert.deepEqual(mapCustomerFailure({ violations: [] }), {
    fields: {},
    other: [],
  });
});
