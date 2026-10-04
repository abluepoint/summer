import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ui = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const [api, page, routes, vite] = await Promise.all([
  readFile(resolve(ui, 'src/pages/dictionary/api.ts'), 'utf8'),
  readFile(resolve(ui, 'src/pages/dictionary/index.tsx'), 'utf8'),
  readFile(resolve(ui, 'src/routes.ts'), 'utf8'),
  readFile(resolve(ui, 'vite.config.ts'), 'utf8'),
]);

assert.match(api, /\/public\/dictionaries/);
assert.match(api, /readEnvelope/);
assert.match(api, /version\?: number/);
assert.match(page, /getPublicDictionary/);
assert.match(page, /Select/);
assert.match(page, /dictionary\.refresh/);
assert.match(page, /setVersion/);
assert.match(page, /some\(\(item\) => item\.value === current\)/);
assert.match(routes, /dictionary\/form/);
assert.match(vite, /['"]\/public['"]\s*:\s*backendOrigin/);
assert.doesNotMatch(api, /localStorage|sessionStorage/);
console.log('Dictionary API, real form consumer, route and proxy contract passed');
