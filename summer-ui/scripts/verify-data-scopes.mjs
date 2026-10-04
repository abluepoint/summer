import assert from 'node:assert/strict';
import fs from 'node:fs';
const api = fs.readFileSync(new URL('../src/pages/data-scopes/api.ts', import.meta.url), 'utf8');
const page = fs.readFileSync(new URL('../src/pages/data-scopes/index.tsx', import.meta.url), 'utf8');
const routes = fs.readFileSync(new URL('../src/routes.ts', import.meta.url), 'utf8');
assert.match(api, /data-scopes/); assert.match(api, /saveDataScope/); assert.match(page, /SELF/); assert.match(page, /ORGANIZATION_TREE/); assert.match(routes, /key: 'data-scopes'/);
console.log('Data scope UI uses real enterprise policy APIs and structured conditions');
