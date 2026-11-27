import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Message,
  Modal,
  Space,
  Table,
  Typography,
  Tag,
} from '@arco-design/web-react';
import { useHistory } from 'react-router-dom';
import { isAuthRedirecting } from '@/auth/oidc';
import { useInterfaceOperations } from '@/auth/useInterfaceOperations';
import { MenuManifest, VisibleMenuItem } from '@/menu/api';
import { getPlatformManifest, MenuError, savePlatformManifest } from './api';

import useActiveEnterprise, {
  activeEnterpriseSelection,
} from '@/hooks/useActiveEnterprise';
import { requestMenuRefresh } from '@/menu/refresh';
import useLocale from '@/utils/useLocale';
import {
  clientDictionaryHelp,
  MenuFields,
  MenuForm,
  menuTree,
  MenuNode,
} from './MenuForm';

type Editor = {
  original?: VisibleMenuItem;
  initial: MenuFields;
  draft: MenuFields;
};
export default function MenuManifestPage() {
  const router = useHistory();
  const { setBusy, setDirty } = useActiveEnterprise();
  const operations = useInterfaceOperations({ kind: 'PLATFORM' }, [
    'menu.manifest.write',
  ]);
  const canPublish = operations['menu.manifest.write'] === true;
  const [manifest, setManifest] = useState<MenuManifest | null>(null);
  const [error, setError] = useState<MenuError | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const request = useRef(0);
  const mutation = useRef<{ payload: string; key: string } | null>(null);
  const [editor, setEditor] = useState<Editor | null>(null);
  const [deleting, setDeleting] = useState<VisibleMenuItem | null>(null);
  const locale = useLocale();
  const menuName = (item: VisibleMenuItem): string =>
    typeof locale[item.titleKey] === 'string'
      ? locale[item.titleKey]
      : item.titleKey;
  const editorDirty =
    editor && JSON.stringify(editor.draft) !== JSON.stringify(editor.initial);
  const dirty = Boolean(editorDirty);
  useEffect(() => setDirty(Boolean(dirty)), [dirty, setDirty]);
  useEffect(() => setBusy(saving), [saving, setBusy]);
  useEffect(() => {
    if (!dirty && !saving) return undefined;
    const unblock = router.block(() =>
      isAuthRedirecting()
        ? undefined
        : saving
        ? false
        : '菜单有未保存修改，确认离开？'
    );
    const guard = (event: BeforeUnloadEvent) => {
      if (!isAuthRedirecting()) {
        event.preventDefault();
        event.returnValue = '';
      }
    };
    window.addEventListener('beforeunload', guard);
    return () => {
      unblock();
      window.removeEventListener('beforeunload', guard);
    };
  }, [dirty, saving, router]);
  const load = useCallback(() => {
    const id = ++request.current;
    setError(null);
    setLoading(true);
    return getPlatformManifest()
      .then((data) => {
        if (id !== request.current) return;
        setManifest(data);
      })
      .catch((failure) => {
        if (id === request.current) {
          setManifest(null);
          setError(failure);
        }
      })
      .finally(() => {
        if (id === request.current) setLoading(false);
      });
  }, []);
  useEffect(() => {
    const generation = request;
    setManifest(null);
    load();
    return () => {
      generation.current++;
    };
  }, [load]);
  const discard = (action: () => void) => {
    if (saving) return;
    if (!dirty) action();
    else
      Modal.confirm({
        title: '放弃未保存的菜单修改？',
        okText: '放弃修改',
        cancelText: '继续编辑',
        onOk: action,
      });
  };
  function keyFor(payload: unknown) {
    const text = JSON.stringify(payload);
    if (mutation.current?.payload !== text)
      mutation.current = {
        payload: text,
        key: `menu-${Date.now()}-${Math.random().toString(36).slice(2)}`,
      };
    return mutation.current.key;
  }
  async function persist(items: VisibleMenuItem[], message: string) {
    if (!canPublish || !manifest || saving || loading) return;
    const version = manifest.manifestVersion;
    const body = {
      manifestVersion: version + 1,
      layoutVersions: Object.fromEntries(
        manifest.items.map((item) => [item.menuId, item.version])
      ),
      items: items.map(
        ({
          menuId,
          parentMenuId,
          path,
          titleKey,
          iconKey,
          sortOrder,
          clientCode,
          enabled,
          capabilityCode,
        }) => ({
          menuId,
          parentMenuId,
          path,
          titleKey,
          iconKey,
          sortOrder,
          clientCode,
          enabled,
          ...(capabilityCode ? { capabilityCode } : {}),
        })
      ),
    };
    const generation = request.current;
    const context = activeEnterpriseSelection.getSnapshot().contextVersion;
    setSaving(true);
    setError(null);
    try {
      const saved = await savePlatformManifest(
        body,
        version,
        keyFor([version, body])
      );
      if (request.current !== generation) return;
      setManifest({
        ...saved,
        clientOptions: saved.clientOptions ?? manifest.clientOptions,
      });
      requestMenuRefresh(context);
      setEditor(null);
      setDeleting(null);
      Message.success(message);
    } catch (failure) {
      if (request.current === generation) setError(failure);
    } finally {
      if (request.current === generation) setSaving(false);
    }
  }
  function openEditor(original?: VisibleMenuItem) {
    setError(null);
    const initial: MenuFields = original
      ? {
          menuId: original.menuId,
          titleKey: menuName(original),
          parentMenuId: original.parentMenuId,
          clientCode: original.clientCode || 'web',
          enabled: original.enabled,
          sortOrder: original.sortOrder,
        }
      : {
          menuId: '',
          titleKey: '',
          parentMenuId: '',
          enabled: true,
          sortOrder: 0,
          clientCode: manifest.clientOptions?.find((c) => c.enabled)?.value,
        };
    setEditor({ original, initial, draft: initial });
  }
  function submitEditor() {
    const { draft, original, initial } = editor;
    const id = draft.menuId.trim();
    const title = draft.titleKey.trim();
    let message = '';
    if (!title) message = '请填写菜单名称';
    else if (
      !/^[A-Za-z0-9._:-]+(?:\/[A-Za-z0-9._:-]+)*$/.test(id) ||
      id.split('/').includes('..')
    )
      message = '请填写有效的菜单 ID';
    else if (!original && manifest.items.some((item) => item.menuId === id))
      message = '菜单 ID 已存在';
    else if (!draft.clientCode) message = '请选择所属客户端';
    else if (!Number.isSafeInteger(draft.sortOrder) || draft.sortOrder < 0)
      message = '排序必须是非负整数';
    if (message) {
      setError({ status: 0, code: 'INVALID_INPUT', requestId: '', message });
      return;
    }
    const item: VisibleMenuItem = {
      version: 1,
      actions: [],
      ...original,
      ...draft,
      menuId: id,
      path: '/' + id,
      titleKey:
        original && title === initial.titleKey ? original.titleKey : title,
      iconKey: original?.iconKey ?? '',
      parentMenuId: draft.parentMenuId || undefined,
    };
    const items = original
      ? manifest.items.map((row) => (row.menuId === id ? item : row))
      : [...manifest.items, item];
    persist(items, original ? '菜单已修改' : '菜单已新建');
  }
  function closeEditor() {
    if (saving) return;
    const close = () => {
      setEditor(null);
      setError(null);
    };
    if (editorDirty)
      Modal.confirm({
        title: '放弃本次菜单修改？',
        okText: '放弃修改',
        cancelText: '继续编辑',
        onOk: close,
      });
    else close();
  }
  const failure =
    error &&
    `${
      error.code === 'DOMAIN_NOT_MANAGED'
        ? error.message
        : error.status === 403
        ? '无权管理菜单'
        : error.status === 409 || error.status === 412
        ? '菜单已被修改，请重新加载后重试'
        : error.message || '菜单清单加载失败'
    }${error.requestId ? ` (${error.requestId})` : ''}`;
  const clients = manifest?.clientOptions || [];
  const items = manifest?.items || [];
  const tree = menuTree(items);
  const parents: { label: string; value: string }[] = [];
  function collectParents(nodes: MenuNode[]) {
    for (const node of nodes) {
      if (node.menuId === editor?.original?.menuId) continue;
      parents.push({ value: node.menuId, label: menuName(node) });
      if (node.children) collectParents(node.children);
    }
  }
  if (editor) collectParents(tree);
  const recover = () =>
    discard(() => {
      setEditor(null);
      setDeleting(null);
      load();
    });
  const errorNotice = failure && (
    <Alert
      type="error"
      content={failure}
      style={{ marginBottom: 16 }}
      action={
        error.status === 409 || error.status === 412 || !manifest ? (
          <Button size="small" onClick={recover}>
            重新加载
          </Button>
        ) : undefined
      }
    />
  );
  return (
    <Card title="菜单管理">
      {!editor && !deleting && errorNotice}
      {manifest && (
        <Space wrap style={{ marginBottom: 16 }}>
          <Button
            type="primary"
            disabled={!canPublish || saving || loading}
            onClick={() => openEditor()}
          >
            新建菜单
          </Button>
          <Typography.Text type="secondary">
            {clientDictionaryHelp}
          </Typography.Text>
        </Space>
      )}
      <Table<MenuNode>
        key={manifest ? 'loaded' : 'loading'}
        rowKey="menuId"
        loading={loading}
        data={tree}
        defaultExpandAllRows
        scroll={{ x: 1080 }}
        pagination={false}
        columns={[
          {
            title: '菜单名称',
            width: 210,
            render: (_, item) => menuName(item),
          },
          { title: '菜单 ID', dataIndex: 'menuId' },
          { title: '路径', dataIndex: 'path' },
          { title: '排序', dataIndex: 'sortOrder', width: 65 },
          {
            title: '状态',
            width: 76,
            render: (_, item) => (
              <Tag color={item.enabled ? 'green' : 'gray'}>
                {item.enabled ? '启用' : '停用'}
              </Tag>
            ),
          },
          {
            title: '所属客户端',
            render: (_, item) => {
              const code = item.clientCode || 'web';
              return (
                clients.find((option) => option.value === code)?.label || code
              );
            },
          },
          {
            title: '操作',
            width: 170,
            render: (_, item: MenuNode) => (
              <Space>
                <Button
                  disabled={!canPublish || saving || loading}
                  onClick={() => openEditor(item)}
                >
                  编辑
                </Button>
                <Button
                  status="danger"
                  disabled={!canPublish || saving || loading}
                  onClick={() => {
                    if (item.children?.length) {
                      Message.warning('请先移动或删除子菜单');
                      return;
                    }
                    setError(null);
                    setDeleting(item);
                  }}
                >
                  删除
                </Button>
              </Space>
            ),
          },
        ]}
      />
      {editor && (
        <Modal
          visible
          title={editor.original ? '编辑菜单' : '新建菜单'}
          okText="保存"
          cancelText="取消"
          confirmLoading={saving}
          onOk={submitEditor}
          onCancel={closeEditor}
          style={{ width: 720, maxWidth: 'calc(100vw - 32px)' }}
          maskClosable={!saving}
          closable={!saving}
          okButtonProps={{ disabled: !canPublish }}
          cancelButtonProps={{ disabled: saving }}
        >
          {errorNotice}
          <div style={{ maxHeight: '70vh', overflowY: 'auto' }}>
            {editor.original?.menuId === 'roles' && (
              <Typography.Paragraph>
                平台角色管理和租户角色管理共用此菜单配置。
              </Typography.Paragraph>
            )}
            <MenuForm
              value={editor.draft}
              existing={!!editor.original}
              parents={parents}
              clients={clients}
              disabled={saving}
              onChange={(draft) => setEditor({ ...editor, draft })}
            />
          </div>
        </Modal>
      )}
      {deleting && (
        <Modal
          visible
          title="删除菜单"
          okText="删除"
          cancelText="取消"
          confirmLoading={saving}
          okButtonProps={{ status: 'danger', disabled: !canPublish }}
          cancelButtonProps={{ disabled: saving }}
          onCancel={() => {
            if (!saving) {
              setDeleting(null);
              setError(null);
            }
          }}
          onOk={() =>
            persist(
              items.filter((row) => row.menuId !== deleting.menuId),
              '菜单已删除'
            )
          }
        >
          {errorNotice}确认删除「{menuName(deleting)}」？
          {deleting.menuId === 'roles' &&
            '平台和租户的角色菜单入口将一并移除。'}
        </Modal>
      )}
    </Card>
  );
}
