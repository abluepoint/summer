import test from 'node:test';
import assert from 'node:assert/strict';
import {
  pageFormRoute,
  pageFormSearch,
  pageListReturn,
} from '../src/pages/customer/page/return-contract.mjs';

test('page mode has direct create and edit routes', () => {
  assert.deepEqual(pageFormRoute('/customer/page/create'), {
    action: 'create',
  });
  assert.deepEqual(pageFormRoute('/customer/page/edit/summer.demo'), {
    action: 'edit',
    username: 'summer.demo',
  });
  assert.deepEqual(pageFormRoute('/customer/page/edit/abc%20def'), {
    action: 'edit',
    username: 'abc def',
  });
  assert.equal(pageFormRoute('/customer/page/edit/'), null);
  assert.equal(pageFormRoute('/customer/page/edit/abc%2Fdef'), null);
});

test('return address preserves list URL and rejects other destinations', () => {
  const source =
    '/customer/page?page=3&size=10&active=false&sort=email&direction=desc#results';
  assert.equal(pageListReturn(source), source);
  assert.equal(new URLSearchParams(pageFormSearch(source)).get('from'), source);
  for (const invalid of [
    undefined,
    '//elsewhere',
    '/customer/drawer',
    '/customer/page/create',
    '/customer/page/edit/alice',
    'https://elsewhere/customer/page',
  ]) {
    assert.equal(pageListReturn(invalid), '/customer/page');
  }
});
