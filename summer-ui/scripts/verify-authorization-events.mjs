import assert from 'node:assert/strict';
import { existsSync } from 'node:fs';
import { readFile } from 'node:fs/promises';

assert.equal(existsSync(new URL('../src/pages/authorization-events', import.meta.url)), false);
const routes = await readFile(new URL('../src/routes.ts', import.meta.url), 'utf8');
const locale = await readFile(new URL('../src/locale/index.ts', import.meta.url), 'utf8');
assert.doesNotMatch(routes, /authorization-events|authorizationEvents/);
assert.doesNotMatch(locale, /authorizationEvents/);
assert.match(routes, /key: 'permissions'/);
console.log('retired authorization events UI is absent; permission management remains available');
