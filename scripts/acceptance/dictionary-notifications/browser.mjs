import { createRequire } from 'node:module';
import { writeFile, rm } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import { createInterface } from 'node:readline';
const { chromium } = createRequire(process.cwd() + '/package.json')('playwright-core');
const out = process.env.SUMMER_DICTIONARY_NOTIFICATIONS_EVIDENCE_DIR;
const base = 'http://127.0.0.1:23573';
const publicPath = '/public/enterprises/ent-acme/dictionaries/customer-segment';
const eventPath = '/api/v1/enterprises/ent-acme/dictionaries/customer-segment/events';
const result = { checks: [], complete: false, networkMocking: false, pageErrors: 0, notices: [], reads: [], streamRequests: [], automaticWrites: 0 };
const recoveryOnly = process.env.SUMMER_DICTIONARY_NOTIFICATIONS_RECOVERY_ONLY === '1';
const lifecycleOnly = process.env.SUMMER_DICTIONARY_NOTIFICATIONS_LIFECYCLE_ONLY === '1';
let counting = !recoveryOnly;
result.lifecycleOnly = lifecycleOnly;
result.recoveryOnly = recoveryOnly; result.menuReads = [];
result.streamResponses = []; result.streamFailures = []; result.streamExtraHeaders = [];
const input = createInterface({ input: process.stdin });
const lines = []; let resolveLine;
input.on('line', line => { if (resolveLine) { const f = resolveLine; resolveLine = undefined; f(line); } else lines.push(line); });
async function signal(command) {
  process.stdout.write(command + '\n');
  const line = lines.length ? lines.shift() : await new Promise(resolve => { resolveLine = resolve; });
  return JSON.parse(line);
}
function counts() { return { tests: result.checks.length, failures: result.checks.filter(c => !c.passed).length, errors: result.failureType ? 1 : 0, skipped: 0 }; }
async function persist() { result.counts = counts(); await writeFile(out + '/browser-result.json', JSON.stringify(result, null, 2) + '\n'); }
async function check(name, passed, facts = {}) { if (!counting) return; result.checks.push({ name, passed: Boolean(passed), ...facts }); await persist(); }
async function until(predicate, timeout = 35000) {
  const end = Date.now() + timeout;
  while (Date.now() < end) { if (await predicate()) return true; await new Promise(resolve => setTimeout(resolve, 100)); }
  return Boolean(await predicate());
}
const deadline = ms => new Promise(resolve => setTimeout(() => resolve('timeout'), ms));
let chrome, browser, page, stage = 'launch';
const dirty = JSON.stringify([{ value: 'draft', label: '未提交输入保留', sortOrder: 0, enabled: true }]);
const selected = () => page.locator('.arco-form-item').filter({ has: page.getByText('客户分层', { exact: true }) }).locator('.arco-select');
const dirtyKept = async () => await page.getByLabel('覆盖选项 JSON', { exact: true }).inputValue() === dirty
  && await page.locator('.arco-form-item').filter({ hasText: '覆盖范围（_tenant 或组织 ID）' }).locator('input').inputValue() === '_tenant'
  && await page.locator('.arco-form-item').filter({ hasText: '覆盖版本（新建留空）' }).locator('input').inputValue() === '7';
const notice = (version, type = 'DICTIONARY_PUBLISHED') => result.notices.find(e => e.type === type && e.version === version);
const readVersion = version => result.reads.find(r => r.status === 200 && r.version === version);
try {
  chrome = spawn('/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', ['--headless=new', '--remote-debugging-port=0', '--user-data-dir=' + out + '/chrome-profile', '--no-first-run', '--no-default-browser-check', '--disable-background-networking', '--disable-component-update', 'about:blank'], { stdio: ['ignore', 'ignore', 'pipe'] });
  const endpoint = await Promise.race([new Promise((resolve, reject) => {
    let text = ''; chrome.stderr.on('data', data => { text += data.toString(); const match = text.match(/DevTools listening on (ws:\/\/[^\s]+)/); if (match) resolve(match[1]); if (text.length > 8000) text = text.slice(-4000); });
    chrome.once('exit', () => reject(new Error('owned_chrome_start_exit')));
  }), deadline(15000)]);
  if (endpoint === 'timeout') throw new Error('owned_chrome_endpoint_timeout');
  browser = await chromium.connectOverCDP(endpoint);
  const context = await browser.newContext({ viewport: { width: 1440, height: 1100 } });
  page = await context.newPage(); page.setDefaultTimeout(30000); page.on('pageerror', () => result.pageErrors++);
  const cdp = await context.newCDPSession(page); await cdp.send('Network.enable');
  if (lifecycleOnly) {
    await page.goto('about:blank'); result.complete = true;
  } else {
    const streamIds = new Set();
    cdp.on('Network.requestWillBeSent', event => { if (new URL(event.request.url).pathname === eventPath) streamIds.add(event.requestId); });
    cdp.on('Network.requestWillBeSentExtraInfo', event => {
      if (!streamIds.has(event.requestId)) return;
      const headers = Object.fromEntries(Object.entries(event.headers).map(([k, v]) => [k.toLowerCase(), v]));
      result.streamExtraHeaders.push({ lastEventId: headers['last-event-id'] || null, authorizationPresent: Boolean(headers.authorization) });
    });
    cdp.on('Network.eventSourceMessageReceived', event => {
      let value; try { value = JSON.parse(event.data); } catch { return; }
      if (!value.dictionaryCode || value.dictionaryCode !== 'customer-segment') return;
      result.notices.push({ type: event.eventName, id: Number(event.eventId || 0), version: value.version, requestId: value.requestId, keys: Object.keys(value).sort() });
    });
    page.on('request', request => {
      const url = new URL(request.url());
      if (request.method() === 'GET' && url.pathname === eventPath) {
        const headers = request.headers();
        result.streamRequests.push({ queryPresent: Boolean(url.search), authorizationPresent: Boolean(headers.authorization), lastEventId: headers['last-event-id'] || null });
      }
      if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(request.method()) && url.pathname.includes('/dictionaries/')) result.automaticWrites++;
    });
    page.on('requestfailed', request => {
      if (new URL(request.url()).pathname === eventPath) result.streamFailures.push({ error: request.failure()?.errorText?.match(/^net::ERR_[A-Z_]+$/)?.[0] || 'network_failure' });
    });
    page.on('response', async response => {
      if (new URL(response.url()).pathname === eventPath) result.streamResponses.push({ status: response.status(), contentType: response.headers()['content-type'] || null, requestIdPresent: Boolean(response.headers()['x-request-id']) });
      if (response.request().method() === 'GET' && new URL(response.url()).pathname.endsWith('/menu-manifest')) result.menuReads.push({ at: Date.now(), status: response.status() });
      if (response.request().method() !== 'GET' || new URL(response.url()).pathname !== publicPath) return;
      try {
        const data = await response.json();
        result.reads.push({ status: response.status(), version: data.data?.version, values: data.data?.items?.map(i => i.value), labels: data.data?.items?.map(i => i.label), requestId: data.requestId, correlated: Boolean(data.requestId) && data.requestId === response.headers()['x-request-id'], code: data.error?.code });
      } catch { result.reads.push({ status: response.status() }); }
    });
    stage = 'login'; await page.goto(base + '/dictionary/form');
    await page.locator('input[autocomplete="username"]').fill(process.env.SUMMER_E2E_USERNAME);
    await page.locator('input[autocomplete="current-password"]').fill(process.env.SUMMER_E2E_PASSWORD);
    await page.getByRole('button', { name: '登录', exact: true }).click();
    await page.waitForURL(base + '/dictionary/form');
    await page.locator('[data-testid="dictionary-version"]').waitFor();
    await until(async () => await page.getByText('OPEN', { exact: true }).isVisible());
    await check('real_pkce_page_reads_version_one_and_opens_notification_stream', Boolean(readVersion(1)) && await page.getByText('OPEN', { exact: true }).isVisible());
    await page.getByLabel('覆盖选项 JSON', { exact: true }).fill(dirty);
    await page.locator('.arco-form-item').filter({ hasText: '覆盖版本（新建留空）' }).locator('input').fill('7');
    stage = 'automatic_publish'; const published = await signal('PUBLISH_DISABLED_SELECTION');
    const notified = await until(() => notice(2));
    const refreshed = await until(() => readVersion(2));
    await check('already_open_page_receives_other_jvm_publish_notification', notified && notice(2)?.id > 0 && notice(2)?.requestId === published.requestId);
    await check('notification_triggers_authorized_version_two_reread_without_manual_refresh', refreshed && readVersion(2).correlated && readVersion(2).values.join(',') === 'bravo');
    await check('notification_contains_only_invalidation_metadata', result.notices.filter(e => e.version).every(e => e.keys.join(',') === 'dictionaryCode,eventType,requestId,tenantId,version'));
    await until(async () => await page.getByText('当前选中值已停用，请重新选择。', { exact: true }).isVisible());
    await check('disabled_selected_value_requests_reselection', await page.getByText('当前选中值已停用，请重新选择。', { exact: true }).isVisible());
    await check('automatic_publish_preserves_unsaved_json_scope_and_version', await dirtyKept());
    await page.screenshot({ path: out + '/notification-disabled-selection-dirty.png', fullPage: true });
    stage = 'reselect'; await selected().click();
    const list = page.locator('.arco-select-popup:visible'); await list.getByText('更新乙', { exact: true }).click();
    await until(async () => !(await page.getByText('当前选中值已停用，请重新选择。', { exact: true }).isVisible()));
    await check('valid_reselection_clears_unavailable_feedback', (await selected().innerText()).includes('更新乙') && !(await page.getByText('当前选中值已停用，请重新选择。', { exact: true }).isVisible()));
    stage = 'notification_outage'; counting = true; const outageStart = Date.now(); await signal('BREAK_SSE_TRANSPORT');
    const unavailable = await until(async () => await page.getByText('RECONNECTING', { exact: true }).isVisible() && await page.getByText('字典更新连接已断开，正在重试。', { exact: true }).isVisible());
    await check('real_sse_transport_outage_displays_diagnosable_reconnecting_state', unavailable);
    await check('periodic_menu_read_stays_authorized_while_sse_is_broken', await until(() => result.menuReads.some(r => r.at > outageStart && r.status === 200), 16000));
    await check('notification_outage_preserves_unsaved_form', await dirtyKept());
    await page.screenshot({ path: out + '/notification-outage-dirty.png', fullPage: true });
    stage = 'notification_recovery'; const recovered = await signal('PUBLISH_WHILE_OFFLINE_AND_RESTORE');
    const caughtUp = await until(() => notice(3) && readVersion(3), 45000);
    if (caughtUp) await until(async () => await page.getByText('OPEN', { exact: true }).isVisible());
    await check('native_reconnect_catches_publish_missed_while_transport_was_down', caughtUp && notice(3)?.requestId === recovered.requestId && [...result.streamRequests, ...result.streamExtraHeaders].some(r => Number(r.lastEventId) > 0));
    await check('recovery_rereads_current_version_and_clears_warning', caughtUp && readVersion(3)?.labels.join(',') === '恢复后乙' && await page.getByText('OPEN', { exact: true }).isVisible() && !(await page.getByText('字典更新连接已断开，正在重试。', { exact: true }).isVisible()));
    await check('reconnect_and_catchup_preserve_unsaved_form', await dirtyKept());
    result.recoveryState = { openVisible: await page.getByText('OPEN', { exact: true }).isVisible(), reconnectingVisible: await page.getByText('RECONNECTING', { exact: true }).isVisible(), version: await page.locator('[data-testid="dictionary-version"]').innerText() };
    if (!caughtUp) throw new Error('notification_recovery_not_completed');
    await page.screenshot({ path: out + '/notification-recovered-dirty.png', fullPage: true });
    stage = 'disable'; await signal('DISABLE');
    await until(() => notice(3, 'DICTIONARY_DISABLED') && result.reads.some(r => r.status === 404));
    const rejected = result.reads.find(r => r.status === 404);
    await check('disable_notification_rereads_and_rejects_unavailable_snapshot', Boolean(rejected?.correlated) && rejected?.code === 'SUMMER_NOT_FOUND' && !(await page.locator('[data-testid="dictionary-version"]').count()));
    await check('automatic_unavailable_refresh_preserves_unsaved_form', await dirtyKept());
    stage = 'rollback'; await signal('ROLLBACK');
    await until(() => notice(1, 'DICTIONARY_ROLLED_BACK'));
    await until(async () => await page.locator('[data-testid="dictionary-version"]').textContent().then(text => text.includes('1')).catch(() => false));
    await check('rollback_notification_restores_accessible_snapshot_automatically', Boolean(notice(1, 'DICTIONARY_ROLLED_BACK')) && (await page.locator('[data-testid="dictionary-version"]').innerText()).includes('1'));
    await check('rollback_preserves_unsaved_form', await dirtyKept());
    await check('notifications_never_auto_submit_dictionary_business_changes', result.automaticWrites === 0);
    await check('native_sse_url_and_headers_do_not_expose_bearer_credentials', result.streamRequests.length > 0 && result.streamRequests.every(r => !r.queryPresent && !r.authorizationPresent));
    await check('no_unhandled_page_errors', result.pageErrors === 0);
    await page.screenshot({ path: out + '/notification-final-dirty.png', fullPage: true });
    result.complete = true;
  }
} catch (error) {
  result.failureType = error.name; result.failureStage = stage;
  result.localFrames = error.stack?.match(/browser\.mjs:\d+:\d+/g) || [];
  if (page && result.checks.length) await page.screenshot({ path: out + '/browser-failed.png', fullPage: true }).catch(() => {});
} finally {
  input.close(); process.stdin.destroy();
  await Promise.race([browser?.close().catch(() => {}), deadline(5000)]);
  result.chromeForced = false;
  if (chrome && chrome.exitCode === null && chrome.signalCode === null) {
    chrome.kill('SIGTERM'); result.chromeForced = true;
    await Promise.race([new Promise(resolve => chrome.once('exit', resolve)), deadline(5000)]);
    if (chrome.exitCode === null && chrome.signalCode === null) { chrome.kill('SIGKILL'); await Promise.race([new Promise(resolve => chrome.once('exit', resolve)), deadline(5000)]); }
  }
  result.chromeExited = !chrome || chrome.exitCode !== null || chrome.signalCode !== null;
  chrome?.stderr?.destroy(); chrome?.stdout?.destroy(); chrome?.stdin?.destroy(); chrome?.unref();
  await rm(out + '/chrome-profile', { recursive: true, force: true }).catch(() => {});
  await persist();
}
process.exitCode = result.complete && result.checks.every(c => c.passed) && result.chromeExited ? 0 : 1;
