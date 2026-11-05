import assert from 'node:assert/strict';
import fs from 'node:fs';

const manifest = fs.readFileSync(new URL('../src/menu/manifest.ts', import.meta.url), 'utf8');
const api = fs.readFileSync(new URL('../src/menu/api.ts', import.meta.url), 'utf8');
const routes = fs.readFileSync(new URL('../src/routes.ts', import.meta.url), 'utf8');
const page = fs.readFileSync(new URL('../src/pages/menu-manifest/index.tsx', import.meta.url), 'utf8');

assert.match(manifest, /createMenuManifest/);
assert.match(manifest, /manifestVersion/);
assert.match(manifest, /capabilityCode/);
assert.doesNotMatch(manifest, /component|importPath|displayExpression/);
assert.match(api, /platform\/menu-manifest/);
assert.match(api, /\$\{base\}\/menu-manifest/);
assert.match(api, /authorization\/navigation/);
assert.match(api, /Idempotency-Key/);
assert.match(routes, /key: 'menu-manifest'/);
assert.match(page, /getManagedManifest/);
console.log('Menu manifest is generated from routes and uses real permission APIs');

await import('./verify-menu-permissions.mjs');
await import('./verify-menu-mode.mjs');
