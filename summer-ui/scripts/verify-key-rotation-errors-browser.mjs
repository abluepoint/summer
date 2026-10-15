// Component regression with synthetic HTTP; does not claim actual authentication.
import assert from 'node:assert/strict';
import { before, after, test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import { chromium } from 'playwright-core';

let vite, browser, origin, pageErrors = 0;
before(async () => {
  vite = await createServer({
    root: fileURLToPath(new URL('..', import.meta.url)), logLevel: 'error',
    cacheDir: process.env.SUMMER_KEY_COMPONENT_CACHE_DIR,
    server: { port: 21573, strictPort: true, hmr: false, watch: { ignored: ['**/*'] } },
    plugins: [{ name: 'key-rotation-regression', configureServer(server) {
      server.middlewares.use(async (req, res, next) => {
        if (req.url !== '/__key-rotation') return next();
        res.setHeader('Content-Type', 'text/html');
        res.end(await server.transformIndexHtml(req.url, '<div id="root"></div><script type="module" src="/scripts/key-rotation-browser-fixture.tsx"></script>'));
      });
    } }],
  });
  await vite.listen(); origin = `http://127.0.0.1:${vite.httpServer.address().port}`;
  browser = await chromium.launch({ headless: true, executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' });
});
after(async () => {
  await browser?.close(); await vite?.watcher.close(); vite?.httpServer.closeAllConnections(); await vite?.close();
  assert.equal(pageErrors, 0);
});

for (const status of [412, 500]) {
  test(`rotation HTTP ${status} presents the response requestId with the error`, async () => {
    const page = await browser.newPage(); page.setDefaultTimeout(10000); page.setDefaultNavigationTimeout(30000);
    page.on('pageerror', () => pageErrors++);
    const requestId = `key-rotation-${status}-request`;
    await page.route('**/api/security/keys**', route => {
      const rotating = route.request().method() === 'POST';
      return route.fulfill({ status: rotating ? status : 200, contentType: 'application/json', headers: { 'X-Request-Id': requestId }, body: JSON.stringify(rotating ? {
        success: false, data: null, error: { code: status === 412 ? 'SUMMER_PRECONDITION_FAILED' : 'SUMMER_INTERNAL_ERROR', message: 'Rotation rejected', violations: [] }, requestId,
      } : { success: true, error: null, requestId, data: { items: [{ kid: 'component-key', algorithm: 'RS256', source: 'mysql', status: 'ACTIVE', version: 1, health: 'READY' }] } }) });
    });
    try {
      await page.goto(origin + '/__key-rotation');
      await page.getByRole('cell', { name: 'component-key', exact: true }).waitFor();
      await page.getByRole('button', { name: '轮换密钥', exact: true }).click();
      await page.locator('.arco-alert-error').waitFor();
      assert.match(await page.locator('.arco-alert-error').innerText(), new RegExp(requestId));
    } finally { await page.close(); }
  });
}
