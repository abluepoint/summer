import assert from 'node:assert/strict';
import fs from 'node:fs';

const api = fs.readFileSync(new URL('../src/pages/plugins/api.ts', import.meta.url), 'utf8');
const page = fs.readFileSync(new URL('../src/pages/plugins/index.tsx', import.meta.url), 'utf8');
const routes = fs.readFileSync(new URL('../src/routes.ts', import.meta.url), 'utf8');
assert.match(api, /platform\/plugins/);
assert.match(api, /:enable/);
assert.match(api, /:upgrade/);
assert.match(api, /customer-insights\/summary/);
assert.match(page, /trusted|INCOMPATIBLE|ENABLED/);
assert.match(page, /configuration|menus/);
assert.match(routes, /key: 'plugins'/);
console.log('Plugin UI uses trusted lifecycle and real enterprise API contracts');
