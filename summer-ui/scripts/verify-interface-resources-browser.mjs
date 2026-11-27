import assert from 'node:assert/strict';
import { before, after, test } from 'node:test';
import { createServer } from 'vite';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright-core';
let vite, browser, origin;
const row = (path, handler) => ({
  httpMethod: 'GET',
  normalizedPath: path,
  handler,
  module: 'example',
  resourceType: 'BUSINESS',
  sensitiveLevel: 'NORMAL',
  permissionCode: `GET:${path}`,
  configurable: true,
  contextId: 'application',
  source: 'MVC',
  conditions: {},
  configurationPolicy: 'MANAGED_ROLE',
});
const current = {
  revision: 2,
  complete: true,
  issues: [],
  items: [row('/api/current', 'Current#read')],
};
const previous = {
  revision: 1,
  complete: true,
  issues: [],
  items: [row('/api/removed', 'Old#read')],
};
const history = {
  items: [2, 1].map((revision) => ({
    revision,
    fingerprint: `revision-${revision}`,
    resourceCount: 1,
    createdAt: '2026-10-15T00:00:00Z',
  })),
};
const difference = {
  from: 1,
  to: 2,
  added: [],
  removed: previous.items,
  changed: [
    {
      before: row('/api/rebound', 'Original#read'),
      after: {
        ...row('/api/rebound', 'Replacement#read'),
        configurable: false,
        configurationPolicy: 'REPLACED_REQUIRES_EXPLICIT_PERMISSION',
      },
      kind: 'REPLACED',
    },
  ],
};
const reply = (route, data, status = 200) =>
  route.fulfill({
    status,
    contentType: 'application/json',
    headers: { 'X-Request-Id': 'catalog-fixture-request' },
    body: JSON.stringify(
      status < 400
        ? { success: true, data, requestId: 'catalog-fixture-request' }
        : {
            success: false,
            error: {
              code: status === 403 ? 'SUMMER_FORBIDDEN' : 'SUMMER_UNAVAILABLE',
              message: 'Catalog unavailable',
            },
            requestId: 'catalog-fixture-request',
          }
    ),
  });
before(async () => {
  vite = await createServer({
    root: fileURLToPath(new URL('..', import.meta.url)),
    logLevel: 'error',
    server: { port: 0, strictPort: false },
    plugins: [
      {
        name: 'catalog-browser-fixture',
        configureServer(server) {
          server.middlewares.use(async (req, res, next) => {
            if (!req.url.startsWith('/__catalog/')) return next();
            res.setHeader('Content-Type', 'text/html');
            res.end(
              await server.transformIndexHtml(
                req.url,
                '<div id="root"></div><script type="module" src="/scripts/interface-resources-browser-fixture.tsx"></script>'
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
async function open(overrides = {}) {
  const page = await browser.newPage();
  page.setDefaultTimeout(5000);
  await page.route('**/api/v1/platform/**', (route) => {
    const path = new URL(route.request().url()).pathname;
    if (overrides.forbidden) return reply(route, null, 403);
    if (path.endsWith('/diff'))
      return overrides.diff ? overrides.diff(route) : reply(route, difference);
    if (path.endsWith('/history/1')) return reply(route, previous);
    if (path.endsWith('/history/2')) return reply(route, current);
    if (path.endsWith('/history')) return reply(route, history);
    if (overrides.directory) return overrides.directory(route);
    return reply(route, overrides.current || current);
  });
  await page.goto(`${origin}/__catalog/page`);
  return page;
}
test('history uses real revision parameters and shows removed and replaced routes', async () => {
  let query;
  const page = await open({
    diff: (route) => {
      query = new URL(route.request().url()).searchParams;
      return reply(route, difference);
    },
  });
  try {
    await page.getByRole('tab', { name: '历史版本', exact: true }).click();
    await page.getByRole('button', { name: '比较版本', exact: true }).click();
    await page
      .getByRole('cell', { name: '/api/removed', exact: true })
      .waitFor();
    await page.getByRole('cell', { name: '已替换', exact: true }).waitFor();
    assert.equal(query.get('from'), '1');
    assert.equal(query.get('to'), '2');
    if (process.env.CATALOG_SCREENSHOT)
      await page.screenshot({
        path: process.env.CATALOG_SCREENSHOT,
        fullPage: true,
      });
    await page.getByRole('button', { name: '查看版本 1', exact: true }).click();
    await page
      .locator('.arco-modal:visible')
      .getByRole('cell', { name: '/api/removed', exact: true })
      .waitFor();
  } finally {
    await page.close();
  }
});
test('incomplete discovery and failed comparison retain the current catalog and correlation id', async () => {
  const page = await open({
    current: {
      ...current,
      complete: false,
      issues: [
        {
          code: 'SUMMER_RESOURCE_DISCOVERY_UNSUPPORTED',
          contextId: 'application',
          source: 'OpaqueMapping',
          detail: 'Metadata contributor required',
        },
      ],
    },
    diff: (route) => reply(route, null, 503),
  });
  try {
    await page.getByRole('tab', { name: '当前版本', selected: true }).waitFor();
    assert.equal(await page.getByRole('button', { name: '比较版本' }).count(), 0);
    await page.getByText('扫描不完整', { exact: true }).waitFor();
    await page.getByRole('tab', { name: '历史版本', exact: true }).click();
    await page.getByRole('button', { name: '比较版本', exact: true }).click();
    await page.getByText(/catalog-fixture-request/).waitFor();
    assert.equal(await page.getByRole('cell', { name: '/api/current' }).count(), 0);
    await page.getByRole('tab', { name: '当前版本' }).click();
    assert.equal(
      await page
        .getByRole('cell', { name: '/api/current', exact: true })
        .count(),
      1
    );
  } finally {
    await page.close();
  }
});
test('forbidden history is visible with requestId and a retry action', async () => {
  const page = await open({ forbidden: true });
  try {
    await page.getByText(/catalog-fixture-request/).waitFor();
    assert.equal(
      await page.getByRole('button', { name: '重新加载', exact: true }).isEnabled(),
      true
    );
  } finally {
    await page.close();
  }
});

test('selects published dictionary values and combines directory filters', async () => {
  const services = [
      { value: 'orders', label: '订单', enabled: true },
      { value: 'stock', label: '库存', enabled: true },
      { value: 'retired', label: '已停用', enabled: false },
    ],
    saves = [];
  let query;
  const page = await open({
    directory: (route) => {
      if (route.request().method() === 'POST') {
        const { serviceCode } = route.request().postDataJSON();
        const serviceName = services.find((s) => s.value === serviceCode).label;
        saves.push(serviceCode);
        return reply(route, { serviceCode, serviceName, revision: 2 });
      }
      query = new URL(route.request().url()).searchParams;
      const serviceName =
        services.find((s) => s.value === query.get('serviceCode'))?.label || '';
      return reply(route, {
        ...current,
        services,
        policies: ['MANAGED_ROLE'],
        items: [{ ...current.items[0], serviceName }],
        total: 1,
        page: 0,
        size: 10,
      });
    },
  });
  try {
    const refresh = page.getByRole('button', { name: '从系统同步接口信息', exact: true });
    for (const name of ['订单', '库存', '订单']) {
      await refresh.click();
      const dialog = page.locator('.arco-modal:visible');
      await dialog.getByRole('combobox').click();
      assert.equal(
        await page
          .locator('.arco-select-option:visible')
          .getByText('已停用')
          .count(),
        0
      );
      await page
        .locator('.arco-select-option:visible')
        .getByText(name, { exact: true })
        .click();
      await dialog.locator('.arco-modal-footer .arco-btn-primary').click();
      await page.getByRole('cell', { name, exact: true }).waitFor();
    }
    assert.deepEqual(saves, ['orders', 'stock', 'orders']);
    await page.locator('#catalog-path').fill('/api');
    await page.locator('form .arco-select').first().click();
    await page
      .locator('.arco-select-option:visible')
      .filter({ hasText: '角色权限' })
      .click();
    await Promise.all([
      page.waitForResponse((r) => new URL(r.url()).searchParams.has('policy')),
      page.getByRole('button', { name: '筛选' }).click(),
    ]);
    assert.deepEqual(
      ['path', 'policy', 'serviceCode', 'page'].map((k) => query.get(k)),
      ['/api', 'MANAGED_ROLE', 'orders', '0']
    );
    await page
      .locator('.arco-select-option:visible')
      .first()
      .waitFor({ state: 'hidden' });
    if (process.env.DIRECTORY_SCREENSHOT)
      await page.screenshot({
        path: process.env.DIRECTORY_SCREENSHOT,
        fullPage: true,
      });
  } finally {
    await page.close();
  }
});

test('current list shows each service revision and last successful sync', async () => {
  const synced = '2026-11-24T02:03:04.123Z';
  const page = await open({
    current: {
      ...current,
      revision: 99,
      items: [
        { ...current.items[0], serviceCode: 'orders', serviceName: '订单', revision: 12, lastSyncedAt: synced },
        { ...current.items[0], serviceCode: 'stock', serviceName: '库存', revision: 7, lastSyncedAt: null },
      ],
    },
  });
  try {
    const panel = page.getByRole('tabpanel', { name: '当前版本' });
    await panel.locator('thead').getByText('版本', { exact: true }).waitFor();
    await panel.locator('thead').getByText('最近同步时间', { exact: true }).waitFor();
    assert.equal(await panel.getByText(/当前版本[:：]/).count(), 0);
    const orders = panel.getByRole('row').filter({ hasText: '订单' });
    await orders.getByRole('cell', { name: '12', exact: true }).waitFor();
    assert.equal(await orders.locator('time').getAttribute('datetime'), synced);
    const stock = panel.getByRole('row').filter({ hasText: '库存' });
    await stock.getByRole('cell', { name: '7', exact: true }).waitFor();
    await stock.getByRole('cell', { name: '—', exact: true }).waitFor();
    if (process.env.SYNC_COLUMNS_SCREENSHOT)
      await page.screenshot({ path: process.env.SYNC_COLUMNS_SCREENSHOT });
  } finally {
    await page.close();
  }
});

test('sync explains service names and cannot edit the dictionary', async () => {
  const writes = [];
  const page = await open({
    directory: (route) => {
      if (route.request().method() !== 'GET') writes.push(route.request().method());
      return reply(route, { ...current, services: [] });
    },
  });
  try {
    await page.getByRole('cell', { name: '/api/current', exact: true }).waitFor();
    await page.getByRole('button', { name: '从系统同步接口信息', exact: true }).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByText(/服务名称由平台字典“服务名称”维护/).waitFor();
    assert.equal(await dialog.getByRole('button', { name: '新增或维护服务名称' }).count(), 0);
    assert.equal(await dialog.getByRole('button', { name: '同步', exact: true }).isDisabled(), true);
    assert.deepEqual(writes, []);
  } finally {
    await page.close();
  }
});

test('resource details open in a dialog above the current list or historical snapshot', async () => {
  const detailFields = ['上下文', '处理器', '敏感等级', '请求条件'];
  const page = await open({
    current: {
      ...current,
      items: [
        current.items[0],
        { ...current.items[0], contextId: 'billing', handler: 'Billing#read',
          conditions: { headers: ['x-client=web'] }, sensitiveLevel: 'SENSITIVE' },
      ],
    },
  });
  try {
    await page.getByRole('tab', { name: '当前版本', selected: true }).waitFor();
    await page.locator('#catalog-path').fill('/api/current');
    const assertListColumns = async (list) => {
      for (const name of detailFields)
        assert.equal(await list.getByRole('columnheader', { name, exact: true }).count(), 0);
    };
    const currentPanel = page.getByRole('tabpanel', { name: '当前版本' });
    await assertListColumns(currentPanel);
    await currentPanel.getByRole('button', { name: '详情', exact: true }).nth(1).click();
    const detail = page.getByRole('dialog', { name: '接口资源详情', exact: true });
    await detail.waitFor();
    assert.equal(await currentPanel.isVisible(), true);
    for (const value of [...detailFields, 'billing', 'Billing#read', 'SENSITIVE', 'headers: x-client=web'])
      await detail.getByText(value, { exact: true }).waitFor();
    if (process.env.RESOURCE_DETAIL_SCREENSHOT)
      await page.screenshot({ path: process.env.RESOURCE_DETAIL_SCREENSHOT });
    await detail.getByRole('button', { name: '关闭', exact: true }).click();
    await detail.waitFor({ state: 'hidden' });
    assert.equal(await page.locator('#catalog-path').inputValue(), '/api/current');
    await page.getByRole('tab', { name: '历史版本' }).click();
    await page.getByRole('button', { name: '查看版本 1' }).click();
    const snapshot = page.getByRole('dialog', { name: '查看版本 1', exact: true });
    await assertListColumns(snapshot);
    await snapshot.getByRole('button', { name: '详情', exact: true }).click();
    assert.equal(await snapshot.isVisible(), true);
    await detail.getByText('Old#read', { exact: true }).waitFor();
    await detail.getByText('无', { exact: true }).waitFor();
    await page.keyboard.press('Escape');
    await detail.waitFor({ state: 'hidden' });
    assert.equal(await snapshot.getByRole('button', { name: '详情' }).evaluate(el => el === document.activeElement), true);
    await snapshot.getByRole('cell', { name: '/api/removed' }).waitFor();
    await snapshot.locator('.arco-modal-close-icon').click();
    await page.getByRole('tab', { name: '历史版本', selected: true }).waitFor();
  } finally {
    await page.close();
  }
});
