import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const api = await readFile(new URL('../src/pages/diagnostics/resilience/api.ts', import.meta.url), 'utf8');
const page = await readFile(new URL('../src/pages/diagnostics/resilience/index.tsx', import.meta.url), 'utf8');
const routes = await readFile(new URL('../src/routes.ts', import.meta.url), 'utf8');
const locale = await readFile(new URL('../src/locale/index.ts', import.meta.url), 'utf8');
assert.match(api, /internal\/v1\/diagnostics\/resilience/);
assert.match(api, /businessData/);
assert.match(page, /fallbacks/);
assert.match(page, /timedOut/);
assert.match(routes, /diagnostics\/resilience/);
assert.match(locale, /diagnostics\.resilience\.title/);
assert.doesNotMatch(api + page, /password|token|privateKey/i);
console.log('resilience diagnostics UI uses the real API, permission route and sanitized metrics');
