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

test('failed permission preview clears the previous effective scope and shows requestId', async () => {
  let previews = 0;
  const page = await pageFor('data-scopes', async (route) => {
    if (route.request().method() === 'GET') return json(route, success([policy]));
    if (++previews === 1) return json(route, success({ decision: 'ALLOW', allowed: true, explanation: 'UI_PREVIEW_OLD', policyVersion: 1, effectiveScope: {} }));
    return json(route, failure(403, 'preview-denied'), 403);
  });
  try {
    await page.getByPlaceholder('用户', { exact: true }).fill('ui-fixture');
    const button = page.getByRole('button', { name: '预览', exact: true });
    await button.click();
    await page.getByText('UI_PREVIEW_OLD').waitFor();
    await button.click();
    await page.getByText('无权管理数据范围 (preview-denied)').waitFor();
    assert.equal(await page.getByText('UI_PREVIEW_OLD').count(), 0);
  } finally { await page.close(); }
});

const parameter = { id: 'synthetic-parameter', key: 'ui-probe', type: 'BOOLEAN', value: true, required: false, public: true, status: 'ACTIVE', version: 1 };
const parameterPage = { items: [parameter], page: 0, size: 20, totalElements: 1, totalPages: 1, projection: 'detail' };
test('denied event reload removes old delivery rows and retains requestId', async () => {
  let events = 0;
  const page = await pageFor('system-parameters', async (route) => {
    if (new URL(route.request().url()).pathname === '/api/system-parameter-events') {
      if (++events === 1) return json(route, success({ items: [{ eventId: 'UI_OLD_DELIVERY', parameterKey: 'UI_OLD_DELIVERY', status: 'PUBLISHED', attempts: 0 }] }));
      return json(route, failure(403, 'events-denied'), 403);
    }
    return json(route, success(parameterPage));
  });
  try {
    await page.getByText('UI_OLD_DELIVERY', { exact: true }).waitFor();
    await page.getByPlaceholder('键', { exact: true }).fill('other');
    await page.getByText('无权执行此配置操作 (events-denied)').waitFor();
    assert.equal(await page.getByText('UI_OLD_DELIVERY', { exact: true }).count(), 0);
  } finally { await page.close(); }
});

test('denied history reload removes the old version values', async () => {
  let histories = 0;
  const page = await pageFor('system-parameters', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/versions')) {
      if (++histories === 1) return json(route, success({ items: [{ revision: 1, state: 'PUBLISHED', value: 'UI_OLD_VERSION', status: 'ACTIVE' }], currentVersion: 1 }));
      return json(route, failure(403, 'history-denied'), 403);
    }
    return json(route, success(path === '/api/system-parameter-events' ? { items: [] } : parameterPage));
  });
  try {
    const button = page.getByRole('button', { name: '版本历史', exact: true });
    await button.click();
    await page.getByText('UI_OLD_VERSION', { exact: true }).waitFor();
    await button.click();
    await page.getByText('无权执行此配置操作 (history-denied)').waitFor();
    assert.equal(await page.getByText('UI_OLD_VERSION', { exact: true }).count(), 0);
  } finally { await page.close(); }
});

const dictionary = { code: 'customer-segment', version: 1, items: [{ value: 'fixture', label: 'Fixture segment', enabled: true, sortOrder: 0 }] };
test('public dictionary reads carry the current identity without requiring anonymous callers to log in', async () => {
  for (const anonymous of [false, true]) {
    let authenticated = false;
    const page = await pageFor('dictionary', async (route) => {
      if (new URL(route.request().url()).pathname.startsWith('/public/')) {
        authenticated = Boolean(route.request().headers().authorization);
        return json(route, success(dictionary));
      }
      return route.fulfill({ contentType: 'text/event-stream', body: 'event: connected\ndata: {}\n\n' });
    }, anonymous ? '?anonymous=1' : '');
    try {
      await page.getByTestId('dictionary-version').waitFor();
      assert.equal(authenticated, !anonymous, 'public reads must use an available identity but remain anonymous-capable');
    } finally { await page.close(); }
  }
});

test('denied dictionary override shows requestId and preserves the unsaved input', async () => {
  const page = await pageFor('dictionary', async (route) => {
    if (route.request().method() === 'PUT') return json(route, failure(403, 'override-denied'), 403);
    if (new URL(route.request().url()).pathname.startsWith('/public/')) return json(route, success(dictionary));
    return route.fulfill({ contentType: 'text/event-stream', body: 'event: connected\ndata: {}\n\n' });
  });
  try {
    await page.getByTestId('dictionary-version').waitFor();
    const draft = JSON.stringify(dictionary.items);
    await page.locator('textarea').fill(draft);
    await page.getByRole('button', { name: '发布字典覆盖', exact: true }).click();
    await page.getByText('无权发布字典覆盖', { exact: false }).waitFor();
    assert.match(await page.locator('.arco-alert-error').innerText(), /override-denied/);
    assert.equal(await page.locator('textarea').inputValue(), draft);
  } finally { await page.close(); }
});

test('parameter edit sends only mutable fields and retains the public marker', async () => {
  let submitted;
  const page = await pageFor('system-parameters', async (route) => {
    if (route.request().method() === 'PATCH') { submitted = route.request().postDataJSON(); return json(route, success(parameter)); }
    return json(route, success(new URL(route.request().url()).pathname === '/api/system-parameter-events' ? { items: [] } : parameterPage));
  });
  try {
    await page.getByRole('button', { name: '编辑', exact: true }).click();
    await page.locator('.arco-drawer').getByRole('button', { name: '确定', exact: true }).click();
    await page.locator('.arco-drawer').waitFor({ state: 'hidden' });
    assert.equal(submitted.publicValue, true);
    assert.equal('id' in submitted, false, 'server metadata must not be PATCH input');
    assert.equal('key' in submitted, false, 'immutable identity must not be PATCH input');
  } finally { await page.close(); }
});

test('invalid boolean input is rejected instead of silently publishing false', async () => {
  let writes = 0;
  const page = await pageFor('system-parameters', async (route) => {
    if (route.request().method() === 'PATCH') { writes++; return json(route, failure(400, 'should-not-submit'), 400); }
    return json(route, success(new URL(route.request().url()).pathname === '/api/system-parameter-events' ? { items: [] } : parameterPage));
  });
  try {
    await page.getByRole('button', { name: '编辑', exact: true }).click();
    const drawer = page.locator('.arco-drawer');
    await drawer.locator('.arco-form-item').filter({ has: page.locator('label').getByText('值', { exact: true }) }).getByRole('textbox').fill('not-a-boolean');
    await drawer.getByRole('button', { name: '确定', exact: true }).click();
    await page.locator('.arco-alert-error').waitFor();
    assert.equal(writes, 0, 'invalid local values must not become runtime writes');
    assert.equal(await drawer.isVisible(), true);
  } finally { await page.close(); }
});

test('unsaved data scope trees block navigation and remain editable after cancellation', async () => {
  const page = await pageFor('data-scopes', (route) => json(route, success([policy])));
  try {
    const textarea = page.getByRole('textbox', { name: 'system-parameters condition tree' });
    await textarea.fill('{"field":"SELF"}');
    const prompt = page.waitForEvent('dialog', { timeout: 2000 });
    const clicked = page.getByRole('link', { name: '离开测试页' }).click();
    const dialog = await prompt;
    await dialog.dismiss();
    await clicked;
    assert.equal(await textarea.inputValue(), '{"field":"SELF"}');
  } finally { await page.close(); }
});

test('parameter drawer cancellation keeps dirty edits until discard is confirmed', async () => {
  const page = await pageFor('system-parameters', (route) => json(route, success(new URL(route.request().url()).pathname === '/api/system-parameter-events' ? { items: [] } : parameterPage)));
  try {
    await page.getByRole('button', { name: '编辑', exact: true }).click();
    const drawer = page.locator('.arco-drawer');
    const field = drawer.locator('.arco-form-item').filter({ has: page.locator('label').getByText('值', { exact: true }) }).getByRole('textbox');
    await field.fill('false');
    await drawer.getByRole('button', { name: '取消', exact: true }).click();
    await page.getByRole('button', { name: '继续编辑', exact: true }).click();
    assert.equal(await field.inputValue(), 'false');
  } finally { await page.close(); }
});

test('unsaved dictionary overrides survive cancelled navigation', async () => {
  const page = await pageFor('dictionary', (route) => new URL(route.request().url()).pathname.startsWith('/public/') ? json(route, success(dictionary)) : route.fulfill({ contentType: 'text/event-stream', body: 'event: connected\ndata: {}\n\n' }));
  try {
    await page.getByTestId('dictionary-version').waitFor();
    await page.locator('textarea').fill('[]');
    const prompt = page.waitForEvent('dialog', { timeout: 2000 });
    const clicked = page.getByRole('link', { name: '离开测试页' }).click();
    const dialog = await prompt;
    await dialog.dismiss();
    await clicked;
    assert.equal(await page.locator('textarea').inputValue(), '[]');
  } finally { await page.close(); }
});

test('dictionary form displays the selected option returned by an authorized read', async () => {
  const page = await pageFor('dictionary', (route) => new URL(route.request().url()).pathname.startsWith('/public/') ? json(route, success(dictionary)) : route.fulfill({ contentType: 'text/event-stream', body: 'event: connected\ndata: {}\n\n' }));
  try {
    await page.getByTestId('dictionary-version').waitFor();
    assert.match(await page.locator('.arco-select-view').innerText(), /Fixture segment/);
  } finally { await page.close(); }
});

test('refreshing data scopes requires confirmation before replacing unsaved trees', async () => {
  const page = await pageFor('data-scopes', (route) => json(route, success([policy])));
  try {
    const textarea = page.getByRole('textbox', { name: 'system-parameters condition tree' });
    await textarea.fill('{"field":"SELF"}');
    await page.getByRole('button', { name: '刷新', exact: true }).click();
    await page.getByRole('button', { name: '继续编辑', exact: true }).click();
    assert.equal(await textarea.inputValue(), '{"field":"SELF"}');
  } finally { await page.close(); }
});

test('a transient dictionary read failure clears server options while retaining a dirty override draft', async () => {
  let reads = 0;
  const page = await pageFor('dictionary', (route) => {
    if (new URL(route.request().url()).pathname.startsWith('/public/')) return ++reads === 1 ? json(route, success(dictionary)) : json(route, failure(503, 'read-unavailable'), 503);
    return route.fulfill({ contentType: 'text/event-stream', body: 'event: connected\ndata: {}\n\n' });
  });
  try {
    await page.getByTestId('dictionary-version').waitFor();
    await page.locator('textarea').fill('[{"value":"fixture","label":"Edited"}]');
    await page.getByRole('button', { name: '刷新选项', exact: true }).click();
    await page.getByText('controlled failure (read-unavailable)').waitFor();
    assert.equal(await page.getByTestId('dictionary-version').count(), 0);
    assert.equal(await page.locator('textarea').inputValue(), '[{"value":"fixture","label":"Edited"}]');
  } finally { await page.close(); }
});

test('a late parameter response cannot restore rows after a newer denied read', async () => {
  let release;
  const page = await pageFor('system-parameters', async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname === '/api/system-parameter-events') return json(route, success({ items: [] }));
    if (url.searchParams.has('key')) return json(route, failure(403, 'newer-read-denied'), 403);
    await new Promise((resolve) => { release = resolve; });
    return json(route, success(parameterPage));
  });
  try {
    await page.getByPlaceholder('键', { exact: true }).fill('restricted');
    await page.getByText('无权执行此配置操作 (newer-read-denied)').waitFor();
    const late = page.waitForResponse((response) => new URL(response.url()).pathname === '/api/system-parameters' && !new URL(response.url()).searchParams.has('key'));
    release();
    await late;
    await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))));
    assert.equal(await page.getByText(parameter.key, { exact: true }).count(), 0);
  } finally { release?.(); await page.close(); }
});

test('saving one resource policy preserves another resource unsaved tree', async () => {
  const policies = [policy, { ...policy, resource: 'dictionaries' }];
  const page = await pageFor('data-scopes', (route) => json(route, success(route.request().method() === 'PUT' ? policy : policies)));
  try {
    await page.getByRole('textbox', { name: 'system-parameters condition tree' }).fill('{"field":"TENANT"}');
    const other = page.getByRole('textbox', { name: 'dictionaries condition tree' });
    await other.fill('{"field":"SELF"}');
    await page.getByRole('button', { name: '保存资源策略', exact: true }).first().click();
    await page.getByText('数据范围已保存。', { exact: true }).waitFor();
    await page.waitForLoadState('networkidle');
    assert.equal(await other.inputValue(), '{"field":"SELF"}');
  } finally { await page.close(); }
});

test('a late dictionary response cannot restore options after a denied notification refresh', async () => {
  let release;
  let reads = 0;
  const page = await pageFor('dictionary', async (route) => {
    if (!new URL(route.request().url()).pathname.startsWith('/public/')) return route.fulfill({ contentType: 'text/event-stream', body: 'event: connected\ndata: {}\n\nevent: DICTIONARY_PUBLISHED\ndata: {"dictionaryCode":"customer-segment","eventType":"DICTIONARY_PUBLISHED","version":2}\n\n' });
    if (++reads > 1) return json(route, failure(403, 'dictionary-newer-denied'), 403);
    await new Promise((resolve) => { release = resolve; });
    return json(route, success(dictionary));
  });
  try {
    await page.getByText('无权读取此字典 (dictionary-newer-denied)').waitFor();
    const late = page.waitForResponse((response) => new URL(response.url()).pathname.startsWith('/public/') && response.status() === 200);
    release();
    await late;
    await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))));
    assert.equal(await page.getByTestId('dictionary-version').count(), 0);
  } finally { release?.(); await page.close(); }
});

test('invalid condition and option JSON remains editable without submitting writes', async () => {
  for (const name of ['data-scopes', 'dictionary']) {
    let writes = 0;
    const page = await pageFor(name, (route) => {
      if (['PUT', 'POST'].includes(route.request().method())) { writes++; return json(route, failure(400, 'unexpected-write'), 400); }
      if (new URL(route.request().url()).pathname.startsWith('/public/')) return json(route, success(dictionary));
      if (new URL(route.request().url()).pathname.endsWith('/events')) return route.fulfill({ contentType: 'text/event-stream', body: 'event: connected\ndata: {}\n\n' });
      return json(route, success([policy]));
    });
    try {
      const textarea = page.getByRole('textbox', { name: name === 'data-scopes' ? 'system-parameters condition tree' : '覆盖选项 JSON', exact: true });
      await textarea.fill('{invalid-json');
      await page.getByRole('button', { name: name === 'data-scopes' ? '保存资源策略' : '发布字典覆盖', exact: true }).click();
      await page.getByText(name === 'data-scopes' ? '条件树必须是有效 JSON' : '请输入有效选项 JSON', { exact: true }).waitFor();
      assert.equal(writes, 0);
      assert.equal(await textarea.inputValue(), '{invalid-json');
    } finally { await page.close(); }
  }
});

test('summary-only parameter rows cannot submit edits using absent values and versions', async () => {
  const summary = { id: parameter.id, key: parameter.key, type: parameter.type, status: parameter.status, public: parameter.public };
  const page = await pageFor('system-parameters', (route) => json(route, success(new URL(route.request().url()).pathname === '/api/system-parameter-events' ? { items: [] } : { ...parameterPage, projection: 'summary', items: [summary] })));
  try {
    const edit = page.getByRole('button', { name: '编辑', exact: true });
    await edit.waitFor();
    assert.equal(await edit.isDisabled(), true);
    assert.equal(await page.getByRole('button', { name: '停用', exact: true }).isDisabled(), true);
    assert.equal(await page.getByRole('button', { name: '版本历史', exact: true }).isDisabled(), false);
  } finally { await page.close(); }
});
