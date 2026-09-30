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

async function navigate(page, path) {
  await page.evaluate((nextPath) => {
    window.history.pushState(null, '', nextPath);
    window.dispatchEvent(new PopStateEvent('popstate'));
  }, path);
}

try {
  const page = await browser.newPage();
  const unexpected = [];
  page.on('pageerror', (error) => unexpected.push(error.message));
  await page.goto(`${base}/customer/list?source=direct`);
  await page
    .locator('input[autocomplete="username"]')
    .fill(process.env.SUMMER_E2E_USERNAME || 'summer-user');
  await page.locator('input[autocomplete="current-password"]').fill(password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await page.waitForURL(`${base}/customer/drawer?source=direct`);
  await page.getByRole('button', { name: 'summer.demo', exact: true }).waitFor();

  // Keep the real backend and authenticated session. Only the in-memory
  // token scope view changes so the browser can exercise each UI gate.
  await page.evaluate(async () => {
    const { oidcManager } = await import('/src/auth/oidc.ts');
    const original = oidcManager.getUser.bind(oidcManager);
    window.__summerScope = ['openid', 'profile', 'customer.write'];
    oidcManager.getUser = async () => {
      const user = await original();
      return user && { ...user, scopes: window.__summerScope };
    };
  });

  let reads = 0;
  let authorizationRequests = 0;
  page.on('request', (request) => {
    const path = new URL(request.url()).pathname;
    if (request.method() === 'GET' && path.startsWith('/api/customers')) reads++;
    if (path === '/oauth2/authorize') authorizationRequests++;
  });

  for (const path of [
    '/customer/drawer',
    '/customer/modal',
    '/customer/page',
    '/customer/detail/summer.demo',
    '/customer/page/edit/summer.demo',
    '/customer/page/create',
  ]) {
    await navigate(page, '/welcome');
    await navigate(page, path);
    await page.getByText('无权查看客户。').waitFor();
    assert.equal(await page.locator('.arco-avatar').count(), 1);
    assert.equal(reads, 0, `${path} must not send a Customer read`);
    assert.equal(authorizationRequests, 0, `${path} must keep authentication`);
  }

  await navigate(page, '/welcome');
  await navigate(page, '/customer/list?page=2&sort=email#list');
  await page.waitForURL(`${base}/customer/drawer?page=2&sort=email#list`);
  await page.getByText('无权查看客户。').waitFor();
  await page.goBack();
  await page.waitForURL(`${base}/welcome`);
  assert.equal(reads, 0, 'alias must also respect the read gate');

  await page.evaluate(() => {
    window.__summerScope = ['openid', 'profile', 'customer.read'];
  });
  await navigate(page, '/customer/drawer');
  await page.getByRole('button', { name: 'summer.demo', exact: true }).waitFor();
  assert.equal(await page.getByRole('button', { name: '新增客户' }).count(), 0);
  assert.ok(reads > 0, 'read-only token must load the real Customer list');
  assert.deepEqual(unexpected, []);
  console.log('Customer scope and alias browser acceptance passed');
} finally {
  await browser.close();
}
