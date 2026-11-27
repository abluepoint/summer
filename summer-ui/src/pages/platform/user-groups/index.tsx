import React, { useState } from 'react';
import listStyles from '@/pages/platform/list-scroll.module.less';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Form,
  Input,
  Modal,
  Space,
  Table,
} from '@arco-design/web-react';
import { useInterfaceOperations } from '@/auth/useInterfaceOperations';
import { call } from '../../permissions/api';
import { base, useDirectory } from '../directory';

const path = '/user-groups';
type Group = {
  id: string;
  name: string;
  description: string;
  roles: string[];
};
type Catalog = {
  version: number;
  groups: Group[];
  roles: { id: string; name: string; active: boolean }[];
};
type Draft = Group & {
  action: 'create' | 'detail' | 'edit' | 'roles' | 'delete';
  version: number;
};

type Candidate = { id: string; name: string; active: boolean };
function RoleTable({
  rows,
  selected,
  onChange,
}: {
  rows: Candidate[];
  selected: string[];
  onChange: (keys: string[]) => void;
}) {
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(1);
  const term = search.trim().toLowerCase();
  const matches = rows.filter((r) => r.name.toLowerCase().includes(term));
  return (
    <Space direction="vertical" style={{ width: '100%' }}>
      <Input.Search
        aria-label="搜索角色名称"
        placeholder="搜索角色名称"
        value={search}
        allowClear
        onChange={(value) => {
          setSearch(value);
          setPage(1);
        }}
      />
      <span role="status">已选 {selected.length} 项</span>
      <Table
        size="small"
        rowKey="id"
        data={matches}
        columns={[
          { title: '角色名称', dataIndex: 'name' },
          {
            title: '状态',
            render: (_, row: Candidate) => (row.active ? '启用' : '停用'),
          },
        ]}
        rowSelection={{
          type: 'checkbox',
          selectedRowKeys: selected,
          checkCrossPage: true,
          preserveSelectedRowKeys: true,
          checkboxProps: (row) => ({
            'aria-label': '选择 ' + row.name,
          }),
          onChange: (keys) => onChange(keys.map(String)),
        }}
        pagination={{ current: page, pageSize: 10, onChange: setPage }}
      />
    </Space>
  );
}

export default function UserGroups() {
  const { data, error, reload } = useDirectory<Catalog>(path);
  const ops = useInterfaceOperations({ kind: 'PLATFORM' }, [
    'userGroups.create',
    'userGroups.edit',
    'userGroups.delete',
  ]);
  const [query, setQuery] = useState('');
  const [draft, setDraft] = useState<Draft>();
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState('');
  const roleNames = new Map(data?.roles.map((r) => [r.id, r.name]));
  const original = data?.groups.find((group) => group.id === draft?.id);
  function boundRoles(group: Group) {
    return group.roles.map((id) => roleNames.get(id) || id).join('、') || '—';
  }
  function open(
    action: Draft['action'],
    row: Group = { id: '', name: '', description: '', roles: [] }
  ) {
    if (!data) return;
    setDraft({ ...row, action, version: data.version });
    setFailure('');
  }
  async function save() {
    if (!draft || draft.action === 'detail' || busy) return;
    setBusy(true);
    setFailure('');
    try {
      let body;
      if (draft.action === 'roles') {
        body = {
          operation: 'ROLES',
          ...(draft.roles.length && {
            roles: draft.roles.map((roleId) => ({ roleId })),
          }),
        };
      } else if (draft.action !== 'delete') {
        body = {
          name: draft.name.trim(),
          description: draft.description.trim(),
          ...(draft.action === 'edit' && { operation: 'DETAILS' }),
        };
      }
      const target = draft.id
        ? path + '/' + encodeURIComponent(draft.id)
        : path;
      const method =
        draft.action === 'create'
          ? 'POST'
          : draft.action === 'delete'
          ? 'DELETE'
          : 'PUT';
      await call(base, target, method, body, draft.version);
      setDraft(undefined);
      reload();
    } catch (e) {
      setFailure(e.message || '操作失败，请重试');
    } finally {
      setBusy(false);
    }
  }
  return (
    <Card
      title="平台用户组管理"
      extra={
        <Button onClick={reload} disabled={busy}>
          刷新
        </Button>
      }
    >
      {error && <Alert type="error" content={error} />}
      <Space wrap style={{ marginBottom: 16 }}>
        <Input
          aria-label="搜索用户组名称"
          placeholder="搜索用户组名称"
          allowClear
          value={query}
          onChange={setQuery}
        />
        <Button
          type="primary"
          disabled={!data || !ops['userGroups.create'] || busy}
          onClick={() => open('create')}
        >
          新增用户组
        </Button>
      </Space>
      <div
        role="region"
        aria-label="平台用户组列表"
        tabIndex={0}
        className={listStyles.listScroll}
        style={{ overflowX: 'auto' }}
      >
        <Table
          style={{ minWidth: 720 }}
          pagination={{ pageSize: 10, showTotal: true }}
          rowKey="id"
          loading={!data && !error}
          data={
            data?.groups.filter((g) =>
              g.name.toLowerCase().includes(query.trim().toLowerCase())
            ) || []
          }
          columns={[
            { title: '用户组名称', dataIndex: 'name' },
            {
              title: '用户组说明',
              render: (_, group: Group) => group.description || '—',
            },
            {
              title: '绑定角色',
              render: (_, g: Group) => boundRoles(g),
            },
            {
              title: '操作',
              render: (_, g: Group) => (
                <Space wrap size={4}>
                  <Button onClick={() => open('detail', g)}>查看</Button>
                  <Button
                    disabled={!ops['userGroups.edit']}
                    onClick={() => open('edit', g)}
                  >
                    编辑
                  </Button>
                  <Button
                    disabled={!ops['userGroups.edit']}
                    onClick={() => open('roles', g)}
                  >
                    绑定角色
                  </Button>
                  <Button
                    status="danger"
                    disabled={!ops['userGroups.delete']}
                    onClick={() => open('delete', g)}
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
        visible={!!draft}
        title={
          draft &&
          {
            create: '新增用户组',
            detail: '用户组详情',
            edit: '编辑用户组',
            roles: '绑定角色 · ' + draft.name,
            delete: '删除用户组',
          }[draft.action]
        }
        maskClosable={false}
        style={{
          width:
            draft?.action === 'roles' || draft?.action === 'detail'
              ? 'min(1120px, 94vw)'
              : 'min(720px, 94vw)',
        }}
        footer={
          draft?.action === 'detail' ? (
            <Button onClick={() => setDraft(undefined)}>关闭</Button>
          ) : undefined
        }
        onCancel={() => !busy && setDraft(undefined)}
        onOk={save}
        confirmLoading={busy}
        okButtonProps={{
          disabled: !draft?.name.trim(),
          status: draft?.action === 'delete' ? 'danger' : 'default',
        }}
      >
        {draft &&
          (draft.action === 'detail' ? (
            <Descriptions
              column={1}
              data={[
                { label: '用户组名称', value: draft.name },
                { label: '用户组说明', value: draft.description || '—' },
                { label: '绑定角色', value: boundRoles(draft) },
              ]}
            />
          ) : draft.action === 'delete' ? (
            <p>删除“{draft.name}”？</p>
          ) : draft.action === 'roles' ? (
            <RoleTable
              rows={(data?.roles || []).filter(
                (role) => role.active || original?.roles.includes(role.id)
              )}
              selected={draft.roles}
              onChange={(roles) => setDraft({ ...draft, roles })}
            />
          ) : (
            <Form layout="vertical">
              <Form.Item label="用户组名称" required>
                <Input
                  aria-label="用户组名称"
                  maxLength={200}
                  value={draft.name}
                  onChange={(name) => setDraft({ ...draft, name })}
                />
              </Form.Item>
              <Form.Item label="用户组说明">
                <Input.TextArea
                  aria-label="用户组说明"
                  maxLength={500}
                  showWordLimit
                  value={draft.description}
                  onChange={(description) =>
                    setDraft({ ...draft, description })
                  }
                />
              </Form.Item>
            </Form>
          ))}
        {failure && <Alert type="error" content={failure} />}
      </Modal>
    </Card>
  );
}
