// Browser component checks with controlled HTTP; no live data or real login.
import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright-core';
import { createServer } from 'vite';

let vite, browser, origin;
const errors = [];
const base = '/api/v1/enterprises/ent-acme/dictionaries';
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
        items: body.items.map((item, index) => ({
          ...item,
          sortOrder: index,
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
  await page.getByRole('button', { name: groupsOnly ? '管理字典组' : '新建字典', exact: true }).first().waitFor();
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

test('a pending create blocks duplicate submission, tenant switching and cancellation', async () => {
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
    assert.match(
      await page
        .getByRole('combobox', { name: '全局活动租户', exact: true })
        .getAttribute('class'),
      /arco-select-disabled/
    );
    await page.getByRole('button', { name: '取消', exact: true }).click();
    assert.equal(await page.getByLabel('新字典名称').isVisible(), true);
    release();
    await page
      .getByRole('button', { name: '新增字典值', exact: true })
      .waitFor();
    assert.doesNotMatch(
      await page
        .getByRole('combobox', { name: '全局活动租户', exact: true })
        .getAttribute('class'),
      /arco-select-disabled/
    );
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

test('group entry edits groups independently, preserves values, rejects invalid codes and keeps conflicts editable', async () => {
  const { page, drafts, writes, state } = await setup(undefined, true);
  const draft = drafts.get('customer-type');
  draft.items = [{ id: 'item-a', value: 'alpha', label: '甲', description: '保留说明', enabled: true, everPublished: true, sortOrder: 0 }, { id: 'item-b', value: 'beta', label: '乙', enabled: true, everPublished: true, sortOrder: 1 }];
  draft.itemOrder = ['item-a', 'item-b'];
  draft.groups = [{ id: 'group-one', code: 'one', name: '旧组', enabled: true, everPublished: true, memberIds: ['item-a', 'item-b'] }];
  try {
    assert.equal(await page.getByRole('button', { name: '新建字典', exact: true }).count(), 0);
    await page.getByRole('button', { name: '管理字典组', exact: true }).click();
    await page.getByRole('button', { name: '编辑字典组', exact: true }).click();
    await page.getByLabel('组名称（必填）').fill('常用组');
    assert.equal(await page.getByLabel('组编码（必填）').isDisabled(), true);
    assert.equal(await page.getByRole('button', { name: '删除字典组', exact: true }).isDisabled(), true);
    assert.equal(await page.getByRole('button', { name: '新增字典值', exact: true }).count(), 0);
    await page.getByRole('button', { name: '下移', exact: true }).first().click();
    await page.getByRole('button', { name: '新增字典组', exact: true }).click();
    await page.getByLabel('组编码（必填）').nth(1).fill('BAD');
    await page.getByLabel('组名称（必填）').nth(1).fill('新组');
    await page.getByRole('button', { name: '保存草稿', exact: true }).click();
    await page.getByText('请检查字典组：', { exact: false }).waitFor();
    assert.equal(writes.length, 0);
    await page.getByLabel('组编码（必填）').nth(1).fill('new-group');
    state.saveStatus = 409;
    await page.getByRole('button', { name: '保存草稿', exact: true }).click();
    await page.getByText('草稿已被其他客户端修改', { exact: false }).waitFor();
    assert.equal(await page.getByLabel('组名称（必填）').first().inputValue(), '常用组');
    state.saveStatus = 200;
    await page.getByRole('button', { name: '保存草稿', exact: true }).click();
    await page.getByText('字典组已保存为草稿，发布后生效', { exact: true }).waitFor();
    assert.deepEqual(writes[1].body.groups[0].memberOrder, ['item-b', 'item-a']);
    assert.equal(writes[1].body.groups[1].code, 'new-group');
    assert.equal(writes[1].body.items[0].description, '保留说明');
    assert.deepEqual(writes[1].body.itemOrder, ['item-a', 'item-b']);
    await page.screenshot({ path: '../.scratch/dictionary-groups-editor.png', fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.screenshot({ path: '../.scratch/dictionary-groups-narrow.png', fullPage: true });
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.getByRole('button', { name: '核对并发布', exact: true }).click();
    await page.getByText('字典组随所属字典整体发布，以下字典值及所有字典组将一同生效。', { exact: true }).waitFor();
  } finally {
    await page.close();
  }
});
