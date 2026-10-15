// T34-CTX-01: real PKCE/shared tenant selection. No mocked HTTP or persisted claims.
import { createRequire } from 'node:module';
import { writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
const require = createRequire(process.cwd() + '/package.json');
const { chromium } = require('playwright-core');
const out = process.env.SUMMER_T34_EVIDENCE_DIR, second = process.env.SUMMER_T34_SECOND_ENTERPRISE;
const base = 'http://127.0.0.1:20573';
const supplement = process.env.SUMMER_T34_CONTEXT_SUPPLEMENT === '1';
const result = { defect: 'T34-CTX-01', networkMocking: false, networkLatencyMs: supplement ? 0 : 300, supplement, checks: [], responses: [], pageErrors: 0, complete: false };
let browser, page;
const check = (name, passed) => result.checks.push({ name, passed: Boolean(passed) });
function pending(path) {
  const request = page.waitForResponse(r => r.request().method() === 'GET' && new URL(r.url()).pathname === path);
  request.catch(() => {}); return request;
}
async function fresh(username, password) {
  await page?.close();
  page = await browser.newPage({ viewport: { width: 1440, height: 1100 } });
  page.setDefaultTimeout(30000);
  page.on('pageerror', () => result.pageErrors++);
  page.on('response', r => {
    const path = new URL(r.url()).pathname;
    if (path === '/oauth2/token' || /\/(statistics|menu-manifest|org-tree|context)$/.test(path)) result.responses.push({ path, method: r.request().method(), status: r.status() });
  });
  await page.goto(base + '/statistics');
  await page.locator('input[autocomplete="username"]').fill(username);
  await page.locator('input[autocomplete="current-password"]').fill(password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await page.waitForURL(base + '/statistics');
}
async function claims() {
  return page.evaluate(async () => {
    const { getAuthenticatedUser } = await import('/src/auth/oidc.ts');
    const user = await getAuthenticatedUser();
    const data = JSON.parse(atob(user.access_token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
    return { multipleActiveMemberships: Array.isArray(data.enterprise_memberships) && data.enterprise_memberships.filter(m => m.status === 'ACTIVE').length > 1, hasActiveEnterprise: Boolean(data.active_enterprise_id), hasOrganizationContext: Boolean(data.organization_context) };
  });
}
async function choose(label, id) {
  await page.locator(`[aria-label="${label}"]`).click();
  await page.getByRole('option', { name: id, exact: true }).click();
}
async function selected(label, id) {
  return (await page.locator(`[aria-label="${label}"]`).innerText()).includes(id);
}
async function screenshot(name) {
  await page.screenshot({ path: resolve(out, name), fullPage: true, animations: 'disabled', timeout: 15000 });
}
try {
  if (!/^[A-Za-z0-9_-]+$/.test(second || '')) throw new Error('invalid_second_enterprise');
  browser = await chromium.launch({ headless: true, executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' });
  await fresh(process.env.SUMMER_E2E_USERNAME, process.env.SUMMER_E2E_PASSWORD);
  await page.getByText('选择活动租户', { exact: true }).first().waitFor();
  result.initialClaims = await claims();
  if (!supplement) check('fresh_multiple_active_members_without_active_or_org_claim', result.initialClaims.multipleActiveMemberships && !result.initialClaims.hasActiveEnterprise && !result.initialClaims.hasOrganizationContext);
  check('unselected_entry_visible_without_automatic_first_tenant', await page.locator('[aria-label="活动租户"]').isVisible() && !result.responses.some(r => r.method === 'GET' && r.path.startsWith('/api/v1/enterprises/') && /\/(statistics|menu-manifest)$/.test(r.path)) && await page.locator('.arco-statistic').count() === 0);
  await screenshot('context-choice.png');
  const acmePath = '/api/v1/enterprises/ent-acme/statistics';
  let response = pending(acmePath); await choose('活动租户', 'ent-acme');
  let reply = await response; let data = (await reply.json()).data;
  await page.locator('.arco-statistic').first().waitFor();
  if (!supplement) check('initial_choice_loads_real_menu_and_current_tenant_statistics', reply.status() === 200 && data.scope.enterpriseId === 'ent-acme' && result.responses.some(r => r.path === '/api/v1/enterprises/ent-acme/menu-manifest' && r.status === 200));
  if (!supplement) check('navbar_and_statistics_selector_share_initial_choice', await selected('全局活动租户', 'ent-acme') && await selected('活动租户', 'ent-acme'));
  if (!supplement) {
  // Delay actual transport so the intervening loading state is observable. Responses are not replaced.
  const cdp = await page.context().newCDPSession(page);
  await cdp.send('Network.enable');
  await cdp.send('Network.emulateNetworkConditions', { offline: false, latency: 300, downloadThroughput: -1, uploadThroughput: -1 });
  let responseArrived = false;
  response = pending('/api/v1/enterprises/' + second + '/statistics');
  response.then(() => { responseArrived = true; }).catch(() => {});
  await choose('全局活动租户', second);
  await page.locator('.arco-statistic').first().waitFor({ state: 'hidden' });
  check('tenant_switch_clears_previous_cards_before_new_response', !responseArrived && await page.locator('.arco-statistic').count() === 0);
  reply = await response; data = (await reply.json()).data;
  await page.locator('.arco-statistic').first().waitFor();
  check('global_switch_loads_only_selected_tenant_scope', reply.status() === 200 && data.scope.enterpriseId === second && await selected('活动租户', second));
  await screenshot('context-second-tenant.png');
  response = pending('/api/v1/enterprises/' + second + '/org-tree');
  await page.locator('.arco-menu').getByText('组织与成员', { exact: true }).click();
  reply = await response; await page.waitForURL(base + '/organization');
  check('client_navigation_preserves_shared_enterprise_on_organization_page', reply.status() === 200 && await selected('活动租户', second) && await selected('全局活动租户', second));
  response = pending('/api/v1/enterprises/ent-acme/org-tree');
  await choose('活动租户', 'ent-acme'); reply = await response;
  check('page_selector_updates_global_context_and_request_scope', reply.status() === 200 && await selected('全局活动租户', 'ent-acme'));
  response = pending(acmePath); await page.goBack(); reply = await response;
  await page.waitForURL(base + '/statistics'); await page.locator('.arco-statistic').first().waitFor();
  check('navigation_back_uses_latest_shared_choice_without_old_local_state', reply.status() === 200 && (await reply.json()).data.scope.enterpriseId === 'ent-acme' && await selected('活动租户', 'ent-acme'));
  result.finalAdminClaims = await claims();
  check('ui_selection_does_not_set_active_or_org_claims_or_write_context', !result.finalAdminClaims.hasActiveEnterprise && !result.finalAdminClaims.hasOrganizationContext && !result.responses.some(r => r.method === 'PUT' && r.path.endsWith('/context')));
  await screenshot('context-returned-acme.png');
  }
  await fresh(process.env.SUMMER_E2E_READER, process.env.SUMMER_E2E_READER_PASSWORD);
  await page.getByText('当前范围和时间窗口没有数据。', { exact: true }).waitFor();
  await page.locator('[aria-label="全局活动租户"]').click();
  if (!supplement) check('new_identity_cannot_select_previous_users_other_tenant', await page.getByRole('option', { name: second, exact: true }).count() === 0 && await selected('全局活动租户', 'ent-acme'));
  await page.keyboard.press('Escape');
  const foreignResponse = pending('/api/v1/enterprises/' + second + '/statistics');
  result.foreignTransport = await page.evaluate(async (id) => {
    const { getAuthenticatedUser } = await import('/src/auth/oidc.ts');
    const user = await getAuthenticatedUser();
    try {
      await fetch('/api/v1/enterprises/' + id + '/statistics', { headers: { Authorization: 'Bearer ' + user.access_token } });
      return { rejected: false };
    } catch (error) { return { rejected: true, failureType: error.name }; }
  }, second);
  const foreign = await foreignResponse;
  check('server_still_rejects_foreign_tenant_despite_ui_selection_feature', foreign.status() === 403);
  await screenshot('context-reader-isolation.png');
  check('no_unhandled_page_errors', result.pageErrors === 0);
  result.sharedUxBlocked = result.checks.some(c => !c.passed); result.complete = true;
} catch (error) {
  result.failureType = error.name;
  result.localFrames = error.stack?.match(/context-retest-browser\.mjs:\d+:\d+/g) || [];
  if (page) {
    result.failurePath = new URL(page.url()).pathname;
    if (!await page.locator('input[autocomplete="current-password"]').count() && result.failurePath !== '/oauth2/callback') await screenshot('context-retest-incomplete.png').catch(() => {});
  }
} finally {
  const save = () => writeFile(resolve(out, 'context-probe-result.json'), JSON.stringify(result, null, 2) + '\n');
  await save();
  await browser?.close(); result.browserClosed = true; await save();
}
process.exitCode = result.complete && result.checks.every(c => c.passed) ? 0 : 1;
