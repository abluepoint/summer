import assert from 'node:assert/strict';
import { test } from 'node:test';
import fs from 'node:fs';
import vm from 'node:vm';
import ts from 'typescript';
const source = fs.readFileSync(new URL('../src/auth/businessCapabilities.ts', import.meta.url), 'utf8');
const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText;
const exported = {};
vm.runInNewContext(code, { exports: exported, atob });
const has = exported.hasEnterpriseCapability;
const read = 'enterprise.members.read', write = 'enterprise.members.write';
const active = (id) => ({ enterpriseId: id, status: 'ACTIVE' });
const claims = (enterprise = 'tenant-a', grants = [read, write]) => ({ enterprise_memberships: [active(enterprise)], enterprise_capabilities: { [enterprise]: grants } });
const user = (payload, profile = {}) => ({ expired: false, scope: 'openid profile customer.read customer.write', profile, access_token: 'synthetic.' + Buffer.from(JSON.stringify(payload)).toString('base64url') + '.fixture' });
test('business capability comes from access-token claims without widening OAuth scope', () => {
  assert.equal(has(user(claims()), 'tenant-a', write), true);
});
test('enterprise grants do not cross tenant boundaries', () => {
  const value = claims('tenant-b'); value.enterprise_memberships.push(active('tenant-a'));
  assert.equal(has(user(value), 'tenant-a', write), false);
});
test('inactive or missing membership cannot activate a grant', () => {
  const value = claims(); value.enterprise_memberships[0].status = 'DISABLED';
  assert.equal(has(user(value), 'tenant-a', write), false);
  value.enterprise_memberships = [];
  assert.equal(has(user(value), 'tenant-a', write), false);
});
test('an OAuth scope alone never becomes a tenant grant', () => {
  const value = user({}); value.scope += ' ' + write;
  assert.equal(has(value, 'tenant-a', write), false);
});
test('expired and unloaded identities fail closed', () => {
  assert.equal(has(null, 'tenant-a', write), false);
  assert.equal(has({ ...user(claims()), expired: true }, 'tenant-a', write), false);
});
test('opaque tokens can use authenticated profile business metadata', () => {
  assert.equal(has({ ...user({}), access_token: 'opaque', profile: claims() }, 'tenant-a', write), true);
});
test('current access claims override stale profile grants and membership', () => {
  assert.equal(has(user(claims('tenant-a', []), claims()), 'tenant-a', write), false);
  const value = claims(); value.enterprise_memberships[0].status = 'RESIGNED';
  assert.equal(has(user(value, claims()), 'tenant-a', write), false);
});
test('UTF-8 tenant identifiers survive access-token decoding', () => {
  assert.equal(has(user(claims('租户甲')), '租户甲', write), true);
});
test('platform capabilities are not flattened into enterprise grants', () => {
  const value = claims('tenant-a', []); value.platform_capabilities = [write];
  assert.equal(has(user(value), 'tenant-a', write), false);
});
test('malformed membership or capability metadata fails closed', () => {
  const value = claims(); value.enterprise_capabilities['tenant-a'] = { [write]: true };
  assert.equal(has(user(value), 'tenant-a', write), false);
  assert.equal(has(user({ ...claims(), enterprise_memberships: 'ACTIVE' }), 'tenant-a', write), false);
  assert.equal(has(user(null), 'tenant-a', write), false);
});
test('read and wildcard metadata do not imply write capability', () => {
  assert.equal(has(user(claims('tenant-a', [read])), 'tenant-a', write), false);
  assert.equal(has(user(claims('tenant-a', ['*'])), 'tenant-a', write), false);
});
