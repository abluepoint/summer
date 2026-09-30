import test from 'node:test';
import assert from 'node:assert/strict';
import {
  readListState,
  listSearch,
  detailPath,
  detailSource,
  detailUsername,
  readEnvelope,
  readFailure,
  canConfirmDeletion,
  pageAfterDeletion,
} from '../src/pages/customer/contract.mjs';

test('list URL restores valid pagination, filter and sort and rejects invalid values', () => {
  assert.deepEqual(
    readListState('?page=2&size=50&active=false&sort=email&direction=desc'),
    {
      page: 2,
      size: 50,
      active: 'false',
      sort: 'email',
      direction: 'desc',
    }
  );
  assert.deepEqual(
    readListState('?page=-1&size=500&active=maybe&sort=id&direction=down'),
    {
      page: 0,
      size: 20,
      active: '',
      sort: 'username',
      direction: 'asc',
    }
  );
  assert.equal(
    listSearch({
      page: 2,
      size: 50,
      active: 'false',
      sort: 'email',
      direction: 'desc',
    }),
    '?page=2&size=50&active=false&sort=email&direction=desc'
  );
});

test('deletion needs exact username and falls back only when the page is gone', () => {
  assert.equal(canConfirmDeletion('alice', 'alice'), true);
  for (const typed of ['Alice', 'alice ', ' alice', 'ali', ''])
    assert.equal(canConfirmDeletion(typed, 'alice'), false);
  assert.equal(pageAfterDeletion(2, 10, 20), 1);
  assert.equal(pageAfterDeletion(2, 10, 21), 2);
  assert.equal(pageAfterDeletion(0, 10, 0), 0);
});

test('Customer detail links use username and preserve list state', () => {
  assert.equal(
    detailPath('drawer', 'alice_1', '?page=2'),
    '/customer/detail/alice_1?page=2'
  );
  assert.equal(detailUsername('/customer/detail/alice_1', 'drawer'), 'alice_1');
  assert.equal(detailUsername('/customer/detail/summer.demo', 'page'), 'summer.demo');
  assert.equal(detailPath('page', 'summer.demo'), '/customer/detail/summer.demo');
  assert.equal(detailUsername('/customer/drawer', 'drawer'), null);
  assert.equal(detailUsername('/customer/detail/not%2Fvalid', 'drawer'), null);
  assert.equal(detailUsername('/customer/detail/Abc', 'drawer'), null);
  assert.equal(detailUsername('/customer/detail/abc-', 'drawer'), null);
  assert.deepEqual(detailSource('/customer/modal?page=2#list'), {
    mode: 'modal',
    url: '/customer/modal?page=2#list',
  });
  assert.equal(detailSource('//outside.example/customer/modal'), null);
  assert.equal(detailSource('/customer/modal/other'), null);
});

test('business envelopes return data and retain request metadata on failure', () => {
  assert.deepEqual(
    readEnvelope(
      { success: true, data: { username: 'alice' }, requestId: 'req-1' },
      200
    ),
    { username: 'alice' }
  );
  assert.deepEqual(
    readFailure(
      {
        success: false,
        error: {
          code: 'CUSTOMER_NOT_FOUND',
          message: 'Missing',
          violations: [],
        },
        requestId: 'req-2',
        timestamp: '2026-09-30T00:00:00Z',
      },
      404
    ),
    {
      status: 404,
      code: 'CUSTOMER_NOT_FOUND',
      message: 'Missing',
      violations: [],
      requestId: 'req-2',
      timestamp: '2026-09-30T00:00:00Z',
    }
  );
  for (const status of [401, 403, 404, 500]) {
    assert.equal(readFailure(undefined, status, 'header-id').status, status);
    assert.equal(
      readFailure(undefined, status, 'header-id').requestId,
      'header-id'
    );
  }
  assert.equal(readFailure({ error: null }, 500).status, 500);
});
