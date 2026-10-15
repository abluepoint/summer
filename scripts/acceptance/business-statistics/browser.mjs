import { createRequire } from 'node:module';
import { writeFile, access } from 'node:fs/promises';
import { resolve } from 'node:path';
const require = createRequire(process.cwd() + '/package.json');
const { chromium } = require('playwright-core');
const base = 'http://127.0.0.1:20573', api = '/api/v1/enterprises/ent-acme/statistics', out = process.env.SUMMER_T34_EVIDENCE_DIR;
const result = { checks: [], networkMocking: false, pageErrors: 0, complete: false, responses: [] }; let browser, page;
const check = (name, passed) => { result.checks.push({ name, passed: Boolean(passed) }); console.log(name, passed ? 'PASS' : 'FAIL'); };
async function fresh() { await page?.close(); page = await browser.newPage({ viewport: { width: 1440, height: 1280 } }); page.setDefaultTimeout(30000); page.on('pageerror', () => result.pageErrors++); page.on('response', r => { const path = new URL(r.url()).pathname; if (path === api || path === '/oauth2/token' || path === '/userinfo' || path.includes('menu-manifest')) result.responses.push({ path, status: r.status(), method: r.request().method() }); }); }
async function login(username, password) { await page.goto(base + '/statistics'); await page.locator('input[autocomplete="username"]').fill(username); await page.locator('input[autocomplete="current-password"]').fill(password); await page.getByRole('button', { name: '登录', exact: true }).click(); await page.waitForURL(base + '/statistics'); await page.locator('[aria-label="活动租户"]').click(); await page.getByRole('option', { name: 'ent-acme', exact: true }).click(); }
function response() { const pending = page.waitForResponse(r => r.request().method() === 'GET' && new URL(r.url()).pathname === api); pending.catch(() => {}); return pending; }
async function command(name) {
  await writeFile(resolve(out, name + '-request'), 'request');
  const deadline = Date.now() + 60000;
  while (Date.now() < deadline) { try { await access(resolve(out, name + '-ready')); return; } catch { await new Promise(r => setTimeout(r, 100)); } }
  throw new Error('fixture_handshake_timeout');
}
try {
  browser = await chromium.launch({ headless: true, executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' }); await fresh();
  let pending = response(); await login(process.env.SUMMER_E2E_USERNAME, process.env.SUMMER_E2E_PASSWORD); let reply = await pending; const data = (await reply.json()).data;
  if (!process.env.SUMMER_T34_INITIAL_FAULT) {
  await page.getByRole('button', { name: '刷新', exact: true }).waitFor(); const text = await page.locator('body').innerText();
  check('chrome_real_statistics_pkce_page', reply.status() === 200 && text.includes('业务统计'));
  check('chrome_persistent_task_success_rate_visible', text.includes('任务成功率') && text.includes((data.taskSuccessRate * 100).toFixed(1) + '%'));
  check('chrome_statistics_window_visible', text.includes(data.window.from) && text.includes(data.window.to));
  check('chrome_task_rate_and_inventory_basis_explained', /成功执行数.*全部执行数/.test(text) && text.includes('库存快照时间'));
  await page.screenshot({ path: resolve(out, 'statistics-normal.png'), fullPage: true, animations: 'disabled', timeout: 45000 });
  }
  if (!process.env.SUMMER_T34_INITIAL_FAULT) { await command('fault'); pending = response(); await page.getByRole('button', { name: '刷新', exact: true }).click(); reply = await pending; }
  result.queryFaultStatus = reply.status();
  const failed = await reply.json(); await page.getByText('统计数据加载失败。', { exact: true }).waitFor();
  check('chrome_actual_query_failure_distinct_from_empty', reply.status() === 500 && !(await page.getByText('当前范围和时间窗口没有数据。', { exact: true }).count()));
  check('chrome_failed_request_correlation_visible', Boolean(failed.requestId) && (await page.locator('body').innerText()).includes(failed.requestId));
  check('chrome_query_failure_clears_previous_cards', !(await page.locator('.arco-statistic').count()));
  await page.screenshot({ path: resolve(out, 'statistics-query-failure.png'), fullPage: true, animations: 'disabled', timeout: 45000 });
  await command('recover'); pending = response(); await page.getByRole('button', { name: '重试', exact: true }).click(); reply = await pending;
  await page.getByRole('button', { name: '刷新', exact: true }).waitFor(); check('chrome_retry_recovers_real_statistics', reply.status() === 200 && !(await page.getByText('统计数据加载失败。', { exact: true }).count()));
  await page.screenshot({ path: resolve(out, 'statistics-recovered.png'), fullPage: true, animations: 'disabled', timeout: 45000 });
  await fresh(); pending = response(); await login(process.env.SUMMER_E2E_READER, process.env.SUMMER_E2E_READER_PASSWORD); reply = await pending; const empty = (await reply.json()).data;
  await page.getByText('当前范围和时间窗口没有数据。', { exact: true }).waitFor();
  check('chrome_real_empty_scope_does_not_hide_interface_inventory', reply.status() === 200 && empty.dictionaryCount === 0 && empty.accessibleInterfaceCount > 0);
  check('chrome_denied_metrics_are_not_fabricated_zero', empty.roleCount === null && (await page.getByText('无权读取', { exact: true }).count()) > 0);
  await page.screenshot({ path: resolve(out, 'statistics-empty.png'), fullPage: true, animations: 'disabled', timeout: 45000 });
  await command('deny'); pending = response(); await page.getByRole('button', { name: '刷新', exact: true }).click(); reply = await pending;
  check('chrome_old_jwt_statistics_revocation_returns_forbidden', reply.status() === 403);
  await page.getByText('没有查看统计数据的权限。', { exact: true }).first().waitFor();
  check('chrome_forbidden_clears_empty_and_previous_cards', !(await page.locator('.arco-statistic').count()) && !(await page.getByText('当前范围和时间窗口没有数据。', { exact: true }).count()));
  await page.screenshot({ path: resolve(out, 'statistics-reader-denied.png'), fullPage: true, animations: 'disabled', timeout: 45000 });
  check('chrome_no_unhandled_page_errors', result.pageErrors === 0); result.complete = true;
} catch (error) { result.failureType = error.name; result.localFrames = error.stack?.match(/browser\.mjs:\d+:\d+/g) || []; if (page) { result.failurePath = new URL(page.url()).pathname; result.failureState = { tenantSelector: await page.locator('[aria-label="活动租户"]').count(), routeDenied: await page.getByText('对不起，您没有访问该资源的权限', { exact: true }).count(), statisticsFailure: await page.getByText('统计数据加载失败。', { exact: true }).count() }; if (result.failurePath === '/statistics' && !(await page.locator('input[autocomplete="current-password"]').count())) await page.screenshot({ path: resolve(out, 'statistics-incomplete.png'), fullPage: true, animations: 'disabled', timeout: 10000 }).catch(() => {}); } }
finally { await writeFile(resolve(out, 'browser-result.json'), JSON.stringify(result, null, 2) + '\n'); await browser?.close(); result.browserClosed = true; await writeFile(resolve(out, 'browser-result.json'), JSON.stringify(result, null, 2) + '\n'); }
process.exitCode = result.complete && result.checks.every(c => c.passed) ? 0 : 1;
