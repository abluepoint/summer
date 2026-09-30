import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { chromium } from 'playwright-core';

const passwordFile = process.env.SUMMER_E2E_PASSWORD_FILE;
if (!passwordFile)
  throw new Error('Set SUMMER_E2E_PASSWORD_FILE to a private temporary file');
const password = (await readFile(passwordFile, 'utf8')).trim();
const base = process.env.SUMMER_E2E_ORIGIN || 'http://127.0.0.1:5173';
const browser = await chromium.launch({
  headless: true,
  executablePath:
    process.env.CHROME_PATH ||
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
});

async function signedInPage(route = '/customer/page?recovery=ready') {
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto(`${base}${route}`);
  await page
    .locator('input[autocomplete="username"]')
    .fill(process.env.SUMMER_E2E_USERNAME || 'summer-user');
  await page.locator('input[autocomplete="current-password"]').fill(password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await page.getByRole('button', { name: 'demo', exact: true }).waitFor();
  return { context, page };
}

function failure(status, requestId) {
  return {
    status,
    contentType: 'application/json',
    body: JSON.stringify({
      success: false,
      error: { code: `TEST_${status}`, message: `simulated ${status}` },
      requestId,
    }),
  };
}

async function navigateInApp(page, path) {
  await page.evaluate((nextPath) => {
    window.history.pushState(null, '', nextPath);
    window.dispatchEvent(new PopStateEvent('popstate'));
  }, path);
}

try {
  {
    const { context, page } = await signedInPage();
    let authorizationRequests = 0;
    page.on('request', (request) => {
      if (new URL(request.url()).pathname === '/oauth2/authorize')
        authorizationRequests++;
    });
    await page.route('**/api/customers?*', (route) =>
      route.fulfill(failure(403, 'recovery-403'))
    );
    await navigateInApp(page, '/customer/page?recovery=forbidden');
    await page.getByText('requestId: recovery-403').waitFor();
    assert.equal(await page.locator('.arco-avatar').count(), 1);
    await page.unroute('**/api/customers?*');
    await page.route('**/api/customers?*', (route) => route.abort('failed'));
    await navigateInApp(page, '/customer/page?recovery=network');
    await page.getByText('网络请求失败，请检查连接后重试。').waitFor();
    assert.equal(authorizationRequests, 0, '403 and network failure keep auth');
    await context.close();
  }

  {
    const route = '/customer/drawer?page=0&size=20&sort=username&direction=asc';
    const { context, page } = await signedInPage(route);
    const username = `c${Date.now().toString(36)}`;
    let posts = 0;
    let writeAuthorization;
    page.on('request', (request) => {
      if (
        request.method() === 'POST' &&
        new URL(request.url()).pathname === '/api/customers'
      ) {
        posts++;
        if (posts === 2) writeAuthorization = request.headers().authorization;
      }
    });
    const drawer = page.locator('.arco-drawer');
    const field = (name) => drawer.getByRole('textbox', { name });
    try {
      await page.getByRole('button', { name: '新增客户' }).click();
      await field('用户名').fill(username);
      await field('邮箱').fill(`${username}@example.test`);
      await field('名称').fill('Draft before 401');
      await page.route(
        '**/api/customers',
        (requestRoute) => requestRoute.fulfill(failure(401, 'draft-401')),
        { times: 1 }
      );
      const authorize = page.waitForRequest(
        (request) => new URL(request.url()).pathname === '/oauth2/authorize'
      );
      const restoredRead = page.waitForResponse(
        (response) =>
          response.url().includes('/api/customers?') &&
          response.status() === 200
      );
      await drawer.getByRole('button', { name: '创建', exact: true }).click();
      await authorize;
      await restoredRead;
      await field('用户名').waitFor();
      assert.equal(await field('用户名').inputValue(), username);
      assert.equal(
        await field('邮箱').inputValue(),
        `${username}@example.test`
      );
      assert.equal(await field('名称').inputValue(), 'Draft before 401');
      assert.equal(
        new URL(page.url()).pathname + new URL(page.url()).search,
        route
      );
      await page.waitForTimeout(500);
      assert.equal(posts, 1, 'restored draft must not replay the failed POST');
      await field('名称').fill('Draft edited after 401');
      await drawer.getByRole('button', { name: '创建', exact: true }).click();
      await drawer.waitFor({ state: 'hidden' });
      assert.equal(posts, 2, 'manual retry sends exactly one new POST');
      assert.ok(writeAuthorization, 'manual retry must carry authorization');
      const saved = await page.request.get(
        `${base}/api/customers/${username}`,
        { headers: { Authorization: writeAuthorization } }
      );
      assert.equal(saved.status(), 200);
      assert.equal(
        (await saved.json()).data.displayName,
        'Draft edited after 401'
      );
    } finally {
      if (posts === 2 && writeAuthorization) {
        const removed = await page.request.delete(
          `${base}/api/customers/${username}`,
          { headers: { Authorization: writeAuthorization } }
        );
        assert.equal(removed.status(), 204, 'test Customer must be removed');
      }
      await context.close();
    }
  }

  {
    const { context, page } = await signedInPage();
    let writes = 0;
    let authorizationRequests = 0;
    page.on('request', (request) => {
      if (new URL(request.url()).pathname === '/oauth2/authorize')
        authorizationRequests++;
    });
    await page.route('**/api/customers', (route) => {
      if (route.request().method() !== 'POST') return route.continue();
      writes++;
      return route.fulfill(failure(401, 'recovery-write-401'));
    });
    const source = await (await fetch(`${base}/src/auth/requests.ts`)).text();
    const axiosUrl = source.match(
      /from "(\/node_modules\/\.vite\/axios\.js\?[^\"]+)"/
    )?.[1];
    assert.ok(axiosUrl, 'Vite must serve the application Axios singleton');
    const shape = await page.evaluate(async (url) => {
      try {
        const module = await import(url);
        const imported = module.default;
        const axios = imported.__esModule ? imported.default : imported;
        return { canPost: typeof axios?.post === 'function' };
      } catch (error) {
        return { canPost: false, error: error?.name };
      }
    }, axiosUrl);
    assert.equal(shape.canPost, true, JSON.stringify(shape));
    const restoredRead = page.waitForResponse(
      (response) =>
        response.url().includes('/api/customers?') && response.status() === 200
    );
    await page
      .evaluate(async (url) => {
        const module = await import(url);
        const imported = module.default;
        const axios = imported.__esModule ? imported.default : imported;
        await axios
          .post('/api/customers', { username: 'not-written' })
          .catch(() => {});
      }, axiosUrl)
      .catch(() => {});
    await restoredRead.catch(() => {
      throw new Error(
        `write recovery stalled: writes=${writes}, authorize=${authorizationRequests}, path=${
          new URL(page.url()).pathname
        }`
      );
    });
    await page.waitForURL(`${base}/customer/page?recovery=ready`);
    assert.equal(writes, 1, 'a failed write must never be replayed');
    await context.close();
  }

  {
    const { context, page } = await signedInPage();
    let authorizationRequests = 0;
    let unauthorizedResponses = 0;
    page.on('request', (request) => {
      if (new URL(request.url()).pathname === '/oauth2/authorize')
        authorizationRequests++;
    });
    const second401 = new Promise((resolve) => {
      page.on('response', (response) => {
        if (
          response.url().includes('/api/customers?') &&
          response.status() === 401 &&
          ++unauthorizedResponses === 2
        )
          resolve();
      });
    });
    await page.route('**/api/customers?*', (route) =>
      route.fulfill(failure(401, 'recovery-loop-401'))
    );
    await navigateInApp(page, '/customer/page?recovery=loop');
    let timeout;
    try {
      await Promise.race([
        second401,
        new Promise((_, reject) => {
          timeout = setTimeout(
            () => reject(new Error('second 401 was not observed')),
            20000
          );
        }),
      ]);
    } finally {
      clearTimeout(timeout);
    }
    await page.waitForTimeout(1200);
    assert.equal(authorizationRequests, 1, '401 may restart PKCE once');
    assert.equal(unauthorizedResponses, 2, 'the second 401 must not loop');
    await page.getByText('requestId: recovery-loop-401').waitFor();
    await context.close();
  }

  for (const reason of ['missing', 'expired']) {
    const { context, page } = await signedInPage();
    await page.evaluate(async (tokenReason) => {
      const { oidcManager } = await import('/src/auth/oidc.ts');
      if (tokenReason === 'missing') {
        await oidcManager.removeUser();
      } else {
        const user = await oidcManager.getUser();
        user.expires_at = Math.floor(Date.now() / 1000) - 1;
        await oidcManager.storeUser(user);
      }
    }, reason);
    await navigateInApp(page, `/customer/page?recovery=${reason}`);
    await page
      .getByText(
        reason === 'missing'
          ? '未找到有效令牌，请登录后继续。'
          : '令牌已过期，请登录后继续。'
      )
      .waitFor();
    await page.waitForURL(`${base}/customer/page?recovery=${reason}`);
    await context.close();
  }

  console.log(
    'Browser 403, network, missing/expired token, draft edit and manual retry without replay, view restore, and 401 limit passed'
  );
} finally {
  await browser.close();
}
