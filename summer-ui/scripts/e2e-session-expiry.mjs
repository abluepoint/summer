import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { chromium } from 'playwright-core';

const passwordFile = process.env.SUMMER_E2E_PASSWORD_FILE;
if (!passwordFile) throw new Error('Set SUMMER_E2E_PASSWORD_FILE');
const password = (await readFile(passwordFile, 'utf8')).trim();
const origin = process.env.SUMMER_E2E_ORIGIN || 'http://127.0.0.1:5193';
const timeoutSeconds = Number(process.env.SUMMER_E2E_SESSION_TIMEOUT_SECONDS || 0);
if (!Number.isInteger(timeoutSeconds) || timeoutSeconds < 5 || timeoutSeconds > 30)
  throw new Error('Run against a backend with SERVER_SERVLET_SESSION_TIMEOUT=5s..30s and set SUMMER_E2E_SESSION_TIMEOUT_SECONDS to that value');

const browser = await chromium.launch({
  headless: true,
  executablePath:
    process.env.CHROME_PATH ||
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
});
try {
  const page = await browser.newPage();
  await page.goto(`${origin}/customer/drawer`);
  await page.locator('input[autocomplete="username"]').fill('summer-user');
  await page.locator('input[autocomplete="current-password"]').fill(password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await page.waitForURL(`${origin}/customer/drawer`, { timeout: 20000 });
  await page.getByText('客户列表').waitFor();

  // Tomcat may reap idle sessions on a coarse background interval.
  await page.waitForTimeout(Math.max((timeoutSeconds + 3) * 1000, 75000));
  await page.reload();
  await page.waitForURL(/\/login(?:\?|$)/, { timeout: 20000 });
  await page.locator('input[autocomplete="current-password"]').waitFor();
  assert.equal(new URL(page.url()).origin, origin);
  await page.locator('input[autocomplete="username"]').fill('summer-user');
  await page.locator('input[autocomplete="current-password"]').fill(password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await page.waitForURL(`${origin}/customer/drawer`, { timeout: 20000 });
  await page.getByText('客户列表').waitFor();
  console.log('Expired backend session requires fresh login and restores the Customer route');
} catch (error) {
  console.error(`Session expiry browser check failed: ${error?.name || 'Error'}`);
  process.exitCode = 1;
} finally {
  await browser.close();
}
