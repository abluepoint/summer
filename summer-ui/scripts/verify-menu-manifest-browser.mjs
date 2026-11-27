import assert from 'node:assert/strict';
import { before, after, test } from 'node:test';
import { createServer } from 'vite';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright-core';
let vite, browser, origin;
const envelope = (data) => ({ success: true, data, requestId: 'menu-fixture' });
const item = { menuId: 'child', parentMenuId: 'root', path: '/child', titleKey: 'menu.child', iconKey: 'child', sortOrder: 20, clientCode: 'web', enabled: false, actions: ['read'], version: 1 };
const manifest = { manifestVersion: 7, clientOptions: [
  {value:'web', label:'Web 管理端', enabled:true}, {value:'mobile',label:'移动端',enabled:true},
  {value:'off',label:'已停用',enabled:false}], items: [{ ...item, menuId: 'root', titleKey:'业务目录', path: '/root', parentMenuId: null, enabled: true }, item] };
before(async () => {
  vite = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)), cacheDir: 'node_modules/.vite-menu-editor', logLevel: 'error', server: { port: 0, strictPort: false }, plugins: [{ name: 'menu-browser-fixture', configureServer(server) { server.middlewares.use(async (req, res, next) => { if (!req.url.startsWith('/__menu/')) return next(); res.setHeader('Content-Type', 'text/html'); res.end(await server.transformIndexHtml(req.url, '<div id="root"></div><script type="module" src="/scripts/menu-manifest-browser-fixture.tsx"></script>')); }); } }] });
  await vite.listen(); origin = `http://127.0.0.1:${vite.httpServer.address().port}`;
  browser = await chromium.launch({ headless: true, executablePath: process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' });
});
after(async () => { await browser?.close(); vite?.httpServer.closeAllConnections(); await vite?.close(); });
async function open(handler, suffix = '') { const page = await browser.newPage(); page.setDefaultTimeout(4000); await page.route('**/api/**', route => {
  const path = new URL(route.request().url()).pathname;
  if (path.endsWith('/authorization/navigation')) return reply(route, {engineMode:'MANAGED',items:[]});
  if (path.endsWith('/authorization/operations')) return reply(route, {actions:{'menu.manifest.write': !suffix.includes('readOnly')}});
  return handler(route);
}); await page.goto(`${origin}/__menu/page${suffix}`); return page; }
const reply = (route, data = manifest, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(status === 200 ? envelope(data) : { success: false, error: { message: 'menu fixture error', code: 'TEST_MENU' }, requestId: 'menu-failure' }) });

function menuRow(page, id) {
  return page.getByRole('row').filter({ has: page.getByRole('cell', {name: '/' + id, exact:true}) });
}

async function editClient(page, row) {
  await row.getByRole('button', {name:'编辑'}).click();
  await page.getByRole('dialog').getByLabel('所属客户端', {exact:true}).click();
  const option = page.getByRole('option', { name: '移动端' });
  await option.click();
  await option.waitFor({ state: 'hidden' });
}

test('client is edited in the menu dialog and only the selected menu changes', async () => {
  let stored = manifest;
  const writes = [];
  const page = await open(route => {
    assert.equal(new URL(route.request().url()).pathname, '/api/v1/platform/authorization/menu-manifest');
    if (route.request().method() === 'PUT') {
      const body = route.request().postDataJSON();
      assert.equal(route.request().headers()['if-match'], '"' + stored.manifestVersion + '"');
      assert.equal(body.manifestVersion, stored.manifestVersion + 1);
      assert.ok(route.request().headers()['idempotency-key']);
      writes.push(body.items.map(item => item.clientCode));
      stored = {...stored, ...body};
    }
    return reply(route, stored);
  });
  try {
    const child = menuRow(page,'child'), root = menuRow(page,'root');
    assert.equal(await root.locator('.arco-select').count(), 0);
    await editClient(page, child);
    const dialog = page.getByRole('dialog');
    await dialog.getByText(/所属客户端由平台字典/).waitFor();
    assert.deepEqual(writes, []);
    await dialog.getByRole('button',{name:'保存'}).click();
    await child.getByText('移动端', {exact:true}).waitFor();
    assert.deepEqual(writes, [['web','mobile']]);
    await page.reload();
    await root.getByText('Web 管理端', {exact:true}).waitFor();
    await child.getByText('移动端', {exact:true}).waitFor();
  } finally { await page.close(); }
});

test('read-only platform view resolves the client label without publication controls', async () => {
  const page = await open(route=>reply(route), '?readOnly=1');
  try {
    await page.getByRole('cell', {name:'Web 管理端'}).first().waitFor();
    assert.equal(await page.getByRole('button',{name:'新建菜单'}).isDisabled(),true);
    for (const label of ['编辑','删除']) assert.equal(await menuRow(page, 'child').getByRole('button',{name:label}).isDisabled(),true);
    assert.equal(await page.getByRole('button',{name:'保存'}).count(),0);
  } finally { await page.close(); }
});

test('a version conflict retains the row selection and refresh asks before discarding it', async () => {
  let puts = 0;
  const page = await open(route => {
    if (route.request().method() === 'PUT') { puts++; return reply(route, null, 412); }
    return reply(route);
  });
  try {
    const child = menuRow(page, 'child');
    await editClient(page, child);
    await page.getByRole('dialog').getByRole('button',{name:'保存'}).click();
    await page.getByText('菜单已被修改，请重新加载后重试 (menu-failure)').waitFor();
    assert.equal(puts, 1);
    await page.getByRole('button', { name: '重新加载' }).click();
    await page.getByRole('button', { name: '继续编辑' }).click();
    await page.getByRole('dialog').getByText('移动端', {exact:true}).waitFor();
    await page.getByRole('dialog').getByRole('button',{name:'保存'}).waitFor();
  } finally { await page.close(); }
});

test('denied platform reads never expose the creation or editing controls', async () => {
  const page = await open(route => reply(route, null, 403));
  try {
    await page.getByText('无权管理菜单 (menu-failure)', { exact: true }).waitFor();
    assert.equal(await page.getByRole('button', { name: '新建菜单' }).count(), 0);
  } finally { await page.close(); }
});

test('tree menus can be created under a parent, renamed, disabled, and deleted', async () => {
  let stored = manifest;
  const page = await open(route => {
    if (route.request().method() === 'PUT') {
      const body = route.request().postDataJSON();
      assert.deepEqual(body.layoutVersions, Object.fromEntries(stored.items.map(i => [i.menuId,i.version])));
      stored = {...stored, manifestVersion:body.manifestVersion, items:body.items.map(i=>({...i, version:2}))};
    }
    return reply(route, stored);
  });
  try {
    const root = menuRow(page,'root');
    await root.getByRole('button',{name:'删除'}).click();
    await page.getByText('请先移动或删除子菜单').waitFor();
    assert.equal(await page.getByRole('button',{name:'刷新',exact:true}).count(),0);
    await page.getByRole('button',{name:'新建菜单'}).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByLabel('菜单名称',{exact:true}).fill('订单列表');
    await dialog.getByLabel('菜单 ID',{exact:true}).fill('orders');
    await dialog.getByLabel('父菜单',{exact:true}).click();
    await page.getByRole('option',{name:'业务目录',exact:true}).click();
    await dialog.getByRole('button',{name:'保存'}).click();
    const row = menuRow(page,'orders');
    await row.getByText('订单列表').waitFor();
    assert.equal(stored.items.find(i=>i.menuId==='orders').parentMenuId,'root');
    const toggle = root.locator('.arco-table-cell-expand-icon').first();
    await toggle.click(); await row.waitFor({state:'hidden'});
    await toggle.click(); await row.waitFor();
    await row.getByRole('button',{name:'编辑'}).click();
    await dialog.getByLabel('菜单名称',{exact:true}).fill('历史订单');
    await dialog.getByRole('switch').click();
    await dialog.getByRole('button',{name:'保存'}).click();
    await row.getByText('停用').waitFor();
    await page.reload();
    await row.getByText('历史订单').waitFor();
    await row.getByRole('button',{name:'删除'}).click();
    await dialog.getByRole('button',{name:'取消'}).click();
    await row.getByText('历史订单').waitFor();
    await row.getByRole('button',{name:'删除'}).click();
    await dialog.getByRole('button',{name:'删除'}).click();
    await row.waitFor({state:'detached'});
    assert.deepEqual(stored.items.map(i=>i.menuId), ['root','child']);
  } finally { await page.close(); }
});

test('the full catalog opens directly, including menus outside the current navigation', async () => {
  const role = {...item, menuId:'roles', path:'/roles', parentMenuId:null, titleKey:'角色与权限', enabled:true};
  const page = await open(route => reply(route, {...manifest, items:[role, ...manifest.items]}));
  try {
    await page.getByRole('button', {name:'新建菜单'}).waitFor();
    assert.equal(await page.getByText('当前菜单', {exact:true}).count(), 0);
    assert.equal(await page.getByRole('radiogroup').count(), 0);
    await menuRow(page,'child').getByText('停用').waitFor();
    const roles = menuRow(page,'roles');
    await roles.getByText('角色与权限').waitFor();
    assert.equal(await menuRow(page,'platform/roles').count(), 0);
    await roles.getByRole('button',{name:'编辑'}).click();
    const dialog = page.getByRole('dialog');
    assert.equal(await dialog.getByLabel('路径',{exact:true}).inputValue(),'/roles');
    await dialog.getByText(/共用此菜单配置/).waitFor();
  } finally { await page.close(); }
});
