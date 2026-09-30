import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { chromium } from 'playwright-core';

const passwordFile = process.env.SUMMER_E2E_PASSWORD_FILE;
if (!passwordFile) throw new Error('Set SUMMER_E2E_PASSWORD_FILE');
const password = (await readFile(passwordFile, 'utf8')).trim();
const base = process.env.SUMMER_E2E_ORIGIN || 'http://127.0.0.1:5173';
const username = `zzzz-edit-${Date.now().toString(36)}`;
const customerUrl = `${base}/api/customers/${username}`;
const browser = await chromium.launch({
  headless: true,
  executablePath:
    process.env.CHROME_PATH ||
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
});
const page = await browser.newPage({ viewport: { width: 1365, height: 900 } });
page.setDefaultTimeout(12000);
const unexpected = [];
let bearer;
let created = false;
let stage = 'login';
const patchBodies = [];
page.on('pageerror', (error) => unexpected.push(error.message));
page.on('request', (request) => {
  if (new URL(request.url()).pathname.startsWith('/api/customers')) {
    bearer = request.headers().authorization || bearer;
  }
  if (request.method() === 'PATCH' && request.url() === customerUrl)
    patchBodies.push(request.postDataJSON());
});
const headers = () => ({ Authorization: bearer });
const drawer = () => page.locator('.arco-drawer:visible');
const field = (name) => drawer().getByRole('textbox', { name });
const save = () => drawer().getByRole('button', { name: '保存修改' });
const edit = () => page.getByRole('button', { name: '编辑', exact: true });
const failureBody = (status) =>
  JSON.stringify({
    success: false,
    error: { code: `TEST_${status}`, message: '', violations: [] },
    requestId: `edit-${status}`,
  });

try {
  const search = '?page=0&size=10&sort=username&direction=desc';
  await page.goto(`${base}/customer/drawer${search}`);
  await page.locator('input[autocomplete="username"]').fill('summer-user');
  await page.locator('input[autocomplete="current-password"]').fill(password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await page.waitForURL(`${base}/customer/drawer${search}`);
  await page.getByRole('button', { name: '新增客户' }).waitFor();
  assert.ok(bearer?.startsWith('Bearer '));

  const createResponse = await page.request.post(`${base}/api/customers`, {
    headers: headers(),
    data: {
      username,
      email: `${username}@example.test`,
      displayName: 'Edit original',
    },
  });
  assert.equal(createResponse.status(), 200);
  created = true;
  await page.getByPlaceholder('输入完整用户名').fill(username);
  await page.getByRole('button', { name: '查询' }).click();
  await page.waitForURL(`${base}/customer/detail/${username}${search}`);
  await page.getByText('Edit original').waitFor();

  await edit().click();
  assert.equal(await field('用户名').getAttribute('readonly'), '');
  assert.equal(await field('用户名').inputValue(), username);
  await field('邮箱').fill(`changed-${username}@example.test`);
  stage = 'dirty navigation dialog';
  const leaveDialog = page.waitForEvent('dialog');
  await page.evaluate(() => window.history.back());
  const prompt = await leaveDialog;
  assert.equal(
    prompt.message().includes('未保存的客户修改'),
    true,
    'dirty route navigation must ask before leaving'
  );
  await prompt.dismiss();
  await page.waitForTimeout(200);
  stage = 'dirty navigation path';
  assert.equal(new URL(page.url()).pathname, `/customer/detail/${username}`);
  stage = 'dirty navigation draft';
  assert.equal(
    await field('邮箱').inputValue(),
    `changed-${username}@example.test`
  );
  stage = 'dirty cancel';
  await drawer().getByRole('button', { name: '取消' }).click();
  await page.getByText('放弃未保存的修改？').waitFor();
  await page.getByRole('button', { name: '继续编辑' }).click();
  assert.equal(
    await field('邮箱').inputValue(),
    `changed-${username}@example.test`
  );
  await save().click();
  await drawer().waitFor({ state: 'hidden' });
  assert.deepEqual(patchBodies.at(-1), {
    email: `changed-${username}@example.test`,
  });
  assert.equal(new URL(page.url()).search, search);
  await page.waitForFunction(
    () => document.activeElement?.textContent?.trim() === '编辑'
  );
  const afterEmailPatch = patchBodies.length;
  await edit().click();
  assert.equal(
    await save().isDisabled(),
    true,
    'unchanged form must not submit'
  );
  await drawer().getByRole('button', { name: '取消' }).click();
  await drawer().waitFor({ state: 'hidden' });
  assert.equal(
    patchBodies.length,
    afterEmailPatch,
    'unchanged form must not PATCH'
  );
  let response = await page.request.get(customerUrl, { headers: headers() });
  assert.equal(response.status(), 200);
  assert.equal(
    (await response.json()).data.email,
    `changed-${username}@example.test`
  );

  await page.getByRole('button', { name: '返回列表' }).click();
  stage = 'list row';
  await page.getByRole('button', { name: username, exact: true }).waitFor();
  stage = 'list open';
  await page
    .getByRole('row')
    .filter({ has: page.getByRole('button', { name: username, exact: true }) })
    .getByRole('button', { name: '编辑', exact: true })
    .click();
  stage = 'list fill';
  await field('名称').fill('Edited from list');
  stage = 'list save';
  await save().click();
  stage = 'list close';
  await drawer().waitFor({ state: 'hidden' });
  stage = 'list payload';
  assert.deepEqual(patchBodies.at(-1), { displayName: 'Edited from list' });
  const editedFromList = await page.request.get(customerUrl, { headers: headers() });
  assert.equal(editedFromList.status(), 200);
  assert.equal((await editedFromList.json()).data.displayName, 'Edited from list');
  assert.equal(
    new URL(page.url()).search,
    search,
    'list filter and page must survive editing'
  );
  stage = 'list detail';
  await page.getByRole('button', { name: username, exact: true }).click();
  stage = 'list detail content';
  await page.getByText('Edited from list').waitFor();

  stage = 'missing target edit';
  await edit().click();
  await field('名称').fill('Missing target draft');
  stage = 'missing target route';
  await page.route(
    customerUrl,
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
  stage = 'missing target save';
  await save().click();
  stage = 'missing target alert';
  await drawer()
    .getByText('此客户已不存在。草稿会保留至您放弃本次编辑。')
    .waitFor();
  assert.equal(await field('名称').inputValue(), 'Missing target draft');
  await drawer().getByRole('button', { name: '取消' }).click();
  await page.getByRole('button', { name: '放弃', exact: true }).click();
  await drawer().waitFor({ state: 'hidden' });

  stage = 'recovery edit';
  await edit().click();
  await field('名称').fill('Recovered edit');
  const beforeUnauthorized = patchBodies.length;
  let unauthorizedPatches = 0;
  await page.route(
    customerUrl,
    (route) => {
      if (route.request().method() !== 'PATCH') return route.continue();
      unauthorizedPatches++;
      return route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: failureBody(401),
      });
    },
    { times: 1 }
  );
  const authorize = page.waitForRequest(
    (request) => new URL(request.url()).pathname === '/oauth2/authorize'
  );
  stage = 'recovery save';
  await save().click();
  stage = 'recovery authorize';
  await authorize;
  stage = 'recovery callback';
  await page.waitForURL(`${base}/customer/detail/${username}${search}`, {
    waitUntil: 'domcontentloaded',
  });
  stage = 'recovery draft';
  await field('名称').waitFor();
  assert.equal(await field('名称').inputValue(), 'Recovered edit');
  assert.equal(unauthorizedPatches, 1, 'PATCH must not replay after sign in');
  assert.equal(
    patchBodies.length,
    beforeUnauthorized + 1,
    'recovery must not send another PATCH'
  );
  assert.deepEqual(patchBodies.at(-1), { displayName: 'Recovered edit' });
  await save().click();
  stage = 'recovery final save';
  await drawer().waitFor({ state: 'hidden' });
  response = await page.request.get(customerUrl, { headers: headers() });
  assert.equal((await response.json()).data.displayName, 'Recovered edit');

  stage = 'deactivate';
  await page.getByRole('button', { name: '停用', exact: true }).click();
  await page.getByRole('button', { name: '停用', exact: true }).last().click();
  await page.getByRole('button', { name: '启用', exact: true }).waitFor();
  assert.deepEqual(patchBodies.at(-1), { active: false });
  response = await page.request.get(customerUrl, { headers: headers() });
  assert.equal((await response.json()).data.active, false);
  stage = 'activate';
  await page.getByRole('button', { name: '启用', exact: true }).click();
  await page.getByRole('button', { name: '启用', exact: true }).last().click();
  await page.getByRole('button', { name: '停用', exact: true }).waitFor();
  assert.deepEqual(patchBodies.at(-1), { active: true });

  stage = 'narrow edit';
  await page.setViewportSize({ width: 390, height: 844 });
  await edit().click();
  await page.waitForTimeout(550);
  const emailBox = await field('邮箱').boundingBox();
  assert.ok(
    emailBox && emailBox.x >= 0 && emailBox.x + emailBox.width <= 390,
    'edit field must remain visible on a narrow screen'
  );
  if (process.env.SUMMER_E2E_SCREENSHOTS === '1')
    await page.screenshot({
      path: '../.e2e89/edit-narrow.png',
      fullPage: true,
    });
  assert.equal(
    await page.evaluate(
      () =>
        document.documentElement.scrollWidth <=
        document.documentElement.clientWidth
    ),
    true
  );
  await field('名称').fill('Deleted while editing');
  const deletion = await page.request.delete(customerUrl, {
    headers: headers(),
  });
  assert.equal(deletion.status(), 204);
  created = false;
  await save().click();
  await drawer()
    .getByText('此客户已不存在。草稿会保留至您放弃本次编辑。')
    .waitFor();
  assert.equal(await field('名称').inputValue(), 'Deleted while editing');
  await drawer().getByRole('button', { name: '取消' }).click();
  await page.getByRole('button', { name: '放弃', exact: true }).click();
  await drawer().waitFor({ state: 'hidden' });
  assert.equal(
    (await page.request.get(customerUrl, { headers: headers() })).status(),
    404
  );
  assert.deepEqual(unexpected, []);
  console.log(
    'Customer edit browser acceptance passed: changed PATCH, dirty draft, 404, 401 recovery, active, deletion and narrow view'
  );
} catch (error) {
  console.error(
    `Customer edit browser acceptance failed at ${stage}: ${
      error?.name || 'Error'
    }`
  );
  process.exitCode = 1;
} finally {
  if (created && bearer) {
    const response = await page.request.delete(customerUrl, {
      headers: headers(),
    });
    if (![204, 404].includes(response.status())) process.exitCode = 1;
  }
  await browser.close();
}
