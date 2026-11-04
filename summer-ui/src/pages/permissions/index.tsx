import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tree,
} from '@arco-design/web-react';
import { Prompt } from 'react-router-dom';
import useActiveEnterprise from '../data-scopes/useActiveEnterprise';
import { call, Failure, Group, Page, Permission } from './api';

const enumLabels: Record<string, string> = {
  INTERFACE: '接口权限',
  MENU: '菜单权限',
  DATA: '数据权限',
  AUTHORIZED: '授权访问',
  AUTHENTICATED: '登录访问',
  PUBLIC: '公开访问',
  ACTIVE: '启用',
  DISABLED: '停用',
};
const filterLabels: Record<string, string> = {
  type: '权限类型',
  status: '状态',
  accessMode: '访问方式',
};
const enumOptions = (values: string[]) =>
  values.map((value) => ({ label: enumLabels[value], value }));

export default function PermissionsPage() {
  const tenant = useActiveEnterprise();
  const [platform, setPlatform] = useState(false);
  return (
    <>
      <Space style={{ marginBottom: 16 }}>
        <Select
          aria-label="权限管理域"
          value={platform ? 'platform' : 'enterprise'}
          options={[
            { label: '企业', value: 'enterprise' },
            { label: '平台', value: 'platform' },
          ]}
          onChange={(v) => {
            if (
              !tenant.busy &&
              (!tenant.dirty || window.confirm('放弃未保存修改？'))
            ) {
              tenant.setDirty(false);
              setPlatform(v === 'platform');
            }
          }}
        />
        {!platform && (
          <Select
            aria-label="活动租户"
            value={tenant.enterpriseId || undefined}
            options={tenant.options}
            onChange={tenant.change}
            disabled={tenant.busy}
          />
        )}
      </Space>
      {(platform || tenant.enterpriseId) && (
        <Content
          key={platform ? 'platform' : tenant.enterpriseId}
          base={
            platform
              ? '/api/v1/platform'
              : `/api/v1/enterprises/${encodeURIComponent(tenant.enterpriseId)}`
          }
          dirty={tenant.setDirty}
          busy={tenant.setBusy}
        />
      )}
    </>
  );
}
function Content({
  base,
  dirty,
  busy,
}: {
  base: string;
  dirty: (v: boolean) => void;
  busy: (v: boolean) => void;
}) {
  const [groups, setGroups] = useState<Group[]>([]);
  const [data, setData] = useState<Page<Permission>>({
    items: [],
    total: 0,
    page: 1,
    size: 20,
  });
  const [filter, setFilter] = useState<Record<string, string>>({});
  const [selected, setSelected] = useState<string>();
  const [error, setError] = useState<Failure>();
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editor, setEditor] =
    useState<{ group: boolean; row?: Group | Permission }>();
  const [changed, setChanged] = useState(false);
  const [form] = Form.useForm();
  const generation = useRef(0);
  const alive = useRef(true);
  const pending = useRef<{ fingerprint: string; key: string }>();
  useEffect(
    () => () => {
      alive.current = false;
      generation.current++;
      dirty(false);
      busy(false);
    },
    [dirty, busy]
  );
  useEffect(() => {
    dirty(changed);
    const before = (e: BeforeUnloadEvent) => {
      if (changed) {
        e.preventDefault();
        e.returnValue = '';
      }
    };
    window.addEventListener('beforeunload', before);
    return () => window.removeEventListener('beforeunload', before);
  }, [changed, dirty]);
  const failure = useCallback((e: Failure) => {
    if (!alive.current) return;
    setError(e);
    if ([401, 403].includes(e.status)) {
      generation.current++;
      setLoading(false);
      setGroups([]);
      setData({ items: [], total: 0, page: 1, size: 20 });
      setEditor(undefined);
      setChanged(false);
    }
  }, []);
  const load = useCallback(async () => {
    const ticket = ++generation.current;
    setLoading(true);
    try {
      const all: Group[] = [];
      let page = 1;
      let total = 0;
      do {
        const result = await call<Page<Group>>(
          base,
          `/permission-groups?page=${page++}&size=200`
        );
        all.push(...result.items);
        total = result.total;
      } while (all.length < total);
      const params = new URLSearchParams({
        page: '1',
        size: '20',
        ...filter,
        ...(selected ? { groupId: selected, includeDescendants: 'true' } : {}),
      });
      const result = await call<Page<Permission>>(
        base,
        `/permissions?${params}`
      );
      if (alive.current && ticket === generation.current) {
        setGroups(all);
        setData(result);
        setError(undefined);
      }
    } catch (e) {
      if (alive.current && ticket === generation.current) failure(e);
    } finally {
      if (alive.current && ticket === generation.current) setLoading(false);
    }
  }, [base, filter, selected, failure]);
  useEffect(() => {
    load();
  }, [load]);
  const open = (group: boolean, row?: Group | Permission) => {
    pending.current = undefined;
    setEditor({ group, row });
    setChanged(false);
    form.resetFields();
    form.setFieldsValue(
      row ||
        (group
          ? { parentGroupId: selected, sortOrder: 0 }
          : {
              groupId: selected,
              type: 'INTERFACE',
              accessMode: 'AUTHORIZED',
              status: 'ACTIVE',
            })
    );
  };
  const save = async (remove = false, target = editor) => {
    if (!target || saving) return;
    let values: Record<string, unknown> = {};
    if (!remove) {
      try {
        await form.validate();
        values = form.getFieldsValue();
      } catch {
        return;
      }
    }
    const id =
      target.row &&
      (target.group
        ? (target.row as Group).groupId
        : (target.row as Permission).permissionId);
    if (!target.group && target.row) {
      delete values.code;
      delete values.type;
    }
    if (target.group) values.parentGroupId = values.parentGroupId || null;
    else if (
      typeof values.description === 'string' &&
      !values.description.trim()
    )
      values.description = null;
    const path = `/${target.group ? 'permission-groups' : 'permissions'}${
      id ? `/${id}` : ''
    }`;
    const method = remove ? 'DELETE' : id ? 'PATCH' : 'POST';
    const fingerprint = JSON.stringify([
      method,
      path,
      values,
      target.row?.version,
    ]);
    if (pending.current?.fingerprint !== fingerprint)
      pending.current = {
        fingerprint,
        key: `ui-permission-${Date.now()}-${Math.random()
          .toString(36)
          .slice(2)}`,
      };
    setSaving(true);
    busy(true);
    try {
      await call(
        base,
        path,
        method,
        remove ? undefined : values,
        target.row?.version,
        pending.current.key
      );
      if (!alive.current) return;
      setEditor(undefined);
      setChanged(false);
      pending.current = undefined;
      if (remove && target.group && id === selected) setSelected(undefined);
      else await load();
    } catch (e) {
      failure(e);
    } finally {
      if (alive.current) {
        setSaving(false);
        busy(false);
      }
    }
  };
  const options = groups.map((g) => ({ value: g.groupId, label: g.name }));
  const tree = (
    parent: string | null
  ): Array<{ key: string; title: string; children: unknown[] }> =>
    groups
      .filter((g) => g.parentGroupId === parent)
      .map((g) => ({
        key: g.groupId,
        title: g.name,
        children: tree(g.groupId),
      }));
  return (
    <Card title="权限管理">
      <Prompt when={changed} message="放弃未保存修改？" />
      {error && (
        <Alert
          type="error"
          content={`${error.status || '网络错误'}：${error.message} ${
            error.requestId || ''
          }`}
          style={{ marginBottom: 16 }}
        />
      )}
      <Space wrap>
        <Button onClick={() => open(true)} disabled={saving}>
          新建权限组
        </Button>
        <Button
          type="primary"
          onClick={() => open(false)}
          disabled={saving || !groups.length}
        >
          新建权限
        </Button>
        <Button onClick={load} disabled={saving}>
          刷新
        </Button>
        <Input
          aria-label="权限名称筛选"
          placeholder="权限名称"
          onChange={(v) => setFilter((q) => ({ ...q, name: v }))}
        />
        {['type', 'status', 'accessMode'].map((k) => (
          <Select
            key={k}
            aria-label={filterLabels[k]}
            placeholder={filterLabels[k]}
            allowClear
            style={{ width: 160 }}
            options={(k === 'type'
              ? ['INTERFACE', 'MENU', 'DATA']
              : k === 'status'
              ? ['ACTIVE', 'DISABLED']
              : ['AUTHORIZED', 'PUBLIC', 'AUTHENTICATED']
            ).map((v) => ({ label: enumLabels[v], value: v }))}
            onChange={(v) =>
              setFilter((q) => {
                const n = { ...q };
                if (v) n[k] = v;
                else delete n[k];
                return n;
              })
            }
          />
        ))}
      </Space>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: '260px 1fr',
          gap: 20,
          marginTop: 20,
        }}
      >
        <div>
          <Button onClick={() => setSelected(undefined)}>全部权限</Button>
          <Tree
            treeData={tree(null) as never}
            selectedKeys={selected ? [selected] : []}
            onSelect={(keys) => setSelected(keys[0] as string)}
          />
          {selected && (
            <Space>
              <Button
                onClick={() =>
                  open(
                    true,
                    groups.find((g) => g.groupId === selected)
                  )
                }
              >
                编辑组
              </Button>
              <Button
                status="danger"
                onClick={() =>
                  Modal.confirm({
                    title: '删除空权限组？',
                    onOk: () =>
                      save(true, {
                        group: true,
                        row: groups.find((g) => g.groupId === selected),
                      }),
                  })
                }
              >
                删除组
              </Button>
            </Space>
          )}
        </div>
        <Table
          rowKey="permissionId"
          loading={loading}
          data={data.items}
          pagination={{
            total: data.total,
            pageSize: data.size,
            current: data.page,
            onChange: async (page) => {
              const ticket = ++generation.current;
              try {
                const q = new URLSearchParams({
                  ...filter,
                  page: String(page),
                  size: '20',
                  ...(selected
                    ? { groupId: selected, includeDescendants: 'true' }
                    : {}),
                });
                const r = await call<Page<Permission>>(
                  base,
                  `/permissions?${q}`
                );
                if (alive.current && ticket === generation.current) setData(r);
              } catch (e) {
                failure(e);
              }
            },
          }}
          columns={[
            { title: '名称', dataIndex: 'name' },
            { title: '标识', dataIndex: 'code' },
            {
              title: '权限类型',
              dataIndex: 'type',
              render: (v) => enumLabels[v],
            },
            {
              title: '访问方式',
              dataIndex: 'accessMode',
              render: (v) => enumLabels[v],
            },
            {
              title: '状态',
              dataIndex: 'status',
              render: (v) => enumLabels[v],
            },
            { title: '版本', dataIndex: 'version' },
            {
              title: '操作',
              render: (_, row: Permission) => (
                <Space>
                  <Button onClick={() => open(false, row)}>编辑</Button>
                  <Button
                    status="danger"
                    onClick={() =>
                      Modal.confirm({
                        title: '删除权限？',
                        content: '仍被引用的权限不可删除。',
                        onOk: () => save(true, { group: false, row }),
                      })
                    }
                  >
                    删除
                  </Button>
                </Space>
              ),
            },
          ]}
        />
      </div>
      <Modal
        title={editor?.group ? '权限组' : '权限'}
        visible={!!editor}
        confirmLoading={saving}
        onOk={() => save()}
        onCancel={() => {
          if (!saving && (!changed || window.confirm('放弃未保存修改？'))) {
            setEditor(undefined);
            setChanged(false);
          }
        }}
        unmountOnExit
      >
        {error && (
          <Alert
            type="error"
            content={`${error.status}：${error.message}`}
            style={{ marginBottom: 12 }}
          />
        )}
        <Form
          form={form}
          layout="vertical"
          onValuesChange={() => setChanged(true)}
        >
          <Form.Item
            label="名称"
            field="name"
            rules={[{ required: true, maxLength: 200 }]}
          >
            <Input />
          </Form.Item>
          {editor?.group ? (
            <>
              <Form.Item label="父组" field="parentGroupId">
                <Select
                  allowClear
                  options={options.filter(
                    (x) => x.value !== (editor.row as Group)?.groupId
                  )}
                />
              </Form.Item>
              <Form.Item
                label="排序"
                field="sortOrder"
                rules={[{ required: true }]}
              >
                <InputNumber precision={0} />
              </Form.Item>
            </>
          ) : (
            <>
              <Form.Item
                label="稳定标识"
                field="code"
                rules={[
                  {
                    required: true,
                    match: /^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/,
                  },
                ]}
              >
                <Input disabled={!!editor?.row} />
              </Form.Item>
              <Form.Item label="说明" field="description">
                <Input.TextArea maxLength={2000} />
              </Form.Item>
              <Form.Item
                label="权限类型"
                field="type"
                rules={[{ required: true }]}
              >
                <Select
                  disabled={!!editor?.row}
                  options={enumOptions(['INTERFACE', 'MENU', 'DATA'])}
                />
              </Form.Item>
              <Alert content="数据权限详情尚未交付，数据权限只能保存为停用。创建权限不会自动授予任何角色。" />
              <Form.Item
                label="所属组"
                field="groupId"
                rules={[{ required: true }]}
              >
                <Select options={options} />
              </Form.Item>
              <Form.Item
                label="访问方式"
                field="accessMode"
                rules={[{ required: true }]}
              >
                <Select
                  options={enumOptions([
                    'AUTHORIZED',
                    'AUTHENTICATED',
                    'PUBLIC',
                  ])}
                />
              </Form.Item>
              <Form.Item
                label="状态"
                field="status"
                rules={[{ required: true }]}
              >
                <Select options={enumOptions(['ACTIVE', 'DISABLED'])} />
              </Form.Item>
            </>
          )}
        </Form>
      </Modal>
    </Card>
  );
}
