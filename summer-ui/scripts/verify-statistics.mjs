import assert from 'node:assert/strict';
import fs from 'node:fs';

const api = fs.readFileSync(new URL('../src/pages/statistics/api.ts', import.meta.url), 'utf8');
const page = fs.readFileSync(new URL('../src/pages/statistics/index.tsx', import.meta.url), 'utf8');
const routes = fs.readFileSync(new URL('../src/routes.ts', import.meta.url), 'utf8');
assert.match(api, /statistics/);
assert.match(api, /status: number/);
assert.match(page, /statistics\.empty/);
assert.match(page, /taskSuccessRate/);
assert.match(routes, /statistics/);
console.log('statistics contract checks passed');
