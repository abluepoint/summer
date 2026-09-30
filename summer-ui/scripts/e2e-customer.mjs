import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { chromium } from 'playwright-core';

const passwordFile = process.env.SUMMER_E2E_PASSWORD_FILE;
if (!passwordFile)
  throw new Error('Set SUMMER_E2E_PASSWORD_FILE to a private temporary file');
const password = (await readFile(passwordFile, 'utf8')).trim();
const base = process.env.SUMMER_E2E_ORIGIN || 'http://127.0.0.1:5173';
const browser = await chromium.launch({
  headless: true,
  executablePath:
    process.env.CHROME_PATH ||
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
});

try {
  const page = await browser.newPage({
    viewport: { width: 1365, height: 900 },
  });
  const unexpected = [];
  page.on('pageerror', (error) => unexpected.push(error.message));
  await page.goto(
    `${base}/customer/page?page=0&size=20&sort=username&direction=asc`
  );
  await page
    .locator('input[autocomplete="username"]')
    .fill(process.env.SUMMER_E2E_USERNAME || 'summer-user');
  await page.locator('input[autocomplete="current-password"]').fill(password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await page.waitForURL(
    `${base}/customer/page?page=0&size=20&sort=username&direction=asc`
  );
  await page.getByRole('button', { name: 'summer.demo', exact: true }).waitFor();
  if (process.env.SUMMER_E2E_SCREENSHOTS === '1')
    await page.screenshot({ path: '.e2e-list.png', fullPage: true });
  await page.getByRole('button', { name: 'summer.demo', exact: true }).click();
  await page.waitForURL(
    `${base}/customer/detail/summer.demo?page=0&size=20&sort=username&direction=asc`
  );
  await page.getByText('demo@summer.local').waitFor();
  if (process.env.SUMMER_E2E_SCREENSHOTS === '1')
    await page.screenshot({ path: '.e2e-detail.png', fullPage: true });
  await page.getByRole('button', { name: '返回列表' }).click();
  await page.waitForURL(
    `${base}/customer/page?page=0&size=20&sort=username&direction=asc`
  );
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByText('demo@summer.local').waitFor();
  assert.equal(
    await page.evaluate(
      () =>
        document.documentElement.scrollWidth <=
        document.documentElement.clientWidth
    ),
    true,
    'narrow layout must not overflow horizontally'
  );
  if (process.env.SUMMER_E2E_SCREENSHOTS === '1')
    await page.screenshot({ path: '.e2e-narrow.png', fullPage: true });
  await page.setViewportSize({ width: 1365, height: 900 });
  await page.getByPlaceholder('输入完整用户名').fill('missing');
  await page.getByRole('button', { name: '查询' }).click();
  await page.waitForURL(
    `${base}/customer/detail/missing?page=0&size=20&sort=username&direction=asc`
  );
  await page.getByText('客户不存在。').waitFor();
  await page.getByText(/requestId:/).waitFor();

  const filteredResponse = page.waitForResponse(
    (response) =>
      response.url().includes('/api/customers?') && response.status() === 200
  );
  await page.goto(
    `${base}/customer/page?page=0&size=10&active=false&sort=email&direction=desc`
  );
  const filtered = await filteredResponse;
  const query = new URL(filtered.url()).searchParams;
  assert.deepEqual(
    [
      query.get('page'),
      query.get('size'),
      query.get('active'),
      query.get('sort'),
      query.get('direction'),
    ],
    ['0', '10', 'false', 'email', 'desc']
  );
  await page.getByText('暂无数据').waitFor();
  for (const mode of ['drawer', 'modal']) {
    await page.goto(`${base}/customer/${mode}`);
    await page.getByRole('button', { name: 'summer.demo', exact: true }).click();
    await page.waitForURL(`${base}/customer/detail/summer.demo`);
    await page.getByText('demo@summer.local').waitFor();
  }

  for (const status of [403, 500]) {
    await page.route('**/api/customers?*', (route) =>
      route.fulfill({
        status,
        contentType: 'application/json',
        body: JSON.stringify({
          success: false,
          error: {
            code: `TEST_${status}`,
            message: `simulated ${status}`,
            violations: [],
          },
          requestId: `browser-${status}`,
          timestamp: '2026-09-30T00:00:00Z',
        }),
      })
    );
    await page.goto(`${base}/customer/page?case=${status}`);
    await page.getByText(`requestId: browser-${status}`).waitFor();
    await page.unroute('**/api/customers?*');
  }
  let unauthorizedCount = 0;
  await page.route(
    '**/api/customers?*',
    (route) => {
      unauthorizedCount++;
      return route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({
          success: false,
          error: { code: 'TEST_401', message: 'expired', violations: [] },
          requestId: 'browser-401',
        }),
      });
    },
    { times: 1 }
  );
  await page.getByRole('button', { name: '重试' }).click();
  await page.getByText('客户列表').waitFor();
  await page.getByRole('button', { name: 'summer.demo', exact: true }).waitFor();
  assert.equal(unauthorizedCount, 1, 'one 401 should restart PKCE only once');
  assert.deepEqual(
    unexpected,
    [],
    'browser should not produce uncaught errors'
  );
  console.log(
    'Real Customer list/detail/filter/404 and controlled 401/403/500 UI passed'
  );
} finally {
  await browser.close();
}
