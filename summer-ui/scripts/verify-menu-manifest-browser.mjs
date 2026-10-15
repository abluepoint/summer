import assert from 'node:assert/strict';
import { before, after, test } from 'node:test';
import { createServer } from 'vite';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright-core';
let vite, browser, origin;
const envelope = (data) => ({ success: true, data, requestId: 'menu-fixture' });
const item = { menuId: 'child', parentMenuId: 'root', path: '/child', titleKey: 'menu.child', iconKey: 'child', sortOrder: 20, capabilityCode: 'customer.read', enabled: false, actions: ['read'], version: 1 };
const manifest = { manifestVersion: 7, items: [{ ...item, menuId: 'root', path: '/root', parentMenuId: null, enabled: true }, item] };
before(async () => {
  vite = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)), logLevel: 'error', server: { port: 0, strictPort: false }, plugins: [{ name: 'menu-browser-fixture', configureServer(server) { server.middlewares.use(async (req, res, next) => { if (!req.url.startsWith('/__menu/')) return next(); res.setHeader('Content-Type', 'text/html'); res.end(await server.transformIndexHtml(req.url, '<div id="root"></div><script type="module" src="/scripts/menu-manifest-browser-fixture.tsx"></script>')); }); } }] });
  await vite.listen(); origin = `http://127.0.0.1:${vite.httpServer.address().port}`;
  browser = await chromium.launch({ headless: true, executablePath: process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' });
});
after(async () => { await browser?.close(); vite?.httpServer.closeAllConnections(); await vite?.close(); });
async function open(handler, suffix = '') { const page = await browser.newPage(); page.setDefaultTimeout(4000); await page.route('**/api/**', handler); await page.goto(`${origin}/__menu/page${suffix}`); return page; }
const reply = (route, data = manifest, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(status === 200 ? envelope(data) : { success: false, error: { message: 'menu fixture error', code: 'TEST_MENU' }, requestId: 'menu-failure' }) });

test('administrator edits a disabled menu and saves parent, ordering and capability with its version', async () => {
  let submitted;
  const page = await open((route) => { if (route.request().method() === 'PATCH') { submitted = { url: new URL(route.request().url()), body: route.request().postDataJSON(), headers: route.request().headers() }; return reply(route, { ...item, ...submitted.body, version: 2 }); } return reply(route); });
  try {
    await page.getByRole('row').filter({ has: page.getByText('child', { exact: true }) }).getByRole('button', { name: '编辑', exact: true }).click();
    const drawer = page.locator('.arco-drawer');
    await drawer.getByRole('switch', { name: '菜单启用' }).click();
    await drawer.getByRole('textbox', { name: '父菜单 ID', exact: true }).fill('');
    await drawer.getByRole('spinbutton', { name: '排序', exact: true }).fill('5');
    await drawer.getByRole('textbox', { name: '能力码', exact: true }).fill('customer.write');
    await drawer.getByRole('button', { name: '保存', exact: true }).click();
    await drawer.waitFor({ state: 'hidden' });
    assert.deepEqual(submitted.body, { enabled: true, parentMenuId: '', sortOrder: 5, capabilityCode: 'customer.write' });
    assert.equal(submitted.headers['if-match'], '"1"'); assert.ok(submitted.headers['idempotency-key']);
    assert.equal(submitted.url.pathname, '/api/v1/enterprises/ent-acme/menu-manifest');
    assert.equal(submitted.url.searchParams.get('menuId'), 'child');
  } finally { await page.close(); }
});

test('failed optimistic save retains the draft and requestId and retries with the same idempotency key', async () => {
  const writes = [];
  const page = await open((route) => { if (route.request().method() === 'PATCH') { writes.push(route.request().headers()['idempotency-key']); return reply(route, null, 412); } return reply(route); });
  try {
    await page.getByRole('row').filter({ has: page.getByText('child', { exact: true }) }).getByRole('button', { name: '编辑', exact: true }).click();
    const drawer = page.locator('.arco-drawer');
    const capability = drawer.getByRole('textbox', { name: '能力码', exact: true });
    await capability.fill('customer.write');
    await drawer.getByRole('button', { name: '保存', exact: true }).click();
    await page.getByText('菜单已被修改，请保留草稿并刷新后重试 (menu-failure)').waitFor();
    assert.equal(await capability.inputValue(), 'customer.write');
    await drawer.getByRole('button', { name: '保存', exact: true }).click();
    await page.waitForFunction(() => !document.querySelector('.arco-btn-loading'));
    assert.equal(writes.length, 2); assert.equal(writes[0], writes[1]);
  } finally { await page.close(); }
});

test('dirty menu protects cancellation, navigation and refresh until explicitly discarded', async () => {
  const page = await open((route) => reply(route));
  try {
    await page.getByRole('row').filter({ has: page.getByText('child', { exact: true }) }).getByRole('button', { name: '编辑', exact: true }).click();
    const drawer = page.locator('.arco-drawer');
    await drawer.getByRole('textbox', { name: '能力码', exact: true }).fill('draft.read');
    await drawer.getByRole('button', { name: '取消', exact: true }).click();
    await page.getByRole('button', { name: '继续编辑', exact: true }).click();
    assert.equal(await drawer.getByRole('textbox', { name: '能力码', exact: true }).inputValue(), 'draft.read');
    await page.locator('.arco-modal-wrapper').waitFor({ state: 'hidden' });
    const dialog = page.waitForEvent('dialog'); const click = page.getByRole('link', { name: '离开测试页' }).dispatchEvent('click');
    await (await dialog).dismiss(); await click;
    assert.equal(new URL(page.url()).pathname, '/__menu/page');
    // The drawer blocks background controls, so first discard through its own explicit dialog.
    await drawer.getByRole('button', { name: '取消', exact: true }).click();
    await page.getByRole('button', { name: '放弃修改', exact: true }).click();
    await drawer.waitFor({ state: 'hidden' });
  } finally { await page.close(); }
});

test('ordinary visible-menu reader cannot see management or platform write controls', async () => {
  const paths = [];
  const page = await open((route) => { const path = new URL(route.request().url()).pathname; paths.push(path); return path.endsWith('/management') || path.includes('/platform/') ? reply(route, null, 403) : reply(route, { ...manifest, items: [manifest.items[0]] }); }, '?readOnly=1');
  try {
    await page.getByRole('cell', { name: 'root', exact: true }).waitFor();
    assert.equal(await page.getByRole('button', { name: '编辑', exact: true }).count(), 0);
    assert.equal(await page.getByRole('button', { name: '平台清单', exact: true }).count(), 0);
    assert.ok(paths.some((path) => path.endsWith('/ent-acme/menu-manifest')));
  } finally { await page.close(); }
});

test('server management access enables editing even when route grants only contain read actions', async () => {
  const page = await open((route) => reply(route), '?readOnly=1');
  try {
    await page.getByRole('row').filter({ has: page.getByText('child', { exact: true }) }).getByRole('button', { name: '编辑', exact: true }).waitFor();
    await page.getByRole('button', { name: '平台清单', exact: true }).waitFor();
  } finally { await page.close(); }
});

test('platform read is real HTTP and invalid JSON or version conflict preserves publication draft', async () => {
  let puts = 0;
  const page = await open((route) => { if (route.request().method() === 'PUT') { puts++; return reply(route, null, 409); } return reply(route); });
  try {
    await page.getByRole('button', { name: '平台清单', exact: true }).click();
    const input = page.getByRole('textbox', { name: '平台菜单 JSON', exact: true });
    await input.waitFor();
    await input.fill('{invalid');
    await page.getByRole('button', { name: '发布清单', exact: true }).click();
    await page.getByText('请输入有效菜单数组 JSON', { exact: true }).waitFor(); assert.equal(puts, 0);
    await input.fill('[]');
    await page.getByRole('button', { name: '发布清单', exact: true }).click();
    await page.getByText('菜单已被修改，请保留草稿并刷新后重试 (menu-failure)').waitFor();
    assert.equal(await input.inputValue(), '[]'); assert.equal(puts, 1);
    await page.getByRole('button', { name: '刷新', exact: true }).click();
    await page.getByRole('button', { name: '继续编辑', exact: true }).click();
    assert.equal(await input.inputValue(), '[]');
  } finally { await page.close(); }
});

test('a denied reload removes previously displayed menu metadata and shows correlation', async () => {
  let reads = 0;
  const page = await open((route) => new URL(route.request().url()).pathname.includes('/platform/') ? reply(route, null, 403) : ++reads === 1 ? reply(route) : reply(route, null, 403));
  try {
    await page.getByRole('cell', { name: 'child', exact: true }).waitFor();
    await page.getByRole('button', { name: '刷新', exact: true }).click();
    await page.getByText('无权管理菜单 (menu-failure)', { exact: true }).waitFor();
    assert.equal(await page.getByRole('cell', { name: 'child', exact: true }).count(), 0);
  } finally { await page.close(); }
});
