import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { chromium } from 'playwright-core';

const passwordFile = process.env.SUMMER_E2E_PASSWORD_FILE;
if (!passwordFile) throw new Error('Set SUMMER_E2E_PASSWORD_FILE');
const password = (await readFile(passwordFile, 'utf8')).trim();
const base = process.env.SUMMER_E2E_ORIGIN || 'http://127.0.0.1:5191';
const username = `page91-${Date.now().toString(36)}`;
const list =
  '/customer/page?page=0&size=10&active=true&sort=email&direction=desc';
const browser = await chromium.launch({
  headless: true,
  executablePath:
    process.env.CHROME_PATH ||
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
});
const page = await browser.newPage({ viewport: { width: 1365, height: 900 } });
page.setDefaultTimeout(15000);
let stage = 'login';
let bearer;
let created = false;
const unexpected = [];
const writes = [];
const form = () =>
  page
    .locator('.arco-card')
    .filter({ has: page.getByRole('button', { name: '保存修改' }) });
const field = (name) => page.getByRole('textbox', { name });
const failureBody = (status, violations = []) =>
  JSON.stringify({
    success: false,
    error: {
      code: `TEST_${status}`,
      message: `controlled ${status}`,
      violations,
    },
    requestId: `page-${status}`,
    timestamp: '2026-09-30T00:00:00Z',
  });

page.on('pageerror', (error) => unexpected.push(error.message));
page.on('request', (request) => {
  if (new URL(request.url()).pathname.startsWith('/api/customers'))
    bearer = request.headers().authorization || bearer;
  if (
    ['POST', 'PATCH'].includes(request.method()) &&
    new URL(request.url()).pathname.startsWith('/api/customers')
  )
    writes.push({
      method: request.method(),
      url: request.url(),
      body: request.postDataJSON(),
    });
});

async function loginIfNeeded() {
  await page
    .locator('input[autocomplete="current-password"]')
    .waitFor({ timeout: 5000 })
    .catch(() => {});
  if (
    await page.locator('input[autocomplete="current-password"]').isVisible()
  ) {
    await page.locator('input[autocomplete="username"]').fill('summer-user');
    await page.locator('input[autocomplete="current-password"]').fill(password);
    await page.getByRole('button', { name: '登录', exact: true }).click();
  }
}

try {
  stage = 'direct create deep link';
  await page.goto(`${base}/customer/page/create`);
  await page.locator('input[autocomplete="current-password"]').waitFor();
  await loginIfNeeded();
  await page.getByRole('button', { name: '创建', exact: true }).waitFor();
  await page.getByRole('button', { name: '取消', exact: true }).click();
  await page.waitForURL(`${base}/customer/page`);

  stage = 'create from full list URL';
  await page.goto(`${base}${list}`);
  await loginIfNeeded();
  await page.getByRole('button', { name: '新增客户' }).click();
  await page.waitForURL((url) => url.pathname === '/customer/page/create');
  assert.equal(new URL(page.url()).searchParams.get('from'), list);
  if (process.env.SUMMER_E2E_SCREENSHOTS === '1')
    await page.screenshot({ path: '.e2e-91-page-desktop.png', fullPage: true });
  await field('用户名').fill(username);
  await field('邮箱').fill(`${username}@example.test`);
  await field('名称').fill('Page original');

  stage = 'dirty navigation';
  const routeDialog = page.waitForEvent('dialog');
  await page.evaluate(() => window.history.back());
  const routePrompt = await routeDialog;
  assert.match(routePrompt.message(), /未保存/);
  await routePrompt.dismiss();
  await page.waitForTimeout(200);
  assert.equal(new URL(page.url()).pathname, '/customer/page/create');
  assert.equal(await field('名称').inputValue(), 'Page original');

  stage = 'dirty refresh';
  const refreshDialog = page.waitForEvent('dialog');
  const refreshRequest = page
    .evaluate(() => window.location.reload())
    .catch(() => undefined);
  const refreshPrompt = await refreshDialog;
  assert.equal(refreshPrompt.type(), 'beforeunload');
  await refreshPrompt.dismiss();
  await refreshRequest;
  await page.waitForTimeout(200);
  assert.equal(await field('名称').inputValue(), 'Page original');

  stage = 'dirty cancel';
  await page.getByRole('button', { name: '取消', exact: true }).click();
  await page.getByText('放弃未保存的客户？').waitFor();
  await page.getByRole('button', { name: '继续编辑' }).click();
  assert.equal(await field('名称').inputValue(), 'Page original');

  stage = 'create validation and conflict';
  await page.route(
    '**/api/customers',
    (route) =>
      route.request().method() === 'POST'
        ? route.fulfill({
            status: 400,
            contentType: 'application/json',
            body: failureBody(400, [
              { field: 'email', message: 'email rejected' },
              { field: '$', message: 'request rejected' },
            ]),
          })
        : route.continue(),
    { times: 1 }
  );
  await page.getByRole('button', { name: '创建', exact: true }).click();
  await page.getByText('email rejected').waitFor();
  await page.getByText('request rejected').waitFor();
  assert.equal(
    await field('邮箱').evaluate((node) => node === document.activeElement),
    true
  );
  await page.route(
    '**/api/customers',
    (route) =>
      route.request().method() === 'POST'
        ? route.fulfill({
            status: 409,
            contentType: 'application/json',
            body: failureBody(409, [
              { field: 'username', message: 'username taken' },
            ]),
          })
        : route.continue(),
    { times: 1 }
  );
  await page.getByRole('button', { name: '创建', exact: true }).click();
  await page.getByText('username taken').waitFor();

  stage = 'create network error';
  await page.route(
    '**/api/customers',
    (route) =>
      route.request().method() === 'POST'
        ? route.abort('failed')
        : route.continue(),
    { times: 1 }
  );
  await page.getByRole('button', { name: '创建', exact: true }).click();
  await page
    .locator('.arco-alert-content')
    .getByText('网络错误，请检查连接后重试。')
    .waitFor();
  assert.equal(await field('名称').inputValue(), 'Page original');

  stage = 'create 401 recovery';
  const beforeCreateRecovery = writes.length;
  await page.route(
    '**/api/customers',
    (route) =>
      route.request().method() === 'POST'
        ? route.fulfill({
            status: 401,
            contentType: 'application/json',
            body: failureBody(401),
          })
        : route.continue(),
    { times: 1 }
  );
  const createAuthorize = page.waitForRequest(
    (request) => new URL(request.url()).pathname === '/oauth2/authorize'
  );
  await page.getByRole('button', { name: '创建', exact: true }).click();
  await createAuthorize;
  await page.waitForURL(
    (url) =>
      url.pathname === '/customer/page/create' &&
      url.searchParams.get('from') === list
  );
  await page.getByRole('button', { name: '创建', exact: true }).waitFor();
  assert.equal(await field('用户名').inputValue(), username);
  assert.equal(await field('邮箱').inputValue(), `${username}@example.test`);
  assert.equal(await field('名称').inputValue(), 'Page original');
  assert.equal(
    writes.length,
    beforeCreateRecovery + 1,
    'POST must not replay after sign in'
  );

  stage = 'create success';
  await page.getByRole('button', { name: '创建', exact: true }).click();
  await page.waitForURL(`${base}${list}`);
  created = true;
  assert.equal(writes.at(-1).method, 'POST');
  assert.deepEqual(writes.at(-1).body, {
    username,
    email: `${username}@example.test`,
    displayName: 'Page original',
  });
  assert.ok(bearer?.startsWith('Bearer '));

  stage = 'edit from list';
  await page
    .getByRole('row')
    .filter({ has: page.getByRole('button', { name: username, exact: true }) })
    .getByRole('button', { name: '编辑', exact: true })
    .click();
  await page.waitForURL(
    (url) => url.pathname === `/customer/page/edit/${username}`
  );
  assert.equal(new URL(page.url()).searchParams.get('from'), list);
  await page.getByRole('button', { name: '保存修改' }).waitFor();
  assert.equal(await field('用户名').getAttribute('readonly'), '');
  assert.equal(
    await page.getByRole('button', { name: '保存修改' }).isDisabled(),
    true
  );
  await field('名称').fill('Page edited');

  stage = 'edit missing target';
  await page.route(
    `**/api/customers/${username}`,
    (route) =>
      route.request().method() === 'PATCH'
        ? route.fulfill({
            status: 404,
            contentType: 'application/json',
            body: failureBody(404),
          })
        : route.continue(),
    { times: 1 }
  );
  await page.getByRole('button', { name: '保存修改' }).click();
  await page
    .getByText('此客户已不存在。草稿会保留至您放弃本次编辑。')
    .waitFor();
  assert.equal(await field('名称').inputValue(), 'Page edited');

  stage = 'edit 401 recovery';
  const beforeRecovery = writes.length;
  await page.route(
    `**/api/customers/${username}`,
    (route) =>
      route.request().method() === 'PATCH'
        ? route.fulfill({
            status: 401,
            contentType: 'application/json',
            body: failureBody(401),
          })
        : route.continue(),
    { times: 1 }
  );
  const authorize = page.waitForRequest(
    (request) => new URL(request.url()).pathname === '/oauth2/authorize'
  );
  await page.getByRole('button', { name: '保存修改' }).click();
  await authorize;
  await page.waitForURL(
    (url) =>
      url.pathname === `/customer/page/edit/${username}` &&
      url.searchParams.get('from') === list
  );
  await page.getByRole('button', { name: '保存修改' }).waitFor();
  assert.equal(await field('名称').inputValue(), 'Page edited');
  assert.equal(
    writes.length,
    beforeRecovery + 1,
    'PATCH must not replay after sign in'
  );

  stage = 'edit success';
  await page.getByRole('button', { name: '保存修改' }).click();
  await page.waitForURL(`${base}${list}`);
  assert.deepEqual(writes.at(-1).body, { displayName: 'Page edited' });

  stage = 'edit refresh and fallback';
  await page.goto(`${base}/customer/page/edit/${username}`);
  await loginIfNeeded();
  await page.getByRole('button', { name: '保存修改' }).waitFor();
  await page.reload();
  await loginIfNeeded();
  await page.getByRole('button', { name: '保存修改' }).waitFor();
  await page.getByRole('button', { name: '取消', exact: true }).click();
  await page.waitForURL(`${base}/customer/page`);

  stage = 'narrow keyboard edit';
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(`${base}/customer/page/edit/${username}`);
  await loginIfNeeded();
  await page.getByRole('button', { name: '保存修改' }).waitFor();
  assert.equal(
    await page.evaluate(
      () =>
        document.documentElement.scrollWidth <=
        document.documentElement.clientWidth
    ),
    true
  );
  if (process.env.SUMMER_E2E_SCREENSHOTS === '1')
    await page.screenshot({ path: '.e2e-91-page-narrow.png', fullPage: true });
  await field('邮箱').focus();
  await page.keyboard.press('Meta+A');
  await page.keyboard.type(`narrow-${username}@example.test`);
  await page.keyboard.press('Tab');
  assert.equal(
    await field('邮箱').inputValue(),
    `narrow-${username}@example.test`
  );
  await page.getByRole('button', { name: '保存修改' }).focus();
  await page.keyboard.press('Enter');
  await page.waitForURL(`${base}/customer/page`);
  const response = await page.request.get(`${base}/api/customers/${username}`, {
    headers: { Authorization: bearer },
  });
  assert.equal(response.status(), 200);
  assert.equal(
    (await response.json()).data.email,
    `narrow-${username}@example.test`
  );

  assert.deepEqual(unexpected, []);
  console.log('Customer page mode real-backend browser acceptance passed');
} catch (error) {
  console.error(`Customer page mode failed at ${stage}: ${error.message}`);
  process.exitCode = 1;
} finally {
  if (created && bearer) {
    const response = await page.request.delete(
      `${base}/api/customers/${username}`,
      { headers: { Authorization: bearer } }
    );
    if (response.status() !== 204)
      console.error(`Customer cleanup returned ${response.status()}`);
  }
  await browser.close();
}
