import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ui = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const [api, page, routes, vite, caddy] = await Promise.all([
  readFile(resolve(ui, 'src/pages/diagnostics/redis/api.ts'), 'utf8'),
  readFile(resolve(ui, 'src/pages/diagnostics/redis/index.tsx'), 'utf8'),
  readFile(resolve(ui, 'src/routes.ts'), 'utf8'),
  readFile(resolve(ui, 'vite.config.ts'), 'utf8'),
  readFile(resolve(ui, 'deploy/Caddyfile'), 'utf8'),
]);

assert.match(api, /\/internal\/v1\/diagnostics\/redis/);
assert.match(api, /businessData/);
assert.match(page, /diagnostics\.redis\.status/);
assert.match(page, /diagnostics\.redis\.metrics/);
assert.match(page, /diagnostics\.redis\.forbidden/);
assert.match(routes, /diagnostics\/redis/);
assert.match(vite, /['"]\/internal['"]\s*:\s*backendOrigin/);
assert.match(caddy, /\/internal\/\*?/);
assert.doesNotMatch(page, /password|connectionUri/i);
assert.doesNotMatch(api, /localStorage|sessionStorage/);
console.log('Redis diagnostics UI route, envelope handling, proxy and redaction contract passed');
