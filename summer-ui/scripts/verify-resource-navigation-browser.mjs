import assert from 'node:assert/strict';
import { before, after, test } from 'node:test';
import { mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import { chromium } from 'playwright-core';

const clientCode = process.env.VITE_MENU_CLIENT_CODE || 'web';
const output = process.env.MANAGEMENT_SCREENSHOTS || fileURLToPath(
  new URL('../../.scratch/access-control-navigation-20261121/', import.meta.url)
);
let vite, browser, origin;
before(async () => {
  await mkdir(output, { recursive: true });
  vite = await createServer({
    root: fileURLToPath(new URL('..', import.meta.url)),
    cacheDir: 'node_modules/.vite-resource-navigation',
    logLevel: 'error',
    server: { port: 0 },
    plugins: [
      {
        name: 'resource-navigation-fixture',
        configureServer(server) {
          server.middlewares.use(async (req, res, next) => {
            if (!req.headers.accept?.includes('text/html')) return next();
            res.setHeader('Content-Type', 'text/html');
            res.end(
              await server.transformIndexHtml(
                req.url,
                '<div id="root"></div><script type="module" src="/scripts/fixtures/resource-navigation.tsx"></script>'
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
  console.log('Closing navigation fixture browser');
  await browser?.close();
  console.log('Closing navigation fixture server');
  vite?.httpServer.closeAllConnections();
  await vite?.close();
  console.log('Navigation fixture closed');
});

async function capture(page, grants, errors, calls) {
  await writeFile(
    output + grants + '-diagnostics.json',
    JSON.stringify(
      {
        errors,
        calls,
        text: await page.locator('body').innerText(),
        menu: await page
          .locator('.arco-layout-sider')
          .innerHTML()
          .catch(() => ''),
      },
      null,
      2
    )
  );
  await page.screenshot({ path: output + grants + '-last-page.png' });
}

async function open(path, grants) {
  const page = await browser.newPage({
    viewport: { width: 1440, height: 960 },
  });
  page.setDefaultTimeout(10000);
  const errors = [],
    calls = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await page.route('**/api/**', async (route) => {
    const req = route.request(),
      url = new URL(req.url());
    calls.push({ path: url.pathname, method: req.method() });
    const platform = url.pathname.includes('/platform/');
    let data = {
      items: [],
      total: 0,
      page: 0,
      size: 10,
      revision: 1,
      complete: true,
      issues: [],
    };
    if (url.pathname.endsWith('/authorization/navigation')) {
      assert.equal(url.searchParams.get('clientCode'), clientCode);
      // Resolve after the first layout render to exercise asynchronous ancestor expansion.
      await new Promise((resolve) => setTimeout(resolve, 50));
      const allowed =
        grants === 'both' ||
        (platform ? grants === 'platform' : grants === 'tenant');
      data = {
        engineMode: 'MANAGED',
        manifestVersion: 9,
        items: allowed
          ? [
              'catalog-operations',
              'roles',
              'permissions',
              'interface-resources',
              'menu-manifest',
              ...(path.startsWith('/dictionary/') ? ['dictionary/manage', 'dictionary/groups'] : []),
            ].map((menuId, index) => ({
              menuId,
              path: '/' + menuId,
              parentMenuId: menuId === 'catalog-operations' ? undefined : 'catalog-operations',
              titleKey: {'catalog-operations':'业务设置', roles:'岗位配置', permissions:'权限管理',
                'interface-resources':'接口资源', 'menu-manifest':'菜单管理',
                'dictionary/manage':'字典管理', 'dictionary/groups':'字典组管理'}[menuId],
              clientCode,
              enabled: true,
              pageEligible: true,
              navOnly: false,
              sortOrder: index,
              version: 1,
              actions: [],
            }))
          : [],
      };
    } else if (url.pathname.endsWith('/authorization/operations')) {
      data = {
        actions: Object.fromEntries(
          (url.searchParams.get('actions') || '')
            .split(',')
            .map((action) => [action, true])
        ),
      };
    } else if (/\/authorization\/menu-(layout|manifest)$/.test(url.pathname)) {
      data = { engineMode: 'MANAGED', manifestVersion: 9, items: [] };
    } else if (
      url.pathname === '/api/v1/platform/dictionaries' ||
      url.pathname.endsWith('/data-resources') ||
      url.pathname === '/api/message/list'
    ) {
      data = [];
    }
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(
        url.pathname === '/api/message/list'
          ? data
          : { success: true, data, requestId: 'resource-navigation-fixture' }
      ),
    });
  });
  await page.goto(`${origin}${path}?grants=${grants}`);
  const menu = page.locator('.arco-layout-sider .arco-menu');
  return { page, menu, calls, errors };
}

test('published menu folder and page names are used for the sidebar and deep links', async () => {
  const { page, menu, calls, errors } = await open(
    '/platform/roles',
    'both'
  );
  try {
    await menu
      .locator('[aria-current="page"]')
      .filter({ hasText: '岗位配置' })
      .waitFor();
    const root = menu
      .locator('.arco-menu-inline')
      .filter({
        has: page.locator('.arco-menu-inline-header', {
          hasText: '业务设置',
        }),
      })
      .first();
    await root.waitFor();
    const categories = root.locator(':scope > .arco-menu-inline-content > *');
    assert.equal(await categories.count(), 5);
    assert.equal(
      await root
        .locator(':scope > .arco-menu-inline-header .arco-icon-settings')
        .count(),
      0
    );
    assert.equal(await root.getByText('岗位配置', { exact: true }).count(), 2);
    await page.reload();
    await root
      .locator('[aria-current="page"]')
      .filter({ hasText: '岗位配置' })
      .waitFor();
    assert.match(
      await page.locator('.arco-breadcrumb').innerText(),
      /业务设置[\s\S]*岗位配置/
    );
    await page.screenshot({ path: output + 'platform-roles.png' });
    await menu.getByText('权限管理', { exact: true }).click();
    await page.waitForURL('**/platform/permissions');
    await page.locator('.arco-card-header-title').filter({ hasText: /^权限管理$/ }).waitFor();
    assert.equal(
      await menu.getByText('租户权限管理', { exact: true }).count(),
      0
    );
    assert.equal(await menu.getByText('权限管理', { exact: true }).count(), 1);
    await menu.getByText('菜单管理', { exact: true }).click();
    await page.waitForURL('**/platform/menu-manifest');
    await page
      .locator('.arco-card-header-title')
      .filter({ hasText: '菜单管理' })
      .waitFor();
    assert.equal(await menu.getByText('租户菜单管理', { exact: true }).count(), 0);
    await page.waitForFunction(
      () => !document.querySelector('.arco-spin-loading')
    );
    assert.ok(
      calls.some(
        (call) => call.path === '/api/v1/platform/authorization/menu-manifest'
      )
    );
    assert.ok(
      !calls.some(
        (call) =>
          call.path === '/api/v1/enterprises/tenant-a/authorization/menu-layout'
      )
    );
    assert.equal(
      await page
        .locator(
          '[aria-label="授权域"], [aria-label="权限管理域"], [aria-label="活动租户"]'
        )
        .count(),
      0
    );
    await page.screenshot({ path: output + 'combined-grants.png' });
    await page.reload();
    await menu
      .locator('[aria-current="page"]')
      .filter({ hasText: '菜单管理' })
      .waitFor();
    assert.deepEqual(errors, []);
    assert.ok(calls.every((call) => call.method === 'GET'));
  } finally {
    await capture(page, 'both', errors, calls);
    await page.close();
  }
});

for (const domain of ['tenant', 'platform'])
  test(`${domain}-only grants keep domain menus but permission management belongs only to platform`, async () => {
    const prefix = domain === 'platform' ? '/platform' : '';

    const { page, menu, calls, errors } = await open(
      domain === 'platform' ? prefix + '/menu-manifest' : '/roles',
      domain
    );
    try {
      await menu
        .locator('[aria-current="page"]')
        .filter({ hasText: domain === 'platform' ? '菜单管理' : '岗位配置' })
        .waitFor();
      if (domain === 'tenant') {
        assert.equal(await menu.getByText('菜单管理', { exact: true }).count(), 0);

      }
      await menu.getByText('接口资源', { exact: true }).waitFor();
      assert.equal(
        await menu.getByText('权限管理', { exact: true }).count(),
        domain === 'platform' ? 1 : 0
      );
      assert.equal(
        await menu.getByText('岗位配置', { exact: true }).count(),
        1
      );
      await page
        .locator('.arco-card-header-title')
        .filter({ hasText: domain === 'platform' ? '菜单管理' : '租户角色管理' })
        .waitFor();
      assert.equal(
        await menu
          .getByText(domain === 'platform' ? '租户权限管理' : '平台权限管理', {
            exact: true,
          })
          .count(),
        0
      );
      await menu.getByText('接口资源', { exact: true }).click();
      await page.waitForURL('**/interface-resources');
      await page
        .locator('.arco-card-header-title')
        .filter({ hasText: '接口资源' })
        .waitFor();
      await page.getByRole('tab', { name: '当前版本' }).waitFor();
      assert.equal(await page.locator('[aria-label="全局活动租户"]').count(), 0);
      assert.ok(calls.some(({ path }) => path === '/api/v1/platform/interface-resources'));
      assert.ok(calls.some(({ path }) => path === '/api/v1/platform/interface-resources/history'));
      assert.ok(!calls.some(({ path }) => path.startsWith('/api/v1/enterprises/') && path.includes('/interface-resources')));
      await page.screenshot({ path: output + domain + '-only.png' });
      assert.deepEqual(errors, []);
      assert.ok(calls.every((call) => call.method === 'GET'));
    } finally {
      await capture(page, domain, errors, calls);
      await page.close();
    }
  });

for (const path of ['/dictionary/manage', '/dictionary/groups', '/permissions', '/platform/permissions', '/menu-manifest', '/platform/menu-manifest'])
  test(`tenant permission grant cannot open ${path}`, async () => {
    const { page, menu, calls, errors } = await open(path, 'tenant');
    try {
      await menu
        .getByText('接口资源', { exact: true })
        .waitFor({ state: 'attached' });
      await page.getByText('对不起，您没有访问该资源的权限').waitFor();
      for (const title of ['权限管理', '字典管理', '字典组管理'])
        assert.equal(await menu.getByText(title, { exact: true }).count(), 0);
      assert.equal(
        calls.some((call) => /\/(permissions|dictionaries)$/.test(call.path)),
        false
      );
      assert.deepEqual(errors, []);
    } finally {
      await page.close();
    }
  });

test('no visible resource grants leave no empty directory or accessible resource page', async () => {
  const { page, menu, calls, errors } = await open('/permissions', 'none');
  try {
    await page.getByText('对不起，您没有访问该资源的权限').waitFor();
    await page.waitForFunction(
      () => !document.querySelector('.arco-spin-loading')
    );
    assert.equal(
      await menu.getByText('业务设置', { exact: true }).count(),
      0
    );
    assert.equal(
      calls.some((call) => /\/permissions$/.test(call.path)),
      false
    );
    assert.deepEqual(errors, []);
  } finally {
    await page.close();
  }
});

for (const [id, title] of [['dictionary/manage', '字典管理'], ['dictionary/groups', '字典组管理']])
  test(`platform grant opens ${id} with an active tenant`, async () => {
    const { page, menu, calls, errors } = await open('/' + id, 'both');
    try {
      await menu.locator('[aria-current="page"]').filter({ hasText: title }).waitFor();
      await page.locator('.arco-card-header-title').filter({ hasText: title }).waitFor();
      assert.equal(await page.locator('[aria-label="全局活动租户"]').count(), 0);
      await page.waitForFunction(() => !document.querySelector('.arco-spin-loading'));
      assert.ok(calls.some(call => call.path === '/api/v1/platform/dictionaries'));
      assert.deepEqual(errors, []);
    } finally {
      await page.close();
    }
  });
