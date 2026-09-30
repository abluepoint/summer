import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { chromium } from 'playwright-core';

const passwordFile = process.env.SUMMER_E2E_PASSWORD_FILE;
if (!passwordFile) throw new Error('Set SUMMER_E2E_PASSWORD_FILE');
const password = (await readFile(passwordFile, 'utf8')).trim();
const origin = process.env.SUMMER_E2E_ORIGIN || 'http://127.0.0.1:5193';
const browser = await chromium.launch({
  headless: true,
  executablePath:
    process.env.CHROME_PATH ||
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
});
const page = await browser.newPage({ viewport: { width: 1365, height: 800 } });
const consoleErrors = [];
const consoleSummaries = [];
const consoleStacks = [];
const failedRequests = [];
const crossOriginBusiness = [];
let stage = 'login';
let sessionCookieAfterLogin = false;
page.on('console', (message) => {
  if (message.type() === 'error') {
    consoleErrors.push(message.location().url);
    consoleSummaries.push(message.text().split('\n')[0].replace(/https?:\/\/\S+/g, '[url]').slice(0, 140));
    consoleStacks.push(message.text().split('\n').slice(1, 6).map((line) => line.match(/\bat ([A-Za-z][A-Za-z0-9_]*)/)?.[1]).filter(Boolean).join(' > '));
  }
});
page.on('pageerror', (error) => consoleErrors.push(error.name));
page.on('requestfailed', (request) =>
  failedRequests.push(new URL(request.url()).pathname)
);
page.on('request', (request) => {
  const url = new URL(request.url());
  if (
    (/^\/api\/customers(?:\/|$)/.test(url.pathname) ||
      /^\/(?:oauth2|connect|csrf)/.test(url.pathname)) &&
    url.origin !== origin
  ) crossOriginBusiness.push(url.pathname);
});

try {
  await page.goto(`${origin}/customer/drawer`);
  await page.locator('input[autocomplete="username"]').fill('summer-user');
  await page.locator('input[autocomplete="current-password"]').fill(password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await page.waitForURL(`${origin}/customer/drawer`, { timeout: 20000 });
  await page.getByText('客户列表').waitFor();
  const sessionCookie = (await page.context().cookies(origin)).find((cookie) => cookie.name === 'JSESSIONID');
  sessionCookieAfterLogin = Boolean(sessionCookie);
  assert.equal(sessionCookie?.httpOnly, true);
  assert.equal(sessionCookie?.sameSite, 'Lax');

  async function inspect(route, heading) {
    stage = `desktop ${route}`;
    await page.waitForURL(`${origin}${route}`, { timeout: 20000 });
    await page.getByText(heading).waitFor();
    assert.equal(
      await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
      true,
      route
    );
    stage = `narrow ${route}`;
    await page.setViewportSize({ width: 390, height: 844 });
    assert.equal(
      await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
      true,
      route
    );
    await page.setViewportSize({ width: 1365, height: 800 });
  }
  await inspect('/customer/drawer', '客户列表');
  await page.getByText(/操作效率、列表上下文保留、表单容量/).waitFor();
  await page.getByRole('button', { name: '弹窗模式' }).waitFor();
  await page.getByRole('button', { name: '页面模式' }).waitFor();
  await page.locator('.arco-menu').getByText('弹窗模式', { exact: true }).click();
  await inspect('/customer/modal', '客户列表');
  await page.locator('.arco-menu').getByText('页面模式', { exact: true }).click();
  await inspect('/customer/page', '客户列表');
  stage = 'canonical detail navigation';
  await page.getByRole('button', { name: 'summer.demo', exact: true }).click();
  await inspect('/customer/detail/summer.demo', '客户详情');
  await page.getByRole('button', { name: '返回列表' }).click();
  await page.waitForURL(`${origin}/customer/page`);

  stage = 'storage';
  const storedToken = await page.evaluate(() =>
    Object.entries(sessionStorage)
      .concat(Object.entries(localStorage))
      .some(([key, value]) => /access_token|id_token|bearer|password/i.test(key) || /"(?:access_token|id_token|password)"\s*:/i.test(value))
  );
  assert.equal(storedToken, false, 'tokens and password must not persist');
  stage = 'console and network';
  assert.deepEqual(consoleErrors, [], 'no console errors or unhandled rejections');
  assert.deepEqual(failedRequests, [], 'no unexpected failed network requests');
  assert.deepEqual(crossOriginBusiness, [], 'business and OIDC traffic stays same-origin');
  console.log('Desktop/narrow Summer routes, console/network, same-origin and storage audit passed');
} catch (error) {
  console.error(`Browser quality audit failed at ${stage}: ${error?.name || 'Error'}`);
  console.error(String(error?.message || '').split('\n')[0].replace(/https?:\/\/\S+/g, '[url]'));
  console.error(`Current path: ${new URL(page.url()).pathname}`);
  console.error(`Session cookie present after form login: ${sessionCookieAfterLogin}`);
  if (stage === 'console and network') {
    console.error(`Console error sources: ${[...new Set(consoleErrors.map((source) => {
      try { return new URL(source).pathname; } catch { return source; }
    }))].join(', ')} (${consoleErrors.length} events)`);
    console.error(`Failed request paths: ${failedRequests.join(', ')}`);
    console.error(`Console summaries: ${[...new Set(consoleSummaries)].join(' | ')}`);
    console.error(`Console component stacks: ${[...new Set(consoleStacks)].join(' | ')}`);
  }
  process.exitCode = 1;
} finally {
  await browser.close();
}
