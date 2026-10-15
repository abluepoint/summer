import assert from 'node:assert/strict';
import { before, after, test } from 'node:test';
import { createServer } from 'vite';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright-core';
let vite, browser, origin;
const task = { taskId: 'task-fixture', enterpriseId: 'ent-acme', name: 'Progress task', taskType: 'system.sleep',
  cronExpression: '0 0 0 1 1 ? 2099', parameters: { durationMs: 5000 }, maxRetries: 1, timeoutMs: 15000,
  status: 'ACTIVE', version: 7, createdAt: '2026-10-15T00:00:00Z', updatedAt: '2026-10-15T00:00:00Z' };
const envelope = (data) => ({ success: true, data, requestId: 'task-fixture' });
before(async () => {
  vite = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)), logLevel: 'error',
    server: { port: 0, strictPort: false }, plugins: [{ name: 'task-browser-fixture', configureServer(server) {
      server.middlewares.use(async (req, res, next) => { if (!req.url.startsWith('/__tasks/')) return next();
        res.setHeader('Content-Type', 'text/html');
        res.end(await server.transformIndexHtml(req.url, '<div id="root"></div><script type="module" src="/scripts/tasks-browser-fixture.tsx"></script>'));
      });
    } }] });
  await vite.listen(); origin = `http://127.0.0.1:${vite.httpServer.address().port}`;
  browser = await chromium.launch({ headless: true, executablePath: process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' });
});
after(async () => { await browser?.close(); vite?.httpServer.closeAllConnections(); await vite?.close(); });
const reply = (route, data, status = 200) => route.fulfill({ status, contentType: 'application/json',
  body: JSON.stringify(status < 400 ? envelope(data) : { success: false, error: { code: 'SUMMER_PRECONDITION_FAILED', message: 'version conflict' }, requestId: 'task-conflict' }) });
async function open(write = (route) => reply(route, task), executions = []) {
  const page = await browser.newPage(); page.setDefaultTimeout(6000);
  // Synthetic transport lets the test control event ordering and assert cleanup independently of HTTP acceptance.
  await page.addInitScript(() => {
    window.taskStreams = [];
    window.EventSource = class extends EventTarget {
      constructor(url) { super(); this.url = url; this.closed = false; window.taskStreams.push(this); }
      close() { this.closed = true; }
    };
  });
  await page.route('**/api/**', (route) => {
    const req = route.request(), path = new URL(req.url()).pathname;
    if (req.method() !== 'GET') return write(route);
    if (path.endsWith('/types')) return reply(route, [{ taskType: 'system.sleep', description: 'Sleep' }]);
    if (path.endsWith('/executions')) return reply(route, { items: executions });
    return reply(route, { items: path.includes('/ent-north/') ? [] : [task] });
  });
  await page.goto(`${origin}/__tasks/page`);
  await page.getByRole('cell', { name: task.name, exact: true }).waitFor();
  return page;
}
async function edit(page) { await page.getByRole('button', { name: '编辑', exact: true }).click(); }

test('edits the existing task with its version and submits only once while saving', async () => {
  const writes = [];
  const page = await open(async (route) => { writes.push({ method: route.request().method(), headers: route.request().headers(), body: route.request().postDataJSON() });
    await new Promise((resolve) => setTimeout(resolve, 150)); return reply(route, { ...task, ...writes[0].body, version: 8 }); });
  try {
    await edit(page); await page.getByPlaceholder('任务名称', { exact: true }).fill('Updated task');
    await page.getByRole('button', { name: '保存任务', exact: true }).evaluate((button) => { button.click(); button.click(); });
    await page.getByRole('cell', { name: 'Updated task', exact: true }).waitFor();
    assert.equal(writes.length, 1); assert.equal(writes[0].method, 'PATCH'); assert.equal(writes[0].headers['if-match'], '"7"');
    assert.deepEqual(writes[0].body.parameters, { durationMs: 5000 });
  } finally { await page.close(); }
});

test('version conflicts retain the draft and correlation id', async () => {
  const page = await open((route) => reply(route, null, 412));
  try {
    await edit(page); await page.getByPlaceholder('任务名称', { exact: true }).fill('Unsaved draft');
    await page.getByRole('button', { name: '保存任务', exact: true }).click();
    await page.getByText('version conflict (task-conflict)', { exact: true }).waitFor();
    assert.equal(await page.getByPlaceholder('任务名称', { exact: true }).inputValue(), 'Unsaved draft');
  } finally { await page.close(); }
});

test('uncertain trigger retries reuse the key and an acknowledged repeat gets a new key', async () => {
  const keys = [];
  const page = await open((route) => {
    keys.push(route.request().headers()['idempotency-key']);
    return reply(route, { taskId: task.taskId, status: 'TRIGGERED' }, keys.length === 1 ? 503 : 202);
  });
  try {
    await page.getByRole('button', { name: '立即执行', exact: true }).click();
    await page.getByText('version conflict (task-conflict)', { exact: true }).waitFor();
    await page.getByRole('button', { name: '立即执行', exact: true }).click();
    await page.getByText('任务已立即执行。', { exact: true }).waitFor();
    await page.getByRole('button', { name: '立即执行', exact: true }).click();
    await page.waitForFunction(() => !document.querySelector('.arco-btn-loading'));
    assert.equal(keys.length, 3); assert.equal(keys[0], keys[1]); assert.notEqual(keys[1], keys[2]);
  } finally { await page.close(); }
});

test('invalid JSON causes no write and dirty cancellation requires confirmation', async () => {
  let writes = 0; const page = await open((route) => { writes++; return reply(route, task); });
  try {
    await edit(page); await page.getByPlaceholder('参数 JSON', { exact: true }).fill('[1]');
    await page.getByRole('button', { name: '保存任务', exact: true }).click();
    await page.getByText('参数必须是 JSON 对象。', { exact: true }).waitFor(); assert.equal(writes, 0);
    await page.getByRole('button', { name: '取消', exact: true }).click();
    await page.getByText('放弃未保存修改？', { exact: true }).waitFor();
    await page.locator('.arco-modal').getByRole('button', { name: '取消', exact: true }).click();
    assert.equal(await page.getByPlaceholder('参数 JSON', { exact: true }).inputValue(), '[1]');
    await page.locator('.arco-modal-wrapper').waitFor({ state: 'hidden' });
    const dialog = page.waitForEvent('dialog');
    const navigation = page.getByRole('link', { name: '离开任务页', exact: true }).click();
    await (await dialog).dismiss(); await navigation;
    assert.equal(new URL(page.url()).pathname, '/__tasks/page');
  } finally { await page.close(); }
});

test('progress remains visible when an older history response arrives and closes on tenant switch', async () => {
  let releaseHistory; const historyGate = new Promise((resolve) => { releaseHistory = resolve; });
  const page = await open();
  try {
    await page.route('**/executions', async (route) => { await historyGate; return reply(route, { items: [{
      executionId: 'live', taskId: task.taskId, enterpriseId: task.enterpriseId, fireInstanceId: 'fire',
      status: 'RUNNING', attempt: 1, startedAt: task.createdAt, idempotencyKey: 'fixture', recovered: false, attempts: [],
    }] }); });
    await page.getByRole('button', { name: '执行历史', exact: true }).click();
    await page.waitForFunction(() => window.taskStreams.length === 1);
    await page.evaluate(() => window.taskStreams[0].dispatchEvent(new MessageEvent('execution.progress', {
      data: JSON.stringify({ executionId: 'live', status: 'RUNNING', attempt: 1, progress: 45 }), lastEventId: '2',
    })));
    releaseHistory(); await page.getByRole('cell', { name: '45%', exact: true }).waitFor();
    await page.getByLabel('活动企业', { exact: true }).click();
    await page.locator('.arco-select-option').filter({ hasText: 'ent-north' }).click();
    await page.waitForFunction(() => window.taskStreams[0].closed);
    assert.equal(await page.getByRole('cell', { name: task.name, exact: true }).count(), 0);
    assert.ok(!await page.evaluate(() => window.taskStreams[0].url.includes('token')));
  } finally { releaseHistory(); await page.close(); }
});

for (const status of ['SUCCESS', 'FAILED']) {
  test(`terminal ${status} uses persisted server timestamps despite browser clock skew`, async () => {
    const execution = {
      executionId: 'terminal', taskId: task.taskId, enterpriseId: task.enterpriseId, fireInstanceId: 'fire',
      status: 'RUNNING', attempt: 1, startedAt: '2040-01-01T00:00:00Z', idempotencyKey: 'terminal', recovered: false, attempts: [],
    };
    const page = await open(undefined, [execution]);
    try {
      await page.getByRole('button', { name: '执行历史', exact: true }).click();
      await page.getByRole('cell', { name: execution.startedAt, exact: true }).waitFor();
      const finishedAt = '2040-01-01T00:00:05Z';
      Object.assign(execution, { status, finishedAt });
      await page.evaluate(({ status }) => window.taskStreams[0].dispatchEvent(new MessageEvent(
        status === 'SUCCESS' ? 'execution.succeeded' : 'execution.failed', {
          data: JSON.stringify({ executionId: 'terminal', status, attempt: 1, progress: 100 }), lastEventId: '3',
        })), { status });
      await page.getByRole('cell', { name: finishedAt, exact: true }).waitFor();
      await page.getByRole('cell', { name: status, exact: true }).waitFor();
    } finally { await page.close(); }
  });
}
