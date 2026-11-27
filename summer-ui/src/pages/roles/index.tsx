import {
  GroupPermissionCheckbox,
  indexGroupPermissions,
} from '../permissions/components/GroupPermissionCheckbox';
import { useInterfaceOperations } from '@/auth/useInterfaceOperations';
import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Input,
  Message,
  Modal,
  Select,
  Space,
  Table,
  Typography,
} from '@arco-design/web-react';
import { Prompt } from 'react-router-dom';
import useActiveEnterprise from '@/hooks/useActiveEnterprise';
import RolePermissionDetails from './RolePermissionDetails';
import RoleTable, { RolePanel } from './RoleTable';
import PermissionGroupTree from '../permissions/components/PermissionGroupTree';
import treeStyles from '../permissions/components/permission-group-tree.module.less';
import { Group, Permission } from '../permissions/api';
import {
  assignUserRoles,
  AuthorizationApiError,
  AuthorizationRole,
  createRole,
  getRole,
  updateRole,
  deleteRole,
  getUserRoleAssignment,
  listPermissionChoices,
  listPermissionGroups,
  listRoles,
  saveRolePermissions,
  UserRoleAssignment,
} from './api';

// Role details remain readable when the viewer lacks either catalog permission.
async function readRoleCatalog<T>(
  next: RolePanel,
  read: () => Promise<T[]>
): Promise<T[]> {
  try {
    return await read();
  } catch (e) {
    if (next === 'detail' && (e as AuthorizationApiError).status === 403)
      return [];
    throw e;
  }
}

export default function RolesPage({
  platform = false,
}: {
  platform?: boolean;
}) {
  const tenant = useActiveEnterprise();
  return (
    <>
      <Prompt
        when={tenant.dirty || tenant.busy}
        message={() => (tenant.busy ? false : '有未保存的授权，确定离开？')}
      />
      {platform || tenant.enterpriseId ? (
        <RolesContent
          key={
            platform ? 'PLATFORM:platform' : `ENTERPRISE:${tenant.enterpriseId}`
          }
          base={
            platform
              ? '/api/v1/platform'
              : `/api/v1/enterprises/${encodeURIComponent(tenant.enterpriseId)}`
          }
          domainLabel={
            platform ? '平台角色' : `租户角色：${tenant.enterpriseId}`
          }
          onDirtyChange={tenant.setDirty}
          onBusyChange={tenant.setBusy}
        />
      ) : (
        tenant.ready && <Alert type="info" content="请先在顶部选择活动租户。" />
      )}
    </>
  );
}
function RolesContent({
  base,
  domainLabel,
  onDirtyChange,
  onBusyChange,
}: {
  base: string;
  domainLabel: string;
  onDirtyChange: (v: boolean) => void;
  onBusyChange: (v: boolean) => void;
}) {
  const platform = base === '/api/v1/platform';
  const operations = useInterfaceOperations(
    base === '/api/v1/platform'
      ? { kind: 'PLATFORM' }
      : {
          kind: 'ENTERPRISE',
          id: decodeURIComponent(base.substring(base.lastIndexOf('/') + 1)),
        },
    [
      'roles.create',
      'roles.permissions.write',
      'roles.update',
      'roles.delete',
      ...(platform ? [] : ['roles.assign']),
    ]
  );
  const [roles, setRoles] = useState<AuthorizationRole[]>([]);
  const [permissions, setPermissions] = useState<Permission[]>([]);
  const [groups, setGroups] = useState<Group[]>([]);
  const [selectedGroupId, setSelectedGroupId] = useState<string>();
  const [editing, setEditing] = useState<AuthorizationRole>();
  const [permissionIds, setPermissionIds] = useState<string[]>([]);
  const [name, setName] = useState('');
  const [code, setCode] = useState('');
  const [panel, setPanel] = useState<RolePanel>();
  const [status, setStatus] = useState('ACTIVE');
  const [username, setUsername] = useState('');
  const [assignment, setAssignment] = useState<UserRoleAssignment>();
  const [roleIds, setRoleIds] = useState<string[]>([]);
  const [dirty, setDirty] = useState(false);
  const [bindingDirty, setBindingDirty] = useState(false);
  const [busy, setBusy] = useState(false);
  const [ready, setReady] = useState(false);
  const [error, setError] = useState<AuthorizationApiError>();
  const alive = useRef(true);
  const epoch = useRef(0);
  const userRead = useRef(0);
  const writing = useRef(false);
  useEffect(
    () => () => {
      alive.current = false;
      epoch.current++;
      onDirtyChange(false);
      onBusyChange(false);
    },
    [onDirtyChange, onBusyChange]
  );
  useEffect(
    () => onDirtyChange(dirty || bindingDirty),
    [dirty, bindingDirty, onDirtyChange]
  );
  const fail = useCallback((e: AuthorizationApiError) => {
    if (!alive.current) return;
    if (e.status === 401 || e.status === 403) {
      epoch.current++;
      userRead.current++;
      setReady(false);
      setPanel(undefined);
      setRoles([]);
      setPermissions([]);
      setGroups([]);
      setEditing(undefined);
      setPermissionIds([]);
      setName('');
      setCode('');
      setUsername('');
      setAssignment(undefined);
      setRoleIds([]);
      setDirty(false);
      setBindingDirty(false);
    }
    setError(e);
  }, []);
  const load = useCallback(async () => {
    if (writing.current) return;
    const generation = ++epoch.current;
    setBusy(true);
    try {
      const r = await listRoles(base);
      if (!alive.current || generation !== epoch.current) return;
      setRoles(r);
      setReady(true);
      setError(undefined);
    } catch (e) {
      if (generation === epoch.current) fail(e as AuthorizationApiError);
    } finally {
      if (alive.current) setBusy(false);
    }
  }, [base, fail]);
  useEffect(() => {
    load();
  }, [load]);
  useEffect(() => {
    const ticket = ++userRead.current;
    const generation = epoch.current;
    setAssignment(undefined);
    setRoleIds([]);
    setBindingDirty(false);
    if (!ready || !username.trim()) return;
    getUserRoleAssignment(base, username.trim())
      .then((v) => {
        if (
          !alive.current ||
          ticket !== userRead.current ||
          generation !== epoch.current
        )
          return;
        setAssignment(v);
        setRoleIds(v.roleIds);
        setError(undefined);
      })
      .catch((e) => {
        if (ticket === userRead.current && generation === epoch.current)
          fail(e);
      });
  }, [base, username, ready, fail]);
  const select = (ids: string[], checked: boolean) => {
    const toggled = new Set(ids);
    setPermissionIds((old) =>
      checked
        ? Array.from(new Set([...old, ...ids]))
        : old.filter((id) => !toggled.has(id))
    );
    setDirty(true);
  };
  const permissionsByGroup = useMemo(
    () => indexGroupPermissions(groups, permissions),
    [groups, permissions]
  );
  const write = async (action: () => Promise<void>) => {
    if (writing.current || !ready) return;
    writing.current = true;
    epoch.current++;
    userRead.current++;
    setBusy(true);
    onBusyChange(true);
    try {
      await action();
      if (alive.current) setError(undefined);
    } catch (e) {
      fail(e as AuthorizationApiError);
    } finally {
      writing.current = false;
      if (alive.current) {
        setBusy(false);
        onBusyChange(false);
      }
    }
  };
  const openPanel = async (next: RolePanel, row?: AuthorizationRole) => {
    if (busy || writing.current) return;
    setError(undefined);
    setBusy(true);
    try {
      const withCatalog =
        next === 'detail' ||
        next === 'permissions' ||
        (!platform && next === 'create');
      const [role, choices, choicesGroups] = await Promise.all([
        row ? getRole(base, row.roleId) : undefined,
        withCatalog
          ? readRoleCatalog(next, () => listPermissionChoices(base))
          : [],
        withCatalog
          ? readRoleCatalog(next, () => listPermissionGroups(base))
          : [],
      ]);
      if (!alive.current) return;
      setEditing(role);
      setName(role?.name || '');
      setCode(role?.code || '');
      setStatus(role?.status || 'ACTIVE');
      setPermissionIds(role?.permissionIds || []);
      const granted = new Set(role?.permissionIds);
      setPermissions(
        next === 'detail'
          ? choices.filter((p) => granted.has(p.permissionId))
          : choices
      );
      setGroups(choicesGroups);
      setSelectedGroupId(undefined);
      setDirty(false);
      setPanel(next);
    } catch (e) {
      fail(e as AuthorizationApiError);
    } finally {
      if (alive.current) setBusy(false);
    }
  };
  const closePanel = () => {
    if (busy || (dirty && !window.confirm('放弃未保存的角色修改？'))) return;
    setPanel(undefined);
    setDirty(false);
    setError(undefined);
  };
  const canSave =
    operations[
      panel === 'edit'
        ? 'roles.update'
        : editing
        ? 'roles.permissions.write'
        : 'roles.create'
    ] === true;
  const save = () =>
    write(async () => {
      if (!canSave) return;
      const r =
        editing && panel === 'edit'
          ? await updateRole(base, editing, name.trim(), status)
          : editing
          ? await saveRolePermissions(base, editing, permissionIds)
          : await createRole(base, name, code, permissionIds);
      if (!alive.current) return;
      setRoles((items) =>
        editing
          ? items.map((item) => (item.roleId === r.roleId ? r : item))
          : [r, ...items]
      );
      setEditing(r);
      setPanel(undefined);
      setPermissionIds(r.permissionIds);
      setDirty(false);
      if (username.trim() && assignment) {
        const current = await getUserRoleAssignment(base, username.trim());
        if (!alive.current) return;
        setAssignment((previous) =>
          previous ? { ...previous, sources: current.sources } : previous
        );
      }
      Message.success(
        panel === 'edit'
          ? '角色已更新'
          : panel === 'create'
          ? '角色已创建'
          : '角色权限已保存'
      );
    });
  const saveAssignment = () =>
    write(async () => {
      if (
        !assignment ||
        assignment.username !== username.trim() ||
        !operations['roles.assign']
      )
        return;
      const v = await assignUserRoles(
        base,
        username.trim(),
        roleIds,
        assignment.version
      );
      if (!alive.current) return;
      setAssignment(v);
      setBindingDirty(false);
      Message.success('用户角色已保存');
    });
  const selectedGroup = groups.find((g) => g.groupId === selectedGroupId);
  const visiblePermissions = selectedGroup
    ? permissionsByGroup.get(selectedGroup.groupId) || []
    : permissions;
  const selectedIds = new Set(permissionIds);
  const selectedInScope = visiblePermissions.filter((p) =>
    selectedIds.has(p.permissionId)
  ).length;
  const legacyFields = !!editing?.legacyPermissions?.length && (
    <Space direction="vertical" style={{ marginTop: 16 }}>
      <Typography.Text>历史授权（可保留或移除）</Typography.Text>
      {editing.legacyPermissions.map((p) => (
        <Checkbox
          key={p.permissionId}
          disabled={busy || !ready || !canSave}
          checked={selectedIds.has(p.permissionId)}
          onChange={(checked) => select([p.permissionId], checked)}
        >
          {p.name}
        </Checkbox>
      ))}
    </Space>
  );
  const permissionFields = (
    <>
      <Checkbox
        disabled={busy || !canSave || !visiblePermissions.length}
        checked={
          visiblePermissions.length > 0 &&
          selectedInScope === visiblePermissions.length
        }
        indeterminate={
          selectedInScope > 0 && selectedInScope < visiblePermissions.length
        }
        onChange={(checked) =>
          select(
            visiblePermissions.map((p) => p.permissionId),
            checked
          )
        }
      >
        {selectedGroup
          ? `全选「${selectedGroup.name}」及子组权限`
          : '全选目录中的权限'}
        （{selectedInScope}/{visiblePermissions.length}）
      </Checkbox>
      {base !== '/api/v1/platform' && (
        <Typography.Paragraph style={{ marginTop: 16 }}>
          权限由平台统一维护，当前租户仅配置角色和人员授权。
        </Typography.Paragraph>
      )}
      <Table<Permission>
        rowKey="permissionId"
        key={selectedGroupId || ''}
        data={visiblePermissions}
        scroll={{ x: 540 }}
        pagination={{
          pageSize: 10,
          showTotal: true,
          hideOnSinglePage: true,
        }}
        style={{ marginTop: 16 }}
        columns={[
          { title: '权限', dataIndex: 'name' },
          { title: '编码', dataIndex: 'code' },
          {
            title: '类型',
            render: (_, p) =>
              ({ INTERFACE: '接口', MENU: '菜单', DATA: '数据' }[p.type] ||
              p.type),
          },
          {
            title: '状态',
            render: (_, p) => (p.status === 'ACTIVE' ? '启用' : '停用'),
          },
          {
            title: '授予',
            render: (_, p) => (
              <Checkbox
                aria-label={`授予 ${p.name}`}
                disabled={busy || !ready || !canSave}
                checked={selectedIds.has(p.permissionId)}
                onChange={(checked) => select([p.permissionId], checked)}
              />
            ),
          },
        ]}
      />
      {!selectedGroup && legacyFields}
    </>
  );
  const assignmentFields = (
    <>
      <Typography.Title heading={6}>用户角色</Typography.Title>
      <Space direction="vertical" style={{ width: '100%' }}>
        <Input
          aria-label="用户名"
          placeholder="用户名"
          value={username}
          disabled={busy}
          onChange={(value) => {
            if (
              bindingDirty &&
              !window.confirm('放弃当前用户未保存的角色分配？')
            )
              return;
            setUsername(value);
          }}
        />
        <Select
          aria-label="用户角色"
          placeholder="请选择角色"
          mode="multiple"
          value={roleIds}
          disabled={!ready || busy || !assignment}
          options={roles.map((r) => ({
            label: r.name,
            value: r.roleId,
            disabled: r.status !== 'ACTIVE' && !roleIds.includes(r.roleId),
          }))}
          onChange={(v) => {
            setRoleIds(v);
            setBindingDirty(true);
          }}
        />
        <Button
          type="primary"
          disabled={
            !operations['roles.assign'] ||
            !ready ||
            busy ||
            !assignment ||
            !bindingDirty
          }
          onClick={saveAssignment}
        >
          保存用户角色
        </Button>
        <Typography.Text>有效权限来源（多个活动角色取并集）</Typography.Text>
        <Table
          rowKey={(s) => `${s.permissionId}:${s.roleId}`}
          data={assignment?.sources || []}
          pagination={false}
          columns={[
            { title: '权限', dataIndex: 'permissionName' },
            { title: '来源角色', dataIndex: 'roleName' },
          ]}
        />
      </Space>
    </>
  );
  return (
    <Card
      title={base === '/api/v1/platform' ? '平台角色管理' : '租户角色管理'}
      extra={
        <Button
          onClick={() => {
            if (
              (dirty || bindingDirty) &&
              !window.confirm('放弃当前未保存的授权并刷新？')
            )
              return;
            setEditing(undefined);
            setPermissionIds([]);
            setName('');
            setCode('');
            setUsername('');
            setAssignment(undefined);
            setRoleIds([]);
            setDirty(false);
            setBindingDirty(false);
            load();
          }}
          disabled={busy}
        >
          刷新
        </Button>
      }
    >
      {!platform && (
        <Typography.Paragraph>
          当前授权域：{domainLabel}
          。按组勾选保存当前具体权限，后续移动或新增权限不会改变此授权。
        </Typography.Paragraph>
      )}
      {error && !panel && (
        <Alert
          type="error"
          content={`${error.message}${
            error.status === 412 ? ' 请刷新后重新核对授权。' : ''
          }${error.requestId ? ` (${error.requestId})` : ''}`}
        />
      )}
      <RoleTable
        platform={platform}
        roles={roles}
        operations={operations}
        busy={busy}
        ready={ready}
        openPanel={openPanel}
      />
      <Modal
        visible={!!panel}
        title={
          panel &&
          {
            create: '新建角色',
            edit: '编辑角色',
            detail: '角色详情',
            permissions: '配置角色权限',
            delete: '删除角色',
          }[panel]
        }
        onCancel={closePanel}
        style={{
          width:
            panel === 'permissions' ||
            panel === 'detail' ||
            (!platform && panel === 'create')
              ? 'min(1120px, 94vw)'
              : 'min(720px, 94vw)',
        }}
        footer={null}
        maskClosable={false}
      >
        {error && (
          <Alert
            type="error"
            content={
              error.status === 412
                ? '角色已变更，请关闭后重新打开。'
                : error.message
            }
          />
        )}
        {panel === 'detail' && editing && (
          <Space wrap size={24}>
            <Typography.Text>角色名称：{editing.name}</Typography.Text>
            <Typography.Text>角色编码：{editing.code}</Typography.Text>
            <Typography.Text>
              状态：{editing.status === 'ACTIVE' ? '启用' : '停用'}
            </Typography.Text>
            <Typography.Text>
              已授权限：{editing.permissionIds.length} 项
            </Typography.Text>
          </Space>
        )}
        {(panel === 'create' || panel === 'edit') && (
          <Space direction="vertical" style={{ width: '100%' }}>
            <label>
              角色名称
              <Input
                aria-label="角色名称"
                placeholder="角色名称"
                maxLength={200}
                value={name}
                disabled={busy}
                onChange={(v) => {
                  setName(v);
                  setDirty(true);
                }}
              />
            </label>
            <label>
              角色编码
              <Input
                aria-label="角色编码"
                placeholder="角色编码"
                maxLength={96}
                value={code}
                disabled={busy || panel === 'edit'}
                onChange={(v) => {
                  setCode(v);
                  setDirty(true);
                }}
              />
            </label>
            {panel === 'edit' && (
              <Select
                aria-label="角色状态"
                value={status}
                disabled={busy}
                options={[
                  { value: 'ACTIVE', label: '启用' },
                  { value: 'DISABLED', label: '停用' },
                ]}
                onChange={(v) => {
                  setStatus(v);
                  setDirty(true);
                }}
              />
            )}
          </Space>
        )}
        {(panel === 'permissions' ||
          panel === 'detail' ||
          (panel === 'create' && !platform)) && (
          <>
            {panel === 'permissions' && (
              <Typography.Text>{editing?.name}</Typography.Text>
            )}
            <div style={{ maxHeight: '65vh', overflow: 'auto' }}>
              <div className={treeStyles.workspace}>
                <PermissionGroupTree
                  groups={
                    panel === 'detail'
                      ? groups.filter(
                          (g) => permissionsByGroup.get(g.groupId)?.length
                        )
                      : groups
                  }
                  selected={selectedGroup}
                  onSelect={setSelectedGroupId}
                  renderGroupCheckbox={
                    panel === 'detail'
                      ? undefined
                      : (group) => {
                          return (
                            <GroupPermissionCheckbox
                              group={group}
                              items={
                                permissionsByGroup.get(group.groupId) || []
                              }
                              selectedIds={selectedIds}
                              disabled={busy || !ready || !canSave}
                              onSelect={select}
                            />
                          );
                        }
                  }
                  loading={false}
                />
                <section
                  className={treeStyles.permissionTables}
                  aria-label={panel === 'detail' ? '已授权限' : '可配置权限'}
                >
                  {panel === 'detail' && editing ? (
                    <RolePermissionDetails
                      role={editing}
                      groups={groups}
                      selected={selectedGroup}
                      permissions={visiblePermissions}
                    />
                  ) : (
                    <div>{permissionFields}</div>
                  )}
                </section>
              </div>
            </div>
          </>
        )}
        {panel === 'delete' && (
          <Typography.Paragraph>
            确定删除角色“{editing?.name}”（{editing?.code}
            ）？系统会同时解除所有用户与该角色的关联。此操作无法撤销。
          </Typography.Paragraph>
        )}
        <Space
          style={{
            marginTop: 20,
            display: 'flex',
            justifyContent: 'flex-end',
          }}
        >
          <Button disabled={busy} onClick={closePanel}>
            {panel === 'detail' ? '关闭' : '取消'}
          </Button>
          {panel === 'delete' && editing && (
            <Button
              status="danger"
              type="primary"
              loading={busy}
              disabled={!operations['roles.delete']}
              onClick={() =>
                write(async () => {
                  await deleteRole(base, editing);
                  if (!alive.current) return;
                  setRoles((items) =>
                    items.filter((x) => x.roleId !== editing.roleId)
                  );
                  setPanel(undefined);
                  setEditing(undefined);
                  Message.success('角色已删除');
                  if (assignment) {
                    const current = await getUserRoleAssignment(
                      base,
                      assignment.username
                    );
                    if (!alive.current) return;
                    if (!bindingDirty) {
                      setAssignment(current);
                      setRoleIds(current.roleIds);
                    } else {
                      // Keep the draft's original version so concurrent assignment changes conflict.
                      setAssignment({
                        ...assignment,
                        sources: current.sources,
                      });
                      setRoleIds((ids) =>
                        ids.filter((id) => id !== editing.roleId)
                      );
                    }
                  }
                })
              }
            >
              确认删除
            </Button>
          )}
          {panel && !['delete', 'detail'].includes(panel) && (
            <Button
              type="primary"
              loading={busy}
              disabled={!canSave || !dirty || !name.trim() || !code.trim()}
              onClick={save}
            >
              {panel === 'create' ? '创建角色' : '保存'}
            </Button>
          )}
        </Space>
      </Modal>
      {!platform && assignmentFields}
    </Card>
  );
}
