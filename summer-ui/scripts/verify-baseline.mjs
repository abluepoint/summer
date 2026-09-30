import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const packageJson = JSON.parse(
  await readFile(path.join(root, 'package.json'), 'utf8')
);
const routes = await readFile(path.join(root, 'src/routes.ts'), 'utf8');
const locale = await readFile(path.join(root, 'src/locale/index.ts'), 'utf8');
const lockfile = await readFile(path.join(root, 'yarn.lock'), 'utf8');
const baseline = await readFile(path.join(root, 'BASELINE.md'), 'utf8');

assert.equal(packageJson.name, 'summer-ui');
assert.equal(packageJson.scripts.build, 'vite build');
assert.equal(packageJson.scripts.typecheck, 'tsc --noEmit');
assert.equal(packageJson.scripts.prepare, undefined);
assert.match(lockfile, /@arco-design\/web-react@/);
assert.match(baseline, /bb6aebcceca6b294b438be4c13dc7328ee80d70b/);
assert.match(routes, /key: 'customer'/);
assert.match(routes, /key: 'arco-examples'/);
assert.match(routes, /key: 'welcome'/);
assert.match(routes, /key: 'oauth2\/callback'/);
assert.match(locale, /menu\.arcoExamples/);
assert.match(locale, /zh-CN/);
assert.match(locale, /en-US/);

for (const file of ['src/mock/index.ts', 'src/mock/user.ts']) {
  const source = await readFile(path.join(root, file), 'utf8');
  assert.doesNotMatch(source, /api\/customers|oauth2|csrf|connect/);
}

assert.doesNotMatch(routes, /summer-sample-arco-pro/);

console.log('Summer UI baseline contract passed');
