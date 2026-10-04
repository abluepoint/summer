import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const api = await readFile(new URL('../src/pages/authorization-events/api.ts', import.meta.url), 'utf8');
const page = await readFile(new URL('../src/pages/authorization-events/index.tsx', import.meta.url), 'utf8');
const routes = await readFile(new URL('../src/routes.ts', import.meta.url), 'utf8');
assert.match(api, /\/api\/authorization-events/);
assert.match(api, /retryAuthorizationChangeEvent/);
assert.match(page, /listAuthorizationChangeEvents/);
assert.match(page, /retryAuthorizationChangeEvent/);
assert.match(routes, /key: 'authorization-events'/);
console.log('authorization change event UI uses the real outbox API and retry flow');
