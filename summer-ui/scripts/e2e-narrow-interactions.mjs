import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { chromium } from 'playwright-core';

const origin = process.env.SUMMER_E2E_ORIGIN;
const passwordFile = process.env.SUMMER_E2E_PASSWORD_FILE;
if (!origin || !passwordFile)
  throw new Error('Set SUMMER_E2E_ORIGIN and SUMMER_E2E_PASSWORD_FILE');
const password = (await readFile(passwordFile, 'utf8')).trim();
const username = `narrow93-${Date.now().toString(36)}`;
const browser = await chromium.launch({
  headless: true,
  executablePath:
    process.env.CHROME_PATH ||
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
});
const context = await browser.newContext({
  viewport: { width: 390, height: 844 },
  ignoreHTTPSErrors: true,
});
const page = await context.newPage();
page.setDefaultTimeout(20000);
const drawer = () => page.locator('.arco-drawer:visible');
const modal = () =>
  page.locator('.arco-modal:visible').filter({ has: page.locator('form') });
const field = (overlay, name) => overlay().getByRole('textbox', { name });
let bearer;
let stage = 'login';
page.on('request', (request) => {
  if (new URL(request.url()).pathname.startsWith('/api/customers'))
    bearer = request.headers().authorization || bearer;
});

async function noOverflow() {
  assert.equal(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth
    ),
    true,
    `horizontal overflow at ${stage}`
  );
}

try {
  await page.goto(`${origin}/customer/drawer`);
  await page
    .locator('input[autocomplete="username"]')
    .fill(process.env.SUMMER_E2E_USERNAME || 'summer-user');
  await page.locator('input[autocomplete="current-password"]').fill(password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await page.waitForURL(`${origin}/customer/drawer`);
  await page.getByText('客户列表').waitFor();
  await noOverflow();

  stage = 'narrow Drawer create and error focus';
  await page.getByRole('button', { name: '新增客户' }).click();
  await field(drawer, '用户名').waitFor();
  assert.equal(
    await field(drawer, '用户名').evaluate(
      (node) => node === document.activeElement
    ),
    true
  );
  await drawer().getByRole('button', { name: '创建', exact: true }).click();
  await page.waitForFunction(
    () => document.activeElement?.id === 'customer-username'
  );
  await field(drawer, '用户名').fill(username);
  await field(drawer, '邮箱').fill(`${username}@example.test`);
  await field(drawer, '名称').fill('Narrow Drawer');
  await noOverflow();
  await drawer().getByRole('button', { name: '创建', exact: true }).click();
  await drawer().waitFor({ state: 'hidden' });
  assert.ok(bearer?.startsWith('Bearer '));

  stage = 'narrow Modal edit, focus trap, Esc and overlay';
  await page.goto(`${origin}/customer/modal`);
  await page.waitForURL(`${origin}/customer/modal`);
  await page.getByPlaceholder('输入完整用户名').fill(username);
  await page.getByRole('button', { name: '查询' }).click();
  await page.waitForURL(`${origin}/customer/detail/${username}`);
  await page.getByRole('button', { name: '编辑', exact: true }).click();
  await modal().waitFor();
  await field(modal, '名称').fill('Narrow Modal');
  for (let index = 0; index < 8; index++) {
    await page.keyboard.press('Tab');
    assert.equal(
      await modal().evaluate((node) => node.contains(document.activeElement)),
      true
    );
  }
  await page.keyboard.press('Escape');
  await page.getByText('放弃未保存的修改？').waitFor();
  await page.getByRole('button', { name: '继续编辑' }).click();
  assert.equal(await field(modal, '名称').inputValue(), 'Narrow Modal');
  await page.getByText('放弃未保存的修改？').waitFor({ state: 'hidden' });
  await page.mouse.click(10, 10);
  await page.getByText('放弃未保存的修改？').waitFor();
  await page.getByRole('button', { name: '继续编辑' }).click();
  await field(modal, '邮箱').fill('invalid');
  await modal().getByRole('button', { name: '保存修改' }).click();
  await page.waitForFunction(
    () => document.activeElement?.id === 'customer-email'
  );
  await field(modal, '邮箱').fill(`${username}@example.test`);
  await noOverflow();
  await modal().getByRole('button', { name: '保存修改' }).click();
  await modal().waitFor({ state: 'hidden' });
  let response = await context.request.get(
    `${origin}/api/customers/${username}`,
    {
      headers: { Authorization: bearer },
    }
  );
  assert.equal((await response.json()).data.displayName, 'Narrow Modal');

  stage = 'narrow Page keyboard edit';
  await page.goto(`${origin}/customer/page/edit/${username}`);
  await page.waitForURL(`${origin}/customer/page/edit/${username}`);
  await page.getByRole('button', { name: '保存修改' }).waitFor();
  await noOverflow();
  await page.getByRole('textbox', { name: '名称' }).fill('Narrow Page');
  await page.getByRole('button', { name: '保存修改' }).focus();
  await page.keyboard.press('Enter');
  await page.waitForURL(`${origin}/customer/page`);
  response = await context.request.get(`${origin}/api/customers/${username}`, {
    headers: { Authorization: bearer },
  });
  assert.equal((await response.json()).data.displayName, 'Narrow Page');

  stage = 'narrow detail and deletion';
  await page.goto(`${origin}/customer/detail/${username}`);
  await page.waitForURL(`${origin}/customer/detail/${username}`);
  await page.getByText('Narrow Page').waitFor();
  await noOverflow();
  await page.getByRole('button', { name: '删除', exact: true }).click();
  const confirm = page.getByRole('dialog');
  const input = confirm.getByPlaceholder('准确的用户名');
  assert.equal(
    await input.evaluate((node) => node === document.activeElement),
    true
  );
  await input.fill(username);
  const button = confirm.getByRole('button', { name: '永久删除' });
  const bounds = await button.boundingBox();
  assert.ok(bounds && bounds.x >= 0 && bounds.x + bounds.width <= 390);
  await button.focus();
  await page.keyboard.press('Enter');
  await page.waitForURL(`${origin}/customer/drawer`);
  response = await context.request.get(`${origin}/api/customers/${username}`, {
    headers: { Authorization: bearer },
  });
  assert.equal(response.status(), 404);
  console.log(
    '390px Drawer create, Modal edit, Page edit, focus/Esc/overlay and deletion passed'
  );
} catch (error) {
  console.error(
    `Narrow browser acceptance failed at ${stage}: ${error?.name || 'Error'}`
  );
  console.error(`Current path: ${new URL(page.url()).pathname}`);
  process.exitCode = 1;
} finally {
  if (bearer) {
    const cleanup = await context.request.delete(
      `${origin}/api/customers/${username}`,
      {
        headers: { Authorization: bearer },
      }
    );
    if (![204, 404].includes(cleanup.status())) {
      console.error(`Narrow test cleanup failed: HTTP ${cleanup.status()}`);
      process.exitCode = 1;
    }
  }
  await context.close();
  await browser.close();
}
