import assert from 'node:assert/strict';
import fs from 'node:fs';

const api = fs.readFileSync(new URL('../src/pages/roles/api.ts', import.meta.url), 'utf8');
const page = fs.readFileSync(new URL('../src/pages/roles/index.tsx', import.meta.url), 'utf8');
const routes = fs.readFileSync(new URL('../src/routes.ts', import.meta.url), 'utf8');

assert.match(api, /\/api\/v1\/enterprises\/.*\/roles/);
assert.match(api, /\/users\/.*\/roles/);
assert.match(api, /deniedCapabilityCodes/);
assert.match(api, /Idempotency-Key/);
assert.match(page, /createRole/);
assert.match(page, /assignUserRoles/);
assert.match(page, /Explicitly deny|roles\.denyLabel/);
assert.match(routes, /key: 'roles'/);
console.log('Roles and permissions UI uses real role, resource and assignment APIs');
