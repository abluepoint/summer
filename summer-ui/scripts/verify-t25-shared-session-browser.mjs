import assert from 'node:assert/strict';
import http from 'node:http';
import { chromium } from 'playwright-core';
import { createServer } from 'vite';

// Requires two real Sample nodes with the same isolated JDBC/Redis stores and issuer.
// Credentials enter only through the launcher environment; no fixtures or routed responses.
const origin = 'http://127.0.0.1:5173';
const username = process.env.SUMMER_E2E_USERNAME;
const password = process.env.SUMMER_E2E_PASSWORD;
if (!username || !password) throw new Error('Missing browser test credentials');
let backend = 18081;
let stage = 'start';
let browser, vite;
const checks = [];
const plannedChecks = 7;
const proxy = http.createServer((request, response) => {
  const upstream = http.request({ hostname: '127.0.0.1', port: backend, path: request.url,
    method: request.method, headers: { ...request.headers, host: '127.0.0.1:5173',
      'x-forwarded-host': '127.0.0.1:5173', 'x-forwarded-proto': 'http', 'x-forwarded-port': '5173' } },
    (incoming) => { response.writeHead(incoming.statusCode, incoming.headers); incoming.pipe(response); });
  upstream.on('error', () => { response.writeHead(502); response.end(); });
  request.pipe(upstream);
});
async function check(name, work) {
  stage = name;
  try { await work(); checks.push({ name, passed: true }); }
  catch (error) { checks.push({ name, passed: false, error: error.name }); throw error; }
}
async function control(action) {
  if (process.env.SUMMER_E2E_NODE_CONTROL !== 'stdio') throw new Error('Node controller is required');
  const reply = new Promise(resolve => process.stdin.once('data', data => resolve(data.toString().trim())));
  console.log(`T25_CONTROL ${action}`);
  assert.equal(await reply, 'OK');
}
async function login(page) {
  await page.goto(`${origin}/dashboard/workplace`);
  await page.locator('input[autocomplete="username"]').waitFor({ timeout: 30000 });
  await page.locator('input[autocomplete="username"]').fill(username);
  await page.locator('input[autocomplete="current-password"]').fill(password);
  await page.getByRole('button', { name: /^(登录|login)$/i }).click();
  await page.waitForURL(url => url.pathname === '/dashboard/workplace', { timeout: 30000 });
  await page.locator('.arco-layout-content').waitFor({ timeout: 30000 });
  assert.equal(await page.evaluate(async () => Boolean(await (await import('/src/auth/oidc.ts')).getAccessTokenFromMemory())), true);
}
try {
  await new Promise(resolve => proxy.listen(18580, '127.0.0.1', resolve));
  process.env.SUMMER_BACKEND_ORIGIN = 'http://127.0.0.1:18580';
  process.env.SUMMER_UI_PORT = '5173';
  process.env.VITE_ENABLE_MOCK = 'false';
  vite = await createServer({ logLevel: 'silent', server: { strictPort: true } });
  await vite.listen();
  browser = await chromium.launch({ headless: true,
    executablePath: process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' });
  const context = await browser.newContext();
  const page = await context.newPage();
  page.setDefaultTimeout(15000);
  await check('real_pkce_login_node_a', () => login(page));
  backend = 18082;
  await check('same_cookie_node_b', async () => {
    assert.equal(await page.evaluate(async () => (await fetch('/api/v1/session')).status), 200);
  });
  await check('node_a_stopped_cookie_survives_on_b', async () => {
    await control('stop_a');
    assert.equal(await page.evaluate(async () => (await fetch('/api/v1/session')).status), 200);
  });
  await check('node_a_restarted_cookie_survives', async () => {
    await control('restart_a');
    backend = 18081;
    assert.equal(await page.evaluate(async () => (await fetch('/api/v1/session')).status), 200);
    backend = 18082;
  });
  await check('cookie_list_and_csrf_revoke_node_b', async () => {
    const result = await page.evaluate(async () => {
      const listed = await fetch('/api/v1/sessions');
      if (listed.status !== 200) return { listed: listed.status };
      const body = await listed.json();
      const current = body.data.items.find(item => item.current && item.status === 'ACTIVE');
      if (!current) return { listed: 200, current: false };
      const missingCsrf = await fetch(`/api/v1/sessions/${current.sessionRef}`, { method: 'DELETE' });
      const csrf = await (await fetch('/csrf')).json();
      const revoked = await fetch(`/api/v1/sessions/${current.sessionRef}`, {
        method: 'DELETE', headers: { [csrf.headerName]: csrf.token } });
      const cookie = await fetch('/api/v1/session');
      const token = await (await import('/src/auth/oidc.ts')).getAccessTokenFromMemory();
      const bearer = await fetch('/api/v1/session', { headers: { Authorization: `Bearer ${token}` } });
      return { listed: listed.status, current: true, missingCsrf: missingCsrf.status,
        revoked: revoked.status, cookie: cookie.status, bearer: bearer.status };
    });
    assert.deepEqual(result, { listed: 200, current: true, missingCsrf: 403, revoked: 200, cookie: 401, bearer: 200 });
  });
  await context.close();
  const logoutContext = await browser.newContext();
  const logoutPage = await logoutContext.newPage();
  await check('fresh_login_node_b', () => login(logoutPage));
  backend = 18081;
  await check('oidc_logout_other_node', async () => {
    await logoutPage.evaluate(() => { import('/src/auth/oidc.ts').then(module => module.signOut()); });
    await logoutPage.waitForURL(url => url.pathname === '/login' && url.searchParams.has('logout'), { timeout: 30000 });
    assert.equal(await logoutPage.evaluate(async () => (await fetch('/api/v1/session')).status), 401);
  });
  await logoutContext.close();
} catch (error) {
  if (!checks.some(check => check.name === stage && !check.passed)) checks.push({ name: stage, passed: false, error: error.name });
  process.exitCode = 1;
} finally {
  await browser?.close();
  await vite?.close();
  proxy.closeAllConnections();
  await new Promise(resolve => proxy.close(resolve));
  process.stdin.pause();
  console.log('T25_RESULT ' + JSON.stringify({ checks, tests: plannedChecks, attempted: checks.length,
    failures: checks.filter(check => !check.passed).length, errors: 0, skipped: Math.max(0, plannedChecks - checks.length) }));
}
