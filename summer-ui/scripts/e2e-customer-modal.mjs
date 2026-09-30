import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { chromium } from 'playwright-core';

const passwordFile = process.env.SUMMER_E2E_PASSWORD_FILE;
if (!passwordFile) throw new Error('Set SUMMER_E2E_PASSWORD_FILE');
const password = (await readFile(passwordFile, 'utf8')).trim();
const base = process.env.SUMMER_E2E_ORIGIN || 'http://127.0.0.1:5173';
const username = `modal90-${Date.now().toString(36)}`;
const search = '?page=0&size=10&active=all&sort=username&direction=desc';
const browser = await chromium.launch({
  headless: true,
  executablePath:
    process.env.CHROME_PATH ||
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
});
const page = await browser.newPage({ viewport: { width: 1365, height: 900 } });
page.setDefaultTimeout(15000);
const errors = [];
let bearer;
let created = false;
let stage = 'start';
let postCount = 0;
const patches = [];
page.on('pageerror', (error) => errors.push(error.message));
page.on('request', (request) => {
  if (!new URL(request.url()).pathname.startsWith('/api/customers')) return;
  bearer = request.headers().authorization || bearer;
  if (request.method() === 'POST') postCount += 1;
  if (request.method() === 'PATCH') patches.push(request.postDataJSON());
});
const modal = () =>
  page.locator('.arco-modal:visible').filter({ has: page.locator('form') });
const field = (name) => modal().getByRole('textbox', { name });
const create = () => modal().getByRole('button', { name: '创建', exact: true });
const save = () => modal().getByRole('button', { name: '保存修改' });
const body = (status, violations = []) =>
  JSON.stringify({
    success: false,
    error: {
      code: `TEST_${status}`,
      message: `controlled ${status}`,
      violations,
    },
    requestId: `modal-${status}`,
  });

try {
  stage = 'login';
  await page.goto(`${base}/customer/modal${search}`);
  await page.locator('input[autocomplete="username"]').fill('summer-user');
  await page.locator('input[autocomplete="current-password"]').fill(password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await page.waitForURL(`${base}/customer/modal${search}`);
  await page.getByRole('button', { name: '新增客户' }).click();
  await field('用户名').waitFor();
  if (process.env.SUMMER_E2E_SCREENSHOTS === '1') {
    await page.waitForTimeout(450);
    await page.screenshot({ path: '.e2e-90-desktop.png', fullPage: true });
  }
  assert.equal(
    await field('用户名').evaluate((node) => node === document.activeElement),
    true
  );
  stage = 'keyboard and focus lock';
  for (let i = 0; i < 8; i += 1) {
    await page.keyboard.press('Tab');
    assert.equal(
      await modal().evaluate((node) => node.contains(document.activeElement)),
      true,
      'Tab focus must remain inside the Modal'
    );
  }
  await field('用户名').fill(username);
  await page.keyboard.press('Escape');
  await page.getByText('放弃未保存的客户？').waitFor();
  await page.getByRole('button', { name: '继续编辑' }).click();
  assert.equal(await field('用户名').inputValue(), username);
  await page.getByText('放弃未保存的客户？').waitFor({ state: 'hidden' });
  stage = 'overlay close';
  await page.mouse.click(10, 10);
  await page.getByText('放弃未保存的客户？').waitFor();
  await page.getByRole('button', { name: '继续编辑' }).click();
  assert.equal(await field('用户名').inputValue(), username);

  stage = 'validation and server errors';
  await create().click();
  await modal().getByText('请输入有效邮箱，最长 200 个字符。').waitFor();
  await field('邮箱').fill(`${username}@example.test`);
  await field('名称').fill('Modal original');
  await page.route('**/api/customers', (route) =>
    route.fulfill({
      status: 400,
      contentType: 'application/json',
      body: body(400, [
        { field: 'email', message: 'email rejected' },
        { field: '$', message: 'request rejected' },
      ]),
    })
  );
  await create().click();
  await modal().getByText('email rejected').waitFor();
  await modal().getByText('request rejected').waitFor();
  await page.unroute('**/api/customers');
  await page.route('**/api/customers', (route) =>
    route.fulfill({
      status: 409,
      contentType: 'application/json',
      body: body(409, [{ field: 'username', message: 'username conflict' }]),
    })
  );
  await create().click();
  await modal().getByText('username conflict').waitFor();
  await page.unroute('**/api/customers');

  stage = '401 create draft and no replay';
  let create401 = false;
  await page.route('**/api/customers', (route) => {
    if (route.request().method() === 'POST' && !create401) {
      create401 = true;
      return route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: body(401),
      });
    }
    return route.continue();
  });
  const beforeCreate401 = postCount;
  await create().click();
  await page.waitForURL(`${base}/customer/modal${search}`);
  await field('用户名').waitFor();
  assert.equal(await field('用户名').inputValue(), username);
  assert.equal(postCount, beforeCreate401 + 1, '401 must not replay POST');
  await page.unroute('**/api/customers');

  stage = 'real create';
  await create().click();
  await modal().waitFor({ state: 'hidden' });
  created = true;
  assert.equal(new URL(page.url()).search, search);
  await page.waitForFunction(
    () => document.activeElement?.id === 'customer-create-trigger'
  );
  assert.equal(postCount, beforeCreate401 + 2);
  let response = await page.request.get(`${base}/api/customers/${username}`, {
    headers: { Authorization: bearer },
  });
  assert.equal(response.status(), 200);
  assert.equal((await response.json()).data.displayName, 'Modal original');

  stage = 'real edit';
  await page.getByPlaceholder('输入完整用户名').fill(username);
  await page.getByRole('button', { name: '查询' }).click();
  await page.waitForURL(`${base}/customer/modal/${username}${search}`);
  await page.getByRole('button', { name: '编辑', exact: true }).click();
  assert.equal(await field('用户名').getAttribute('readonly'), '');
  assert.equal(await save().isDisabled(), true);
  await field('名称').fill('Modal changed');
  stage = 'dirty route leave';
  const leaveDialog = page.waitForEvent('dialog');
  await page.evaluate(() => window.history.back());
  const prompt = await leaveDialog;
  assert.equal(prompt.message().includes('未保存的客户修改'), true);
  await prompt.dismiss();
  assert.equal(await field('名称').inputValue(), 'Modal changed');
  await modal().getByRole('button', { name: '取消' }).click();
  await page.getByText('放弃未保存的修改？').waitFor();
  await page.getByRole('button', { name: '继续编辑' }).click();
  assert.equal(await field('名称').inputValue(), 'Modal changed');
  await save().click();
  await modal().waitFor({ state: 'hidden' });
  assert.deepEqual(patches.at(-1), { displayName: 'Modal changed' });
  assert.equal(new URL(page.url()).search, search);
  await page.waitForFunction(() =>
    document.activeElement?.id?.startsWith('customer-edit-trigger-')
  );
  response = await page.request.get(`${base}/api/customers/${username}`, {
    headers: { Authorization: bearer },
  });
  assert.equal((await response.json()).data.displayName, 'Modal changed');

  stage = 'narrow screen';
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole('button', { name: '编辑', exact: true }).click();
  await modal().waitFor();
  await page.waitForTimeout(350);
  if (process.env.SUMMER_E2E_SCREENSHOTS === '1')
    await page.screenshot({ path: '.e2e-90-narrow.png', fullPage: true });
  const bounds = await modal().boundingBox();
  assert.ok(bounds && bounds.x >= 0 && bounds.x + bounds.width <= 390);
  assert.equal(await save().isVisible(), true);
  await modal().getByRole('button', { name: '取消' }).click();
  await modal().waitFor({ state: 'hidden' });

  stage = '401 draft and no replay';
  await page.setViewportSize({ width: 1365, height: 900 });
  await page.getByRole('button', { name: '编辑', exact: true }).click();
  await field('名称').fill('After login');
  let injected = false;
  await page.route(`**/api/customers/${username}`, (route) => {
    if (route.request().method() === 'PATCH' && !injected) {
      injected = true;
      return route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: body(401),
      });
    }
    return route.continue();
  });
  const before401 = patches.length;
  await save().click();
  await page.waitForURL(`${base}/customer/modal/${username}${search}`);
  await field('名称').waitFor();
  assert.equal(await field('名称').inputValue(), 'After login');
  assert.equal(patches.length, before401 + 1, '401 must not replay PATCH');
  await page.unroute(`**/api/customers/${username}`);
  await save().click();
  await modal().waitFor({ state: 'hidden' });
  assert.equal(patches.length, before401 + 2);
  response = await page.request.get(`${base}/api/customers/${username}`, {
    headers: { Authorization: bearer },
  });
  assert.equal((await response.json()).data.displayName, 'After login');
  assert.deepEqual(errors, []);
  console.log('Modal create/edit browser acceptance passed');
} catch (error) {
  console.error(
    `Modal browser acceptance failed at ${stage}: ${error.message}`
  );
  throw error;
} finally {
  if (created && bearer) {
    const cleanup = await page.request.delete(
      `${base}/api/customers/${username}`,
      {
        headers: { Authorization: bearer },
      }
    );
    assert.equal(cleanup.status(), 204, 'test Customer cleanup must succeed');
  }
  await browser.close();
}
