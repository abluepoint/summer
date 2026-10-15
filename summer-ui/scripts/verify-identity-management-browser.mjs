import assert from 'node:assert/strict';
import { before, after, test } from 'node:test';
import { randomUUID } from 'node:crypto';
import { mkdir } from 'node:fs/promises';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { createServer } from 'vite';
import { chromium } from 'playwright-core';
let vite, browser, origin, pageErrors = 0;
const envelope = (data) => ({ success: true, data, requestId: 'identity-fixture' });
const row = { username: 'fixture-account', role: 'USER', active: true };
const json = (route, data, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(status < 400 ? envelope(data) : { success: false, error: { code: 'FIXTURE_ERROR', message: 'controlled failure' }, requestId: 'identity-conflict' }) });
before(async () => {
  vite = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)), logLevel: 'error', cacheDir: process.env.SUMMER_IDENTITY_COMPONENT_CACHE_DIR || fileURLToPath(new URL('../../.git/summer4-closeout-20261015/frontend/identity-claims-fix/vite-cache/', import.meta.url)), server: { port: 0, strictPort: false, hmr: false, watch: { ignored: ['**/*'] } }, plugins: [{ name: 'identity-fixture', configureServer(server) { server.middlewares.use(async (req, res, next) => { if (!req.url.startsWith('/__identity/')) return next(); res.setHeader('Content-Type', 'text/html'); res.end(await server.transformIndexHtml(req.url, '<div id="root"></div><script type="module" src="/scripts/identity-management-browser-fixture.tsx"></script>')); }); } }] });
  await vite.listen(); origin = `http://127.0.0.1:${vite.httpServer.address().port}`;
  browser = await chromium.launch({ headless: true, executablePath: process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' });
});
after(async () => { await browser?.close(); vite?.httpServer.closeAllConnections(); await vite?.close(); assert.equal(pageErrors, 0, 'component pages must not throw unhandled browser errors'); });
async function open(pageName, handler, query = '') { const page = await browser.newPage(); page.on('pageerror', () => pageErrors++); page.setDefaultTimeout(5000); page.setDefaultNavigationTimeout(30000); await page.route('**/api/**', handler); await page.goto(`${origin}/__identity/${pageName}${query}`); return page; }
async function capture(page, name) { if (process.env.SUMMER_UI_CAPTURE_VISUALS !== '1') return; const directory = process.env.SUMMER_IDENTITY_COMPONENT_VISUAL_DIR ? pathToFileURL(process.env.SUMMER_IDENTITY_COMPONENT_VISUAL_DIR + '/') : new URL('../../.git/summer4-closeout-20261015/frontend/identity-claims-fix/screenshots/', import.meta.url); await mkdir(directory, { recursive: true }); await page.screenshot({ path: fileURLToPath(new URL(name, directory)), fullPage: true }); }

test('authenticated user management queries, creates and changes account login status through the real API contract', async () => {
  let records = [row], created = false, changed = false;
  const page = await open('users', (route) => {
    const request = route.request();
    if (request.method() === 'POST') { const body = request.postDataJSON(); created = body.username === 'new-account' && body.role === 'USER' && typeof body.password === 'string' && body.password.length >= 12; records = [...records, { username: body.username, role: body.role, active: true }]; return json(route, records[1], 201); }
    if (request.method() === 'PATCH') { changed = request.postDataJSON().active === false && new URL(request.url()).pathname === '/api/users/new-account'; records = records.map((item) => item.username === 'new-account' ? { ...item, active: false } : item); return json(route, records[1]); }
    assert.equal(new URL(request.url()).pathname, '/api/users'); return json(route, records);
  });
  try {
    await page.getByRole('cell', { name: 'fixture-account', exact: true }).waitFor();
    await capture(page, 'auth-users.png');
    await page.getByRole('button', { name: '新建用户', exact: true }).click();
    await page.getByRole('textbox', { name: '用户名', exact: true }).fill('new-account');
    await page.locator('input[type="password"]').fill(randomUUID());
    await page.locator('.arco-drawer').getByRole('button', { name: '创建', exact: true }).click();
    await page.locator('.arco-drawer').waitFor({ state: 'hidden' }); assert.equal(created, true);
    await page.getByRole('textbox', { name: '搜索用户名', exact: true }).fill('new-account');
    assert.equal(await page.getByRole('cell', { name: 'fixture-account', exact: true }).count(), 0);
    await page.getByRole('button', { name: '停用', exact: true }).click();
    await page.getByRole('button', { name: '恢复', exact: true }).waitFor(); assert.equal(changed, true);
    assert.equal(await page.locator('input[type="password"]').count(), 0);
  } finally { await page.close(); }
});

test('user conflict keeps the form but never displays rejected password details or stores the password', async () => {
  const password = randomUUID();
  let writes = 0;
  const page = await open('users', (route) => {
    if (route.request().method() !== 'POST') return json(route, [row]);
    if (++writes === 1) return route.fulfill({ status: 409, contentType: 'application/json', body: JSON.stringify({ success: false, error: { message: password, violations: [{ rejectedValue: password }] }, requestId: 'identity-conflict' }) });
    return json(route, row, 201);
  });
  try {
    await page.getByRole('button', { name: '新建用户', exact: true }).click();
    await page.getByRole('textbox', { name: '用户名', exact: true }).fill('duplicate-account');
    const input = page.locator('input[type="password"]'); await input.fill(password);
    const submit = page.locator('.arco-drawer').getByRole('button', { name: '创建', exact: true }); await submit.click();
    await page.getByText('用户已存在或数据发生冲突，请保留表单并核对后重试 (identity-conflict)', { exact: true }).first().waitFor();
    assert.equal(await page.getByRole('textbox', { name: '用户名', exact: true }).inputValue(), 'duplicate-account');
    assert.equal((await input.inputValue()) === password, true);
    assert.equal((await page.locator('body').innerText()).includes(password), false);
    assert.equal(await page.evaluate((value) => [...Object.values(localStorage), ...Object.values(sessionStorage)].some((item) => item.includes(value)), password), false);
    await submit.click(); await page.locator('.arco-drawer').waitFor({ state: 'hidden' }); assert.equal(writes, 2);
  } finally { await page.close(); }
});

test('invalid user input submits nothing and read-only identities have no account mutations', async () => {
  let writes = 0;
  const page = await open('users', (route) => { if (route.request().method() !== 'GET') writes++; return json(route, [row]); });
  try {
    await page.getByRole('button', { name: '新建用户', exact: true }).click();
    await page.getByRole('textbox', { name: '用户名', exact: true }).fill('new-account');
    await page.locator('input[type="password"]').fill(randomUUID().slice(0, 8));
    await page.locator('.arco-drawer').getByRole('button', { name: '创建', exact: true }).click();
    await page.getByText('密码需要 12–200 个字符', { exact: true }).waitFor(); assert.equal(writes, 0);
  } finally { await page.close(); }
  const reader = await open('users', (route) => json(route, [row]), '?readOnly=1');
  try { await reader.getByRole('cell', { name: row.username, exact: true }).waitFor(); assert.equal(await reader.getByRole('button', { name: '新建用户', exact: true }).count(), 0); assert.equal(await reader.getByRole('button', { name: '停用', exact: true }).count(), 0); }
  finally { await reader.close(); }
});

test('user creation protects dirty cancellation, navigation and a pending request', async () => {
  let started, release;
  const pending = new Promise((resolve) => { started = resolve; }); const deferred = new Promise((resolve) => { release = resolve; });
  const page = await open('users', async (route) => { if (route.request().method() === 'POST') { started(); await deferred; return json(route, row, 201); } return json(route, [row]); });
  try {
    await page.getByRole('button', { name: '新建用户', exact: true }).click();
    const username = page.getByRole('textbox', { name: '用户名', exact: true }); await username.fill('draft-account');
    const drawer = page.locator('.arco-drawer'); await drawer.getByRole('button', { name: '取消', exact: true }).click();
    await page.getByRole('button', { name: '继续编辑', exact: true }).click(); await page.locator('.arco-modal-wrapper').waitFor({ state: 'hidden' });
    assert.equal(await username.inputValue(), 'draft-account');
    const dialog = page.waitForEvent('dialog'); const click = page.getByRole('link', { name: '离开测试页' }).dispatchEvent('click'); await (await dialog).dismiss(); await click;
    await page.locator('input[type="password"]').fill(randomUUID()); await drawer.getByRole('button', { name: '创建', exact: true }).click(); await pending;
    assert.equal(await drawer.getByRole('button', { name: '创建', exact: true }).isDisabled(), true); assert.equal(await drawer.getByRole('button', { name: '取消', exact: true }).isDisabled(), true);
    await page.getByRole('link', { name: '离开测试页' }).dispatchEvent('click'); assert.equal(new URL(page.url()).pathname, '/__identity/users');
  } finally { release(); await page.close(); }
});

test('denied user reload removes previously displayed accounts and retains request correlation', async () => {
  let reads = 0;
  const page = await open('users', (route) => ++reads === 1 ? json(route, [row]) : json(route, null, 403));
  try { await page.getByRole('cell', { name: row.username, exact: true }).waitFor(); await page.getByRole('button', { name: '刷新', exact: true }).click(); await page.getByText('无权管理认证用户 (identity-conflict)', { exact: true }).waitFor(); assert.equal(await page.getByRole('cell', { name: row.username, exact: true }).count(), 0); }
  finally { await page.close(); }
});

const unit = (id, name, parentId, children = []) => ({ id, enterpriseId: 'tenant-a', parentId, name, code: id, unitType: 'DEPARTMENT', description: '', leaderMemberId: '', sortOrder: 0, status: 'ACTIVE', memberCount: 0, children });
const tree = { enterpriseId: 'tenant-a', revision: 'tree-v1', items: [unit('root', '总部', null, [unit('child', '第二级组织', 'root', [unit('grandchild', '第三级组织', 'child')])])] };
test('organization browsing renders deep trees and changes personal context only through an explicit action', async () => {
  const contexts = [], reads = [];
  const page = await open('organization', (route) => {
    const path = new URL(route.request().url()).pathname; reads.push(path);
    if (path.endsWith('/context')) { if (route.request().method() === 'GET') return json(route, { enterpriseId: 'tenant-a', organizationId: contexts.at(-1)?.organizationId || null, revision: 'tree-v1' }); contexts.push(route.request().postDataJSON()); return json(route, { organizationId: contexts.at(-1).organizationId, revision: 'tree-v1' }); }
    if (path.endsWith('/org-tree')) return json(route, tree);
    if (path.includes('/org-units/') && path.endsWith('/members')) return json(route, { items: [{ memberId: 'member-a', displayName: '组织成员', email: 'fixture@example.test', status: 'ACTIVE', primary: true }] });
    return json(route, { items: [], total: 0, page: 0, size: 20, revision: 'member-v1' });
  });
  try {
    await page.getByRole('combobox', { name: '活动租户', exact: true }).waitFor();
    await page.getByText('第三级组织 (0)', { exact: true }).click();
    await page.getByRole('cell', { name: '组织成员', exact: true }).waitFor(); assert.equal(contexts.length, 0);
    await capture(page, 'organization.png');
    assert.ok(reads.some((path) => path.endsWith('/tenant-a/org-units/grandchild/members')));
    await page.getByRole('button', { name: '设为当前组织', exact: true }).click();
    await page.getByText('本页已确认上下文：第三级组织', { exact: true }).waitFor(); assert.deepEqual(contexts, [{ organizationId: 'grandchild' }]);
  } finally { await page.close(); }
});

test('organization metadata keeps a conflicted draft and uses detail ETag for edit and delete', async () => {
  const data = structuredClone(tree), writes = []; let attempts = 0;
  const page = await open('organization', (route) => {
    const request = route.request(), path = new URL(request.url()).pathname;
    const leaf = data.items[0].children[0].children[0];
    if (request.method() === 'PATCH') {
      writes.push({ method: 'PATCH', body: request.postDataJSON(), headers: request.headers() });
      if (++attempts === 1) return json(route, null, 412);
      Object.assign(leaf, request.postDataJSON()); data.revision = 'tree-v2'; return json(route, leaf);
    }
    if (request.method() === 'POST') { writes.push({ method: 'POST', body: request.postDataJSON(), headers: request.headers() }); leaf.children.push(unit('new-team', request.postDataJSON().name, leaf.id)); data.revision = 'tree-v3'; return json(route, leaf.children[0], 201); }
    if (request.method() === 'DELETE') { writes.push({ method: 'DELETE', headers: request.headers() }); leaf.children = []; data.revision = 'tree-v4'; return route.fulfill({ status: 204 }); }
    if (path.endsWith('/org-tree')) return json(route, data);
    if (path.includes('/org-units/') && !path.endsWith('/members')) { const item = path.endsWith('/new-team') ? leaf.children[0] : leaf; return route.fulfill({ contentType: 'application/json', headers: { etag: '"unit-v1"' }, body: JSON.stringify(envelope(item)) }); }
    return json(route, { items: [], total: 0, size: 20, page: 0 });
  });
  try {
    await page.getByText('第三级组织 (0)', { exact: true }).click(); await page.getByRole('button', { name: '编辑组织', exact: true }).click();
    const name = page.getByRole('textbox', { name: '组织名称', exact: true }); await name.fill('更新后的组织');
    const drawer = page.locator('.arco-drawer'); await drawer.getByRole('button', { name: '保存组织', exact: true }).click();
    await drawer.getByText('组织或成员已变更，请保留表单并核对后重试 (identity-conflict)', { exact: true }).waitFor(); assert.equal(await name.inputValue(), '更新后的组织');
    await drawer.getByRole('button', { name: '保存组织', exact: true }).click(); await drawer.waitFor({ state: 'hidden' });
    assert.equal(writes[0].headers['if-match'], '"unit-v1"'); assert.equal(writes[0].headers['idempotency-key'], writes[1].headers['idempotency-key']);
    await page.getByText('更新后的组织 (0)', { exact: true }).waitFor(); await page.getByRole('button', { name: '新建组织', exact: true }).click();
    await name.fill('新团队'); await page.getByRole('textbox', { name: '组织编码', exact: true }).fill('NEW_TEAM'); await drawer.getByRole('button', { name: '保存组织', exact: true }).click(); await drawer.waitFor({ state: 'hidden' });
    assert.equal(writes[2].body.parentId, 'grandchild'); assert.ok(writes[2].headers['idempotency-key']);
    await page.getByText('新团队 (0)', { exact: true }).click(); await page.getByRole('button', { name: '删除组织', exact: true }).click(); await page.getByRole('button', { name: '删除', exact: true }).click();
    await page.getByText('新团队 (0)', { exact: true }).waitFor({ state: 'hidden' }); assert.equal(writes[3].headers['if-match'], '"unit-v1"');
  } finally { await page.close(); }
});

test('member organization replacement validates primary organization, retains conflicts and blocks pending actions', async () => {
  let release, started; const deferred = new Promise((resolve) => { release = resolve; }); const pending = new Promise((resolve) => { started = resolve; }); const writes = [];
  const member = { memberId: 'member-a', displayName: '租户成员甲', email: 'fixture@example.test', status: 'ACTIVE' };
  const page = await open('organization', async (route) => {
    const request = route.request(), path = new URL(request.url()).pathname;
    if (path.endsWith('/org-assignments:batch')) { writes.push({ body: request.postDataJSON(), headers: request.headers() }); if (writes.length === 1) { started(); await deferred; return json(route, null, 412); } return json(route, { revision: 'tree-v2' }); }
    if (path.endsWith('/org-tree')) return json(route, tree);
    return json(route, { items: [member], total: 1, size: 20, page: 0 });
  });
  try {
    await page.getByRole('button', { name: '分配组织', exact: true }).click(); const drawer = page.locator('.arco-drawer');
    await page.getByRole('combobox', { name: '分配组织', exact: true }).click(); await page.getByRole('option', { name: '第三级组织', exact: true }).click(); await page.getByRole('combobox', { name: '分配组织', exact: true }).press('Escape');
    await drawer.getByRole('button', { name: '保存关系', exact: true }).click(); await drawer.getByText('请选择成员，并将一个已选组织设为主组织', { exact: true }).waitFor(); assert.equal(writes.length, 0);
    await page.getByRole('combobox', { name: '主组织', exact: true }).click(); await page.getByRole('option', { name: '第三级组织', exact: true, selected: false }).click();
    await drawer.getByRole('button', { name: '保存关系', exact: true }).click(); await pending;
    assert.equal(await drawer.getByRole('button', { name: '保存关系', exact: true }).isDisabled(), true); assert.equal(await drawer.getByRole('button', { name: '取消', exact: true }).isDisabled(), true);
    assert.match(await page.getByRole('combobox', { name: '活动租户', exact: true }).getAttribute('class'), /arco-select-disabled/);
    release(); await drawer.getByText('组织或成员已变更，请保留表单并核对后重试 (identity-conflict)', { exact: true }).waitFor();
    assert.equal((await page.getByRole('combobox', { name: '主组织', exact: true }).innerText()).includes('第三级组织'), true);
    await drawer.getByRole('button', { name: '保存关系', exact: true }).click(); await drawer.waitFor({ state: 'hidden' });
    assert.deepEqual(writes[0].body, { memberIds: ['member-a'], organizationIds: ['grandchild'], primaryOrganizationId: 'grandchild' });
    assert.equal(writes[0].headers['if-match'], '"tree-v1"'); assert.equal(writes[0].headers['idempotency-key'], writes[1].headers['idempotency-key']);
  } finally { release(); await page.close(); }
});

test('switching tenants clears directory state and ignores late organization reads', async () => {
  let release, started; const deferred = new Promise((resolve) => { release = resolve; }); const pending = new Promise((resolve) => { started = resolve; });
  const page = await open('organization', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/org-tree')) return json(route, path.includes('/tenant-b/') ? { ...tree, enterpriseId: 'tenant-b', items: [unit('b-root', '新租户组织', null)] } : tree);
    if (path.endsWith('/grandchild/members')) { started(); await deferred; return json(route, { items: [{ memberId: 'old', displayName: 'OLD_TENANT_SECRET', status: 'ACTIVE' }] }); }
    return json(route, { items: [], total: 0, size: 20, page: 0 });
  });
  try {
    await page.getByText('第三级组织 (0)', { exact: true }).click(); await pending;
    await page.getByRole('combobox', { name: '活动租户', exact: true }).click(); await page.getByRole('option', { name: 'tenant-b', exact: true }).click();
    await page.getByText('新租户组织 (0)', { exact: true }).waitFor(); release(); await page.waitForTimeout(100);
    assert.equal(await page.getByText('第三级组织 (0)', { exact: true }).count(), 0); assert.equal(await page.getByText('OLD_TENANT_SECRET', { exact: true }).count(), 0);
  } finally { release(); await page.close(); }
});

test('member status changes use the member revision and read-only organization access hides write actions', async () => {
  const member = { memberId: 'member-a', displayName: '租户成员甲', email: 'fixture@example.test', status: 'ACTIVE' }; let changed;
  const page = await open('organization', (route) => {
    const request = route.request(), path = new URL(request.url()).pathname;
    if (request.method() === 'POST') { changed = { path, headers: request.headers() }; member.status = 'DISABLED'; return json(route, { ...member, revision: 'member-v2' }); }
    if (path.endsWith('/org-tree')) return json(route, tree);
    if (path.endsWith('/members/member-a')) return json(route, { ...member, revision: 'member-v1' });
    return json(route, { items: [member], total: 1, size: 20, page: 0 });
  });
  try { await page.getByRole('button', { name: '停用成员', exact: true }).click(); await page.getByRole('button', { name: '恢复成员', exact: true }).waitFor(); assert.equal(changed.path, '/api/v1/enterprises/tenant-a/members/member-a:disable'); assert.equal(changed.headers['if-match'], '"member-v1"'); assert.ok(changed.headers['idempotency-key']); }
  finally { await page.close(); }
  const reader = await open('organization', (route) => json(route, new URL(route.request().url()).pathname.endsWith('/org-tree') ? tree : { items: [member], total: 1, size: 20, page: 0 }), '?readOnly=1');
  try { await reader.getByText('第三级组织 (0)', { exact: true }).waitFor(); assert.equal(await reader.getByRole('button', { name: '新建组织', exact: true }).count(), 0); assert.equal(await reader.getByRole('button', { name: '分配组织', exact: true }).count(), 0); assert.equal(await reader.getByRole('button', { name: '停用成员', exact: true }).count(), 0); }
  finally { await reader.close(); }
});

test('a denied organization reload cannot be undone by a late detail or member response', async () => {
  let reads = 0, release, detailStarted;
  const deferred = new Promise((resolve) => { release = resolve; }); const pending = new Promise((resolve) => { detailStarted = resolve; });
  const page = await open('organization', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/org-tree')) return ++reads === 1 ? json(route, tree) : json(route, null, 403);
    if (path.endsWith('/org-units/grandchild')) { detailStarted(); await deferred; return route.fulfill({ contentType: 'application/json', headers: { etag: '"unit-v1"' }, body: JSON.stringify(envelope(unit('grandchild', 'OLD_ORGANIZATION_DETAIL', 'child'))) }); }
    if (path.endsWith('/grandchild/members')) { await deferred; return json(route, { items: [{ memberId: 'old', displayName: 'OLD_ORGANIZATION_MEMBER', status: 'ACTIVE' }] }); }
    return json(route, { items: [], total: 0, size: 20, page: 0 });
  });
  try {
    await page.getByText('第三级组织 (0)', { exact: true }).click(); await page.getByRole('button', { name: '编辑组织', exact: true }).click(); await pending;
    await page.getByRole('button', { name: '刷新', exact: true }).click(); await page.getByText('无权操作此租户的组织或成员 (identity-conflict)', { exact: true }).waitFor();
    release(); await page.waitForTimeout(200);
    assert.equal(await page.locator('.arco-drawer').count(), 0); assert.equal(await page.getByText('OLD_ORGANIZATION_MEMBER', { exact: true }).count(), 0);
  } finally { release(); await page.close(); }
});

test('inactive tenant members cannot enter a batch organization assignment', async () => {
  const member = { memberId: 'inactive-member', displayName: '停用成员甲', email: 'fixture@example.test', status: 'DISABLED' };
  const page = await open('organization', (route) => json(route, new URL(route.request().url()).pathname.endsWith('/org-tree') ? tree : { items: [member], total: 1, page: 0, size: 20 }));
  try {
    const row = page.getByRole('row').filter({ has: page.getByRole('cell', { name: '停用成员甲', exact: true }) });
    await row.waitFor(); assert.equal(await row.getByRole('checkbox').isDisabled(), true); assert.equal(await row.getByRole('button', { name: '分配组织', exact: true }).isDisabled(), true);
  } finally { await page.close(); }
});

// Regression for the exact three real Chrome failures: business claims are not OAuth scopes.
test('business claims allow authorized user creation without widening OAuth scope', async () => {
  const page = await open('users', (route) => json(route, [row]));
  try {
    await page.getByRole('cell', { name: row.username, exact: true }).waitFor();
    assert.equal(await page.getByRole('button', { name: '新建用户', exact: true }).count(), 1);
  } finally { await page.close(); }
});
test('business claims allow organization creation for the selected enterprise', async () => {
  const page = await open('organization', (route) => json(route, new URL(route.request().url()).pathname.endsWith('/org-tree') ? tree : { items: [], total: 0, size: 20, page: 0 }));
  try {
    await page.getByText('第三级组织 (0)', { exact: true }).waitFor();
    assert.equal(await page.getByRole('button', { name: '新建组织', exact: true }).count(), 1);
  } finally { await page.close(); }
});
test('business claims reveal the authorized tenant member directory', async () => {
  const page = await open('organization', (route) => json(route, new URL(route.request().url()).pathname.endsWith('/org-tree') ? tree : { items: [], total: 0, size: 20, page: 0 }));
  try {
    await page.getByText('第三级组织 (0)', { exact: true }).waitFor();
    assert.equal(await page.getByText('租户成员', { exact: true }).count(), 1);
  } finally { await page.close(); }
});

test('foreign enterprise claims do not reveal Sample account or selected tenant controls', async () => {
  const users = await open('users', (route) => json(route, [row]), '?foreignGrant=1');
  try {
    await users.getByRole('cell', { name: row.username, exact: true }).waitFor();
    assert.equal(await users.getByRole('button', { name: '新建用户', exact: true }).count(), 0);
  } finally { await users.close(); }
  const organization = await open('organization', (route) => json(route, new URL(route.request().url()).pathname.endsWith('/org-tree') ? tree : { items: [], total: 0, size: 20, page: 0 }), '?foreignGrant=1');
  try {
    await organization.getByText('第三级组织 (0)', { exact: true }).waitFor();
    assert.equal(await organization.getByRole('button', { name: '新建组织', exact: true }).count(), 0);
    assert.equal(await organization.getByText('租户成员', { exact: true }).count(), 0);
  } finally { await organization.close(); }
});
test('inactive Sample membership cannot reveal account mutations', async () => {
  const page = await open('users', (route) => json(route, [row]), '?inactiveSample=1');
  try {
    await page.getByRole('cell', { name: row.username, exact: true }).waitFor();
    assert.equal(await page.getByRole('button', { name: '新建用户', exact: true }).count(), 0);
  } finally { await page.close(); }
});
test('unloading the authenticated identity hides previously granted account actions', async () => {
  const page = await open('users', (route) => json(route, [row]));
  try {
    const create = page.getByRole('button', { name: '新建用户', exact: true });
    await create.waitFor();
    await page.evaluate(async () => { await window.__summerOidcManager.removeUser(); });
    await create.waitFor({ state: 'hidden' });
  } finally { await page.close(); }
});


test('saved organization context is restored on page reload without writing a selection', async () => {
  let contextReads = 0, contextWrites = 0;
  const page = await open('organization', (route) => {
    const request = route.request(), path = new URL(request.url()).pathname;
    if (path.endsWith('/context')) { if (request.method() === 'GET') contextReads++; else contextWrites++; return json(route, { enterpriseId: 'tenant-a', organizationId: 'grandchild', revision: 'tree-v1' }); }
    return json(route, path.endsWith('/org-tree') ? tree : { items: [], total: 0, size: 20, page: 0 });
  });
  try {
    await page.getByText('本页已确认上下文：第三级组织', { exact: true }).waitFor();
    await page.reload(); await page.getByText('本页已确认上下文：第三级组织', { exact: true }).waitFor();
    assert.equal(contextReads, 2); assert.equal(contextWrites, 0); await capture(page, 'organization-restored-context.png');
  } finally { await page.close(); }
});

test('saved organization context ignores a late response after switching tenants', async () => {
  let release; const deferred = new Promise((resolve) => { release = resolve; });
  const page = await open('organization', async (route) => {
    const path = new URL(route.request().url()).pathname, second = path.includes('/tenant-b/');
    if (path.endsWith('/context')) { assert.equal(route.request().method(), 'GET'); if (!second) await deferred; return json(route, { enterpriseId: second ? 'tenant-b' : 'tenant-a', organizationId: second ? 'b-root' : 'grandchild', revision: 'tree-v1' }); }
    return json(route, path.endsWith('/org-tree') ? second ? { ...tree, enterpriseId: 'tenant-b', items: [unit('b-root', '新租户组织', null)] } : tree : { items: [], total: 0, size: 20, page: 0 });
  });
  try {
    await page.getByRole('combobox', { name: '活动租户', exact: true }).click(); await page.getByRole('option', { name: 'tenant-b', exact: true }).click();
    await page.getByText('本页已确认上下文：新租户组织', { exact: true }).waitFor(); release(); await page.waitForTimeout(100);
    assert.equal(await page.getByText('本页已确认上下文：第三级组织', { exact: true }).count(), 0);
    await page.getByText('本页已确认上下文：新租户组织', { exact: true }).waitFor();
  } finally { release(); await page.close(); }
});
