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
    await page.getByText('扫描不完整', { exact: true }).waitFor();
    await page.getByRole('button', { name: '比较版本', exact: true }).click();
    await page.getByText(/catalog-fixture-request/).waitFor();
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
      await page.getByRole('button', { name: '刷新', exact: true }).isEnabled(),
      true
    );
  } finally {
    await page.close();
  }
});
