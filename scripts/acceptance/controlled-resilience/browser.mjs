import { createRequire } from 'node:module';
import { writeFile, rename, rm } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import { createInterface } from 'node:readline';

const { chromium } = createRequire(process.cwd() + '/package.json')('playwright-core');
const out = process.env.SUMMER_RESILIENCE_BROWSER_EVIDENCE_DIR;
const base = 'http://127.0.0.1:28573';
const diag = '/internal/v1/diagnostics/resilience';
const result = { checks: [], complete: false, networkMocking: false, syntheticIdentity: false,
  pageErrors: 0, responses: [], automaticWrites: 0 };
const input = createInterface({ input: process.stdin });
const queued = []; let receive;
input.on('line', line => { if (receive) { const resolve = receive; receive = null; resolve(line); } else queued.push(line); });
async function signal(command) {
  process.stdout.write(command + '\n');
  const line = queued.length ? queued.shift() : await new Promise(resolve => { receive = resolve; });
  const response = JSON.parse(line);
  if (!response.ok) throw new Error('parent_command_failed');
  return response;
}
async function persist() {
  result.counts = { tests: result.checks.length, failures: result.checks.filter(c => !c.passed).length,
    errors: result.failureType ? 1 : 0, skipped: 0 };
  await writeFile(out + '/browser-result.tmp', JSON.stringify(result, null, 2) + '\n');
  await rename(out + '/browser-result.tmp', out + '/browser-result.json');
}
async function check(name, passed, facts = {}) {
  result.checks.push({ name, passed: Boolean(passed), ...facts }); await persist();
}
async function until(predicate, timeout = 30000) {
  const end = Date.now() + timeout;
  while (Date.now() < end) { if (await predicate()) return true; await new Promise(resolve => setTimeout(resolve, 100)); }
  return Boolean(await predicate());
}
const delay = ms => new Promise(resolve => { const timer = setTimeout(resolve, ms); timer.unref(); });
let chrome, browser, page, stage = 'launch';
const pending = new Set();
process.on('SIGTERM', () => {
  if (receive) { const resolve = receive; receive = null; resolve('{"ok":false}'); }
  browser?.close().catch(() => {});
  if (chrome?.exitCode === null && chrome?.signalCode === null) chrome.kill('SIGTERM');
});
async function observe(response) {
  if (new URL(response.url()).pathname !== diag) return;
  try {
    const body = await response.json();
    const data = body.data;
    result.responses.push({ status: response.status(), requestId: body.requestId,
      correlated: Boolean(body.requestId) && body.requestId === response.headers()['x-request-id'],
      code: body.error?.code, operations: Array.isArray(data?.operations) ? data.operations : undefined });
  } catch { result.responses.push({ status: response.status(), bodyUnreadable: true }); }
}
async function refresh(button) {
  const count = result.responses.length;
  await page.getByRole('button', { name: button, exact: true }).click();
  if (!await until(() => result.responses.length > count)) throw new Error('diagnostics_response_timeout');
  return result.responses.at(-1);
}
async function renderedMetricsMatch(snapshot) {
  for (const operation of snapshot.operations || []) {
    const row = page.locator('tr').filter({ has: page.getByText(operation.name, { exact: true }) });
    if (await row.count() !== 1) return false;
    const actual = (await row.locator('td').allTextContents()).map(v => v.trim());
    const expected = [operation.name, operation.circuitBreaker, operation.calls, operation.retryAttempts, operation.failures,
      operation.fallbacks, operation.rejected, operation.timedOut].map(String);
    if (JSON.stringify(actual) !== JSON.stringify(expected)) return false;
  }
  return snapshot.operations?.length === 2;
}
try {
  chrome = spawn('/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', [
    '--headless=new', '--remote-debugging-port=0', '--user-data-dir=' + out + '/chrome-profile',
    '--no-first-run', '--no-default-browser-check', '--disable-background-networking',
    '--disable-component-update', 'about:blank'], { stdio: ['ignore', 'ignore', 'pipe'] });
  const endpoint = await Promise.race([new Promise((resolve, reject) => {
    let text = ''; chrome.stderr.on('data', data => {
      text += data.toString(); const match = text.match(/DevTools listening on (ws:\/\/[^\s]+)/);
      if (match) resolve(match[1]); if (text.length > 8000) text = text.slice(-4000);
    }); chrome.once('exit', () => reject(new Error('owned_chrome_start_exit')));
  }), delay(15000)]);
  if (!endpoint) throw new Error('owned_chrome_endpoint_timeout');
  browser = await chromium.connectOverCDP(endpoint);
  const context = await browser.newContext({ viewport: { width: 1440, height: 1100 } });
  page = await context.newPage(); page.setDefaultTimeout(30000);
  page.on('pageerror', () => result.pageErrors++);
  page.on('response', response => { const task = observe(response); pending.add(task); task.finally(() => pending.delete(task)); });
  page.on('request', request => {
    const path = new URL(request.url()).pathname;
    if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(request.method()) && /system-parameters|dictionaries/.test(path)) result.automaticWrites++;
  });
  stage = 'login'; await page.goto(base + '/diagnostics/resilience');
  await page.locator('input[autocomplete="username"]').fill(process.env.SUMMER_E2E_USERNAME);
  await page.locator('input[autocomplete="current-password"]').fill(process.env.SUMMER_E2E_PASSWORD);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await page.waitForURL(base + '/diagnostics/resilience');
  await page.getByRole('button', { name: '刷新', exact: true }).waitFor();
  await until(() => result.responses.some(r => r.status === 200));
  const initial = result.responses.at(-1);
  await check('real_pkce_diagnostics_read_is_correlated', initial?.status === 200 && initial.correlated);
  await check('both_operation_rows_render_exact_server_metrics', await until(() => renderedMetricsMatch(initial)));
  const metrics = initial.operations?.find(r => r.name === 'system-parameters.read');
  await check('real_fault_metrics_are_nonzero_and_circuit_open', metrics?.timedOut > 0 && metrics?.fallbacks > 0 && metrics?.rejected > 0 && Number.isInteger(metrics?.retryAttempts) && metrics.retryAttempts >= 0 && metrics?.circuitBreaker === 'OPEN');
  await check('diagnostics_request_id_is_visible', await page.getByText(initial.requestId, { exact: true }).isVisible());
  await page.screenshot({ path: out + '/resilience-nonzero.png', fullPage: true });
  stage = 'dependency_recovery'; await signal('RECOVER_READS');
  const recovered = await refresh('刷新');
  await check('recovery_metrics_render_closed_circuit', recovered.status === 200 && recovered.operations?.find(r => r.name === 'system-parameters.read')?.circuitBreaker === 'CLOSED' && await until(() => renderedMetricsMatch(recovered)));
  await page.screenshot({ path: out + '/resilience-recovered.png', fullPage: true });
  stage = 'authorization_denial'; await signal('DENY_DIAGNOSTICS');
  const denied = await refresh('刷新');
  await check('same_browser_diagnostics_permission_revocation_rejects', denied.status === 403 && denied.correlated && /^SUMMER_/.test(denied.code || ''));
  await check('denial_displays_request_id', await until(() => page.getByText('您没有查看韧性诊断的权限。', { exact: true }).isVisible()) && await page.getByText('requestId: ' + denied.requestId, { exact: true }).isVisible());
  await check('denial_hides_previous_metrics', await page.locator('tr').filter({ hasText: 'system-parameters.read' }).count() === 0);
  await page.screenshot({ path: out + '/resilience-denied.png', fullPage: true });
  stage = 'permission_recovery'; await signal('RESTORE_DIAGNOSTICS');
  const restored = await refresh('重试');
  await check('explicit_retry_recovers_after_permission_restore', restored.status === 200 && restored.correlated && await until(() => renderedMetricsMatch(restored)));
  await check('successful_retry_clears_error_feedback', await page.getByText('您没有查看韧性诊断的权限。', { exact: true }).count() === 0);
  await check('diagnostics_never_replays_business_writes', result.automaticWrites === 0);
  await check('no_unhandled_page_errors', result.pageErrors === 0);
  await page.screenshot({ path: out + '/resilience-restored.png', fullPage: true });
  result.complete = true;
} catch (error) {
  result.failureType = error.name; result.failureStage = stage;
  result.localFrames = error.stack?.match(/browser\.mjs:\d+:\d+/g) || [];
  if (stage !== 'login') await page?.screenshot({ path: out + '/browser-failed.png', fullPage: true }).catch(() => {});
} finally {
  input.close(); process.stdin.destroy();
  await Promise.allSettled([...pending]);
  await Promise.race([browser?.close().catch(() => {}), delay(5000)]);
  result.chromeForced = false;
  if (chrome && chrome.exitCode === null && chrome.signalCode === null) {
    const exited = new Promise(resolve => chrome.once('exit', resolve));
    chrome.kill('SIGTERM'); result.chromeForced = true;
    await Promise.race([exited, delay(5000)]);
    if (chrome.exitCode === null && chrome.signalCode === null) { chrome.kill('SIGKILL'); await Promise.race([exited, delay(5000)]); }
  }
  result.chromeExited = !chrome || chrome.exitCode !== null || chrome.signalCode !== null;
  chrome?.stderr?.destroy(); chrome?.stdout?.destroy(); chrome?.unref();
  await rm(out + '/chrome-profile', { recursive: true, force: true }); await persist();
}
process.exitCode = result.complete && result.checks.every(c => c.passed) && result.chromeExited ? 0 : 1;
