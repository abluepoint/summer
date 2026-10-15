import { createRequire } from 'node:module';
import { writeFile } from 'node:fs/promises';
import { createInterface } from 'node:readline';
const { chromium } = createRequire(process.cwd() + '/package.json')('playwright-core');
const out = process.env.SUMMER_DICTIONARY_RELEASE_EVIDENCE_DIR;
const base = 'http://127.0.0.1:24573';
const publicPath = '/public/enterprises/ent-acme/dictionaries/customer-segment';
const result = { checks: [], networkMocking: false, pageErrors: 0, complete: false };
const input = createInterface({ input: process.stdin });
const lines = [];
let resolveLine;
input.on('line', line => { if (resolveLine) { const ready = resolveLine; resolveLine = undefined; ready(line); } else lines.push(line); });
async function signal(command) {
  process.stdout.write(command + '\n');
  const line = lines.length ? lines.shift() : await new Promise(resolve => { resolveLine = resolve; });
  if (line !== 'GO') throw new Error('unexpected_fixture_reply');
}
const check = (name, passed) => result.checks.push({ name, passed: Boolean(passed) });
let browser, page, stage = 'login';
async function login(username, password) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1100 } });
  const next = await context.newPage();
  next.setDefaultTimeout(30000); next.on('pageerror', () => result.pageErrors++);
  await next.goto(base + '/dictionary/form');
  await next.locator('input[autocomplete="username"]').fill(username);
  await next.locator('input[autocomplete="current-password"]').fill(password);
  await next.getByRole('button', { name: '登录', exact: true }).click();
  await next.waitForURL(base + '/dictionary/form');
  await next.locator('[data-testid="dictionary-version"]').waitFor();
  return next;
}
async function refresh(expected) {
  const waiting = page.waitForResponse(r => r.request().method() === 'GET' && new URL(r.url()).pathname === publicPath);
  await page.getByRole('button', { name: '刷新选项', exact: true }).click();
  const response = await waiting;
  if (response.status() !== expected) throw new Error('unexpected_manual_refresh_status');
  return response.json();
}
const selected = () => page.locator('.arco-form-item').filter({ has: page.getByText('客户分层', { exact: true }) }).locator('.arco-select');
const dirty = JSON.stringify([{ value: 'alpha', label: '未保存覆盖', sortOrder: 0, enabled: true }]);
try {
  browser = await chromium.launch({ executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: true });
  page = await login(process.env.SUMMER_E2E_USERNAME, process.env.SUMMER_E2E_PASSWORD);
  let value = await refresh(200);
  check('actual_pkce_form_reads_first_published_snapshot', value.data?.version === 1 && value.data?.items.length === 2);
  await page.getByLabel('覆盖选项 JSON', { exact: true }).fill(dirty);
  stage = 'draft_isolation'; await signal('DRAFT_EDIT');
  value = await refresh(200);
  check('manual_reread_ignores_unpublished_item_changes', value.data?.version === 1 && value.data?.items.length === 2 && value.data?.items[0].label === '发布一甲');
  check('draft_reread_keeps_unsaved_form_text', await page.getByLabel('覆盖选项 JSON', { exact: true }).inputValue() === dirty);
  stage = 'publish'; await signal('PUBLISH');
  value = await refresh(200);
  await page.getByText('发布二甲', { exact: true }).waitFor();
  check('manual_reread_displays_new_published_version_and_options', value.data?.version === 2 && value.data?.items.length === 3 && value.data?.items[0].label === '发布二甲');
  check('published_reread_preserves_selected_value_and_unsaved_form', (await selected().innerText()).includes('发布二甲') && await page.getByLabel('覆盖选项 JSON', { exact: true }).inputValue() === dirty);
  await page.screenshot({ path: out + '/dictionary-published-dirty-preserved.png', fullPage: true });
  stage = 'disable'; await signal('DISABLE');
  value = await refresh(404);
  await page.locator('.arco-alert-error').filter({ hasText: value.requestId }).waitFor();
  check('disabled_snapshot_shows_correlated_unavailable_error', value.error?.code === 'SUMMER_NOT_FOUND' && (await page.locator('.arco-alert-error').innerText()).includes(value.requestId));
  check('disable_failure_keeps_unsaved_form_text', await page.getByLabel('覆盖选项 JSON', { exact: true }).inputValue() === dirty);
  await page.screenshot({ path: out + '/dictionary-disabled.png', fullPage: true });
  stage = 'rollback'; await signal('ROLLBACK');
  value = await refresh(200); await page.getByText('发布一甲', { exact: true }).waitFor();
  check('rollback_reread_restores_old_snapshot', value.data?.version === 1 && value.data?.items.length === 2 && value.data?.items[0].label === '发布一甲');
  check('rollback_reread_keeps_unsaved_form_text', await page.getByLabel('覆盖选项 JSON', { exact: true }).inputValue() === dirty);
  await page.screenshot({ path: out + '/dictionary-rollback.png', fullPage: true });
  stage = 'invalid_input'; await page.getByLabel('覆盖选项 JSON', { exact: true }).fill('{');
  await page.getByRole('button', { name: '发布字典覆盖', exact: true }).click();
  await page.getByText('请输入有效选项 JSON', { exact: true }).waitFor();
  check('invalid_form_json_is_rejected_without_losing_input', await page.getByLabel('覆盖选项 JSON', { exact: true }).inputValue() === '{');
  await page.close();
  stage = 'reader_denied'; page = await login(process.env.SUMMER_E2E_READER, process.env.SUMMER_E2E_READER_PASSWORD);
  await page.getByLabel('覆盖选项 JSON', { exact: true }).fill(dirty);
  const waiting = page.waitForResponse(r => r.request().method() === 'PUT' && new URL(r.url()).pathname.endsWith('/dictionaries/customer-segment/overrides/_tenant'));
  await page.getByRole('button', { name: '发布字典覆盖', exact: true }).click();
  const denied = await waiting; value = await denied.json();
  result.readerDeniedResponse = { status: denied.status(), code: value.error?.code, requestIdPresent: Boolean(value.requestId) };
  await page.getByText(/无权发布字典覆盖/).waitFor();
  check('actual_reader_write_denied_with_request_id', denied.status() === 403 && value.error?.code === 'SUMMER_FORBIDDEN' && (await page.locator('.arco-alert-error').innerText()).includes(value.requestId));
  await page.screenshot({ path: out + '/dictionary-reader-denied.png', fullPage: true });
  check('no_unhandled_page_errors', result.pageErrors === 0); result.complete = true;
} catch (error) {
  result.failureType = error.name; result.failureStage = stage;
  result.localFrames = error.stack?.match(/browser\.mjs:\d+:\d+/g) || [];
  if (page && result.checks.length) await page.screenshot({ path: out + '/browser-failed.png', fullPage: true }).catch(() => {});
} finally {
  input.close(); process.stdin.destroy();
  await writeFile(out + '/browser-result.json', JSON.stringify(result, null, 2) + '\n');
  await browser?.close();
}
process.exitCode = result.complete && result.checks.every(item => item.passed) ? 0 : 1;
