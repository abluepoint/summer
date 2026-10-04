import assert from 'node:assert/strict';
import fs from 'node:fs';

const api = fs.readFileSync(new URL('../src/pages/tasks/api.ts', import.meta.url), 'utf8');
const page = fs.readFileSync(new URL('../src/pages/tasks/index.tsx', import.meta.url), 'utf8');
const routes = fs.readFileSync(new URL('../src/routes.ts', import.meta.url), 'utf8');
assert.match(api, /tasks/);
assert.match(api, /\/types/);
assert.match(api, /:trigger/);
assert.match(api, /executions/);
assert.match(page, /createTask/);
assert.match(page, /setTaskStatus/);
assert.match(page, /triggerTask/);
assert.match(routes, /key: 'tasks'/);
assert.match(routes, /capabilityCode: 'enterprise\.tasks\.read'/);
console.log('task UI contract: 7 checks passed');
