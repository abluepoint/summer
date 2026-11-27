import assert from 'node:assert/strict';
import fs from 'node:fs';
const routes = fs.readFileSync(new URL('../src/routes.ts', import.meta.url), 'utf8');
assert.doesNotMatch(routes, /key: 'data-scopes'|menu\.dataScopes/);
assert.equal(fs.existsSync(new URL('../src/pages/data-scopes', import.meta.url)), false);
assert.ok(fs.existsSync(new URL('../src/pages/permissions/index.tsx', import.meta.url)));
assert.ok(fs.existsSync(new URL('../src/hooks/useActiveEnterprise.ts', import.meta.url)));
console.log('Retired data scope page is absent; permission management and shared enterprise context remain');
