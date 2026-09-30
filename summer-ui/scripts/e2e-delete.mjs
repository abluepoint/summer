import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { chromium } from 'playwright-core';

const passwordFile = process.env.SUMMER_E2E_PASSWORD_FILE;
if (!passwordFile)
  throw new Error('Set SUMMER_E2E_PASSWORD_FILE to a private temporary file');
const password = (await readFile(passwordFile, 'utf8')).trim();
const base = process.env.SUMMER_E2E_ORIGIN || 'http://127.0.0.1:5173';
const username = `zzzz-delete-${Date.now().toString(36)}`;
const email = `${username}@summer.local`;
const browser = await chromium.launch({
  headless: true,
  executablePath:
    process.env.CHROME_PATH ||
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
});

try {
  const page = await browser.newPage({
    viewport: { width: 1365, height: 900 },
  });
  const unexpected = [];
  page.on('pageerror', (error) => unexpected.push(error.message));
  let bearer;
  await page.route('**/api/customers?*', async (route) => {
    bearer ||= route.request().headers().authorization;
    await route.continue();
  });
  await page.goto(`${base}/customer/page`);
  await page.locator('input[autocomplete="username"]').fill('summer-user');
  await page.locator('input[autocomplete="current-password"]').fill(password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await page.waitForURL(`${base}/customer/page`);
  await page.getByRole('button', { name: 'demo', exact: true }).waitFor();
  assert.ok(bearer?.startsWith('Bearer '));

  const headers = { Authorization: bearer };
  const customerUrl = `${base}/api/customers/${username}`;
  const created = await page.request.post(`${base}/api/customers`, {
    headers,
    data: { username, email, displayName: 'Delete acceptance' },
  });
  assert.equal(created.status(), 200);

  try {
    await page.goto(`${base}/customer/page/${username}`);
    await page.getByText(email).waitFor();
    await page.getByRole('button', { name: '删除', exact: true }).click();
    const modal = page.getByRole('dialog');
    const confirm = modal.getByRole('button', { name: '永久删除' });
    const confirmationInput = modal.getByPlaceholder('准确的用户名');
    assert.equal(
      await confirmationInput.evaluate(
        (element) => document.activeElement === element
      ),
      true,
      'confirmation input should receive focus'
    );
    assert.equal(await confirm.isDisabled(), true);
    await confirmationInput.fill(`${username} `);
    assert.equal(await confirm.isDisabled(), true);
    await confirmationInput.fill(username);
    assert.equal(await confirm.isEnabled(), true);
    if (process.env.SUMMER_E2E_SCREENSHOTS === '1')
      await page.screenshot({
        path: '.e2e-delete-confirm.png',
        fullPage: true,
      });
    await page.setViewportSize({ width: 390, height: 844 });
    assert.equal(
      await page.evaluate(
        () =>
          document.documentElement.scrollWidth <=
          document.documentElement.clientWidth
      ),
      true,
      'narrow confirmation must not overflow horizontally'
    );
    const narrowConfirm = await confirm.boundingBox();
    assert.ok(
      narrowConfirm &&
        narrowConfirm.x >= 0 &&
        narrowConfirm.x + narrowConfirm.width <= 390,
      'confirm action must be visible on a narrow screen'
    );
    if (process.env.SUMMER_E2E_SCREENSHOTS === '1')
      await page.screenshot({ path: '.e2e-delete-narrow.png', fullPage: true });
    await page.setViewportSize({ width: 1365, height: 900 });

    for (const status of [403, 404]) {
      await page.route(
        `**/api/customers/${username}`,
        (route) =>
          route.request().method() === 'DELETE'
            ? route.fulfill({
                status,
                contentType: 'application/json',
                body: JSON.stringify({
                  success: false,
                  error: {
                    code: `TEST_${status}`,
                    message: '',
                    violations: [],
                  },
                  requestId: `delete-${status}`,
                }),
              })
            : route.continue(),
        { times: 1 }
      );
      await confirm.click();
      await modal.getByText(`requestId: delete-${status}`).waitFor();
      assert.equal(await modal.isVisible(), true);
      await modal.getByRole('button', { name: '取消' }).click();
      await page.getByRole('button', { name: '删除', exact: true }).click();
      await modal.getByPlaceholder('准确的用户名').fill(username);
    }

    await page.route(
      `**/api/customers/${username}`,
      (route) =>
        route.request().method() === 'DELETE'
          ? route.abort('failed')
          : route.continue(),
      { times: 1 }
    );
    await confirm.click();
    await modal.getByText('删除失败，请检查网络并明确重试。').waitFor();
    assert.equal(await modal.isVisible(), true);
    await modal.getByRole('button', { name: '取消' }).click();

    const all = await page.request.get(
      `${base}/api/customers?page=0&size=100&sort=username&direction=asc&projection=detail`,
      { headers }
    );
    assert.equal(all.status(), 200);
    const body = await all.json();
    const lastPage = body.data.totalElements - 1;
    assert.equal(body.data.items.at(-1).username, username);
    await page.goto(
      `${base}/customer/page?page=${lastPage}&size=1&sort=username&direction=asc`
    );
    await page.getByRole('button', { name: username, exact: true }).waitFor();
    await page.getByRole('button', { name: '删除', exact: true }).click();
    await modal.getByPlaceholder('准确的用户名').fill(username);
    await confirm.click();
    await page.waitForURL(
      `${base}/customer/page?page=${
        lastPage - 1
      }&size=1&sort=username&direction=asc`
    );
    assert.equal(
      (await page.request.get(customerUrl, { headers })).status(),
      404
    );
    const reused = await page.request.post(`${base}/api/customers`, {
      headers,
      data: { username, email, displayName: 'Reused username' },
    });
    assert.equal(reused.status(), 200);

    await page.goto(`${base}/customer/page/${username}`);
    await page.getByText('Reused username').waitFor();
    await page.getByRole('button', { name: '删除', exact: true }).click();
    await modal.getByPlaceholder('准确的用户名').fill(username);
    let unauthorizedDeletes = 0;
    await page.route(
      `**/api/customers/${username}`,
      (route) => {
        if (route.request().method() !== 'DELETE') return route.continue();
        unauthorizedDeletes++;
        return route.fulfill({
          status: 401,
          contentType: 'application/json',
          body: JSON.stringify({
            success: false,
            error: { code: 'TEST_401', message: '', violations: [] },
          }),
        });
      },
      { times: 1 }
    );
    const rejected = page.waitForResponse(
      (response) =>
        response.request().method() === 'DELETE' && response.status() === 401
    );
    const callback = page.waitForURL('**/oauth2/callback**');
    await confirm.click();
    await rejected;
    await callback;
    await page.waitForURL(`${base}/customer/page/${username}`);
    await page.getByText('Reused username').waitFor();
    assert.equal(unauthorizedDeletes, 1, '401 must not replay DELETE');
    assert.equal(
      (await page.request.get(customerUrl, { headers })).status(),
      200
    );
    assert.deepEqual(unexpected, []);
    console.log(
      'Real Customer deletion, last-page fallback, 404, username reuse and one-shot 401 passed'
    );
  } finally {
    const cleanup = await page.request.delete(customerUrl, { headers });
    assert.ok([204, 404].includes(cleanup.status()), 'test Customer must be removed');
  }
} finally {
  await browser.close();
}
