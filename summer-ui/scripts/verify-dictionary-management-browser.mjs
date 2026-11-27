// Browser component checks with controlled HTTP; no live data or real login.
import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright-core';
import { createServer } from 'vite';

let vite, browser, origin;
const errors = [];
const base = '/api/v1/platform/dictionaries';
const makeRow = (code, name) => ({
  code,
  name,
  publicReadable: false,
  enabled: true,
  published: false,
  publishedVersion: null,
  draftRevision: 0,
  effectiveRevision: 0,
  items: [],
});
const json = (route, data, status = 200) =>
  route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(
      status === 200
        ? { success: true, data, requestId: 'ui-fixture' }
        : {
            success: false,
            error: { code: `FIXTURE_${status}`, message: 'controlled failure' },
            requestId: `fixture-${status}`,
          }
    ),
  });
before(async () => {
  vite = await createServer({
    root: fileURLToPath(new URL('..', import.meta.url)),
    logLevel: 'error',
    server: { port: 0, hmr: false },
    plugins: [
      {
        name: 'dictionary-management-fixture',
        configureServer(server) {
          server.middlewares.use(async (req, res, next) => {
            if (!req.url.startsWith('/__t18-t19/')) return next();
            res.setHeader('Content-Type', 'text/html');
            res.end(
              await server.transformIndexHtml(
                req.url,
                '<div id="root"></div><script type="module" src="/scripts/t18-t19-browser-fixture.tsx"></script>'
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
  assert.deepEqual(errors, []);
});
async function setup(initial = [makeRow('customer-type', '客户类型')], groupsOnly = false) {
  const rows = structuredClone(initial);
  const drafts = new Map(
    rows.map((row) => [
      row.code,
      { ...row, publishedVersion: 0, groups: [], itemOrder: [] },
    ])
  );
  const writes = [];
  const state = {
    createStatus: 200,
    saveStatus: 200,
    listStatus: 200,
    history: [],
  };
  const page = await browser.newPage({
    viewport: { width: 1280, height: 900 },
  });
  page.setDefaultTimeout(8000);
  page.on('pageerror', (e) => errors.push(e.message));
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    const method = route.request().method();
    if (method === 'POST' && path === base) {
      const body = route.request().postDataJSON();
      writes.push({ method, body });
      if (state.createGate) await state.createGate;
      if (state.createStatus !== 200)
        return json(route, null, state.createStatus);
      const row = { ...makeRow(body.code, body.name), ...body };
      rows.push(row);
      drafts.set(row.code, {
        ...row,
        publishedVersion: 0,
        groups: [],
        itemOrder: [],
      });
      return json(route, row);
    }
    if (path === base) return json(route, rows, state.listStatus);
    const code = path.slice(base.length + 1).split('/')[0];
    if (path.endsWith('/disable') && method === 'POST') {
      writes.push({ method, body: route.request().postDataJSON() });
      return json(route, null, 403);
    }
    if (path.endsWith('/draft') && method === 'PUT') {
      const body = route.request().postDataJSON();
      writes.push({ method, body });
      if (state.saveStatus !== 200) return json(route, null, state.saveStatus);
      const saved = {
        ...drafts.get(code),
        ...body,
        draftRevision: body.expectedDraftRevision + 1,
        groups: body.groups.map(group => ({
          ...group,
          id: group.id.startsWith('new:') ? `saved-${group.code}` : group.id,
          everPublished: drafts.get(code).groups.find(previous => previous.id === group.id)?.everPublished ?? false,
        })),
        items: body.items.map((item, index) => ({
          ...item,
          sortOrder: item.sortOrder ?? index,
          everPublished: false,
        })),
      };
      drafts.set(code, saved);
      Object.assign(
        rows.find((row) => row.code === code),
        { name: saved.name, items: saved.items }
      );
      return json(route, saved);
    }
    if (path.endsWith('/draft')) return json(route, drafts.get(code));
    if (path.endsWith('/effective')) {
      const row = rows.find((row) => row.code === code);
      return json(route, {
        code,
        name: row.published ? row.name : null,
        status: row.published ? 'PUBLISHED' : 'UNPUBLISHED',
        publishedVersion: row.publishedVersion || 0,
        publicReadable: row.publicReadable,
        enabled: row.enabled,
        effectiveRevision: 0,
        items: row.published ? row.items : [],
        groups: row.groups || [],
      });
    }
    if (path.endsWith('/versions')) return json(route, state.history);
    if (/\/versions\/\d+$/.test(path))
      return json(
        route,
        state.history.find(
          (row) => row.version === Number(path.split('/').pop())
        )
      );
    throw new Error(`Unexpected fixture endpoint: ${method} ${path}`);
  });
  await page.goto(origin + '/__t18-t19/dictionary-manage' + (groupsOnly ? '?groups' : ''));
  await page.getByRole('button', { name: groupsOnly ? '新建字典组' : '新建字典', exact: true }).first().waitFor();
  await page.waitForFunction(
    () => !document.querySelector('.arco-spin-loading')
  );
  return { page, rows, drafts, writes, state };
}

test('list search, details and return preserve the filter; existing dictionaries default to effective content', async () => {
  const { page } = await setup([
    makeRow('customer-type', '客户类型'),
    makeRow('order-status', '订单状态'),
  ]);
  try {
    await page.getByLabel('搜索字典').fill('订单');
    assert.equal(
      await page.getByRole('button', { name: '详情', exact: true }).count(),
      1
    );
    await page.getByRole('button', { name: '详情', exact: true }).click();
    await page.getByText('字典详情 · order-status', { exact: true }).waitFor();
    await page
      .getByRole('button', { name: '编辑字典值', exact: true })
      .waitFor();
    await page
      .getByRole('button', { name: '返回字典列表', exact: true })
      .click();
    assert.equal(await page.getByLabel('搜索字典').inputValue(), '订单');
    assert.equal(
      await page.getByRole('button', { name: '详情', exact: true }).count(),
      1
    );
  } finally {
    await page.close();
  }
});

test('create from an empty list, add and edit values in detail, save and reopen the persisted draft', async () => {
  const { page, writes } = await setup([]);
  try {
    await page.getByText('暂无字典，点击“新建字典”开始创建').waitFor();
    await page.getByRole('button', { name: '新建字典', exact: true }).click();
    await page.getByLabel('新字典名称').fill('客户类型');
    await page.getByLabel('新字典编码').fill('customer-type');
    await page
      .getByRole('button', { name: '创建并编辑字典值', exact: true })
      .click();
    await page.getByRole('button', { name: '新增字典值', exact: true }).click();
    await page.getByLabel('值 1', { exact: true }).fill('vip');
    await page.getByLabel('显示名称 1', { exact: true }).fill('重点客户');
    await page.getByLabel('说明 1', { exact: true }).fill('优先服务');
    await page.getByRole('button', { name: '保存草稿', exact: true }).click();
    await page.getByText('草稿修订 1', { exact: true }).waitFor();
    assert.equal(writes.length, 2);
    assert.deepEqual(writes[0].body, {
      name: '客户类型',
      code: 'customer-type',
      publicReadable: false,
      enabled: true,
    });
    assert.equal(writes[1].body.expectedDraftRevision, 0);
    assert.equal(writes[1].body.items[0].value, 'vip');
    assert.deepEqual(writes[1].body.groups, []);
    await page
      .getByRole('button', { name: '返回字典列表', exact: true })
      .click();
    await page.getByRole('button', { name: '详情', exact: true }).click();
    await page.getByRole('button', { name: '编辑字典值', exact: true }).click();
    await page.getByLabel('显示名称 1', { exact: true }).fill('贵宾客户');
    await page.getByRole('button', { name: '保存草稿', exact: true }).click();
    await page.getByText('草稿修订 2', { exact: true }).waitFor();
    assert.equal(writes[2].body.items[0].label, '贵宾客户');
    assert.equal(writes[2].body.expectedDraftRevision, 1);
    if (process.env.DICTIONARY_SCREENSHOT_DIR) {
      await page.screenshot({
        path: `${process.env.DICTIONARY_SCREENSHOT_DIR}/detail.png`,
        fullPage: true,
      });
      await page
        .getByRole('button', { name: '返回字典列表', exact: true })
        .click();
      await page.getByRole('button', { name: '详情', exact: true }).waitFor();
      await page.screenshot({
        path: `${process.env.DICTIONARY_SCREENSHOT_DIR}/list.png`,
        fullPage: true,
      });
      await page.getByRole('button', { name: '新建字典', exact: true }).click();
      await page.getByLabel('新字典名称').waitFor();
      await page.screenshot({
        path: `${process.env.DICTIONARY_SCREENSHOT_DIR}/create.png`,
        fullPage: true,
      });
    }
  } finally {
    await page.close();
  }
});

test('validation blocks invalid requests and conflicts keep the create form editable', async () => {
  const { page, writes, state } = await setup();
  try {
    await page.getByRole('button', { name: '新建字典', exact: true }).click();
    await page.getByLabel('新字典名称').fill('  ');
    await page.getByLabel('新字典编码').fill('BAD');
    await page
      .getByRole('button', { name: '创建并编辑字典值', exact: true })
      .click();
    await page.getByText('请输入符合规则的字典编码', { exact: true }).waitFor();
    assert.equal(writes.length, 0);
    await page.getByLabel('新字典名称').fill('客户类型');
    await page.getByLabel('新字典编码').fill('customer-type');
    state.createStatus = 409;
    await page
      .getByRole('button', { name: '创建并编辑字典值', exact: true })
      .click();
    await page.getByText(/字典编码已存在.*fixture-409/).waitFor();
    assert.equal(await page.getByLabel('新字典名称').inputValue(), '客户类型');
    assert.equal(await page.getByLabel('新字典编码').isEnabled(), true);
  } finally {
    await page.close();
  }
});

test('failed draft saves retain edits; leaving requires confirmation', async () => {
  const { page, state } = await setup();
  try {
    await page.getByRole('button', { name: '详情', exact: true }).click();
    await page.getByRole('button', { name: '编辑字典值', exact: true }).click();
    await page.getByRole('button', { name: '新增字典值', exact: true }).click();
    await page.getByLabel('值 1', { exact: true }).fill('vip');
    await page.getByLabel('显示名称 1', { exact: true }).fill('保留的输入');
    state.saveStatus = 409;
    await page.getByRole('button', { name: '保存草稿', exact: true }).click();
    await page.getByText(/草稿已被其他客户端修改/).waitFor();
    assert.equal(
      await page.getByLabel('显示名称 1', { exact: true }).inputValue(),
      '保留的输入'
    );
    page.once('dialog', (dialog) => dialog.dismiss());
    await page
      .getByRole('button', { name: '返回字典列表', exact: true })
      .click();
    assert.equal(
      await page.getByLabel('显示名称 1', { exact: true }).inputValue(),
      '保留的输入'
    );
    page.once('dialog', (dialog) => dialog.accept());
    await page
      .getByRole('button', { name: '返回字典列表', exact: true })
      .click();
    await page.getByRole('button', { name: '新建字典', exact: true }).waitFor();
  } finally {
    await page.close();
  }
});

test('list failures show a retryable error and refresh recovers', async () => {
  const { page, state } = await setup();
  try {
    state.listStatus = 403;
    await page.getByRole('button', { name: '刷新列表', exact: true }).click();
    await page.getByText('字典列表加载失败，请重试', { exact: true }).waitFor();
    state.listStatus = 200;
    await page.getByRole('button', { name: '刷新列表', exact: true }).click();
    await page.getByRole('button', { name: '详情', exact: true }).waitFor();
  } finally {
    await page.close();
  }
});

test('a pending create blocks duplicate submission and cancellation', async () => {
  const { page, state, writes } = await setup([]);
  let release;
  state.createGate = new Promise((resolve) => {
    release = resolve;
  });
  try {
    await page.getByRole('button', { name: '新建字典', exact: true }).click();
    await page.getByLabel('新字典名称').fill('客户类型');
    await page.getByLabel('新字典编码').fill('customer-type');
    const started = page.waitForRequest(
      (request) =>
        request.method() === 'POST' && new URL(request.url()).pathname === base
    );
    await page
      .getByRole('button', { name: '创建并编辑字典值', exact: true })
      .dblclick();
    await started;
    assert.equal(writes.length, 1);
    await page.getByRole('button', { name: '取消', exact: true }).click();
    assert.equal(await page.getByLabel('新字典名称').isVisible(), true);
    release();
    await page
      .getByRole('button', { name: '新增字典值', exact: true })
      .waitFor();
  } finally {
    release();
    await page.close();
  }
});

test('all detail views omit group definitions while value edits preserve legacy data and remove deleted member references', async () => {
  const items = [
    {
      id: 'item-standard',
      value: 'standard',
      label: '普通客户',
      description: '',
      enabled: true,
      everPublished: true,
      sortOrder: 0,
    },
    {
      id: 'item-trial',
      value: 'trial',
      label: '试用客户',
      description: '',
      enabled: true,
      everPublished: false,
      sortOrder: 1,
    },
  ];
  const legacyGroup = {
    id: 'legacy-group',
    code: 'legacy-customers',
    name: '历史兼容组',
    enabled: true,
    everPublished: true,
    memberIds: ['item-trial', 'item-standard'],
  };
  const snapshotGroup = {
    ...legacyGroup,
    memberValues: ['trial', 'standard'],
  };
  const row = {
    ...makeRow('customer-type', '客户类型'),
    published: true,
    publishedVersion: 1,
    items,
    groups: [snapshotGroup],
  };
  const { page, drafts, state, writes } = await setup([row]);
  Object.assign(drafts.get(row.code), {
    groups: [legacyGroup],
    itemOrder: items.map((item) => item.id),
  });
  state.history = [
    {
      version: 1,
      status: 'PUBLISHED',
      createdAt: '2026-11-19',
      createdBy: 'fixture',
      snapshot: row,
    },
  ];
  const assertNoGroupUI = async () =>
    assert.doesNotMatch(
      await page.locator('body').innerText(),
      /字典组|组编码|组名称|组成员|新增组|删除组|组内顺序|历史兼容组/
    );
  try {
    await page.getByRole('button', { name: '详情', exact: true }).click();
    await page.getByText('发布版本 v1', { exact: true }).waitFor();
    await assertNoGroupUI();
    await page.getByRole('button', { name: '编辑字典值', exact: true }).click();
    await page.getByLabel('显示名称 1', { exact: true }).fill('普通客户（新）');
    assert.equal(
      await page.getByLabel('值 1', { exact: true }).isDisabled(),
      true
    );
    assert.equal(
      await page
        .getByLabel('默认项 1', { exact: true })
        .getByRole('button', { name: '删除', exact: true })
        .isDisabled(),
      true
    );
    if (process.env.DICTIONARY_SCREENSHOT_DIR) {
      await page.screenshot({
        path: `${process.env.DICTIONARY_SCREENSHOT_DIR}/published-editor.png`,
        fullPage: true,
      });
    }
    await assertNoGroupUI();
    await page.getByRole('button', { name: '保存草稿', exact: true }).click();
    await page.getByText('草稿修订 1', { exact: true }).waitFor();
    const expectedGroup = {
      id: legacyGroup.id,
      code: legacyGroup.code,
      name: legacyGroup.name,
      enabled: legacyGroup.enabled,
      memberIds: legacyGroup.memberIds,
      memberOrder: legacyGroup.memberIds,
      memberSortOrders: { 'item-trial': 1, 'item-standard': 2 },
    };
    assert.deepEqual(writes[0].body.groups, [expectedGroup]);
    await page
      .getByLabel('默认项 2', { exact: true })
      .getByRole('button', { name: '删除', exact: true })
      .click();
    await page.getByRole('button', { name: '保存草稿', exact: true }).click();
    await page.getByText('草稿修订 2', { exact: true }).waitFor();
    assert.deepEqual(writes[1].body.groups, [
      {
        ...expectedGroup,
        memberIds: ['item-standard'],
        memberOrder: ['item-standard'],
        memberSortOrders: { 'item-standard': 2 },
      },
    ]);
    await page.getByRole('button', { name: '核对并发布', exact: true }).click();
    const modal = page.locator('.arco-modal');
    await modal.getByText('普通客户（新）', { exact: true }).waitFor();
    await assertNoGroupUI();
    await modal.getByRole('button', { name: '取消', exact: true }).click();
    await modal.waitFor({ state: 'hidden' });
    await page.getByRole('tab', { name: '历史版本', exact: true }).click();
    await page.getByLabel('历史版本号', { exact: true }).click();
    await page
      .getByRole('option', { name: '版本 1 · 2026-11-19', exact: true })
      .click();
    await page
      .getByRole('button', { name: '回滚此版本', exact: true })
      .waitFor();
    await assertNoGroupUI();
    assert.equal(writes.length, 2);
  } finally {
    await page.close();
  }
});

test('value rows retain their fields when reordered by handle and keyboard, then save the new order', async () => {
  const { page, writes } = await setup();
  try {
    await page.getByRole('button', { name: '详情', exact: true }).click();
    await page.getByRole('button', { name: '编辑字典值', exact: true }).click();
    for (const [index, value] of ['standard', 'vip', 'trial'].entries()) {
      await page
        .getByRole('button', { name: '新增字典值', exact: true })
        .click();
      await page.getByLabel(`值 ${index + 1}`, { exact: true }).fill(value);
      await page
        .getByLabel(`显示名称 ${index + 1}`, { exact: true })
        .fill(`${value} 客户`);
    }
    await page
      .getByLabel('说明 1', { exact: true })
      .fill('第一行说明\n第二行说明');
    await page
      .getByRole('button', { name: '排序第 1 项', exact: true })
      .dragTo(page.getByLabel('默认项 3', { exact: true }));
    assert.equal(
      await page.getByLabel('值 1', { exact: true }).inputValue(),
      'vip'
    );
    assert.equal(
      await page.getByLabel('值 3', { exact: true }).inputValue(),
      'standard'
    );
    await page
      .getByRole('button', { name: '排序第 3 项', exact: true })
      .press('ArrowUp');
    assert.equal(
      await page.getByLabel('值 2', { exact: true }).inputValue(),
      'standard'
    );
    await page
      .getByRole('button', { name: '排序第 2 项', exact: true })
      .press('ArrowDown');
    assert.equal(
      await page.getByLabel('值 3', { exact: true }).inputValue(),
      'standard'
    );
    await page
      .getByRole('button', { name: '排序第 3 项', exact: true })
      .press('ArrowUp');
    assert.equal(
      await page.getByLabel('显示名称 2', { exact: true }).inputValue(),
      'standard 客户'
    );
    assert.equal(
      await page.getByLabel('说明 2', { exact: true }).inputValue(),
      '第一行说明\n第二行说明'
    );
    await page.getByRole('switch', { name: '启用项 1', exact: true }).click();
    await page.getByRole('button', { name: '保存草稿', exact: true }).click();
    await page.getByText('草稿修订 1', { exact: true }).waitFor();
    const body = writes[0].body;
    assert.deepEqual(
      body.itemOrder.map(
        (id) => body.items.find((item) => item.id === id).value
      ),
      ['vip', 'standard', 'trial']
    );
    assert.equal(
      body.items.find((item) => item.value === 'vip').enabled,
      false
    );
    assert.equal(
      body.items.find((item) => item.value === 'standard').description,
      '第一行说明\n第二行说明'
    );
    if (process.env.DICTIONARY_SCREENSHOT_DIR) {
      await page.setViewportSize({ width: 768, height: 900 });
      await page.screenshot({
        path: `${process.env.DICTIONARY_SCREENSHOT_DIR}/editor-narrow.png`,
        fullPage: true,
      });
    }
  } finally {
    await page.close();
  }
});

async function setupMany(count = 25) {
  const items = Array.from({ length: count }, (_, index) => {
    const number = String(index + 1).padStart(2, '0');
    return {
      id: `item-${number}`,
      value: `value-${number}`,
      label: `客户${number}`,
      description: index === 22 ? '检索说明' : `说明${number}`,
      enabled: true,
      everPublished: false,
      sortOrder: index,
    };
  });
  const row = {
    ...makeRow('customer-type', '客户类型'),
    published: true,
    publishedVersion: 1,
    items,
  };
  const fixture = await setup([row]);
  fixture.drafts.get(row.code).itemOrder = items.map((item) => item.id);
  fixture.state.history = [
    {
      version: 1,
      status: 'PUBLISHED',
      createdAt: '2026-11-19',
      createdBy: 'fixture',
      snapshot: row,
    },
  ];
  return fixture;
}

test('draft pagination and search preserve edits across pages and save every item', async () => {
  const { page, writes } = await setupMany();
  try {
    await page.getByRole('button', { name: '详情', exact: true }).click();
    await page.getByRole('button', { name: '编辑字典值', exact: true }).click();
    const pager = page.getByRole('navigation', {
      name: '草稿字典值分页',
      exact: true,
    });
    const search = page.getByLabel('搜索草稿字典值', { exact: true });
    assert.equal(
      await page.getByRole('textbox', { name: /^值 \d+$/ }).count(),
      10
    );
    await page.getByLabel('显示名称 1', { exact: true }).fill('首页已修改');
    await pager.getByText('2', { exact: true }).click();
    await page.getByLabel('显示名称 11', { exact: true }).fill('第二页已修改');
    await search.fill('  VALUE-25 ');
    assert.equal(
      await page.getByRole('textbox', { name: /^值 \d+$/ }).count(),
      1
    );
    assert.equal(
      await page.getByLabel('值 25', { exact: true }).inputValue(),
      'value-25'
    );
    assert.equal(
      await page
        .getByRole('button', { name: '排序第 25 项', exact: true })
        .isDisabled(),
      true
    );
    await search.fill('客户21');
    await page
      .getByLabel('显示名称 21', { exact: true })
      .fill('编辑后继续留在当前结果');
    assert.equal(
      await page.getByLabel('显示名称 21', { exact: true }).isVisible(),
      true
    );
    await search.fill('检索说明');
    await page.getByLabel('说明 23', { exact: true }).fill('说明也已修改');
    await page.getByRole('button', { name: '保存草稿', exact: true }).click();
    await page.getByText('草稿修订 1', { exact: true }).waitFor();
    assert.equal(writes.length, 1);
    assert.equal(writes[0].body.items.length, 25);
    assert.equal(writes[0].body.itemOrder.length, 25);
    for (const [id, label] of [
      ['item-01', '首页已修改'],
      ['item-11', '第二页已修改'],
      ['item-21', '编辑后继续留在当前结果'],
    ]) {
      assert.equal(
        writes[0].body.items.find((item) => item.id === id).label,
        label
      );
    }
    assert.equal(
      writes[0].body.items.find((item) => item.id === 'item-23').description,
      '说明也已修改'
    );
    await page
      .getByText('没有匹配的字典值，请调整搜索条件。', { exact: true })
      .waitFor();
    await search.fill('');
    assert.equal(
      await page.getByLabel('显示名称 1', { exact: true }).inputValue(),
      '首页已修改'
    );
    await pager.getByRole('combobox').click();
    await page.getByRole('option', { name: /20/ }).click();
    assert.equal(
      await page.getByRole('textbox', { name: /^值 \d+$/ }).count(),
      20
    );
    await pager.getByText('2', { exact: true }).click();
    assert.equal(
      await page.getByRole('textbox', { name: /^值 \d+$/ }).count(),
      5
    );
    if (process.env.DICTIONARY_SCREENSHOT_DIR) {
      await page.screenshot({
        path: `${process.env.DICTIONARY_SCREENSHOT_DIR}/paged-editor.png`,
        fullPage: true,
      });
    }
  } finally {
    await page.close();
  }
});

test('adding and removing values keep pages valid; hidden invalid entries are revealed before saving', async () => {
  const { page, writes } = await setupMany(20);
  try {
    await page.getByRole('button', { name: '详情', exact: true }).click();
    await page.getByRole('button', { name: '编辑字典值', exact: true }).click();
    const search = page.getByLabel('搜索草稿字典值', { exact: true });
    const pager = page.getByRole('navigation', {
      name: '草稿字典值分页',
      exact: true,
    });
    await search.fill('value-01');
    await page.getByRole('button', { name: '新增字典值', exact: true }).click();
    assert.equal(await search.inputValue(), '');
    await page.getByLabel('值 21', { exact: true }).waitFor();
    await search.fill('value-01');
    await page.getByRole('button', { name: '保存草稿', exact: true }).click();
    await page.getByLabel('值 21', { exact: true }).waitFor();
    assert.equal(writes.length, 0);
    assert.equal(await search.inputValue(), '');
    await page.getByLabel('值 21', { exact: true }).fill('value-01');
    await page.getByLabel('显示名称 21', { exact: true }).fill('重复值');
    await page.getByRole('button', { name: '保存草稿', exact: true }).click();
    assert.equal(writes.length, 0);
    await page
      .getByLabel('默认项 21', { exact: true })
      .getByRole('button', { name: '删除', exact: true })
      .click();
    await page.getByLabel('值 11', { exact: true }).waitFor();
    assert.equal(
      await page.getByRole('textbox', { name: /^值 \d+$/ }).count(),
      10
    );
    await pager.getByText('1', { exact: true }).click();
    await page
      .getByRole('button', { name: '排序第 10 项', exact: true })
      .press('ArrowDown');
    assert.equal(
      await page.getByLabel('值 11', { exact: true }).inputValue(),
      'value-10'
    );
    await page
      .getByRole('button', { name: '排序第 11 项', exact: true })
      .press('ArrowUp');
    assert.equal(
      await page.getByLabel('值 10', { exact: true }).inputValue(),
      'value-10'
    );
    await page.getByRole('button', { name: '保存草稿', exact: true }).click();
    await page.getByText('草稿修订 1', { exact: true }).waitFor();
    assert.equal(writes[0].body.items.length, 20);
    assert.equal(writes[0].body.itemOrder[9], 'item-10');
  } finally {
    await page.close();
  }
});

test('effective, historical and publication views paginate and search the whole snapshot', async () => {
  const { page } = await setupMany();
  try {
    await page.getByRole('button', { name: '详情', exact: true }).click();
    await page.getByText('发布版本 v1', { exact: true }).waitFor();
    const effective = page.getByRole('tabpanel', {
      name: '生效版本',
      exact: true,
    });
    await effective
      .getByRole('navigation', { name: '字典值分页', exact: true })
      .getByText('3', { exact: true })
      .click();
    await page.getByRole('cell', { name: 'value-25', exact: true }).waitFor();
    await effective
      .getByLabel('搜索字典值', { exact: true })
      .fill('  VALUE-01 ');
    await page.getByRole('cell', { name: 'value-01', exact: true }).waitFor();
    assert.equal(
      await page.getByRole('cell', { name: 'value-25', exact: true }).count(),
      0
    );
    await page.getByRole('tab', { name: '历史版本', exact: true }).click();
    await page.getByLabel('历史版本号', { exact: true }).click();
    await page
      .getByRole('option', { name: '版本 1 · 2026-11-19', exact: true })
      .click();
    await page
      .getByRole('button', { name: '回滚此版本', exact: true })
      .waitFor();
    const historical = page.getByRole('tabpanel', {
      name: '历史版本',
      exact: true,
    });
    await historical.getByLabel('搜索字典值', { exact: true }).fill('检索说明');
    await historical
      .getByRole('cell', { name: 'value-23', exact: true })
      .waitFor();
    await page.getByRole('tab', { name: '草稿', exact: true }).click();
    await page.getByRole('button', { name: '核对并发布', exact: true }).click();
    const modal = page.locator('.arco-modal');
    await modal
      .getByRole('navigation', { name: '字典值分页', exact: true })
      .getByText('3', { exact: true })
      .click();
    await modal.getByRole('cell', { name: 'value-25', exact: true }).waitFor();
    await modal.getByLabel('搜索字典值', { exact: true }).fill('客户21');
    await modal.getByRole('cell', { name: 'value-21', exact: true }).waitFor();
    await modal.getByRole('button', { name: '取消', exact: true }).click();
  } finally {
    await page.close();
  }
});

test('the disable menu requires confirmation, cancellation sends no write and failures remain visible in the dialog', async () => {
  const { page, writes } = await setup([
    {
      ...makeRow('customer-type', '客户类型'),
      published: true,
      publishedVersion: 1,
    },
  ]);
  try {
    await page.getByRole('button', { name: '详情', exact: true }).click();
    await page.getByText('发布版本 v1', { exact: true }).waitFor();
    assert.equal(
      await page.getByRole('button', { name: '停用字典', exact: true }).count(),
      0
    );
    await page.getByRole('button', { name: '更多操作', exact: true }).click();
    await page.getByRole('menuitem', { name: '停用字典', exact: true }).click();
    const modal = page.locator('.arco-modal');
    await modal.getByText('停用字典“客户类型”？', { exact: true }).waitFor();
    assert.equal(writes.length, 0);
    assert.match(
      await modal
        .getByRole('button', { name: '确认停用', exact: true })
        .getAttribute('class'),
      /arco-btn-status-danger/
    );
    await modal.getByRole('button', { name: '取消', exact: true }).click();
    await modal.waitFor({ state: 'hidden' });
    assert.equal(writes.length, 0);
    await page.getByRole('button', { name: '更多操作', exact: true }).click();
    await page.getByRole('menuitem', { name: '停用字典', exact: true }).click();
    await modal.getByRole('button', { name: '确认停用', exact: true }).click();
    await modal.getByText(/fixture-403/).waitFor();
    assert.equal(writes.length, 1);
    assert.equal(writes[0].body.expectedVersion, 1);
    assert.equal(writes[0].body.expectedEffectiveRevision, 0);
    assert.equal(
      await modal
        .getByRole('button', { name: '取消', exact: true })
        .isEnabled(),
      true
    );
  } finally {
    await page.close();
  }
});

test('group list shows groups across dictionaries, searches, and edits only the selected group while preserving the aggregate', async () => {
  const groups = [{ id: 'group-one', code: 'one', name: '常用组', enabled: true, memberValues: ['alpha'] }];
  const { page, drafts, writes, state } = await setup([
    { ...makeRow('customer-type', '客户类型'), published: true, publishedVersion: 1, groups },
    { ...makeRow('other-type', '其他类型'), published: true, publishedVersion: 2, groups: [{ ...groups[0], name: '另一个组' }] },
  ], true);
  const draft = drafts.get('customer-type');
  draft.items = [{ id: 'item-a', value: 'alpha', label: '甲', enabled: true, everPublished: true, sortOrder: 0 }];
  draft.itemOrder = ['item-a'];
  draft.groups = [{ id: 'group-one', code: 'one', name: '常用组', enabled: true, everPublished: true, memberIds: ['item-a'] }, { id: 'group-two', code: 'two', name: '保留组', enabled: true, everPublished: true, memberIds: [] }];
  try {
    await page.getByRole('cell', { name: '常用组', exact: true }).waitFor();
    await page.getByRole('cell', { name: '另一个组', exact: true }).waitFor();
    assert.equal(await page.getByRole('button', { name: '管理字典组', exact: true }).count(), 0);
    await page.screenshot({ path: '../.scratch/dictionary-groups-list-20261120/list.png', fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.screenshot({ path: '../.scratch/dictionary-groups-list-20261120/narrow.png', fullPage: true });
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.getByLabel('搜索字典组', { exact: true }).fill('客户类型');
    assert.equal(await page.getByRole('cell', { name: '另一个组', exact: true }).count(), 0);
    await page.getByRole('button', { name: '详情', exact: true }).click();
    await page.getByRole('button', { name: '编辑字典组', exact: true }).click();
    await page.getByLabel('组名称（必填）').waitFor();
    assert.equal(await page.getByLabel('组名称（必填）').count(), 1);
    await page.getByLabel('组名称（必填）').fill('已修改组');
    state.saveStatus = 409;
    await page.getByRole('button', { name: '保存草稿', exact: true }).click();
    await page.getByText('草稿已被其他客户端修改', { exact: false }).waitFor();
    assert.equal(await page.getByLabel('组名称（必填）').inputValue(), '已修改组');
    state.saveStatus = 200;
    await page.getByRole('button', { name: '保存草稿', exact: true }).click();
    await page.getByText('字典组已保存为草稿，发布后生效', { exact: true }).waitFor();
    assert.equal(writes[1].body.groups[1].name, '保留组');
    assert.deepEqual(writes[1].body.itemOrder, ['item-a']);
    await page.getByRole('button', { name: '返回字典组列表', exact: true }).click();
    assert.equal(await page.getByLabel('搜索字典组').inputValue(), '客户类型');
    await page.getByRole('row').filter({ hasText: '常用组' }).getByText('已发布').waitFor();
    await page.getByRole('row').filter({ hasText: '保留组' }).getByText('当前未生效').waitFor();
  } finally { await page.close(); }
});

test('empty group list creates a group after selecting its dictionary and keeps validation and publication preview', async () => {
  const { page, writes } = await setup(undefined, true);
  try {
    await page.getByText('暂无字典组，点击“新建字典组”开始创建', { exact: true }).waitFor();
    await page.getByRole('button', { name: '新建字典组', exact: true }).click();
    assert.equal(await page.getByRole('button', { name: '开始编辑', exact: true }).isDisabled(), true);
    await page.getByLabel('新组所属字典').click();
    await page.getByRole('option', { name: '客户类型 (customer-type)', exact: true }).click();
    await page.getByRole('button', { name: '开始编辑', exact: true }).click();
    await page.getByLabel('组编码（必填）').fill('BAD');
    await page.getByLabel('组名称（必填）').fill('新组');
    await page.getByRole('button', { name: '保存草稿', exact: true }).click();
    await page.getByText('请检查字典组：', { exact: false }).waitFor();
    assert.equal(writes.length, 0);
    await page.getByLabel('组编码（必填）').fill('new-group');
    await page.getByRole('button', { name: '保存草稿', exact: true }).click();
    await page.getByText('字典组已保存为草稿，发布后生效', { exact: true }).waitFor();
    assert.equal(writes[0].body.groups[0].code, 'new-group');
    assert.equal(await page.getByLabel('组编码（必填）').inputValue(), 'new-group');
    await page.getByRole('button', { name: '核对并发布', exact: true }).click();
    await page.getByText('字典组随所属字典整体发布，以下字典值及所有字典组将一同生效。', { exact: true }).waitFor();
  } finally { await page.close(); }
});

test('group value lists resolve references and removal preserves original values and other groups', async () => {
  const items = [
    { value: 'alpha', label: '字典最新名称', description: '来自字典的说明', enabled: true, sortOrder: 0 },
    { value: 'beta', label: '停用值', description: '保留停用状态', enabled: false, sortOrder: 1 },
    { value: 'gamma', label: '未引用值', enabled: true, sortOrder: 2 },
  ];
  const group = { id: 'group-one', code: 'common', name: '常用字典组', enabled: true, memberValues: ['beta', 'alpha'] };
  const { page, drafts, writes, state } = await setup([{ ...makeRow('customer-type', '客户类型'), published: true, publishedVersion: 2, items, groups: [group] }], true);
  const draft = drafts.get('customer-type');
  draft.items = items.map(item => ({ ...item, id: `item-${item.value}`, everPublished: true }));
  draft.itemOrder = draft.items.map(item => item.id);
  draft.groups = [
    { ...group, everPublished: true, memberIds: ['item-beta', 'item-alpha'] },
    { id: 'group-two', code: 'other', name: '其他字典组', enabled: true, everPublished: true, memberIds: ['item-beta'] },
  ];
  state.history = [{ version: 1, createdAt: '2026-11-19', snapshot: { name: '客户类型', enabled: true, publicReadable: false, items: items.map(item => ({ ...item, label: `历史${item.label}` })), groups: [group] } }];
  try {
    await page.locator('thead').getByText('字典组名称', { exact: true }).waitFor();
    await page.locator('thead').getByText('字典组编码', { exact: true }).waitFor();
    await page.getByRole('button', { name: '详情', exact: true }).click();
    const effective = page.getByRole('tabpanel', { name: '生效版本', exact: true });
    await effective.getByRole('cell', { name: '字典最新名称', exact: true }).waitFor();
    await effective.getByRole('cell', { name: '来自字典的说明', exact: true }).waitFor();
    await effective.getByRole('cell', { name: '停用', exact: true }).waitFor();
    assert.equal(await effective.getByRole('cell', { name: '未引用值', exact: true }).count(), 0);
    assert.deepEqual(await effective.locator('.arco-table tbody tr td:first-child').allTextContents(), ['beta', 'alpha']);
    await page.screenshot({ path: '../.scratch/dictionary-group-references-20261120/detail.png', fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.screenshot({ path: '../.scratch/dictionary-group-references-20261120/narrow.png', fullPage: true });
    await page.setViewportSize({ width: 1280, height: 900 });
    await effective.getByLabel('搜索字典值', { exact: true }).fill('来自字典的说明');
    assert.equal(await effective.getByRole('cell', { name: 'beta', exact: true }).count(), 0);
    await page.getByRole('tab', { name: '历史版本', exact: true }).click();
    await page.getByLabel('历史版本号', { exact: true }).click();
    await page.getByRole('option', { name: '版本 1 · 2026-11-19', exact: true }).click();
    await page.getByRole('tabpanel', { name: '历史版本', exact: true }).getByRole('cell', { name: '历史字典最新名称', exact: true }).waitFor();
    await page.getByRole('tab', { name: '草稿', exact: true }).click();
    const editing = page.getByRole('tabpanel', { name: '草稿', exact: true });
    await editing.getByRole('button', { name: '移除引用', exact: true }).first().click();
    assert.equal(await editing.getByRole('cell', { name: 'beta', exact: true }).count(), 0);
    await page.getByRole('button', { name: '保存草稿', exact: true }).click();
    await page.getByText('字典组已保存为草稿，发布后生效', { exact: true }).waitFor();
    assert.deepEqual(writes[0].body.groups[0].memberIds, ['item-alpha']);
    assert.deepEqual(writes[0].body.groups[1].memberIds, ['item-beta']);
    assert.deepEqual(writes[0].body.items, items.map((item) => ({ ...item, id: `item-${item.value}` })));
    assert.deepEqual(writes[0].body.itemOrder, ['item-alpha', 'item-beta', 'item-gamma']);
  } finally { await page.close(); }
});

test('group members drag, use keyboard and edit absolute order across pages without changing values or other groups', async () => {
  const items = Array.from({ length: 37 }, (_, index) => ({ id: `item-${index + 1}`, value: `value-${index + 1}`, label: `客户${index + 1}`, description: `说明${index + 1}`, enabled: true, sortOrder: index, everPublished: true }));
  const ids = items.slice(0, 25).map(item => item.id);
  const group = { id: 'group-order', code: 'ordered', name: '可排序组', enabled: true, memberValues: items.slice(0, 25).map(item => item.value) };
  const { page, drafts, writes } = await setup([{ ...makeRow('customer-type', '客户类型'), published: true, publishedVersion: 1, items, groups: [group] }], true);
  const draft = drafts.get('customer-type');
  draft.itemOrder = items.map(item => item.id);
  draft.groups = [{ ...group, everPublished: true, memberIds: ids }, { id: 'group-other', code: 'other', name: '其他组', enabled: true, everPublished: true, memberIds: ['item-1'] }];
  try {
    await page.getByRole('button', { name: '详情', exact: true }).click();
    await page.getByRole('button', { name: '编辑字典组', exact: true }).click();
    const editing = page.getByRole('tabpanel', { name: '草稿', exact: true });
    assert.equal(await editing.getByRole('button', { name: '上移', exact: true }).count(), 0);
    await page.getByLabel('组内项 3', { exact: true }).scrollIntoViewIfNeeded();
    await page.getByRole('button', { name: '排序组内第 1 项', exact: true }).dragTo(page.getByLabel('组内项 3', { exact: true }));
    await page.getByLabel('组内项 3', { exact: true }).getByRole('cell', { name: 'value-1', exact: true }).waitFor();
    await page.getByRole('button', { name: '排序组内第 3 项', exact: true }).press('ArrowUp');
    await page.getByLabel('组内项 2', { exact: true }).getByRole('cell', { name: 'value-1', exact: true }).waitFor();
    await page.getByLabel('组内顺序 2', { exact: true }).fill('1000');
    await page.getByLabel('组内顺序 2', { exact: true }).press('Enter');
    await page.getByLabel('组内项 25', { exact: true }).getByRole('cell', { name: 'value-1', exact: true }).waitFor();
    await page.waitForFunction(() => document.activeElement?.getAttribute('aria-label') === '组内顺序 25');
    await page.getByLabel('组内顺序 25', { exact: true }).fill('1');
    await page.getByLabel('组内顺序 25', { exact: true }).press('Tab');
    await page.getByLabel('组内项 1', { exact: true }).getByRole('cell', { name: 'value-1', exact: true }).waitFor();
    for (const invalid of ['0', '2147483648', '1.5', '']) {
      const input = page.getByLabel('组内顺序 1', { exact: true });
      await input.fill(invalid); await input.press('Enter');
      assert.equal(await input.inputValue(), '1');
    }
    await page.getByRole('button', { name: '排序组内第 10 项', exact: true }).press('ArrowDown');
    await page.getByLabel('组内项 11', { exact: true }).getByRole('cell', { name: 'value-10', exact: true }).waitFor();
    await page.waitForFunction(() => document.activeElement?.getAttribute('aria-label') === '排序组内第 11 项');
    await page.waitForFunction(() => !document.body.textContent.includes('顺序编号须为'));
    await page.screenshot({ path: '../.scratch/dictionary-group-order-20261120/editor.png', fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.screenshot({ path: '../.scratch/dictionary-group-order-20261120/narrow.png', fullPage: true });
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.getByRole('button', { name: '保存草稿', exact: true }).click();
    await page.getByText('字典组已保存为草稿，发布后生效', { exact: true }).waitFor();
    const expected = [...ids]; [expected[9], expected[10]] = [expected[10], expected[9]];
    assert.deepEqual(writes[0].body.groups[0].memberOrder, expected);
    assert.deepEqual(writes[0].body.groups[1].memberOrder, ['item-1']);
    assert.deepEqual(writes[0].body.itemOrder, items.map(item => item.id));
    assert.deepEqual(writes[0].body.items, items.map(({ everPublished, ...item }) => item));
    await editing.getByRole('button', { name: '添加字典值' }).click();
    const picker = page.getByRole('dialog', { name: '添加字典值' });
    const check = (value) => picker.getByRole('row').filter({ has: page.getByRole('cell', { name: value, exact: true }) }).locator('label.arco-checkbox').click();
    await check('value-27');
    await picker.getByRole('button', { name: '取消' }).click();
    await editing.getByRole('button', { name: '添加字典值' }).click();
    await check('value-26');
    await picker.locator('.arco-pagination-item').filter({ hasText: /^2$/ }).click();
    await check('value-37');
    await picker.getByRole('button', { name: '添加（2）' }).click();
    assert.equal(await editing.getByRole('button', { name: /^排序组内第/ }).first().isDisabled(), true);
    await page.getByRole('button', { name: '保存草稿', exact: true }).click();
    await page.waitForFunction(() => document.querySelector('button[aria-label^="排序组内第"]:not([disabled])'));
    assert.deepEqual(writes[1].body.groups[0].memberIds, [...expected, 'item-26', 'item-37']);
    await editing.getByRole('button', { name: '核对并发布' }).click();
    const review = page.getByRole('dialog', { name: '核对完整草稿后发布' });
    await review.getByRole('listitem').filter({ hasText: '可排序组（ordered） · 启用 · 27 个成员' }).waitFor();
    assert.equal(await review.locator('thead').getByText('字典组名称', { exact: true }).count(), 0);
    await review.getByLabel('搜索字典值').waitFor();
    assert.equal(await review.locator('thead').getByText('字典值', { exact: true }).count(), 1);
    await review.getByLabel('搜索字典值').fill('value-1');
    const shared = review.getByRole('row').filter({ has: page.getByRole('cell', { name: 'value-1', exact: true }) });
    await shared.getByText(/可排序组.*其他组/).waitFor();
  } finally { await page.close(); }
});

test('sparse group ranks survive saving and reopening, and dragging preserves gaps', async () => {
  const items = ['alpha', 'beta', 'gamma'].map((value, index) => ({ id: `item-${value}`, value, label: value, enabled: true, sortOrder: index, everPublished: true }));
  const group = { id: 'group-sparse', code: 'sparse', name: '间隔编号组', enabled: true, memberValues: items.map(item => item.value), memberSortOrders: { alpha: 10, beta: 20, gamma: 50 } };
  const { page, drafts, writes } = await setup([{ ...makeRow('customer-type', '客户类型'), published: true, publishedVersion: 1, items, groups: [group] }], true);
  const draft = drafts.get('customer-type');
  draft.itemOrder = items.map(item => item.id);
  draft.groups = [{ ...group, everPublished: true, memberIds: draft.itemOrder, memberSortOrders: { 'item-alpha': 10, 'item-beta': 20, 'item-gamma': 50 } }];
  try {
    await page.getByRole('button', { name: '详情', exact: true }).click();
    await page.getByRole('cell', { name: '50', exact: true }).waitFor();
    await page.getByRole('button', { name: '编辑字典组', exact: true }).click();
    assert.equal(await page.getByLabel('组内顺序 1', { exact: true }).inputValue(), '10');
    await page.getByLabel('组内顺序 1', { exact: true }).fill('1000');
    await page.getByLabel('组内顺序 1', { exact: true }).press('Enter');
    await page.getByLabel('组内项 3', { exact: true }).getByRole('cell', { name: 'alpha', exact: true }).first().waitFor();
    await page.getByRole('button', { name: '保存草稿', exact: true }).click();
    await page.getByText('字典组已保存为草稿，发布后生效', { exact: true }).waitFor();
    assert.deepEqual(writes[0].body.groups[0].memberSortOrders, { 'item-alpha': 1000, 'item-beta': 20, 'item-gamma': 50 });
    await page.getByRole('button', { name: '返回字典组列表', exact: true }).click();
    await page.getByRole('button', { name: '详情', exact: true }).click();
    await page.getByRole('button', { name: '编辑字典组', exact: true }).click();
    assert.equal(await page.getByLabel('组内顺序 3', { exact: true }).inputValue(), '1000');
    await page.getByLabel('组内项 3', { exact: true }).scrollIntoViewIfNeeded();
    await page.getByRole('button', { name: '排序组内第 3 项', exact: true }).dragTo(page.getByLabel('组内项 2', { exact: true }));
    await page.getByLabel('组内项 2', { exact: true }).getByRole('cell', { name: 'alpha', exact: true }).first().waitFor();
    assert.equal(await page.getByLabel('组内顺序 2', { exact: true }).inputValue(), '50');
    assert.equal(await page.getByLabel('组内顺序 3', { exact: true }).inputValue(), '1000');
    await page.screenshot({ path: '../.scratch/dictionary-group-ranks-20261120/editor.png', fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.screenshot({ path: '../.scratch/dictionary-group-ranks-20261120/narrow.png', fullPage: true });
    await page.getByRole('button', { name: '保存草稿', exact: true }).click();
    await page.waitForFunction(() => document.body.textContent.includes('字典组已保存为草稿，发布后生效'));
    assert.deepEqual(writes[1].body.groups[0].memberSortOrders, { 'item-alpha': 50, 'item-beta': 20, 'item-gamma': 1000 });
  } finally { await page.close(); }
});

test('dictionary values edit sparse ranks across pages, reopen saved ranks and keep group references', async () => {
  const items = Array.from({ length: 25 }, (_, index) => ({ id: `item-${index + 1}`, value: `value-${index + 1}`, label: `客户${index + 1}`, description: '', enabled: true, sortOrder: (index + 1) * 10, everPublished: true }));
  const { page, drafts, writes } = await setup([{ ...makeRow('item-order', '编号排序'), published: true, publishedVersion: 1, items }]);
  const draft = drafts.get('item-order');
  draft.itemOrder = items.map(item => item.id);
  draft.groups = [{ id: 'group-kept', code: 'kept', name: '保留组', enabled: true, everPublished: true, memberIds: ['item-25', 'item-1'], memberSortOrders: { 'item-25': 10, 'item-1': 50 } }];
  try {
    await page.getByRole('button', { name: '详情', exact: true }).click();
    await page.getByRole('button', { name: '编辑字典值', exact: true }).click();
    assert.equal(await page.getByLabel('顺序编号 1', { exact: true }).inputValue(), '10');
    await page.getByLabel('顺序编号 1', { exact: true }).fill('1000');
    await page.getByLabel('顺序编号 1', { exact: true }).press('Enter');
    assert.equal(await page.getByLabel('值 25', { exact: true }).inputValue(), 'value-1');
    await page.waitForFunction(() => document.activeElement?.getAttribute('aria-label') === '顺序编号 25');
    await page.getByRole('button', { name: '保存草稿', exact: true }).click();
    await page.getByText('草稿修订 1', { exact: true }).waitFor();
    assert.equal(writes[0].body.items.find(item => item.id === 'item-1').sortOrder, 1000);
    assert.deepEqual(writes[0].body.groups[0].memberSortOrders, { 'item-25': 10, 'item-1': 50 });
    await page.getByRole('button', { name: '返回字典列表', exact: true }).click();
    await page.getByRole('button', { name: '详情', exact: true }).click();
    await page.getByRole('button', { name: '编辑字典值', exact: true }).click();
    await page.getByRole('tabpanel', { name: '草稿', exact: true }).getByLabel('第 3 页', { exact: true }).click();
    assert.equal(await page.getByLabel('顺序编号 25', { exact: true }).inputValue(), '1000');
    await page.getByLabel('顺序编号 25', { exact: true }).fill('5');
    await page.getByLabel('顺序编号 25', { exact: true }).press('Tab');
    assert.equal(await page.getByLabel('值 1', { exact: true }).inputValue(), 'value-1');
    for (const invalid of ['-1', '2147483648', '1.5', '']) {
      const input = page.getByLabel('顺序编号 1', { exact: true });
      await input.fill(invalid); await input.press('Enter');
      assert.equal(await input.inputValue(), '5');
    }
    await page.getByLabel('默认项 3', { exact: true }).scrollIntoViewIfNeeded();
    await page.getByRole('button', { name: '排序第 1 项', exact: true }).dragTo(page.getByLabel('默认项 3', { exact: true }));
    assert.equal(await page.getByLabel('值 3', { exact: true }).inputValue(), 'value-1');
    assert.equal(await page.getByLabel('顺序编号 3', { exact: true }).inputValue(), '30');
    await page.getByRole('button', { name: '排序第 3 项', exact: true }).press('ArrowUp');
    assert.equal(await page.getByLabel('顺序编号 2', { exact: true }).inputValue(), '20');
    await page.getByLabel('搜索草稿字典值', { exact: true }).fill('客户1');
    assert.equal(await page.getByLabel('顺序编号 2', { exact: true }).isDisabled(), true);
    await page.getByLabel('搜索草稿字典值', { exact: true }).fill('');
    await page.waitForFunction(() => !document.body.textContent.includes('顺序编号须为'));
    await page.screenshot({ path: '../.scratch/dictionary-item-ranks-20261120/editor.png', fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.screenshot({ path: '../.scratch/dictionary-item-ranks-20261120/narrow.png', fullPage: true });
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.getByRole('button', { name: '保存草稿', exact: true }).click();
    await page.getByText('草稿修订 2', { exact: true }).waitFor();
    assert.deepEqual(writes[1].body.itemOrder, ['item-2', 'item-1', ...items.slice(2).map(item => item.id)]);
    assert.deepEqual(writes[1].body.groups, writes[0].body.groups);
    assert.deepEqual(writes[1].body.items.filter(item => !['item-1', 'item-2'].includes(item.id)).map(item => item.sortOrder), items.slice(2).map(item => item.sortOrder));
    await page.getByRole('button', { name: '核对并发布', exact: true }).click();
    await page.locator('.arco-modal').getByRole('cell', { name: '5', exact: true }).waitFor();
    await page.locator('.arco-modal').getByRole('button', { name: '取消', exact: true }).click();
    await page.getByRole('button', { name: '新增字典值', exact: true }).click();
    assert.equal(await page.getByLabel('顺序编号 26', { exact: true }).inputValue(), '251');
  } finally { await page.close(); }
});
