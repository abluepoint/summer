import test from 'node:test';
import assert from 'node:assert/strict';
import {
  customerDraft,
  customerPayload,
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
      email: 'a@b.test',
      displayName: 'A',
    }
  );
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
