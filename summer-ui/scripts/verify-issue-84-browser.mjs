import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { chromium } from 'playwright-core';

const origin = process.env.SUMMER_E2E_ORIGIN || 'http://127.0.0.1:5194';
const passwordFile = process.env.SUMMER_E2E_PASSWORD_FILE;
if (!passwordFile) throw new Error('Set SUMMER_E2E_PASSWORD_FILE');
const password = (await readFile(passwordFile, 'utf8')).trim();
const browser = await chromium.launch({
  headless: true,
  executablePath:
    process.env.CHROME_PATH ||
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
});

const examples = [
  ['welcome', '欢迎'],
  ['dashboard/workplace', '工作台', '仪表盘'],
  ['dashboard/monitor', '实时监控', '仪表盘'],
  ['visualization/data-analysis', '分析页', '数据可视化'],
  ['visualization/multi-dimension-data-analysis', '多维数据分析', '数据可视化'],
  ['list/search-table', '查询表格', '列表页'],
  ['list/card', '卡片列表', '列表页'],
  ['form/group', '分组表单', '表单页'],
  ['form/step', '分步表单', '表单页'],
  ['profile/basic', '基础详情页', '详情页'],
  ['result/success', '成功页', '结果页'],
  ['result/error', '失败页', '结果页'],
  ['exception/403', '403', '异常页'],
  ['exception/404', '404', '异常页'],
  ['exception/500', '500', '异常页'],
  ['user/info', '用户信息', '个人中心'],
  ['user/setting', '用户设置', '个人中心'],
];

const page = await browser.newPage({ viewport: { width: 1365, height: 800 } });
const pageErrors = [];
page.on('pageerror', (error) => pageErrors.push(error.name));
let stage = 'login';
let menuCount = 0;
let directCount = 0;
let customerCount = 0;

async function checkCustomerLocale(labels) {
  for (const [mode, menuName] of [
    ['drawer', labels.drawer],
    ['modal', labels.modal],
    ['page', labels.page],
  ]) {
    stage = `${labels.lang} Customer ${mode}`;
    if (new URL(page.url()).pathname !== `/customer/${mode}`)
      await page.locator('.arco-menu').getByText(menuName, { exact: true }).click();
    await page.waitForURL((url) => url.pathname === `/customer/${mode}`);
    await page.getByText(labels.list, { exact: true }).first().waitFor();

    await page.getByRole('button', { name: labels.newCustomer }).click();
    if (mode === 'page')
      await page.waitForURL((url) => url.pathname === '/customer/page/create');
    stage = `${labels.lang} Customer ${mode} create title`;
    await page.getByText(labels.createTitle, { exact: true }).first().waitFor();
    await page.locator('#customer-username').fill('Bad.');
    await page.getByRole('button', { name: labels.create, exact: true }).click();
    stage = `${labels.lang} Customer ${mode} username validation`;
    await page.getByText(labels.usernameValidation).waitFor();
    await page.locator('#customer-username').fill('');
    await page.getByRole('button', { name: labels.cancel, exact: true }).click();
    stage = `${labels.lang} Customer ${mode} cancel create`;
    await page.waitForURL((url) => url.pathname === `/customer/${mode}`);

    await page
      .getByRole('row')
      .filter({ has: page.getByRole('button', { name: 'summer.demo', exact: true }) })
      .getByRole('button', { name: labels.edit, exact: true })
      .click();
    stage = `${labels.lang} Customer ${mode} edit`;
    if (mode === 'page')
      await page.waitForURL((url) => url.pathname === '/customer/page/edit/summer.demo');
    await page.getByText(labels.editTitle, { exact: true }).first().waitFor();
    await page.getByRole('button', { name: labels.cancel, exact: true }).click();
    await page.waitForURL((url) => url.pathname === `/customer/${mode}`);

    await page.getByRole('button', { name: 'summer.demo', exact: true }).click();
    stage = `${labels.lang} Customer ${mode} detail`;
    await page.waitForURL((url) => url.pathname === '/customer/detail/summer.demo');
    await page.getByText(labels.detailTitle, { exact: true }).first().waitFor();
    await page.getByRole('button', { name: labels.back }).click();
    await page.waitForURL((url) => url.pathname === `/customer/${mode}`);
    customerCount++;
  }
}

async function loginIfNeeded(path) {
  await page.goto(`${origin}/${path}`);
  await page.waitForFunction(
    () =>
      Boolean(
        document.querySelector('input[autocomplete="username"]') ||
          document.querySelector('.arco-layout-content')
      ),
    { timeout: 25000 }
  );
  if (await page.locator('input[autocomplete="username"]').isVisible()) {
    await page.locator('input[autocomplete="username"]').fill('summer-user');
    await page.locator('input[autocomplete="current-password"]').fill(password);
    await page
      .getByRole('button', { name: /^(登录|login)$/i })
      .click();
  }
  await page.waitForURL(`${origin}/${path}`, { timeout: 25000 });
  await page.locator('.arco-layout-content').waitFor();
}

try {
  await page.goto(`${origin}/customer/drawer`);
  await page.getByText('登录 Summer UI').waitFor();
  await page.locator('input[autocomplete="username"]').fill('summer-user');
  await page.locator('input[autocomplete="current-password"]').fill(password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await page.waitForURL(`${origin}/customer/drawer`, { timeout: 25000 });
  await page.getByText('客户列表').waitFor();

  stage = 'language switch and Customer pages';
  await checkCustomerLocale({
    lang: 'zh-CN', drawer: '抽屉模式', modal: '弹窗模式', page: '页面模式',
    list: '客户列表', newCustomer: '新增客户', createTitle: '新增客户',
    create: '创建', cancel: '取消', detailTitle: '客户详情', edit: '编辑',
    editTitle: '编辑客户', back: '返回列表',
    usernameValidation: /首尾须为字母或数字/,
  });
  await page.locator('.arco-icon-language').hover();
  await page.getByText('English', { exact: true }).click();
  await page.getByText('Customers', { exact: true }).first().waitFor();
  assert.equal(await page.evaluate(() => localStorage.getItem('arco-lang')), 'en-US');
  await checkCustomerLocale({
    lang: 'en-US', drawer: 'Drawer mode', modal: 'Modal mode', page: 'Page mode',
    list: 'Customers', newCustomer: 'New Customer', createTitle: 'Create Customer',
    create: 'Create', cancel: 'Cancel', detailTitle: 'Customer detail', edit: 'Edit',
    editTitle: 'Edit Customer', back: 'Back to list',
    usernameValidation: /start and end with a letter or digit/,
  });

  stage = 'official example menu routes';
  await page.locator('.arco-menu').getByText('Arco Pro Examples', { exact: true }).click();
  await page.locator('.arco-icon-language').hover();
  await page.getByText('中文', { exact: true }).click();
  for (const [path, label, parent] of examples) {
    const menu = page.locator('.arco-menu');
    if (parent && !(await menu.getByText(label, { exact: true }).isVisible()))
      await menu.getByText(parent, { exact: true }).click();
    await menu.getByText(label, { exact: true }).click();
    await page.waitForURL(`${origin}/${path}`);
    await page.locator('.arco-layout-content').waitFor();
    assert((await page.locator('.arco-layout-content').innerText()).trim().length > 2, path);
    menuCount++;
  }

  stage = 'official example direct routes';
  for (const [path] of examples) {
    stage = `direct /${path}`;
    await loginIfNeeded(path);
    assert((await page.locator('.arco-layout-content').innerText()).trim().length > 2, path);
    directCount++;
  }

  stage = 'English login after language switch';
  await page.locator('.arco-icon-language').hover();
  await page.getByText('English', { exact: true }).click();
  await page.goto(`${origin}/login`);
  await page.getByText('Login to Summer UI').waitFor();
  assert.equal(await page.evaluate(() => localStorage.getItem('arco-lang')), 'en-US');
  assert.deepEqual(pageErrors, []);
  console.log(`Issue #84 browser acceptance passed: ${menuCount} menu and ${directCount} direct official routes; ${customerCount} Customer mode/language flows and zh-CN/en-US login`);
} catch (error) {
  console.error(`Issue #84 browser acceptance failed at ${stage}: ${error?.name || 'Error'}`);
  console.error(`Current path: ${new URL(page.url()).pathname}`);
  process.exitCode = 1;
} finally {
  await browser.close();
}
