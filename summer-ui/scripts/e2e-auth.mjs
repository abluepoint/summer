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

async function atLogin(page, route) {
  await page.goto(`${base}${route}`);
  await page.waitForURL(/\/login(?:\?|$)/);
  await page.locator('input[autocomplete="username"]').waitFor();
}

async function submitLogin(page) {
  await page
    .locator('input[autocomplete="username"]')
    .fill(process.env.SUMMER_E2E_USERNAME || 'summer-user');
  await page.locator('input[autocomplete="current-password"]').fill(password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
}

try {
  {
    const context = await browser.newContext();
    try {
      const page = await context.newPage();
      await atLogin(page, '/login');
      await submitLogin(page);
      await page.waitForURL(`${base}/dashboard/workplace`, { timeout: 20000 });
      await page
        .getByText('欢迎回来，summer-user', { exact: true })
        .waitFor();
      assert.equal(
        await page.getByText('未找到有效令牌，请登录后继续。', { exact: true }).count(),
        0,
        'initial route login must not show the missing-token notice after success'
      );
    } finally {
      await context.close();
    }
  }
  {
    const firstContext = await browser.newContext();
    const secondContext = await browser.newContext();
    try {
      const first = await firstContext.newPage();
      const second = await secondContext.newPage();
      for (const page of [first, second]) {
        await atLogin(page, '/customer/drawer');
        await submitLogin(page);
        await page.waitForURL(`${base}/customer/drawer`, { timeout: 20000 });
        await page.getByText('客户列表').waitFor();
      }
      const firstSession = (await firstContext.cookies()).find(
        (cookie) => cookie.name === 'JSESSIONID'
      );
      const secondSession = (await secondContext.cookies()).find(
        (cookie) => cookie.name === 'JSESSIONID'
      );
      assert.ok(firstSession && secondSession);
      assert.equal(
        firstSession.value === secondSession.value,
        false,
        'browser contexts must have separate login sessions'
      );
      for (const page of [first, second]) {
        await page.reload();
        await page.waitForURL(`${base}/customer/drawer`, { timeout: 20000 });
        await page.getByText('客户列表').waitFor();
      }
    } finally {
      await firstContext.close();
      await secondContext.close();
    }
  }
  {
    const context = await browser.newContext();
    const page = await context.newPage();
    await atLogin(page, '/customer/page?from=auth-e2e');
    const stateCount = await page.evaluate(
      () =>
        Object.keys(sessionStorage).filter((key) => key.startsWith('oidc.'))
          .length
    );
    assert.equal(
      stateCount,
      1,
      'one PKCE transaction should exist before form login'
    );
    await submitLogin(page);
    await page.waitForURL(`${base}/customer/page?from=auth-e2e`, {
      timeout: 20000,
    });
    await page.getByText('客户列表').waitFor();
    const storage = await page.evaluate(() => ({
      session: { ...sessionStorage },
      local: { ...localStorage },
    }));
    assert.equal(
      Object.keys(storage.session).filter((key) => key.startsWith('oidc.'))
        .length,
      0
    );
    assert.equal(
      Object.values(storage.session)
        .concat(Object.values(storage.local))
        .some((value) => /access_token|id_token|code_verifier/.test(value)),
      false
    );
    assert.equal(storage.local.userStatus, undefined);
    let rpLogoutRequests = 0;
    page.on('request', (request) => {
      if (new URL(request.url()).pathname === '/connect/logout') {
        rpLogoutRequests++;
      }
    });
    await page.locator('.arco-avatar').click();
    await page
      .getByText(process.env.SUMMER_E2E_USERNAME || 'summer-user', {
        exact: true,
      })
      .waitFor();
    await page.getByText('退出登录', { exact: true }).click();
    await page.waitForURL(`${base}/login?logout`, { timeout: 20000 });
    assert.equal(
      rpLogoutRequests,
      1,
      'logout must call the OIDC end-session endpoint'
    );
    const afterLogout = await page.evaluate(() => ({ ...sessionStorage }));
    assert.equal(
      Object.keys(afterLogout).some(
        (key) =>
          key.startsWith('oidc.') ||
          key.startsWith('summer.auth.') ||
          key === 'summer.pkce.customer-draft'
      ),
      false,
      'logout must clear local transaction and recovery state'
    );
    await atLogin(page, '/customer/drawer');
    await context.close();
  }
  {
    const context = await browser.newContext();
    const page = await context.newPage();
    await atLogin(page, '/customer/drawer');
    await submitLogin(page);
    await page.waitForURL(`${base}/customer/drawer`, { timeout: 20000 });
    await page.getByText('客户列表').waitFor();
    await page.evaluate(() => {
      sessionStorage.setItem('summer.auth.notice', 'expired');
      sessionStorage.setItem('summer.pkce.customer-draft', 'temporary');
    });
    await page.evaluate(async () => {
      const { oidcManager } = await import('/src/auth/oidc.ts');
      oidcManager.signoutRedirect = async () => {
        throw new Error('simulated end-session navigation failure');
      };
    });
    await page.locator('.arco-avatar').click();
    await page.getByText('退出登录', { exact: true }).click();
    await page.waitForURL(`${base}/login?logoutError`, { timeout: 20000 });
    await page.getByText('服务器会话可能仍然有效', { exact: false }).waitFor();
    const cleared = await page.evaluate(async () => {
      const { oidcManager } = await import('/src/auth/oidc.ts');
      return {
        userMissing: (await oidcManager.getUser()) === null,
        temporaryKeys: Object.keys(sessionStorage).filter(
          (key) =>
            key.startsWith('oidc.') ||
            key.startsWith('summer.auth.') ||
            key === 'summer.pkce.customer-draft'
        ),
      };
    });
    assert.equal(cleared.userMissing, true);
    assert.deepEqual(cleared.temporaryKeys, []);
    await context.close();
  }
  {
    const context = await browser.newContext();
    const page = await context.newPage();
    await atLogin(page, '/customer/drawer');
    await page.evaluate(() => {
      for (const key of Object.keys(sessionStorage))
        if (key.startsWith('oidc.')) sessionStorage.removeItem(key);
    });
    await submitLogin(page);
    await page.waitForURL(`${base}/oauth2/callback`, { timeout: 20000 });
    await page.getByText('无法验证登录，请重新开始。').waitFor();
    await context.close();
  }
  for (const field of ['nonce', 'code_verifier']) {
    const context = await browser.newContext();
    const page = await context.newPage();
    await atLogin(page, '/customer/drawer');
    const found = await page.evaluate((name) => {
      const key = Object.keys(sessionStorage).find((item) =>
        item.startsWith('oidc.')
      );
      const transaction = JSON.parse(sessionStorage.getItem(key));
      if (!transaction[name]) return false;
      transaction[name] = 'tampered-browser-test-value';
      sessionStorage.setItem(key, JSON.stringify(transaction));
      return true;
    }, field);
    assert.equal(found, true, `the PKCE transaction must include ${field}`);
    await submitLogin(page);
    await page.waitForURL(`${base}/oauth2/callback`, { timeout: 20000 });
    await page.getByText('无法验证登录，请重新开始。').waitFor();
    await context.close();
  }
  console.log(
    'Browser OIDC PKCE, concurrent sessions, logout cleanup, and state/nonce/verifier rejection passed'
  );
} finally {
  await browser.close();
}
