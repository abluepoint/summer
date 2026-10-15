import assert from 'node:assert/strict';
import { before, after, test } from 'node:test';
import { createServer } from 'vite';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright-core';
let vite, browser, origin;
const client = {
  clientId: 'fixture-client',
  clientName: 'Original client',
  authenticationMethods: ['none'],
  grantTypes: ['authorization_code'],
  redirectUris: ['https://example.test/callback'],
  postLogoutRedirectUris: [],
  scopes: ['openid'],
  status: 'ACTIVE',
  version: 7,
};
const reply = (route, data, status = 200) =>
  route.fulfill({
    status,
    contentType: 'application/json',
    headers: { 'X-Request-Id': 'oauth-fixture-request' },
    body: JSON.stringify(
      status < 400
        ? { success: true, data, requestId: 'oauth-fixture-request' }
        : {
            success: false,
            error: {
              code:
                status === 412
                  ? 'SUMMER_OAUTH_CLIENT_VERSION_CONFLICT'
                  : 'SUMMER_OAUTH_CLIENT_INVALID',
              message:
                status === 412
                  ? 'Client version has changed'
                  : 'Invalid callback',
            },
            requestId: 'oauth-fixture-request',
          }
    ),
  });
before(async () => {
  vite = await createServer({
    root: fileURLToPath(new URL('..', import.meta.url)),
    logLevel: 'error',
    server: { port: 0, strictPort: false },
    plugins: [
      {
        name: 'oauth-browser-fixture',
        configureServer(server) {
          server.middlewares.use(async (req, res, next) => {
            if (!req.url.startsWith('/__oauth/')) return next();
            res.setHeader('Content-Type', 'text/html');
            res.end(
              await server.transformIndexHtml(
                req.url,
                '<div id="root"></div><script type="module" src="/scripts/oauth-clients-browser-fixture.tsx"></script>'
              )
            );
          });
        },
      },
    ],
  });
  await vite.listen();
  origin = `http://127.0.0.1:${vite.httpServer.address().port}`;
  browser = await chromium.launch({
    headless: true,
    executablePath:
      process.env.CHROME_PATH ||
      '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
  });
});
after(async () => {
  await browser?.close();
  vite?.httpServer.closeAllConnections();
  await vite?.close();
});
async function open(write) {
  const page = await browser.newPage();
  page.setDefaultTimeout(4000);
  await page.route('**/api/security/**', (route) => {
    if (route.request().method() !== 'GET') return write(route);
    return reply(route, {
      items: route.request().url().includes('oauth-consents') ? [] : [client],
    });
  });
  await page.goto(`${origin}/__oauth/page`);
  await page
    .getByRole('cell', { name: 'Original client', exact: true })
    .waitFor();
  return page;
}
const modal = (page) =>
  page.locator('.arco-modal:visible').filter({ has: page.locator('form') });
test('metadata edit uses the listed version and suppresses duplicate submission', async () => {
  const writes = [];
  const page = await open(async (route) => {
    writes.push({
      method: route.request().method(),
      body: route.request().postDataJSON(),
    });
    await new Promise((resolve) => setTimeout(resolve, 150));
    return reply(route, {
      clientId: client.clientId,
      status: 'ACTIVE',
      version: 8,
    });
  });
  try {
    await page.getByRole('button', { name: '编辑', exact: true }).click();
    await modal(page)
      .getByRole('textbox', { name: '客户端名称', exact: true })
      .fill('Edited name');
    await modal(page)
      .getByRole('button', { name: '保存', exact: true })
      .evaluate((button) => {
        button.click();
        button.click();
      });
    await modal(page).waitFor({ state: 'hidden' });
    assert.equal(writes.length, 1);
    assert.equal(writes[0].method, 'PATCH');
    assert.equal(writes[0].body.version, 7);
    assert.equal(writes[0].body.clientName, 'Edited name');
    assert.deepEqual(writes[0].body.redirectUris, client.redirectUris);
    assert.deepEqual(writes[0].body.scopes, ['openid']);
    assert.equal(writes[0].body.clientSecret, undefined);
  } finally {
    await page.close();
  }
});

test('conflict, invalid, denied and unavailable responses preserve the draft and requestId', async () => {
  for (const status of [412, 400, 403, 503]) {
    const page = await open((route) => reply(route, null, status));
    try {
      await page.getByRole('button', { name: '编辑', exact: true }).click();
      await modal(page)
        .getByRole('textbox', { name: '客户端名称', exact: true })
        .fill('Retained draft');
      await modal(page)
        .getByRole('button', { name: '保存', exact: true })
        .click();
      await modal(page)
        .getByText(/oauth-fixture-request/)
        .waitFor();
      assert.equal(
        await modal(page)
          .getByRole('textbox', { name: '客户端名称', exact: true })
          .inputValue(),
        'Retained draft'
      );
      await modal(page)
        .getByRole('button', { name: '取消', exact: true })
        .click();
      await page.getByText('放弃未保存修改？', { exact: true }).waitFor();
      await page
        .locator('.arco-modal')
        .getByRole('button', { name: '取消', exact: true })
        .last()
        .click();
      assert.equal(
        await modal(page)
          .getByRole('textbox', { name: '客户端名称', exact: true })
          .inputValue(),
        'Retained draft'
      );
    } finally {
      await page.close();
    }
  }
});

test('required fields block writes and create does not reuse an edit identity', async () => {
  const writes = [];
  const page = await open((route) => {
    writes.push({
      method: route.request().method(),
      body: route.request().postDataJSON(),
    });
    return reply(route, { clientId: 'new-client', status: 'ACTIVE' });
  });
  try {
    await page.getByRole('button', { name: '编辑', exact: true }).click();
    await modal(page)
      .getByRole('textbox', { name: '客户端名称', exact: true })
      .fill('');
    await modal(page)
      .getByRole('button', { name: '保存', exact: true })
      .click();
    await modal(page).locator('.arco-form-message').waitFor();
    assert.equal(writes.length, 0);
    await modal(page)
      .getByRole('button', { name: '取消', exact: true })
      .click();
    await page.getByRole('button', { name: '放弃修改', exact: true }).click();
    await page
      .getByRole('button', { name: '注册 public 客户端', exact: true })
      .click();
    const fields = modal(page).getByRole('textbox');
    assert.equal(await fields.nth(0).inputValue(), '');
    assert.equal(await fields.nth(0).isEnabled(), true);
    await fields.nth(0).fill('new-client');
    await fields.nth(1).fill('New client');
    await fields.nth(2).fill('https://example.test/new');
    await fields.nth(3).fill('openid profile');
    await modal(page)
      .getByRole('button', { name: '保存', exact: true })
      .click();
    await modal(page).waitFor({ state: 'hidden' });
    assert.equal(writes.length, 1);
    assert.equal(writes[0].method, 'POST');
    assert.equal(writes[0].body.clientId, 'new-client');
    assert.equal(writes[0].body.publicClient, true);
    assert.equal(writes[0].body.version, undefined);
  } finally {
    await page.close();
  }
});
