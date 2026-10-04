import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const root = path.resolve(new URL('../..', import.meta.url).pathname);
const manifestPath = path.join(root, 'docs/guides/summer4-t36-release-gates.json');
const runbookPath = path.join(root, 'docs/guides/summer4-t36-upgrade-acceptance.md');
const gateScriptPath = path.join(root, 'scripts/t36-upgrade-gate.sh');
const configPath = path.join(root, 'summer-sample/src/main/resources/application.yml');
const releaseControllerPath = path.join(
  root,
  'summer-sample/src/main/java/com/abluepoint/summer/sample/release/ReleaseGateController.java',
);
const routesPath = path.join(root, 'summer-ui/src/routes.ts');

const read = (file) => fs.readFileSync(file, 'utf8');
const manifest = JSON.parse(read(manifestPath));
const expectedChecks = [
  'migration',
  'configuration',
  'keys',
  'authorization',
  'tasks',
  'cache',
  'events',
  'realtime',
  'plugins',
  'ui',
];

assert.equal(manifest.ticket, 'T36');
assert.deepEqual(
  manifest.releaseChecks.map((check) => check.id),
  expectedChecks,
  'the release gate matrix must remain complete and ordered',
);
assert.equal(manifest.compatibilityWindow.migration, 'expand-contract');
assert.equal(manifest.compatibilityWindow.sessionStore, 'shared-redis');
assert.equal(manifest.compatibilityWindow.realtime, 'last-event-id-or-message-cursor');
assert.equal(manifest.microserviceBoundary.current, 'modular-monolith');
assert.ok(manifest.microserviceBoundary.splitTriggers.length >= 4);

const runbook = read(runbookPath);
for (const heading of [
  '迁移、备份与恢复',
  '双节点滚动升级',
  '错误码、审计、权限、缓存与实时推送',
  '微服务边界',
  '回滚方案',
]) {
  assert.match(runbook, new RegExp(heading));
}

const gateScript = read(gateScriptPath);
for (const command of ['contract', 'migrations', 'preflight', 'backup', 'restore', 'rolling']) {
  assert.match(gateScript, new RegExp(`^\\s*${command}\\)`, 'm'));
}
assert.ok((fs.statSync(gateScriptPath).mode & 0o111) !== 0, 'gate script must be executable');
assert.doesNotMatch(gateScript, /echo\s+.*(PASSWORD|TOKEN|PRIVATE_KEY)/i);

const config = read(configPath);
assert.match(config, /ddl-auto: \$\{SPRING_JPA_HIBERNATE_DDL_AUTO:none\}/);
assert.match(config, /validate-on-migrate: true/);
assert.match(config, /store-type: \$\{SUMMER_SAMPLE_SESSION_STORE_TYPE:redis\}/);
assert.match(config, /org\.quartz\.jobStore\.isClustered: true/);
assert.match(config, /node-id: \$\{SUMMER_SAMPLE_CACHE_NODE_ID/);

const releaseController = read(releaseControllerPath);
for (const endpoint of [
  '/internal/v1/release/readiness',
  '/internal/v1/release/gates:validate',
  '/internal/v1/release/rollback:dry-run',
  '/internal/v1/release/failure-drills',
]) {
  assert.match(releaseController, new RegExp(endpoint.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
}

const routes = read(routesPath);
for (const route of ["key: 'plugins'", "key: 'tasks'", "key: 'messages'"]) {
  assert.match(routes, new RegExp(route.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
}

console.log(`T36 upgrade gate contract: ${expectedChecks.length} release checks, compatibility and rollback contracts passed`);
