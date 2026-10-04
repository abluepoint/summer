import fs from 'node:fs';
import assert from 'node:assert/strict';

const api = fs.readFileSync(new URL('../src/pages/messages/api.ts', import.meta.url), 'utf8');
const page = fs.readFileSync(new URL('../src/pages/messages/index.tsx', import.meta.url), 'utf8');
const routes = fs.readFileSync(new URL('../src/routes.ts', import.meta.url), 'utf8');
assert.match(api, /\/ws\/v1\/messages/);
assert.match(api, /RECONNECTING/);
assert.match(api, /Math\.min\(8000/);
assert.match(api, /after/);
assert.match(page, /acknowledgeMessage/);
assert.match(page, /messages\.title/);
assert.match(routes, /key: 'messages'/);
console.log('realtime messages UI route, WebSocket reconnect and acknowledgement contract passed');
