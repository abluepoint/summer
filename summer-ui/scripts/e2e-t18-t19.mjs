// Real-backend acceptance only. No request interception, saved auth state,
// trace, network dump or credential file. Supply credentials through the
// process environment after T25 has provisioned a disposable test schema.
// Required: SUMMER_E2E_ORIGIN, SUMMER_E2E_USERNAME, SUMMER_E2E_PASSWORD,
// SUMMER_E2E_DENIED_USERNAME, SUMMER_E2E_DENIED_PASSWORD,
// SUMMER_E2E_CONFIGURATION_FIXTURE (path to non-secret JSON metadata):
// { isolatedSchema: "summer_t19_...", enterpriseId: "existing-tenant", organizationId: "existing-org",
//   previewResource: "system-parameters", previewSubject: "fixture-user",
//   expectedPreviewDecision: "ALLOW", expectedDictionaryValues: ["fixture"] }
// The schema owner must verify the running backend uses that schema and seed
// the organization's dictionary override, with different tenant/base values.
// The privileged account's resolved organization must equal organizationId.
// This script leaves synthetic inactive rows for the schema owner to drop.
import assert from 'node:assert/strict';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { resolve, relative } from 'node:path';
import { chromium } from 'playwright-core';

const required = ['SUMMER_E2E_ORIGIN', 'SUMMER_E2E_USERNAME', 'SUMMER_E2E_PASSWORD', 'SUMMER_E2E_DENIED_USERNAME', 'SUMMER_E2E_DENIED_PASSWORD', 'SUMMER_E2E_CONFIGURATION_FIXTURE'];
if (required.some((name) => !process.env[name])) {
  console.log(JSON.stringify({ mode: 'real-backend', executed: false, reason: 'T25 isolated login environment and configuration fixture are required', requiredEnvironmentNames: required }));
  process.exitCode = 2;
} else {
  try { await run(); } catch {
    console.log(JSON.stringify({ mode: 'real-backend', executed: false, reason: 'runner configuration or evidence setup failed; inspect non-secret fixture contract' }));
    process.exitCode = 2;
  }
}

async function run() {
  const base = new URL(process.env.SUMMER_E2E_ORIGIN);
  assert.ok(['127.0.0.1', 'localhost', '[::1]'].includes(base.hostname) && !base.username && !base.password && base.pathname === '/' && !base.search && !base.hash && base.port !== '18080', 'Use the explicitly provisioned isolated local UI origin');
  const fixture = JSON.parse(await readFile(process.env.SUMMER_E2E_CONFIGURATION_FIXTURE, 'utf8'));
  assert.match(fixture.isolatedSchema, /^summer_t(?:19|25|36)_[a-z0-9_]+$/);
  assert.match(fixture.enterpriseId, /^[a-zA-Z0-9_-]{1,64}$/);
  const tenantRoot = `/api/v1/enterprises/${encodeURIComponent(fixture.enterpriseId)}`;
  const publicRoot = `/public/enterprises/${encodeURIComponent(fixture.enterpriseId)}`;
  assert.match(fixture.organizationId, /^[a-zA-Z0-9_-]{1,64}$/);
  assert.notEqual(fixture.organizationId, '_tenant');
  assert.ok(['ALLOW', 'DENY'].includes(fixture.expectedPreviewDecision));
  assert.ok(typeof fixture.previewResource === 'string' && typeof fixture.previewSubject === 'string');
  assert.ok(Array.isArray(fixture.expectedDictionaryValues) && fixture.expectedDictionaryValues.length > 0);
  const root = fileURLToPath(new URL('../..', import.meta.url));
  const evidence = resolve(root, '.git/summer4-review-20261007/t18-t19-ui/live');
  assert.ok(!relative(root, evidence).startsWith('..'));
  await mkdir(evidence, { recursive: true });
  const expectedTests = 6;
  const results = { mode: 'real-backend', executed: true, expectedTests, checks: [], syntheticKey: `ui-t19-${randomUUID().slice(0, 8)}`, failures: 0, errors: 0, skipped: 0, requiresSchemaOwnerTeardown: true };
  let browser;
  let admin;
  let bearer;
  let created;
  let deniedRead;
  let stage = 'browser launch';
  const header = (response) => response.headers()['x-request-id'] || '';
  const formField = (page, drawer, label) => drawer.locator('.arco-form-item').filter({ has: page.locator('label').getByText(label, { exact: true }) }).getByRole('textbox');
  const mark = (name) => results.checks.push({ name, status: 'passed' });
  async function login(path, denied = false) {
    const context = await browser.newContext({ viewport: { width: 1365, height: 900 } });
    const page = await context.newPage();
    page.setDefaultTimeout(30000);
    if (!denied) page.on('request', (request) => {
      if (new URL(request.url()).pathname.startsWith('/api/')) bearer = request.headers().authorization || bearer;
    });
    if (denied) page.on('response', (response) => {
      if (new URL(response.url()).pathname === `${tenantRoot}/data-scopes`) deniedRead = response;
    });
    await page.goto(new URL(path, base).href);
    await page.locator('input[autocomplete="current-password"]').waitFor();
    await page.locator('input[autocomplete="username"]').fill(process.env[denied ? 'SUMMER_E2E_DENIED_USERNAME' : 'SUMMER_E2E_USERNAME']);
    await page.locator('input[autocomplete="current-password"]').fill(process.env[denied ? 'SUMMER_E2E_DENIED_PASSWORD' : 'SUMMER_E2E_PASSWORD']);
    await page.getByRole('button', { name: /^(登录|login)$/i }).click();
    await page.waitForURL((url) => url.pathname === path);
    await selectTenant(page);
    return page;
  }
  async function selectTenant(page) {
    const selector = page.getByRole('combobox', { name: '活动租户', exact: true });
    await selector.click();
    await page.getByRole('option', { name: fixture.enterpriseId, exact: true }).click();
  }
  try {
    browser = await chromium.launch({ headless: true, executablePath: process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' });
    stage = 'authenticated policy preview';
    admin = await login('/data-scopes');
    const policyCard = admin.locator('.arco-card').filter({ has: admin.getByRole('textbox', { name: `${fixture.previewResource} condition tree` }) }).last();
    await policyCard.waitFor();
    await admin.getByPlaceholder('用户', { exact: true }).fill(fixture.previewSubject);
    await admin.getByPlaceholder('组织', { exact: true }).fill(fixture.organizationId);
    const previewResponse = admin.waitForResponse((response) => new URL(response.url()).pathname.endsWith(':preview') && response.request().method() === 'POST');
    await policyCard.getByRole('button', { name: '预览', exact: true }).click();
    const preview = await previewResponse;
    assert.equal(preview.status(), 200);
    const previewBody = await preview.json();
    assert.equal(previewBody.success, true);
    assert.equal(previewBody.data.decision, fixture.expectedPreviewDecision);
    await admin.getByText(previewBody.data.explanation, { exact: true }).waitFor();
    mark(stage);

    stage = 'isolated parameter creation';
    await admin.goto(new URL('/system-parameters', base).href);
    await selectTenant(admin);
    await admin.getByRole('button', { name: '新建参数', exact: true }).click();
    const drawer = admin.locator('.arco-drawer');
    await formField(admin, drawer, '键').fill(results.syntheticKey);
    await formField(admin, drawer, '值').fill('base-fixture');
    await drawer.locator('.arco-form-item').filter({ has: admin.locator('label').getByText('公开', { exact: true }) }).getByRole('switch').click();
    const createResponse = admin.waitForResponse((response) => new URL(response.url()).pathname === `${tenantRoot}/system-parameters` && response.request().method() === 'POST');
    await drawer.getByRole('button', { name: '确定', exact: true }).click();
    const creation = await createResponse;
    assert.equal(creation.status(), 201);
    const creationBody = await creation.json();
    assert.equal(creationBody.success, true);
    created = creationBody.data;
    assert.equal(created.key, results.syntheticKey);
    await drawer.waitFor({ state: 'hidden' });
    mark(stage);

    stage = 'organization > tenant > base parameter precedence';
    await admin.getByPlaceholder('键', { exact: true }).fill(results.syntheticKey);
    const row = admin.getByRole('row').filter({ has: admin.getByText(results.syntheticKey, { exact: true }) });
    await row.getByRole('button', { name: '编辑', exact: true }).click();
    for (const [scope, value] of [['_tenant', 'tenant-fixture'], [fixture.organizationId, 'organization-fixture']]) {
      await formField(admin, drawer, '值').fill(value);
      await formField(admin, drawer, '覆盖范围（_tenant 为租户，或组织 ID）').fill(scope);
      const saved = admin.waitForResponse((response) => new URL(response.url()).pathname.endsWith(`/overrides/${encodeURIComponent(scope)}`) && response.request().method() === 'PUT');
      await drawer.getByRole('button', { name: '发布当前表单值为覆盖', exact: true }).click();
      assert.equal((await saved).status(), 200);
    }
    assert.ok(bearer);
    const publicParameter = await admin.request.get(new URL(`${publicRoot}/system-parameters/${results.syntheticKey}`, base).href, { headers: { Authorization: bearer } });
    assert.equal(publicParameter.status(), 200);
    const publicBody = await publicParameter.json();
    assert.equal(publicBody.success, true);
    assert.equal(publicBody.data.value, 'organization-fixture');
    await drawer.getByRole('button', { name: '取消', exact: true }).click();
    await admin.getByRole('button', { name: '放弃修改', exact: true }).click();
    await drawer.waitFor({ state: 'hidden' });
    mark(stage);

    stage = 'authorized dictionary organization fixture';
    const dictionaryResponse = admin.waitForResponse((response) => new URL(response.url()).pathname === `${publicRoot}/dictionaries/customer-segment`);
    await admin.goto(new URL('/dictionary/form', base).href);
    await selectTenant(admin);
    const dictionary = await dictionaryResponse;
    assert.equal(dictionary.status(), 200);
    assert.ok(dictionary.request().headers().authorization);
    const dictionaryBody = await dictionary.json();
    assert.equal(dictionaryBody.success, true);
    assert.deepEqual(dictionaryBody.data.items.map((option) => option.value), fixture.expectedDictionaryValues);
    await admin.getByTestId('dictionary-version').waitFor();
    mark(stage);

    stage = 'invalid dictionary input without writes';
    let writes = 0;
    admin.on('request', (request) => { if (request.method() === 'PUT' && new URL(request.url()).pathname.startsWith(`${tenantRoot}/dictionaries/`)) writes++; });
    const optionsInput = admin.getByRole('textbox', { name: '覆盖选项 JSON', exact: true });
    await optionsInput.fill('{invalid-json');
    await admin.getByRole('button', { name: '发布字典覆盖', exact: true }).click();
    await admin.getByText('请输入有效选项 JSON', { exact: true }).waitFor();
    assert.equal(writes, 0);
    assert.equal(await optionsInput.inputValue(), '{invalid-json');
    mark(stage);

    stage = 'real denied policy read with request correlation';
    const denied = await login('/data-scopes', true);
    await denied.locator('.arco-alert-error').waitFor();
    assert.equal(await denied.locator('textarea').count(), 0);
    // The denied account is seeded without data-scope read/write authority.
    // The account must remain an ACTIVE member of fixture.enterpriseId.
    // The UI read must itself have returned 403; no synthetic response is used.
    assert.equal(deniedRead.status(), 403);
    assert.ok(header(deniedRead));
    assert.ok((await denied.locator('.arco-alert-error').innerText()).includes(header(deniedRead)));
    mark(stage);
    await denied.context().close();
  } catch {
    results.failures++;
    results.checks.push({ name: stage, status: 'failed' });
    process.exitCode = 1;
  } finally {
    if (created && bearer && admin) {
      try {
        const response = await admin.request.post(new URL(`${tenantRoot}/system-parameters/${results.syntheticKey}/deactivate`, base).href, { headers: { Authorization: bearer }, data: { expectedVersion: created.version, idempotencyKey: `cleanup-${randomUUID()}` } });
        results.cleanupStatus = response.status();
        if (response.status() !== 200) { results.errors++; process.exitCode = 1; }
      } catch { results.errors++; process.exitCode = 1; results.cleanupStatus = 'unavailable'; }
    }
    await browser?.close();
    results.tests = expectedTests;
    results.skipped = expectedTests - results.checks.length;
    results.passed = results.checks.filter((check) => check.status === 'passed').length;
    await writeFile(resolve(evidence, `result-${Date.now()}.json`), JSON.stringify(results, null, 2) + '\n');
    console.log(JSON.stringify(results));
  }
}
