import assert from 'node:assert/strict';
import { before, after, test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import { chromium } from 'playwright-core';
let vite, browser, origin;
const errors = [];
before(async () => {
  vite = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)), logLevel: 'error', server: { port: 0, hmr: false }, plugins: [{ name: 'dictionary-select-fixture', configureServer(server) {
    server.middlewares.use(async (req, res, next) => {
      if (!req.url.startsWith('/__t18-t19/')) return next();
      res.setHeader('Content-Type', 'text/html');
      res.end(await server.transformIndexHtml(req.url, '<div id="root"></div><script type="module" src="/scripts/t18-t19-browser-fixture.tsx"></script>'));
    });
  } }] });
  await vite.listen(); origin = `http://127.0.0.1:${vite.httpServer.address().port}`;
  browser = await chromium.launch({ headless: true, executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' });
});
after(async () => { await browser?.close(); vite?.httpServer.closeAllConnections(); await vite?.close(); assert.deepEqual(errors, []); });
async function setup() {
  const page = await browser.newPage({ viewport: { width: 1100, height: 850 } });
  page.setDefaultTimeout(8000); page.on('pageerror', error => errors.push(error.message));
  const requests = [];
  const state = { status: 200, unavailable: false };
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/events')) return route.fulfill({ status: 200, contentType: 'text/event-stream', body: 'event: connected\ndata: {}\n\n' });
    const body = route.request().postDataJSON(); requests.push({ path, body });
    if (state.status !== 200) return route.fulfill({ status: state.status, contentType: 'application/json', body: JSON.stringify({ success: false, error: { code: 'DENIED', message: '不可用' }, requestId: 'component-denied' }) });
    const choices = [{ value: '01', label: '普通客户', description: '', sortOrder: 10, enabled: true }, { value: 'vip', label: '贵宾客户', description: '', sortOrder: 30, enabled: true }];
    const facets = Object.fromEntries((body.groupCodes || []).map(group => [group, { choices: state.unavailable ? [] : group === 'vip' ? [choices[1]] : choices, groupStatus: state.unavailable ? 'unavailable' : 'available' }]));
    const items = (body.values || []).map(value => ({ value, label: value === 'retired' ? '历史客户' : choices.find(i => i.value === value)?.label || null, description: '', status: value === 'retired' ? 'disabled' : choices.some(i => i.value === value) ? 'enabled' : 'unknown' }));
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: { items, facets, publishedVersion: 1, effectiveRevision: 1, publishedRevision: null, source: 'base', effectiveToken: 'fixture-1' }, requestId: 'component-read' }) });
  });
  await page.goto(origin + '/__t18-t19/dictionary-select');
  await page.locator('[data-dictionary-selection="ready"]').waitFor();
  return { page, requests, state, select: page.locator('.arco-select').first() };
}

test('standard Form.Item binding validates on submit, preserves string values, searches, clears and resets', async () => {
  const { page, select } = await setup();
  try {
    assert.equal(await page.locator('.arco-form-message').count(), 0);
    await page.getByRole('button', { name: '提交', exact: true }).click();
    await page.locator('.arco-form-message').getByText('请选择客户分类', { exact: true }).waitFor();
    await select.click(); await page.getByRole('option', { name: '普通客户', exact: true }).click();
    await page.getByTestId('selected-value').getByText('"01"', { exact: true }).waitFor();
    await page.getByRole('button', { name: '提交', exact: true }).click();
    await page.getByTestId('submitted-value').getByText('{"segment":"01"}', { exact: true }).waitFor();
    await select.hover(); await select.locator('.arco-select-clear-icon').click();
    await page.getByTestId('selected-value').getByText('""', { exact: true }).waitFor();
    await select.click(); await select.locator('input').fill('贵宾');
    assert.deepEqual(await page.getByRole('option').allTextContents(), ['贵宾客户']);
    await page.getByRole('option', { name: '贵宾客户', exact: true }).click();
    await page.getByRole('button', { name: '重置', exact: true }).click();
    assert.equal(await page.getByTestId('selected-value').textContent(), '""');
  } finally { await page.close(); }
});

test('group-scoped choices and disabled control preserve the caller value, with a reusable field layout', async () => {
  const { page, select, requests } = await setup();
  try {
    await page.getByLabel('组件字典组编码', { exact: true }).fill('vip');
    await page.locator('[data-dictionary-selection="ready"]').waitFor();
    await select.click(); await page.getByRole('option', { name: '贵宾客户', exact: true }).waitFor();
    assert.deepEqual(await page.getByRole('option').allTextContents(), ['贵宾客户']);
    await page.getByRole('option', { name: '贵宾客户', exact: true }).click();
    await page.getByRole('button', { name: '切换禁用', exact: true }).click();
    assert.match(await select.getAttribute('class'), /arco-select-disabled/);
    assert.equal(await page.getByTestId('selected-value').textContent(), '"vip"');
    assert.ok(requests.some(r => r.body.groupCodes.includes('vip')));
    await page.getByRole('button', { name: '切换禁用', exact: true }).click();
    await page.locator('.arco-select-popup').waitFor({ state: 'hidden' });
    await page.screenshot({ path: '../.scratch/dictionary-select-20261120/component.png', fullPage: false, animations: 'disabled' });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.getByRole('button', { name: '提交', exact: true }).focus();
    assert.equal(await page.getByText('业务表单中的字典选择', { exact: true }).count(), 1);
    await page.screenshot({ path: '../.scratch/dictionary-select-20261120/component-narrow.png', fullPage: false, animations: 'disabled' });
  } finally { await page.close(); }
});

test('unavailable groups and denied reads never silently clear existing business values', async () => {
  const { page, state, select } = await setup();
  try {
    await page.getByRole('button', { name: '设置旧值', exact: true }).click();
    await page.getByText('历史客户（已停用）').first().waitFor();
    assert.equal(await page.getByTestId('selected-value').textContent(), '"retired"');
    state.unavailable = true;
    await page.getByLabel('组件字典组编码', { exact: true }).fill('missing');
    await page.getByText('字典组不可用', { exact: true }).waitFor();
    assert.match(await select.getAttribute('class'), /arco-select-disabled/);
    assert.equal(await page.getByTestId('selected-value').textContent(), '"retired"');
    state.status = 403;
    await page.getByLabel('组件字典编码', { exact: true }).fill('denied-dictionary');
    await page.locator('[data-dictionary-selection="forbidden"]').waitFor();
    assert.equal(await page.getByTestId('selected-value').textContent(), '"retired"');
  } finally { await page.close(); }
});

test('an empty dictionary code disables the field without requesting an empty dictionary', async () => {
  const { page, requests, select } = await setup();
  try {
    await page.getByLabel('组件字典编码', { exact: true }).fill('');
    assert.match(await select.getAttribute('class'), /arco-select-disabled/);
    assert.equal(requests.some(r => r.path.includes('/dictionaries//')), false);
    await page.getByLabel('组件字典编码', { exact: true }).fill('other-dictionary');
    await page.locator('[data-dictionary-selection="ready"]').waitFor();
    assert.ok(requests.some(r => r.path.endsWith('/other-dictionary/resolve-selection')));
  } finally { await page.close(); }
});
