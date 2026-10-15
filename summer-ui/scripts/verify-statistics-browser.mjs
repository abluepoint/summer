// Component regression only: synthetic HTTP, never a substitute for real acceptance.
import assert from 'node:assert/strict';
import { before, after, test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import { chromium } from 'playwright-core';
let vite, browser, origin;
before(async () => {
  vite = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)), logLevel: 'error', cacheDir: process.env.SUMMER_STATISTICS_CACHE_DIR,
    server: { port: 20573, strictPort: true, hmr: false, watch: { ignored: ['**/*'] } },
    plugins: [{ name: 'statistics-regression', configureServer(server) { server.middlewares.use(async (req, res, next) => {
      if (req.url !== '/__statistics') return next();
      res.setHeader('Content-Type', 'text/html'); res.end(await server.transformIndexHtml(req.url, '<div id="root"></div><script type="module" src="/scripts/statistics-browser-fixture.tsx"></script>'));
    }); } }],
  });
  await vite.listen(); origin = 'http://127.0.0.1:20573';
  browser = await chromium.launch({ headless: true, executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' });
});
after(async () => { await browser?.close(); await vite?.watcher.close(); vite?.httpServer.closeAllConnections(); await vite?.close(); });
const data = { window: { from: '2026-01-01T00:00:00Z', to: '2026-02-01T00:00:00Z' }, scope: { enterpriseId: 'tenant-b', organizationId: null, subject: 'fixture' }, asOf: '2026-02-01T00:00:00Z', userCount: 0, roleCount: 0, interfaceCount: 100, accessibleInterfaceCount: 0, dictionaryCount: 0, taskCount: 0, taskExecutionCount: 0, successfulTaskExecutionCount: 0, failedTaskExecutionCount: 0, taskSuccessRate: 0, auditEventCount: 0 };
async function pageWith(value) {
  const page = await browser.newPage(); page.setDefaultTimeout(10000); page.setDefaultNavigationTimeout(30000); const requests = [];
  await page.route('**/api/v1/enterprises/*/statistics*', route => { requests.push(new URL(route.request().url()).pathname); return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, error: null, requestId: 'component-statistics', data: value }) }); });
  await page.goto(origin + '/__statistics'); await page.locator('.arco-card-header').first().waitFor();
  return { page, requests };
}
test('statistics uses the selected active enterprise', async () => { const { page, requests } = await pageWith(data); try { assert.equal(requests[0], '/api/v1/enterprises/tenant-b/statistics'); } finally { await page.close(); } });
test('zero visible data is empty regardless of catalog inventory', async () => { const { page } = await pageWith(data); try { await page.getByText('当前范围和时间窗口没有数据。', { exact: true }).waitFor(); } finally { await page.close(); } });
test('denied metrics are distinct from zero and formula is explicit', async () => { const { page } = await pageWith({ ...data, userCount: 2, roleCount: null }); try { await page.getByText('无权读取', { exact: true }).waitFor(); assert.match(await page.locator('body').innerText(), /成功执行数.*全部执行数/); } finally { await page.close(); } });
