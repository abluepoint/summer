import assert from 'node:assert/strict';
import fs from 'node:fs';

const api = fs.readFileSync(new URL('../src/pages/sessions/api.ts', import.meta.url), 'utf8');
const page = fs.readFileSync(new URL('../src/pages/sessions/index.tsx', import.meta.url), 'utf8');
const routes = fs.readFileSync(new URL('../src/routes.ts', import.meta.url), 'utf8');

assert.match(api, /\/api\/v1\/sessions/);
assert.match(api, /axios\.delete\(`\/api\/v1\/sessions\/\$\{encodeURIComponent\(sessionRef\)\}`\)/);
assert.match(api, /SUMMER_SESSION_STORE_UNAVAILABLE|SessionApiError/);
assert.match(page, /sessions\.requestId/);
assert.match(page, /sessions\.retry/);
assert.match(page, /sessions\.revoke/);
assert.match(routes, /key: 'sessions'/);
assert.doesNotMatch(api + page, /localStorage|sessionStorage|access_token|refresh_token/);
console.log('sessions UI contract passed');
