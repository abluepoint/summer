import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { chromium } from 'playwright-core';

const origin = process.env.SUMMER_E2E_HTTPS_ORIGIN;
const passwordFile = process.env.SUMMER_E2E_PASSWORD_FILE;
if (!origin?.startsWith('https://') || !passwordFile)
  throw new Error('Set SUMMER_E2E_HTTPS_ORIGIN and SUMMER_E2E_PASSWORD_FILE');
const password = (await readFile(passwordFile, 'utf8')).trim();
const browser = await chromium.launch({
  headless: true,
  executablePath:
    process.env.CHROME_PATH ||
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
});
const context = await browser.newContext({ ignoreHTTPSErrors: true });
const page = await context.newPage();
const crossOrigin = [];
const redirectOrigins = [];
const failures = [];
const incorrectClientUris = [];
const responseStatus = [];
let callbackCount = 0;
let logoutCount = 0;
let stage = 'discovery';

page.on('request', (request) => {
  const url = new URL(request.url());
  if (
    /^\/(?:api|oauth2|connect|csrf|\.well-known|login)(?:\/|$)/.test(
      url.pathname
    )
  ) {
    if (url.origin !== origin) crossOrigin.push(url.pathname);
    if (url.pathname === '/oauth2/authorize')
      if (url.searchParams.get('redirect_uri') !== `${origin}/oauth2/callback`)
        incorrectClientUris.push('callback');
    if (url.pathname === '/oauth2/callback') callbackCount++;
    if (url.pathname === '/connect/logout') {
      logoutCount++;
      if (
        url.searchParams.get('post_logout_redirect_uri') !==
        `${origin}/login?logout`
      )
        incorrectClientUris.push('logout');
    }
  }
});
page.on('response', (response) => {
  const path = new URL(response.url()).pathname;
  if (/^\/(?:login|oauth2|connect|csrf|\.well-known)(?:\/|$)/.test(path))
    responseStatus.push(`${path}:${response.status()}`);
  const location = response.headers().location;
  if (location) {
    const target = new URL(location, origin);
    if (target.origin !== origin) redirectOrigins.push(target.pathname);
  }
});
page.on('requestfailed', (request) =>
  failures.push(new URL(request.url()).pathname)
);

try {
  const response = await context.request.get(
    `${origin}/.well-known/openid-configuration`
  );
  assert.equal(response.status(), 200);
  const discovery = await response.json();
  for (const key of [
    'issuer',
    'authorization_endpoint',
    'token_endpoint',
    'jwks_uri',
    'end_session_endpoint',
  ])
    assert.equal(new URL(discovery[key]).origin, origin, key);
  assert.equal(discovery.issuer, origin);
  const metadataUrls = Object.values(discovery)
    .flatMap((value) => (Array.isArray(value) ? value : [value]))
    .filter((value) => typeof value === 'string' && /^https?:\/\//.test(value));
  assert.ok(metadataUrls.length >= 5);
  for (const url of metadataUrls)
    assert.equal(new URL(url).origin, origin, 'discovery URL origin');

  stage = 'login and callback';
  await page.goto(`${origin}/customer/drawer`);
  await page
    .locator('input[autocomplete="username"]')
    .fill(process.env.SUMMER_E2E_USERNAME || 'summer-user');
  await page.locator('input[autocomplete="current-password"]').fill(password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await page.waitForURL(`${origin}/customer/drawer`, { timeout: 20000 });
  await page.getByText('客户列表').waitFor();
  assert.equal(callbackCount, 1);
  const session = (await context.cookies(origin)).find(
    (cookie) => cookie.name === 'JSESSIONID'
  );
  assert.equal(session?.secure, true);
  assert.equal(session?.httpOnly, true);

  stage = 'logout';
  await page.locator('.arco-avatar').click();
  await page.getByText('退出登录', { exact: true }).click();
  await page.waitForURL(`${origin}/login?logout`, { timeout: 20000 });
  assert.equal(logoutCount, 1);
  assert.deepEqual(crossOrigin, []);
  assert.deepEqual(redirectOrigins, []);
  assert.deepEqual(incorrectClientUris, []);
  assert.deepEqual(failures, []);
  console.log(
    'HTTPS discovery, callback, logout, Secure cookie and browser origin passed'
  );
} catch (error) {
  console.error(
    `Public issuer browser acceptance failed at ${stage}: ${
      error?.name || 'Error'
    }`
  );
  console.error(`Current path: ${new URL(page.url()).pathname}`);
  console.error(`Protocol status: ${responseStatus.join(', ')}`);
  console.error(`Failed paths: ${failures.join(', ')}`);
  process.exitCode = 1;
} finally {
  await context.close();
  await browser.close();
}
