import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { chromium } from 'playwright-core';

const passwordFile = process.env.SUMMER_E2E_PASSWORD_FILE;
if (!passwordFile) throw new Error('Set SUMMER_E2E_PASSWORD_FILE');
const password = (await readFile(passwordFile, 'utf8')).trim();
const base = process.env.SUMMER_E2E_ORIGIN || 'http://127.0.0.1:5173';
console.log('Starting isolated Customer browser acceptance');
const browser = await chromium.launch({
  headless: true,
  executablePath:
    process.env.CHROME_PATH ||
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
});

let createdUsername;
let authorization;
const page = await browser.newPage({ viewport: { width: 1365, height: 900 } });
page.setDefaultTimeout(10000);
const unexpected = [];
page.on('pageerror', (error) => unexpected.push(error.message));
page.on('request', (request) => {
  if (
    request.method() === 'POST' &&
    new URL(request.url()).pathname === '/api/customers'
  ) {
    authorization = request.headers().authorization;
  }
});
const form = () => page.locator('.arco-drawer');
const field = (name) => form().getByRole('textbox', { name });
const submit = () => form().getByRole('button', { name: '创建', exact: true });
const open = () => page.getByRole('button', { name: '新增客户' }).click();
const errorBody = (status, violations = []) =>
  JSON.stringify({
    success: false,
    error: {
      code: `TEST_${status}`,
      message: `controlled ${status}`,
      violations,
    },
    requestId: `create-${status}`,
    timestamp: '2026-09-30T00:00:00Z',
  });

try {
  console.log('Browser launched');
  const search = '?page=2&size=10&active=false&sort=email&direction=desc';
  await page.goto(`${base}/customer/drawer${search}`);
  console.log('Customer route loaded');
  await page
    .locator('input[autocomplete="username"]')
    .fill(process.env.SUMMER_E2E_USERNAME || 'summer-user');
  await page.locator('input[autocomplete="current-password"]').fill(password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await page.waitForURL(`${base}/customer/drawer${search}`);
  console.log('Authenticated');
  await open();
  await page.waitForTimeout(500);
  if (process.env.SUMMER_E2E_SCREENSHOTS === '1')
    await page.screenshot({ path: '.e2e-create-desktop.png', fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.waitForTimeout(200);
  assert.equal(
    await page.evaluate(
      () =>
        document.documentElement.scrollWidth <=
        document.documentElement.clientWidth
    ),
    true,
    'narrow create page must not overflow'
  );
  if (process.env.SUMMER_E2E_SCREENSHOTS === '1')
    await page.screenshot({ path: '.e2e-create-narrow.png', fullPage: true });
  await page.setViewportSize({ width: 1365, height: 900 });
  await submit().click();
  await form().getByText(/3–64/).waitFor();
  console.log('Client validation passed');

  await field('用户名').fill('create88');
  console.log('Username entered');
  await field('邮箱').fill('create88@example.test');
  console.log('Email entered');
  await field('名称').fill('Create 88');
  console.log('Name entered');
  await page.route('**/api/customers', (route) =>
    route.fulfill({
      status: 400,
      contentType: 'application/json',
      body: errorBody(400, [
        { field: 'email', message: 'email rejected' },
        { field: '$', message: 'request rejected' },
      ]),
    })
  );
  await submit().click();
  console.log('Controlled 400 submitted');
  await form().getByText('email rejected').waitFor();
  await form().getByText('request rejected').waitFor();
  await page.unroute('**/api/customers');

  await page.route('**/api/customers', (route) =>
    route.fulfill({
      status: 409,
      contentType: 'application/json',
      body: errorBody(409),
    })
  );
  await submit().click();
  await form().getByText('controlled 409').waitFor();
  await page.unroute('**/api/customers');

  await page.route('**/api/customers', (route) => route.abort('failed'));
  await submit().click();
  await form().getByText('网络错误，请检查连接后重试。').waitFor();
  await page.unroute('**/api/customers');

  await form().getByRole('button', { name: '取消' }).click();
  await page.getByText('放弃未保存的客户？').waitFor();
  await page.getByRole('button', { name: '继续编辑' }).click();
  assert.equal(await field('用户名').inputValue(), 'create88');
  await form().getByRole('button', { name: '取消' }).click();
  await page.getByRole('button', { name: '放弃', exact: true }).click();
  await form().waitFor({ state: 'hidden' });

  await open();
  await field('用户名').fill('auth88');
  await field('邮箱').fill('auth88@example.test');
  await field('名称').fill('Interrupted Customer');
  let unauthorizedPosts = 0;
  await page.route(
    '**/api/customers',
    (route) => {
      unauthorizedPosts++;
      return route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: errorBody(401),
      });
    },
    { times: 1 }
  );
  const authorize = page.waitForRequest(
    (request) => new URL(request.url()).pathname === '/oauth2/authorize'
  );
  await submit().click();
  await authorize;
  await page.waitForURL(`${base}/customer/drawer${search}`, {
    waitUntil: 'domcontentloaded',
  });
  await field('用户名')
    .waitFor()
    .catch(async () => {
      console.error(
        'Recovery status',
        await page.evaluate(() => ({
          path: location.pathname,
          draftPresent:
            sessionStorage.getItem('summer.pkce.customer-draft') !== null,
          drawerCount: document.querySelectorAll('.arco-drawer').length,
        }))
      );
      throw new Error('Customer draft was not restored after 401');
    });
  assert.equal(
    await field('用户名').inputValue(),
    'auth88',
    'reauthentication must restore unsaved form fields'
  );
  assert.equal(unauthorizedPosts, 1, '401 must not replay the create request');
  await form().getByRole('button', { name: '取消' }).click();
  await page.getByRole('button', { name: '放弃', exact: true }).click();
  await form().waitFor({ state: 'hidden' });

  await open();
  createdUsername = `c${Date.now().toString(36)}`;
  await field('用户名').fill(createdUsername);
  await field('邮箱').fill(`${createdUsername}@example.test`);
  await field('名称').fill('Customer create acceptance');
  let posts = 0;
  page.on('request', (request) => {
    if (
      request.method() === 'POST' &&
      new URL(request.url()).pathname === '/api/customers'
    )
      posts++;
  });
  await submit().evaluate((button) => {
    button.click();
    button.click();
  });
  await form().waitFor({ state: 'hidden' });
  assert.equal(posts, 1, 'double click must send one create request');
  assert.equal(
    new URL(page.url()).search,
    search,
    'list URL state must remain unchanged'
  );
  await page.waitForFunction(
    () =>
      document.activeElement instanceof HTMLButtonElement &&
      document.activeElement.textContent?.trim() === '新增客户',
    undefined,
    { timeout: 3000 }
  );
  assert.equal(
    await page
      .getByRole('button', { name: '新增客户' })
      .evaluate((button) => document.activeElement === button),
    true,
    'focus must return to the create trigger'
  );
  assert.ok(authorization, 'create request must carry an access token');
  const saved = await page.request.get(
    `${base}/api/customers/${createdUsername}`,
    { headers: { Authorization: authorization } }
  );
  assert.equal(saved.status(), 200, 'created Customer must persist');
  assert.equal((await saved.json()).data.username, createdUsername);
  assert.deepEqual(unexpected, [], 'no uncaught browser errors');
  console.log(
    'Customer create: real persistence, 400, 409, network, 401 recovery without replay, duplicate submit, confirmation, URL and focus passed'
  );
} catch (error) {
  console.error(
    `Customer browser acceptance failed: ${error?.name || 'Error'}`
  );
  process.exitCode = 1;
} finally {
  if (createdUsername && authorization) {
    const deletion = await page.request.delete(
      `${base}/api/customers/${createdUsername}`,
      { headers: { Authorization: authorization } }
    );
    if (deletion.status() === 204)
      console.log('Test Customer cleanup returned 204');
    else if (deletion.status() !== 404) {
      console.error(`Test Customer cleanup returned ${deletion.status()}`);
      process.exitCode = 1;
    }
  }
  await browser.close();
}
