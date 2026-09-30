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
    await page.getByText('Customer 工作区').waitFor();
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
    await page.locator('.arco-avatar').click();
    await page
      .getByText(process.env.SUMMER_E2E_USERNAME || 'summer-user', {
        exact: true,
      })
      .waitFor();
    await page.getByText('退出登录', { exact: true }).click();
    await page.waitForURL(`${base}/login?logout`, { timeout: 20000 });
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
    'Browser OIDC PKCE success and state, nonce, verifier rejection passed'
  );
} finally {
  await browser.close();
}
