import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright-core';
import { createServer } from 'vite';

const ui = fileURLToPath(new URL('..', import.meta.url));
let vite;
let browser;
let origin;
const success = (data) => ({ success: true, data, requestId: 'ui-fixture' });
const failure = (status, requestId) => ({ success: false, error: { code: `FIXTURE_${status}`, message: 'controlled failure' }, requestId });
const policy = { resource: 'system-parameters', enterpriseId: 'ent-acme', operator: 'AND', rules: [{ type: 'TENANT', value: 'ent-acme' }], version: 1 };

before(async () => {
  vite = await createServer({ root: ui, logLevel: 'error', server: { port: 0, strictPort: false }, plugins: [{
    name: 't18-t19-component-fixture',
    configureServer(server) {
      server.middlewares.use(async (request, response, next) => {
        if (!request.url.startsWith('/__t18-t19/')) return next();
        response.setHeader('Content-Type', 'text/html');
        response.end(await server.transformIndexHtml(request.url, '<div id="root"></div><script type="module" src="/scripts/t18-t19-browser-fixture.tsx"></script>'));
      });
    },
  }] });
  await vite.listen();
  origin = `http://127.0.0.1:${vite.httpServer.address().port}`;
  browser = await chromium.launch({ headless: true, executablePath: process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' });
});
after(async () => { await browser?.close(); vite?.httpServer.closeAllConnections(); await vite?.close(); });

async function pageFor(name, handler, query = '') {
  const page = await browser.newPage();
  page.setDefaultTimeout(5000);
  await page.route('**/api/**', handler);
  await page.route('**/public/**', handler);
  await page.goto(`${origin}/__t18-t19/${name}${query}`);
  return page;
}
async function json(route, data, status = 200) {
  await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(data) });
}

test('an anonymous query cannot create an active enterprise choice or load configuration', async () => {
  let reads = 0;
  const page = await pageFor('dictionary', async (route) => { reads++; return json(route, success({ code: 'customer-segment', version: 1, items: [] })); }, '?anonymous=1&enterpriseId=forged-tenant');
  try {
    await page.getByText('请选择一个活动成员租户；没有可用租户时不会读取配置。', { exact: true }).waitFor();
    assert.equal(reads, 0);
    await page.getByRole('combobox', { name: '活动租户', exact: true }).click();
    assert.equal(await page.getByRole('option', { name: 'forged-tenant', exact: true }).count(), 0);
  } finally { await page.close(); }
});

test('a pending policy or dictionary write blocks duplicate submissions and tenant switching', async () => {
  for (const kind of ['data-scopes', 'dictionary']) {
    let release, started;
    const pending = new Promise((resolve) => { started = resolve; });
    const deferred = new Promise((resolve) => { release = resolve; });
    const page = await pageFor(kind, async (route) => {
      const path = new URL(route.request().url()).pathname;
      if (route.request().method() === 'PUT') { started(); await deferred; return json(route, success({ ...policy, version: 2 })); }
      if (path.startsWith('/public/')) return json(route, success({ code: 'customer-segment', version: 1, items: [] }));
      if (path.endsWith('/events')) return route.fulfill({ contentType: 'text/event-stream', body: 'event: connected\ndata: {}\n\n' });
      return json(route, success([policy]));
    }, '?multi=1');
    try {
      const save = page.getByRole('button', { name: kind === 'dictionary' ? '发布字典覆盖' : '保存资源策略', exact: true });
      await save.click(); await pending;
      assert.equal(await save.isDisabled(), true);
      assert.match(await page.getByRole('combobox', { name: '活动租户', exact: true }).getAttribute('class'), /arco-select-disabled/);
    } finally { release(); await page.close(); }
  }
});

test('a pending parameter override blocks other writes, closing and tenant switching', async () => {
  let release, started;
  const pending = new Promise((resolve) => { started = resolve; });
  const deferred = new Promise((resolve) => { release = resolve; });
  const parameter = { key: 'tenant-setting', type: 'BOOLEAN', value: true, public: true, version: 1, status: 'ACTIVE' };
  const page = await pageFor('system-parameters', async (route) => {
    if (route.request().method() === 'PUT') { started(); await deferred; return json(route, success({ version: 1 })); }
    if (new URL(route.request().url()).pathname.endsWith('/system-parameter-events')) return json(route, success({ items: [] }));
    return json(route, success({ items: [parameter], page: 0, size: 20, totalElements: 1, totalPages: 1, projection: 'detail' }));
  }, '?multi=1');
  try {
    await page.getByRole('button', { name: '编辑', exact: true }).click();
    const save = page.getByRole('button', { name: '发布当前表单值为覆盖', exact: true });
    await save.click(); await pending;
    assert.equal(await save.isDisabled(), true);
    assert.match(await page.getByRole('combobox', { name: '活动租户', exact: true }).getAttribute('class'), /arco-select-disabled/);
    await page.locator('.arco-drawer').getByRole('button', { name: '取消', exact: true }).click();
    assert.equal(await page.locator('.arco-drawer').isVisible(), true);
  } finally { release(); await page.close(); }
});

test('switching active tenants clears policy previews and ignores a late response from the previous tenant', async () => {
  let release;
  let started;
  const pending = new Promise((resolve) => { started = resolve; });
  const delayed = new Promise((resolve) => { release = resolve; });
  const page = await pageFor('data-scopes', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (route.request().method() === 'POST') { started(); await delayed; return json(route, success({ decision: 'ALLOW', allowed: true, explanation: 'OLD_TENANT_SECRET', policyVersion: 1, effectiveScope: {} })); }
    return json(route, success([{ ...policy, enterpriseId: path.includes('/tenant-b/') ? 'tenant-b' : 'tenant-a', resource: path.includes('/tenant-b/') ? 'new-tenant-resource' : 'system-parameters' }]));
  }, '?multi=1');
  try {
    await page.getByRole('button', { name: '预览', exact: true }).click(); await pending;
    await page.getByRole('combobox', { name: '活动租户', exact: true }).click();
    await page.getByRole('option', { name: 'tenant-b', exact: true }).click();
    await page.getByRole('textbox', { name: 'new-tenant-resource condition tree', exact: true }).waitFor();
    release();
    await page.waitForTimeout(100);
    assert.equal(await page.getByText('OLD_TENANT_SECRET', { exact: true }).count(), 0);
    assert.equal(await page.getByRole('textbox', { name: 'system-parameters condition tree', exact: true }).count(), 0);
  } finally { release(); await page.close(); }
});

const parameter = { id: 'parameter-fixture', key: 'tenant-parameter', type: 'STRING', value: 'tenant-value', public: true, required: false, status: 'ACTIVE', version: 1 };
const parameterPage = { items: [parameter], page: 0, size: 20, totalElements: 1, totalPages: 1, projection: 'detail' };
test('parameter tenant switch removes history and ignores old tenant history completion', async () => {
  let release; let started;
  const pending = new Promise((resolve) => { started = resolve; }); const delay = new Promise((resolve) => { release = resolve; });
  const paths = [];
  const page = await pageFor('system-parameters', async (route) => {
    const path = new URL(route.request().url()).pathname; paths.push(path);
    if (path.endsWith('/versions')) { started(); await delay; return json(route, success({ items: [{ revision: 1, state: 'PUBLISHED', value: 'OLD_HISTORY_SECRET' }], currentVersion: 1 })); }
    if (path.endsWith('/system-parameter-events')) return json(route, success({ items: [] }));
    return json(route, success({ ...parameterPage, items: [{ ...parameter, key: path.includes('/tenant-b/') ? 'new-tenant-key' : parameter.key }] }));
  }, '?multi=1');
  try {
    await page.getByRole('button', { name: '版本历史', exact: true }).click(); await pending;
    await page.getByRole('combobox', { name: '活动租户', exact: true }).click(); await page.getByRole('option', { name: 'tenant-b', exact: true }).click();
    await page.getByRole('cell', { name: 'new-tenant-key', exact: true }).waitFor(); release();
    await page.waitForTimeout(100);
    assert.equal(await page.getByText('OLD_HISTORY_SECRET', { exact: true }).count(), 0);
    assert.ok(paths.some((path) => path === '/api/v1/enterprises/tenant-b/system-parameters'));
    assert.ok(paths.every((path) => path.startsWith('/api/v1/enterprises/')));
  } finally { release(); await page.close(); }
});

test('dictionary tenant switch rejects old public results and closes the previous subscription', async () => {
  let release; let started;
  const pending = new Promise((resolve) => { started = resolve; }); const delay = new Promise((resolve) => { release = resolve; });
  const paths = [];
  const page = await pageFor('dictionary', async (route) => {
    const path = new URL(route.request().url()).pathname; paths.push(path);
    if (!path.startsWith('/public/')) return route.fulfill({ contentType: 'text/event-stream', body: 'event: connected\ndata: {}\n\n' });
    if (path.includes('/tenant-a/')) { started(); await delay; return json(route, success({ code: 'customer-segment', version: 99, items: [{ value: 'OLD_SECRET', label: 'OLD_SECRET' }] })); }
    return json(route, success({ code: 'customer-segment', version: 2, items: [{ value: 'NEW_PUBLIC', label: 'NEW_PUBLIC' }] }));
  }, '?multi=1');
  try {
    await pending;
    await page.getByRole('combobox', { name: '活动租户', exact: true }).click(); await page.getByRole('option', { name: 'tenant-b', exact: true }).click();
    await page.getByTestId('dictionary-version').waitFor(); release(); await page.waitForTimeout(100);
    assert.equal(await page.getByText('OLD_SECRET', { exact: true }).count(), 0);
    assert.ok((await page.getByRole('textbox', { name: '覆盖选项 JSON', exact: true }).inputValue()).includes('NEW_PUBLIC'));
    assert.ok(paths.includes('/public/enterprises/tenant-b/dictionaries/customer-segment'));
    assert.ok(paths.includes('/api/v1/enterprises/tenant-b/dictionaries/customer-segment/events'));
  } finally { release(); await page.close(); }
});

test('dirty policy confirms tenant switch and inactive memberships are not selectable', async () => {
  const paths = [];
  const page = await pageFor('data-scopes', async (route) => { paths.push(new URL(route.request().url()).pathname); return json(route, success([policy])); }, '?multi=1');
  try {
    const input = page.getByRole('textbox', { name: 'system-parameters condition tree', exact: true }); await input.fill('{draft');
    await page.getByRole('combobox', { name: '活动租户', exact: true }).click();
    assert.equal(await page.getByRole('option', { name: 'inactive', exact: true }).count(), 0);
    await page.getByRole('option', { name: 'tenant-b', exact: true }).click();
    await page.getByRole('button', { name: '继续编辑', exact: true }).click();
    assert.equal(await input.inputValue(), '{draft'); assert.ok(paths.every((path) => !path.includes('/tenant-b/')));
    await page.getByRole('combobox', { name: '活动租户', exact: true }).click(); await page.getByRole('option', { name: 'tenant-b', exact: true }).click();
    await page.getByRole('button', { name: '放弃修改', exact: true }).click();
    await page.waitForFunction(() => !document.querySelector('textarea[aria-label="system-parameters condition tree"]')?.value.includes('{draft'));
    assert.ok(paths.some((path) => path.includes('/tenant-b/')));
  } finally { await page.close(); }
});
