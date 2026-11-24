import assert from 'node:assert/strict';
import { before, after, test } from 'node:test';
import { createServer } from 'vite';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright-core';
let vite, browser, origin;
const output =
  process.env.MANAGEMENT_SCREENSHOTS ||
  fileURLToPath(
    new URL(
      '../../.scratch/platform-only-permissions-20261120/',
      import.meta.url
    )
  );
before(async () => {
  vite = await createServer({
    root: fileURLToPath(new URL('..', import.meta.url)),
    logLevel: 'error',
    server: { port: 0 },
    plugins: [
      {
        name: 'separate-fixture',
        configureServer(server) {
          server.middlewares.use(async (req, res, next) => {
            if (!req.url.startsWith('/__separate/')) return next();
            res.setHeader('Content-Type', 'text/html');
            res.end(
              await server.transformIndexHtml(
                req.url,
                '<div id="root"></div><script type="module" src="/scripts/fixtures/separate-management.tsx"></script>'
              )
            );
          });
        },
      },
    ],
  });
  await vite.listen();
  origin = `http://127.0.0.1:${vite.httpServer.address().port}`;
  browser = await chromium.launch({
    headless: true,
    executablePath:
      process.env.CHROME_PATH ||
      '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
  });
});
after(async () => {
  await browser?.close();
  vite?.httpServer.closeAllConnections();
  await vite?.close();
});
const manifest = {
  engineMode: 'MANAGED',
  manifestVersion: 6,
  items: [
    {
      menuId: 'roles',
      path: '/roles',
      titleKey: '角色管理',
      iconKey: 'roles',
      sortOrder: 1,
      enabled: true,
      version: 1,
      actions: [],
    },
  ],
};
async function open(key, suffix = '', catalog = false, preparePage) {
  const page = await browser.newPage({
    viewport: { width: 1280, height: 900 },
  });
  page.setDefaultTimeout(6000);
  const calls = [],
    errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await page.route('**/api/**', async (route) => {
    const request = route.request(),
      url = new URL(request.url()),
      path = url.pathname;
    calls.push({
      path,
      query: Object.fromEntries(url.searchParams),
      method: request.method(),
      body: request.postDataJSON(),
    });
    let data = { items: [], total: 0, page: 1, size: 200 };
    if (path.endsWith('/authorization/operations'))
      data = {
        actions: Object.fromEntries(
          (url.searchParams.get('actions') || '')
            .split(',')
            .map((x) => [x, true])
        ),
      };
    else if (
      path.endsWith('/authorization/navigation') ||
      path.endsWith('/authorization/menu-layout') ||
      path.endsWith('/authorization/menu-manifest')
    )
      data = manifest;
    else if (catalog && (path.endsWith('/permission-groups') || url.searchParams.get('catalog') === 'permission-groups')) {
      const items = [
        {
          groupId: 'management',
          parentGroupId: null,
          name: '管理组',
          sortOrder: 1,
          version: 1,
        },
        {
          groupId: 'team',
          parentGroupId: 'management',
          name: '团队权限',
          sortOrder: 2,
          version: 1,
        },
        {
          groupId: 'reports',
          parentGroupId: 'team',
          name: '报表查询',
          sortOrder: 3,
          version: 1,
        },
        {
          groupId: 'operations',
          parentGroupId: null,
          name: '日常运维',
          sortOrder: 4,
          version: 1,
        },
      ];
      data = { items, total: items.length };
    } else if (catalog && request.method() === 'GET' && path.endsWith('/roles') && !url.searchParams.has('catalog'))
      data = {
        items: [
          {
            roleId: 'r1',
            name: '已有角色',
            code: 'existing',
            status: 'ACTIVE',
            permissionIds: ['shared-permission', 'legacy-permission'],
            legacyPermissions: [
              { permissionId: 'legacy-permission', name: '历史菜单授权' },
            ],
            version: 1,
          },
        ],
        total: 1,
      };
    else if (
      catalog &&
      key === 'platform/permissions' &&
      request.method() === 'GET' &&
      path.endsWith('/permissions')
    ) {
      const type = url.searchParams.get('type');
      const label = { INTERFACE: '接口', MENU: '菜单', DATA: '数据' }[type];
      const current = Number(url.searchParams.get('page'));
      const size = Number(url.searchParams.get('size'));
      const rows = Array.from(
        { length: type === 'MENU' ? 21 : type === 'INTERFACE' && size === 200 ? 12 : 1 },
        (_, index) => ({
          permissionId: `${type}-${index + 1}`,
          name: `${label}条目${index + 1}`,
          ...(type === 'DATA' ? { dataScope: { interfacePermissionIds: [], scopePreset: 'SELF' } } : {}),
          code: `${type.toLowerCase()}.${index + 1}`,
          type,
          status: 'ACTIVE',
          accessMode: 'AUTHORIZED',
          groupId: 'reports',
          version: 1,
        })
      ).filter((row) => row.name.includes(url.searchParams.get('name') || ''));
      data = {
        items: rows.slice((current - 1) * size, current * size),
        total: rows.length,
        page: current,
        size,
      };
    } else if (
      catalog &&
      request.method() === 'GET' &&
      (path.endsWith('/permissions') || url.searchParams.get('catalog') === 'permissions')
    )
      data = {
        items: [
          {
            permissionId: 'shared-permission',
            name: '平台目录权限',
            code: 'catalog',
            type: 'MENU',
            status: 'ACTIVE',
            accessMode: 'AUTHORIZED',
            groupId: 'shared-group',
            version: 1,
          },
        ],
        total: 1,
      };
    else if (
      catalog &&
      request.method() === 'PUT' &&
      path.endsWith('/permissions')
    )
      data = {
        roleId: 'r1',
        name: '已有角色',
        code: 'existing',
        status: 'ACTIVE',
        permissionIds: request.postDataJSON().permissionIds,
        legacyPermissions: [],
        version: 2,
      };
    else if (path.endsWith('/data-resources')) data = [];
    else if (request.method() === 'POST')
      data = {
        roleId: 'r1',
        name: '测试角色',
        code: 'test',
        status: 'ACTIVE',
        permissionIds: [],
        version: 1,
      };
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        data,
        requestId: 'separate-fixture',
      }),
    });
  });
  if (preparePage) await preparePage(page);
  await page.goto(`${origin}/__separate/${key}${suffix}`);
  return { page, calls, errors };
}
const names = {
  roles: '角色管理',
  permissions: '权限管理',
  'menu-manifest': '菜单管理',
};
for (const platform of [false, true])
  for (const key of Object.keys(names).filter(
    (key) => platform || key !== 'permissions'
  ))
    test(`${
      platform ? 'platform' : 'tenant'
    } ${key} has fixed domain and no scope switch`, async () => {
      const { page, calls, errors } = await open(
        (platform ? 'platform/' : '') + key
      );
      try {
        await page
          .getByText(
            key === 'permissions'
              ? '权限管理'
              : `${platform ? '平台' : '租户'}${names[key]}`,
            {
              exact: true,
            }
          )
          .waitFor();
        await page.waitForFunction(
          () => document.querySelectorAll('.arco-spin-loading').length === 0
        );
        await page.waitForTimeout(150);
        assert.equal(
          await page
            .locator(
              '[aria-label="授权域"], [aria-label="权限管理域"], [aria-label="活动租户"]'
            )
            .count(),
          0
        );
        assert.equal(
          await page
            .getByRole('button', { name: '平台清单', exact: true })
            .count(),
          0
        );
        assert.equal(
          await page
            .getByRole('button', { name: '租户菜单', exact: true })
            .count(),
          0
        );
        assert.ok(calls.length > 0);
        assert.ok(
          calls.every((x) =>
            x.path.startsWith(
              platform ? '/api/v1/platform/' : '/api/v1/enterprises/tenant-a/'
            )
          ),
          JSON.stringify(calls)
        );
        assert.deepEqual(errors, []);
        await page.screenshot({
          path: output + `${platform ? 'platform' : 'tenant'}-${key}.png`,
        });
        if (platform && key === 'permissions') {
          await page.setViewportSize({ width: 390, height: 844 });
          await page.screenshot({
            path: output + 'platform-permissions-narrow.png',
          });
        }
      } finally {
        await page.close();
      }
    });
for (const key of Object.keys(names).filter((key) => key !== 'permissions'))
  test(`tenant ${key} without membership never falls back to platform`, async () => {
    const { page, calls } = await open(key, '?empty=1');
    try {
      await page
        .getByText(
          key === 'menu-manifest' ? '租户菜单管理' : '请先在顶部选择活动租户。',
          { exact: true }
        )
        .waitFor();
      await page.waitForTimeout(150);
      assert.deepEqual(calls, []);
    } finally {
      await page.close();
    }
  });
for (const platform of [false, true])
  test(`${
    platform ? 'platform' : 'tenant'
  } role write stays in its domain`, async () => {
    const { page, calls } = await open((platform ? 'platform/' : '') + 'roles');
    try {
      if (platform)
        await page.getByRole('button', { name: '新建角色' }).click();
      await page.getByPlaceholder('角色名称', { exact: true }).fill('测试角色');
      await page.getByPlaceholder('角色编码', { exact: true }).fill('test');
      await page.getByRole('button', { name: '创建角色', exact: true }).click();
      await page
        .getByText(platform ? '角色已创建' : '角色权限已保存', { exact: true })
        .waitFor();
      const write = calls.find((x) => x.method === 'POST');
      assert.equal(
        write.path,
        (platform ? '/api/v1/platform' : '/api/v1/enterprises/tenant-a') +
          '/roles'
      );
      assert.equal(write.body.code, 'test');
    } finally {
      await page.close();
    }
  });
test('dirty role blocks navigation to separate platform entry', async () => {
  const { page } = await open('roles');
  try {
    await page.getByPlaceholder('角色名称', { exact: true }).fill('未保存');
    const dialog = page.waitForEvent('dialog');
    const click = page
      .getByRole('link', { name: 'platform/roles', exact: true })
      .click();
    await (await dialog).dismiss();
    await click;
    assert.ok(page.url().endsWith('/__separate/roles'));
    assert.equal(
      await page.getByPlaceholder('角色名称', { exact: true }).inputValue(),
      '未保存'
    );
  } finally {
    await page.close();
  }
});
test('platform entry works without tenant membership', async () => {
  const { page, calls } = await open('platform/menu-manifest', '?empty=1');
  try {
    await page.getByRole('cell', { name: '/roles', exact: true }).waitFor();
    assert.equal(await page.getByRole('button', { name: '管理客户端字典' }).count(), 0);
    assert.ok(
      calls.length > 0 &&
        calls.every((x) => x.path.startsWith('/api/v1/platform/'))
    );
  } finally {
    await page.close();
  }
});

test('tenant role edits use catalog choices and let users explicitly remove historical grants', async () => {
  const { page, calls, errors } = await open('roles', '', true);
  try {
    await page
      .getByText('权限由平台统一维护，当前租户仅配置角色和人员授权。')
      .waitFor();
    await page.getByLabel('编辑角色').click();
    await page.getByText('已有角色 (启用)', { exact: true }).click();
    await page.getByText('历史授权（可保留或移除）', { exact: true }).waitFor();
    assert.equal(
      await page
        .getByRole('checkbox', { name: '授予 平台目录权限' })
        .isChecked(),
      true
    );
    const legacy = page.getByRole('checkbox', {
      name: '历史菜单授权',
      exact: true,
    });
    assert.equal(await legacy.isChecked(), true);
    await page.getByText('历史菜单授权', { exact: true }).click();
    assert.equal(await legacy.isChecked(), false);
    await page
      .getByRole('button', { name: '保存角色权限', exact: true })
      .click();
    await page.waitForFunction(
      () => document.querySelectorAll('.arco-spin-loading').length === 0
    );
    const saved = calls.find(
      (x) => x.method === 'PUT' && x.path.endsWith('/roles/r1/permissions')
    );
    assert.deepEqual(saved?.body.permissionIds, ['shared-permission']);
    assert.equal(
      calls.some((x) => x.path.startsWith('/api/v1/platform/')),
      false
    );
    assert.deepEqual(errors, []);
    await page.screenshot({ path: output + 'tenant-shared-catalog-role.png' });
  } finally {
    await page.close();
  }
});

test('platform role list supports search, details, edit and confirmed delete', async () => {
  const { page, calls, errors } = await open('platform/roles', '', true);
  try {
    let role = {
      roleId: 'r1',
      name: '已有角色',
      code: 'existing',
      status: 'ACTIVE',
      permissionIds: ['shared-permission'],
      version: 1,
    };
    let deleted = false;
    await page.route('**/api/v1/platform/roles/r1', async (route) => {
      const req = route.request();
      if (req.method() === 'PATCH') {
        assert.equal(req.headers()['if-match'], '"1"');
        role = { ...role, ...req.postDataJSON(), version: 2 };
      }
      if (req.method() === 'DELETE') {
        assert.equal(req.headers()['if-match'], '"2"');
        deleted = true;
      }
      await route.fulfill({ json: { success: true, data: role } });
    });
    const list = page.getByRole('region', { name: '平台角色列表' });
    await list.getByText('已有角色').waitFor();
    assert.ok(!calls.some((x) => x.path.endsWith('/permissions')));
    await page.getByLabel('搜索角色').fill('missing');
    await list.getByText('已有角色').waitFor({ state: 'hidden' });
    await page.getByLabel('搜索角色').fill('EXIST');
    await list.getByRole('button', { name: '查看' }).click();
    await page
      .getByRole('region', { name: '已授权限' })
      .getByText('平台目录权限', { exact: true })
      .waitFor();
    await page
      .getByRole('button', { name: '关闭', exact: true })
      .last()
      .click();
    await list.getByRole('button', { name: '编辑' }).click();
    await page.getByLabel('角色名称').fill('更新角色');
    await page.getByRole('button', { name: '保存', exact: true }).click();
    await list.getByText('更新角色').waitFor();
    await page
      .locator('.arco-modal-wrapper:visible')
      .waitFor({ state: 'hidden' });
    await page
      .getByText('角色已更新', { exact: true })
      .waitFor({ state: 'hidden' });
    await page.screenshot({ path: output + 'platform-roles-list.png' });
    await page.setViewportSize({ width: 390, height: 844 });
    assert.ok(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth
      )
    );
    await page.screenshot({ path: output + 'platform-roles-list-narrow.png' });
    await list.getByRole('button', { name: '删除' }).click();
    await page.getByRole('button', { name: '取消', exact: true }).click();
    assert.equal(deleted, false);
    await list.getByRole('button', { name: '删除' }).click();
    await page.getByRole('button', { name: '确认删除', exact: true }).click();
    await list.getByText('更新角色').waitFor({ state: 'hidden' });
    assert.equal(deleted, true);
    assert.deepEqual(errors, []);
  } finally {
    await page.close();
  }
});

test('platform toolbar opens a fresh user assignment and saves the selected roles', async () => {
  const { page, errors } = await open('platform/roles', '', true);
  try {
    let writes = 0;
    await page.route(
      '**/api/v1/platform/users/operator/roles',
      async (route) => {
        const request = route.request();
        let assigned = [];
        if (request.method() === 'PUT') {
          assert.equal(request.headers()['if-match'], '"3"');
          assigned = request.postDataJSON().roleIds;
          assert.deepEqual(assigned, ['r1']);
          writes++;
        }
        await route.fulfill({
          json: {
            success: true,
            data: {
              username: 'operator',
              roleIds: assigned,
              sources: [],
              version: 3 + writes,
            },
          },
        });
      }
    );
    const trigger = page.getByRole('button', {
      name: '分配用户角色',
      exact: true,
    });
    const username = page.getByRole('textbox', { name: '用户名' });
    assert.equal(await username.count(), 0);
    await trigger.click();
    await username.fill('operator');
    await page.getByLabel('用户角色', { exact: true }).click();
    await page
      .locator('.arco-select-option')
      .getByText('已有角色', { exact: true })
      .click();
    await username.click();
    await page
      .locator('.arco-select-option:visible')
      .waitFor({ state: 'hidden' });
    await page.screenshot({ path: output + 'platform-role-assignment.png' });
    await page.getByRole('button', { name: '保存用户角色' }).click();
    await username.waitFor({ state: 'hidden' });
    assert.equal(writes, 1);
    await trigger.click();
    assert.equal(await username.inputValue(), '');
    await username.press('Escape');
    await username.waitFor({ state: 'hidden' });
    assert.deepEqual(errors, []);
  } finally {
    await page.close();
  }
});

test('permission group search reveals ancestors and preserves the selected list filter', async () => {
  const { page, calls, errors } = await open('platform/permissions', '', true);
  try {
    const panel = page.getByRole('complementary', { name: '权限组' });
    const button = (name) => panel.getByRole('button', { name, exact: true });
    await button('报表查询').waitFor();
    const divider = panel.getByRole('separator', { name: '调整权限组宽度' });
    const original = (await panel.boundingBox()).width;
    assert.equal(original, 320);
    const expand = button('全部展开');
    const collapse = button('全部收起');
    assert.equal(await expand.isDisabled(), true);
    await collapse.press('Enter');
    assert.equal(await button('团队权限').count(), 0);
    assert.equal(await collapse.isDisabled(), true);
    await button('展开管理组').click();
    assert.equal(await button('报表查询').count(), 0);
    await expand.press('Space');
    await button('报表查询').waitFor();
    assert.equal(await expand.isDisabled(), true);
    await panel.screenshot({ path: output + 'permission-group-bulk.png' });
    const grip = await divider.boundingBox();
    await page.mouse.move(grip.x + grip.width / 2, grip.y + 16);
    await page.mouse.down();
    await page.mouse.move(grip.x + grip.width / 2 + 80, grip.y + 16);
    await page.mouse.up();
    assert.equal((await panel.boundingBox()).width, original + 80);
    await divider.press('ArrowLeft');
    assert.equal((await panel.boundingBox()).width, original + 70);
    await divider.press('End');
    assert.equal((await panel.boundingBox()).width, 480);
    await divider.press('Home');
    assert.equal((await panel.boundingBox()).width, 200);
    await button('收起管理组').press('Enter');
    assert.equal(await button('报表查询').count(), 0);
    const search = panel.getByRole('textbox', { name: '搜索权限组' });
    await search.fill('报表');
    await button('团队权限').waitFor();
    await collapse.click();
    assert.equal(await button('报表查询').count(), 0);
    await expand.click();
    assert.equal(await button('报表查询').locator('mark').innerText(), '报表');
    assert.equal(await panel.locator('li svg').count(), 0);
    await panel.screenshot({ path: output + 'permission-group-match.png' });
    assert.equal(await button('日常运维').count(), 0);
    await Promise.all([
      page.waitForResponse(
        (r) =>
          r.url().includes('groupId=reports') &&
          r.url().includes('includeDescendants=true')
      ),
      button('报表查询').press('Enter'),
    ]);
    assert.equal(await button('报表查询').getAttribute('aria-pressed'), 'true');
    await search.fill('');
    assert.equal(await button('报表查询').count(), 0);
    await button('展开管理组').click();
    await button('新建子组').click();
    await page
      .getByRole('dialog')
      .getByText('报表查询', { exact: true })
      .waitFor();
    await page
      .getByRole('dialog')
      .getByRole('button', { name: '取消' })
      .click();
    await search.fill('不存在的分组');
    await panel.getByText('未找到匹配的权限组').waitFor();
    await search.fill('');
    await Promise.all([
      page.waitForResponse(
        (r) =>
          new URL(r.url()).pathname.endsWith('/permissions') &&
          !new URL(r.url()).searchParams.has('groupId')
      ),
      button('全部权限').click(),
    ]);
    assert.equal(
      calls.filter((x) => x.path.endsWith('/permission-groups')).length,
      1
    );
    assert.deepEqual(errors, []);
    await page.screenshot({ path: output + 'permission-group-tree.png' });
    await page.setViewportSize({ width: 390, height: 844 });
    assert.equal(await divider.isVisible(), false);
    assert.ok((await panel.boundingBox()).width > 200);
    await page.screenshot({
      path: output + 'permission-group-tree-narrow.png',
    });
    assert.ok(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth
      )
    );
  } finally {
    await page.close();
  }
});

test('permission tables filter together and paginate independently', async () => {
  const { page, calls, errors } = await open('platform/permissions', '', true);
  try {
    const table = (name) => page.getByRole('region', { name });
    const menu = table('菜单权限');
    const api = table('接口权限');
    const data = table('数据权限');
    await api.getByText('接口条目1', { exact: true }).waitFor();
    await menu.getByText('菜单条目1', { exact: true }).waitFor();
    await data.getByText('数据条目1', { exact: true }).waitFor();
    assert.equal(await api.getByText('菜单条目1', { exact: true }).count(), 0);
    const lists = () => calls.filter((c) => c.path.endsWith('/permissions'));
    const before = lists().length;
    await menu
      .locator('.arco-pagination-item')
      .filter({ hasText: /^2$/ })
      .click();
    await menu.getByText('菜单条目21').waitFor();
    assert.deepEqual(
      lists()
        .slice(before)
        .map((c) => [c.query.type, c.query.page]),
      [['MENU', '2']]
    );
    await page.getByRole('button', { name: '报表查询', exact: true }).click();
    await menu.getByText('菜单条目1', { exact: true }).waitFor();
    assert.deepEqual(
      lists()
        .filter(
          (c) =>
            c.query.groupId === 'reports' &&
            c.query.includeDescendants === 'true' &&
            c.query.page === '1'
        )
        .map((c) => c.query.type)
        .sort(),
      ['DATA', 'INTERFACE', 'MENU']
    );
    await page.getByRole('textbox', { name: '权限名称筛选' }).fill('数据条目');
    await data.getByText('数据条目1', { exact: true }).waitFor();
    await api.locator('.arco-empty').waitFor();
    await menu.locator('.arco-empty').waitFor();
    assert.deepEqual(errors, []);
  } finally {
    await page.close();
  }
});

test('platform role grants follow the group tree across groups and pages', async () => {
  const { page, errors } = await open('platform/roles', '', true);
  try {
    const reports = Array.from({ length: 11 }, (_, i) => ({
      permissionId: 'report-' + i,
      name: '报表权限' + i,
      groupId: 'reports',
      code: 'report.' + i,
      type: 'DATA',
      status: 'ACTIVE',
    }));
    const ops = {
      ...reports[0],
      permissionId: 'ops',
      groupId: 'operations',
      name: '运维查询',
      type: 'INTERFACE',
      code: 'ops.read',
    };
    let role = {
      roleId: 'r1',
      name: '已有角色',
      code: 'existing',
      status: 'ACTIVE',
      version: 1,
      permissionIds: ['report-0', 'legacy'],
      legacyPermissions: [{ permissionId: 'legacy', name: '历史菜单授权' }],
    };
    await page.route('**/api/v1/platform/permissions?*', (route) =>
      route.fulfill({
        json: {
          success: true,
          data: { items: [...reports, ops], total: 12, page: 1, size: 200 },
        },
      })
    );
    await page.route('**/api/v1/platform/roles/r1', (route) =>
      route.fulfill({ json: { success: true, data: role } })
    );
    let writes = 0;
    await page.route(
      '**/api/v1/platform/roles/r1/permissions',
      async (route) => {
        const req = route.request();
        assert.equal(req.method(), 'PUT');
        assert.equal(req.headers()['if-match'], '"1"');
        assert.ok(req.headers()['idempotency-key']);
        const ids = req.postDataJSON().permissionIds;
        assert.deepEqual(
          [...ids].sort(),
          [
            'legacy',
            'ops',
            ...reports.slice(0, 10).map((p) => p.permissionId),
          ].sort()
        );
        writes++;
        role = { ...role, version: 2, permissionIds: ids };
        await route.fulfill({ json: { success: true, data: role } });
      }
    );
    const openEditor = () =>
      page.getByRole('button', { name: '配置权限', exact: true }).click();
    await openEditor();
    const dialog = page.getByRole('dialog');
    const tree = dialog.getByRole('complementary', { name: '权限组' });
    const pick = (name) =>
      tree.getByRole('button', { name, exact: true }).click();
    const grant = (name) =>
      dialog.getByRole('checkbox', { name: '授予 ' + name, exact: true });
    const groupCheck = (name) =>
      tree.getByRole('checkbox', {
        name: '全选' + name + '及子组权限',
        exact: true,
      });
    const parent = groupCheck('管理组');
    assert.equal(await parent.evaluate((el) => el.indeterminate), true);
    await tree.getByRole('button', { name: '全部收起' }).click();
    await parent.press('Space');
    assert.equal(await parent.isChecked(), true);
    assert.equal(await groupCheck('日常运维').isChecked(), false);
    await parent.uncheck();
    assert.equal(await grant('报表权限0').isChecked(), false);
    await tree.getByRole('button', { name: '全部展开' }).click();
    assert.equal(await groupCheck('报表查询').isChecked(), false);
    await pick('管理组');
    const bulk = dialog.getByRole('checkbox', { name: /全选「管理组」/ });
    assert.equal(await grant('报表权限0').isChecked(), false);
    assert.equal(await grant('运维查询').count(), 0);
    await bulk.press('Space');
    await dialog
      .locator('.arco-pagination-item')
      .filter({ hasText: /^2$/ })
      .click();
    assert.equal(await grant('报表权限10').isChecked(), true);
    await grant('报表权限10').press('Space');
    assert.equal(await parent.evaluate((el) => el.indeterminate), true);
    await tree.getByRole('textbox', { name: '搜索权限组' }).fill('日常');
    await pick('日常运维');
    await groupCheck('日常运维').check();
    assert.equal(await grant('运维查询').isChecked(), true);
    await tree.getByRole('textbox', { name: '搜索权限组' }).fill('');
    await pick('报表查询');
    await dialog.getByText('报表权限0', { exact: true }).waitFor();
    await page.screenshot({
      path: output + 'platform-role-permission-tree.png',
    });
    await page.setViewportSize({ width: 390, height: 844 });
    assert.ok(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth
      )
    );
    await page.screenshot({
      path: output + 'platform-role-permission-tree-narrow.png',
    });
    await dialog.getByRole('button', { name: '保存', exact: true }).click();
    await dialog.waitFor({ state: 'hidden' });
    assert.equal(writes, 1);
    await openEditor();
    assert.equal(
      await dialog
        .getByRole('checkbox', { name: '历史菜单授权', exact: true })
        .isChecked(),
      true
    );
    await pick('日常运维');
    assert.equal(await grant('运维查询').isChecked(), true);
    assert.deepEqual(errors, []);
  } finally {
    await page.close();
  }
});

test('role details browse only granted groups and pages, including historical and unavailable catalog entries', async () => {
  const { page, calls, errors } = await open('platform/roles', '', true);
  try {
    const reports = Array.from({ length: 11 }, (_, i) => ({
      permissionId: 'p' + i,
      name: '报表查看' + i,
      code: 'report.read.' + i,
      groupId: 'reports',
      type: 'DATA',
      status: 'ACTIVE',
    }));
    const excluded = {
      ...reports[0],
      permissionId: 'other',
      name: '未授权操作',
      groupId: 'operations',
    };
    let ids = [...reports.map((p) => p.permissionId), 'legacy', 'missing'];
    let denied = false;
    await page.route('**/api/v1/platform/roles/r1', (r) =>
      r.fulfill({
        json: {
          success: true,
          data: {
            roleId: 'r1',
            name: '已有角色',
            code: 'existing',
            status: 'ACTIVE',
            permissionIds: ids,
            legacyPermissions: [{ permissionId: 'legacy', name: '历史查看' }],
            version: 1,
          },
        },
      })
    );
    await page.route('**/api/v1/platform/permissions?*', (r) =>
      denied
        ? r.fulfill({
            status: 403,
            json: { success: false, message: 'Forbidden' },
          })
        : r.fulfill({
            json: {
              success: true,
              data: {
                items: [...reports, excluded],
                total: 12,
                page: 1,
                size: 200,
              },
            },
          })
    );
    await page.route('**/api/v1/platform/permission-groups?*', (r) =>
      denied
        ? r.fulfill({
            status: 403,
            json: { success: false, message: 'Forbidden' },
          })
        : r.fallback()
    );
    const view = page.getByRole('button', { name: '查看', exact: true });
    const dialog = page.getByRole('dialog');
    const close = async () => {
      await dialog
        .getByRole('button', { name: '关闭', exact: true })
        .last()
        .click();
      await dialog.waitFor({ state: 'hidden' });
    };
    await view.click();
    const tree = dialog.getByRole('complementary', { name: '权限组' });
    const result = dialog.getByRole('region', { name: '已授权限' });
    const filter = result.getByRole('textbox', { name: '搜索已授权限' });
    await result.getByText('全部权限（13）').waitFor();
    assert.equal(
      await tree.getByRole('button', { name: '日常运维', exact: true }).count(),
      0
    );
    assert.equal(await dialog.getByRole('checkbox').count(), 0);
    await tree.getByRole('button', { name: '管理组', exact: true }).click();
    await result.getByText('管理组（11）').waitFor();
    await result
      .locator('.arco-pagination-item')
      .filter({ hasText: /^2$/ })
      .click();
    await result.getByText('报表查看10', { exact: true }).waitFor();
    await filter.fill('REPORT.READ.0');
    await result.getByText('报表查看0', { exact: true }).waitFor();
    await filter.fill('');
    await page.screenshot({ path: output + 'role-detail-groups.png' });
    await tree.getByRole('button', { name: '全部权限', exact: true }).click();
    await filter.fill('历史查看');
    await result.getByText('历史授权', { exact: true }).waitFor();
    await filter.fill('missing');
    await result.getByText('目录信息不可用', { exact: true }).waitFor();
    await filter.fill('未授权操作');
    await result.getByText('没有匹配的权限').waitFor();
    await page.setViewportSize({ width: 390, height: 844 });
    assert.ok(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth
      )
    );
    await close();
    denied = true;
    ids = ['p0', 'legacy'];
    await view.click();
    await result.getByText('p0', { exact: true }).waitFor();
    await result.getByText('历史查看', { exact: true }).waitFor();
    await close();
    ids = [];
    await view.click();
    await result.getByText('该角色暂无已授权限').waitFor();
    await close();
    assert.ok(calls.every((c) => c.method === 'GET'));
    assert.deepEqual(errors, []);
  } finally {
    await page.close();
  }
});

test('data permission replaces its legacy scope using the interface permission group tree', async () => {
  const { page, calls, errors } = await open('platform/permissions', '', true);
  try {
    await page
      .getByRole('region', { name: '数据权限' })
      .getByRole('button', { name: '编辑' })
      .click();
    const dialog = page.getByRole('dialog');
    assert.equal(await dialog.getByText(/业务资源|applications/).count(), 0);
    await dialog.getByRole('button', { name: /确定|确认/ }).click();
    await dialog.getByText('请选择至少一个接口权限').waitFor();
    const parent = dialog.getByRole('checkbox', {
      name: '全选管理组及子组权限',
    });
    await parent.check();
    await dialog.getByText('已选 12 项接口权限').waitFor();
    await dialog
      .locator('.arco-pagination-item')
      .filter({ hasText: /^2$/ })
      .click();
    await dialog
      .getByRole('row')
      .filter({ hasText: '接口条目12' })
      .locator('label')
      .click();
    assert.equal(await parent.evaluate((input) => input.indeterminate), true);
    await dialog.getByText('已选 11 项接口权限').waitFor();
    await dialog.screenshot({
      path: output + 'data-permission-interface-selection.png',
    });
    const saved = page.waitForRequest(
      (r) => r.method() === 'PATCH' && r.url().endsWith('/permissions/DATA-1')
    );
    await dialog.getByRole('button', { name: /确定|确认/ }).click();
    const body = (await saved).postDataJSON();
    assert.deepEqual(body.dataScope, {
      scopePreset: 'SELF',
      interfacePermissionIds: Array.from(
        { length: 11 },
        (_, i) => `INTERFACE-${i + 1}`
      ),
    });
    assert.equal(
      calls.some((c) => c.path.endsWith('/data-resources')),
      false
    );
    assert.deepEqual(errors, []);
  } finally {
    await page.close();
  }
});

test('interface transfer searches across directory pages and saves explicit moves', async () => {
  const { page, calls, errors } = await open('platform/permissions', '', true);
  try {
    const items = Array.from({ length: 201 }, (_, i) => ({
      resourceId: 'id-' + i,
      httpMethod: 'GET',
      normalizedPath: i === 0 ? '/api/v1/platform/authorization/interface-directory' : '/reports/' + i,
      source: 'MVC',
      conditions: {},
    }));
    const respond = (route, data) => route.fulfill({
      json: { success: true, data, requestId: 'transfer-fixture' },
    });
    await page.route('**/authorization/interface-directory?**', (route) => {
      const params = new URL(route.request().url()).searchParams;
      const size = Number(params.get('size'));
      const offset = Number(params.get('page')) * size;
      return respond(route, { items: items.slice(offset, offset + size), total: items.length });
    });
    let binding = { resourceIds: ['id-5', 'retired'], version: 3 };
    await page.route('**/permissions/INTERFACE-1/interfaces', (route) => {
      if (route.request().method() === 'PUT') {
        binding = { ...route.request().postDataJSON(), version: 4 };
      }
      return respond(route, binding);
    });
    const edit = page.getByRole('region', { name: '接口权限' }).getByRole('button', { name: '编辑' });
    await edit.click();
    const dialog = page.getByRole('dialog');
    const target = dialog.locator('.arco-transfer-view-target');
    const source = dialog.locator('.arco-transfer-view-source');
    await target.getByText('GET /reports/5 · MVC', { exact: true }).waitFor();
    const incoming = source.getByRole('checkbox', { name: 'GET /reports/200 · MVC' });
    await source.getByPlaceholder('搜索可选接口').fill('get /REPORTS/200');
    await source.getByText('GET /reports/200 · MVC').click();
    assert.equal(await incoming.isChecked(), true);
    assert.equal(await target.getByText('GET /reports/200 · MVC').count(), 0);
    await target.getByText('目录中已失效 · retired').click();
    await dialog.getByRole('button', { name: 'move selected left' }).click();
    assert.equal(await incoming.isChecked(), true);
    await dialog.getByRole('button', { name: 'move selected right' }).click();
    await target.getByText('GET /reports/200 · MVC').waitFor();
    assert.equal(await dialog.getByRole('button', { name: '保存接口关联' }).count(), 0);
    const saved = page.waitForResponse(r => r.request().method() === 'PUT' && r.url().endsWith('/interfaces'));
    await dialog.getByRole('button', { name: /确定|确认/ }).click();
    const request = (await saved).request();
    assert.deepEqual(request.postDataJSON().resourceIds.sort(), ['id-200', 'id-5']);
    assert.equal(request.headers()['if-match'], '"3"');
    await dialog.waitFor({ state: 'hidden' });
    await edit.click();
    await target.getByText('GET /reports/200 · MVC').waitFor();
    await dialog.locator('.arco-transfer').screenshot({ path: output + 'interface-transfer.png' });
    assert.equal(await target.getByText(/retired/).count(), 0);
    assert.equal(calls.filter(c => c.method === 'PATCH').length, 0);
    await target.getByText('GET /reports/200 · MVC').click();
    await dialog.getByRole('button', { name: 'move selected left' }).click();
    page.once('dialog', prompt => prompt.accept());
    await dialog.getByRole('button', { name: '取消' }).click();
    await edit.click();
    await target.getByText('GET /reports/200 · MVC').waitFor();
    assert.deepEqual(errors, []);
  } finally {
    await page.close();
  }
});

test('interface transfer retries only the pending association after metadata succeeds', async () => {
  const { page, errors } = await open('platform/permissions', '', true);
  try {
    const row = {
      permissionId: 'INTERFACE-1', type: 'INTERFACE', code: 'interface.1',
      groupId: 'reports', accessMode: 'AUTHORIZED', status: 'ACTIVE', version: 2,
    };
    let patches = 0;
    const attempts = [];
    const ok = (route, data) => route.fulfill({ json: { success: true, data } });
    await page.route('**/authorization/interface-directory?**', route =>
      ok(route, { items: [], total: 0 }));
    await page.route('**/permissions/INTERFACE-1', route => {
      patches++;
      return ok(route, { ...row, name: route.request().postDataJSON().name });
    });
    await page.route('**/permissions/INTERFACE-1/interfaces', route => {
      const req = route.request();
      if (req.method() === 'GET') return ok(route, { resourceIds: ['old'], version: 3 });
      attempts.push({ key: req.headers()['idempotency-key'], body: req.postDataJSON() });
      if (attempts.length === 1) return route.fulfill({
        status: 503, json: { success: false, error: { message: '临时失败' } },
      });
      return ok(route, { resourceIds: [], version: 4 });
    });
    await page.getByRole('region', { name: '接口权限' }).getByRole('button', { name: '编辑' }).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByText('目录中已失效 · old').click();
    await dialog.getByRole('button', { name: 'move selected left' }).click();
    await dialog.getByLabel('名称', { exact: true }).fill('更新名称');
    const confirm = dialog.getByRole('button', { name: /确定|确认/ });
    await confirm.click();
    await dialog.getByText(/权限资料已保存，接口关联保存失败/).waitFor();
    await confirm.click();
    await dialog.waitFor({ state: 'hidden' });
    assert.equal(patches, 1);
    assert.equal(attempts.length, 2);
    assert.deepEqual(attempts[0], attempts[1]);
    assert.ok(attempts[1].key);
    assert.deepEqual(attempts[1].body, { resourceIds: [] });
    assert.deepEqual(errors, []);
  } finally {
    await page.close();
  }
});

test('administrative divisions expand by parent, edit with revision and retain update time', async () => {
  const respond = (route, data) => route.fulfill({ json: { success: true, data } });
  const root = { id: 'bj', name: '北京市', code: '110000', level: 1, hasChildren: true,
    revision: 1, updatedAt: '2026-01-10T01:00:00Z', sourceDate: '2025-12-31' };
  const child = { ...root, id: 'dc', parentId: 'bj', code: '110101', level: 3, name: '东城区', hasChildren: false };
  const reads = [];
  const { page, errors } = await open('dictionary/divisions', '', false, async page => {
    await page.route(/\/api\/v1\/platform\/administrative-divisions(?:[/?]|$)/, async route => {
      const request = route.request();
      if (request.method() === 'PUT') {
        assert.equal(new URL(request.url()).pathname.split('/').pop(), child.id);
        assert.deepEqual(request.postDataJSON(), { name: '东城区测试', revision: 1 });
        child.name = '东城区测试';
        child.updatedAt = '2026-11-24T02:00:00Z';
        child.revision++;
        return respond(route, null);
      }
      const parent = new URL(request.url()).searchParams.get('parentId');
      reads.push(parent);
      return respond(route, parent === root.id ? [child] : [root]);
    });
  });
  try {
    const parent = page.getByRole('row').filter({ hasText: '北京市' });
    await parent.waitFor();
    assert.deepEqual(reads, [null]);
    assert.equal(await parent.getByRole('button', {name:'删除'}).isDisabled(), true);
    await parent.getByRole('button').first().click();
    const row = page.getByRole('row').filter({ hasText: child.code });
    await row.getByRole('button', {name:'修改'}).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByLabel('区划名称').fill('东城区测试');
    await dialog.locator('.arco-modal-footer .arco-btn-primary').click();
    await row.getByText('东城区测试').waitFor();
    await row.getByText(/2026\/11\/24/).waitFor();
    assert.deepEqual(reads, [null, 'bj', null, 'bj']);
    assert.deepEqual(errors, []);
  } finally {
    await page.close();
  }
});
