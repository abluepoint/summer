import { useInterfaceOperations } from '@/auth/useInterfaceOperations';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Input,
  Message,
  Select,
  Space,
  Table,
  Typography,
} from '@arco-design/web-react';
import useActiveEnterprise from '../data-scopes/useActiveEnterprise';
import { Group, Permission } from '../permissions/api';
import {
  assignUserRoles,
  AuthorizationApiError,
  AuthorizationRole,
  createRole,
  getUserRoleAssignment,
  listPermissionChoices,
  listPermissionGroups,
  listRoles,
  saveRolePermissions,
  UserRoleAssignment,
} from './api';

export default function RolesPage() {
  const tenant = useActiveEnterprise();
  const [platform, setPlatform] = useState(false);
  useEffect(() => {
    if (tenant.ready && !tenant.enterpriseId) setPlatform(true);
  }, [tenant.ready, tenant.enterpriseId]);
  return (
    <>
      <Space style={{ marginBottom: 16 }}>
        <Select
          aria-label="授权域"
          value={platform ? 'platform' : 'enterprise'}
          disabled={tenant.busy}
          options={[
            { label: '企业域', value: 'enterprise' },
            { label: '平台域', value: 'platform' },
          ]}
          onChange={(v) => {
            if (tenant.dirty && !window.confirm('有未保存的授权，确定切换域？'))
              return;
            setPlatform(v === 'platform');
          }}
        />
        {!platform && (
          <Select
            aria-label="活动租户"
            placeholder="选择活动租户"
            value={tenant.enterpriseId || undefined}
            options={tenant.options}
            onChange={tenant.change}
            disabled={tenant.busy}
            style={{ width: 240 }}
          />
        )}
      </Space>
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
          domainLabel={platform ? '平台域' : `企业域：${tenant.enterpriseId}`}
          onDirtyChange={tenant.setDirty}
          onBusyChange={tenant.setBusy}
        />
      ) : (
        tenant.ready && <Alert type="info" content="请选择一个活动成员租户。" />
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
  const operations = useInterfaceOperations(
    base === '/api/v1/platform'
      ? { kind: 'PLATFORM' }
      : {
          kind: 'ENTERPRISE',
          id: decodeURIComponent(base.substring(base.lastIndexOf('/') + 1)),
        },
    ['roles.create', 'roles.permissions.write', 'roles.assign']
  );
  const [roles, setRoles] = useState<AuthorizationRole[]>([]);
  const [permissions, setPermissions] = useState<Permission[]>([]);
  const [groups, setGroups] = useState<Group[]>([]);
  const [editing, setEditing] = useState<AuthorizationRole>();
  const [permissionIds, setPermissionIds] = useState<string[]>([]);
  const [name, setName] = useState('');
  const [code, setCode] = useState('');
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
      const [r, p, g] = await Promise.all([
        listRoles(base),
        listPermissionChoices(base),
        listPermissionGroups(base),
      ]);
      if (!alive.current || generation !== epoch.current) return;
      setRoles(r);
      setPermissions(p);
      setGroups(g);
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
    setPermissionIds((old) =>
      checked
        ? Array.from(new Set([...old, ...ids]))
        : old.filter((id) => !ids.includes(id))
    );
    setDirty(true);
  };
  const groupIds = (root: string) => {
    const descendants = new Set([root]);
    for (let changed = true; changed; ) {
      changed = false;
      groups.forEach((g) => {
        if (
          g.parentGroupId &&
          descendants.has(g.parentGroupId) &&
          !descendants.has(g.groupId)
        ) {
          descendants.add(g.groupId);
          changed = true;
        }
      });
    }
    return permissions
      .filter((p) => descendants.has(p.groupId))
      .map((p) => p.permissionId);
  };
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
  const canSave =
    operations[editing ? 'roles.permissions.write' : 'roles.create'] === true;
  const save = () =>
    write(async () => {
      if (!canSave) return;
      const r = editing
        ? await saveRolePermissions(base, editing, permissionIds)
        : await createRole(base, name, code, permissionIds);
      if (!alive.current) return;
      setRoles((items) => [...items.filter((x) => x.roleId !== r.roleId), r]);
      setEditing(r);
      setPermissionIds(r.permissionIds);
      setDirty(false);
      if (username.trim() && assignment) {
        const current = await getUserRoleAssignment(base, username.trim());
        if (!alive.current) return;
        setAssignment((previous) =>
          previous ? { ...previous, sources: current.sources } : previous
        );
      }
      Message.success('角色权限已保存');
    });
  const saveAssignment = () =>
    write(async () => {
      if (!assignment || !operations['roles.assign']) return;
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
  return (
    <Card
      title="角色与权限"
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
      <Typography.Paragraph>
        当前授权域：{domainLabel}
        。按组勾选保存当前具体权限，后续移动或新增权限不会改变此授权。
      </Typography.Paragraph>
      {error && (
        <Alert
          type="error"
          content={`${error.message}${
            error.status === 412 ? ' 请刷新后重新核对授权。' : ''
          }${error.requestId ? ` (${error.requestId})` : ''}`}
        />
      )}
      <Space wrap>
        <Select
          aria-label="编辑角色"
          placeholder="新建角色"
          allowClear
          value={editing?.roleId}
          disabled={busy || dirty}
          options={roles.map((r) => ({
            label: `${r.name} (${r.status === 'ACTIVE' ? '启用' : '停用'})`,
            value: r.roleId,
          }))}
          onChange={(id) => {
            const r = roles.find((x) => x.roleId === id);
            setEditing(r);
            if (!r) {
              setName('');
              setCode('');
            }
            setPermissionIds(r?.permissionIds || []);
            setDirty(false);
          }}
        />
        {!editing && (
          <>
            <Input
              aria-label="角色名称"
              placeholder="角色名称"
              value={name}
              disabled={busy}
              onChange={(v) => {
                setName(v);
                setDirty(true);
              }}
            />
            <Input
              aria-label="角色编码"
              placeholder="角色编码"
              value={code}
              disabled={busy}
              onChange={(v) => {
                setCode(v);
                setDirty(true);
              }}
            />
          </>
        )}
        <Button
          type="primary"
          onClick={save}
          disabled={
            !canSave || !ready || busy || (!editing && (!name || !code))
          }
        >
          {editing ? '保存角色权限' : '创建角色'}
        </Button>
      </Space>
      <Space wrap style={{ marginTop: 16 }}>
        {groups.map((g) => {
          const ids = groupIds(g.groupId);
          return (
            <Checkbox
              key={g.groupId}
              disabled={busy || !ready || !ids.length}
              checked={
                ids.length > 0 && ids.every((id) => permissionIds.includes(id))
              }
              indeterminate={
                ids.some((id) => permissionIds.includes(id)) &&
                !ids.every((id) => permissionIds.includes(id))
              }
              onChange={(checked) => select(ids, checked)}
            >
              组：{g.name}
            </Checkbox>
          );
        })}
      </Space>
      <Table<Permission>
        rowKey="permissionId"
        data={permissions}
        pagination={{ pageSize: 10 }}
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
                disabled={busy || !ready}
                checked={permissionIds.includes(p.permissionId)}
                onChange={(checked) => select([p.permissionId], checked)}
              />
            ),
          },
        ]}
      />
      <Typography.Title heading={6}>用户角色</Typography.Title>
      <Space direction="vertical" style={{ width: '100%' }}>
        <Input
          aria-label="用户名"
          placeholder="用户名"
          value={username}
          disabled={busy}
          onChange={setUsername}
        />
        <Select
          aria-label="用户角色"
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
    </Card>
  );
}
