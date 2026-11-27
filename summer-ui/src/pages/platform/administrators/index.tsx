import React, { useEffect, useState } from 'react';
import listStyles from '@/pages/platform/list-scroll.module.less';
import {
  Alert,
  Button,
  Card,
  Input,
  Modal,
  Checkbox,
  Space,
  Table,
  Tabs,
} from '@arco-design/web-react';
import { useSelector } from 'react-redux';
import { GlobalState } from '@/store';
import { setUserActive } from '../../auth-users/api';
import { useInterfaceOperations } from '@/auth/useInterfaceOperations';
import { call } from '../../permissions/api';
import { assignUserRoles } from '../../roles/api';
import {
  base,
  Person,
  PersonPicker,
  RosterGroup,
  saveGroupMembers,
  useDirectory,
} from '../directory';

import MemberAssignments, { MembershipChange } from './MemberAssignments';
import { RoleAssignmentRow, saveRoleMembers } from './roleMembers';
import AssignUserGroups from './AssignUserGroups';

type Dialog =
  | { kind: 'add' }
  | { kind: 'remove' | 'roles' | 'status'; person: Person };

export default function Administrators() {
  const subject = useSelector((s: GlobalState) => s.userInfo?.subject);
  const { data, error, reload } = useDirectory<{
    version: number;
    people: Person[];
    groups: RosterGroup[];
    roles?: { roleId: string; name: string; status: string }[];
    assignments?: RoleAssignmentRow[];
  }>('/administrators');
  const ops = useInterfaceOperations({ kind: 'PLATFORM' }, [
    'platformDirectory.add',
    'identity.users.write',
    'platformDirectory.remove',
    'roles.assign',
    'platformDirectory.assignGroup',
  ]);
  const [query, setQuery] = useState('');
  const [dialog, setDialog] = useState<Dialog>();
  const [groupUsername, setGroupUsername] = useState<string>();
  const groupPerson = data?.people.find((p) => p.username === groupUsername);
  const canAssignGroups = !!ops['platformDirectory.assignGroup'];
  const [user, setUser] = useState('');
  const [name, setName] = useState('');
  const roles = data?.roles || [];
  const [selected, setSelected] = useState<string[]>([]);
  const [bindingVersion, setBindingVersion] = useState<number>();
  const [failure, setFailure] = useState('');
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    setFailure('');
    setUser('');
    setName('');
    setSelected([]);
    setBindingVersion(undefined);
    if (dialog?.kind === 'roles' && data?.assignments) {
      const rows = data.assignments.filter(
        (r) => r.username === dialog.person.username
      );
      setSelected(rows.filter((r) => r.roleId).map((r) => r.roleId));
      setBindingVersion(rows.length ? rows[0].version : 0);
    }
  }, [dialog, data]);
  async function save() {
    if (!data || !dialog || busy) return;
    if (dialog.kind === 'status' && !ops['identity.users.write']) return;
    setBusy(true);
    setFailure('');
    try {
      if (dialog.kind === 'add')
        await call(
          base,
          '/administrators',
          'POST',
          { username: user, displayName: name || user },
          data.version
        );
      else if (dialog.kind === 'remove')
        await call(
          base,
          '/administrators/' + encodeURIComponent(dialog.person.username),
          'DELETE',
          undefined,
          data.version
        );
      else if (dialog.kind === 'status')
        await setUserActive(dialog.person.username, !dialog.person.active);
      else if (bindingVersion !== undefined)
        await assignUserRoles(
          base,
          dialog.person.username,
          selected,
          bindingVersion
        );
      setDialog(undefined);
      reload();
    } catch (e) {
      setFailure(
        dialog.kind === 'status' && e.status === 403
          ? '无权更改账号状态'
          : e.message || '保存失败，请重试'
      );
    } finally {
      setBusy(false);
    }
  }
  const roleAction =
    ops['roles.assign'] && data?.assignments
      ? (person: Person) =>
          person.active && (
            <Button onClick={() => setDialog({ kind: 'roles', person })}>
              分配角色
            </Button>
          )
      : undefined;
  const statusAction = ops['identity.users.write']
    ? (person: Person) => (
        <Button onClick={() => setDialog({ kind: 'status', person })}>
          {person.active ? '停用' : '启用'}
        </Button>
      )
    : undefined;
  const people = (data?.people || []).filter((p) =>
    (p.displayName + ' ' + p.username)
      .toLowerCase()
      .includes(query.trim().toLowerCase())
  );
  const groupNames = new Map<string, string[]>();
  for (const group of data?.groups || []) {
    for (const username of group.administrators) {
      const names = groupNames.get(username) || [];
      names.push(group.name);
      groupNames.set(username, names);
    }
  }
  const roleNames = new Map(roles.map((role) => [role.roleId, role.name]));
  const assignments = new Map<string, string[]>();
  const membersByRole = new Map<string, string[]>();
  for (const entry of data?.assignments || []) {
    if (!entry.roleId) continue;
    const names = assignments.get(entry.username) || [];
    const members = membersByRole.get(entry.roleId) || [];
    members.push(entry.username);
    membersByRole.set(entry.roleId, members);
    names.push(roleNames.get(entry.roleId) || entry.roleId);
    assignments.set(entry.username, names);
  }
  function saveGroup(change: MembershipChange) {
    const members =
      change.kind === 'add'
        ? [...change.list.administrators, ...change.selected]
        : change.list.administrators.filter(
            (username) => username !== change.person.username
          );
    return saveGroupMembers(change.list.id, members, change.version);
  }
  return (
    <Card title="平台管理员管理">
      {error && <Alert type="error" content={error} />}
      <Tabs defaultActiveTab="administrators">
        <Tabs.TabPane key="administrators" title="管理员">
          <Space wrap>
            <Input
              aria-label="搜索管理员"
              placeholder="搜索姓名或账号"
              value={query}
              onChange={setQuery}
            />
            {ops['platformDirectory.add'] && (
              <Button type="primary" onClick={() => setDialog({ kind: 'add' })}>
                添加管理员
              </Button>
            )}
            <Button onClick={reload}>刷新</Button>
          </Space>
          <div
            role="region"
            aria-label="平台管理员名单"
            tabIndex={0}
            className={listStyles.listScroll}
            style={{ overflowX: 'auto' }}
          >
            <Table
              style={{ minWidth: 'min-content' }}
              rowKey="username"
              data={people}
              pagination={{ pageSize: 20, showTotal: true }}
              columns={[
                { title: '姓名', dataIndex: 'displayName' },
                { title: '账号', dataIndex: 'username' },
                ...(data?.assignments
                  ? [
                      {
                        title: '分配角色',
                        render: (_, p: Person) =>
                          assignments.get(p.username)?.join('、') || '—',
                      },
                    ]
                  : []),
                {
                  title: '所属用户组',
                  render: (_, p: Person) =>
                    groupNames.get(p.username)?.join('、') || '—',
                },
                {
                  title: '账号状态',
                  render: (_, p: Person) => (p.active ? '启用' : '停用'),
                },
                {
                  title: '操作',
                  render: (_, p: Person) => (
                    <Space>
                      {roleAction?.(p)}
                      {canAssignGroups && (
                        <Button onClick={() => setGroupUsername(p.username)}>
                          分配用户组
                        </Button>
                      )}
                      {statusAction?.(p)}
                      {ops['platformDirectory.remove'] && (
                        <Button
                          status="danger"
                          onClick={() =>
                            setDialog({ kind: 'remove', person: p })
                          }
                        >
                          移出名单
                        </Button>
                      )}
                    </Space>
                  ),
                },
              ]}
            />
          </div>
        </Tabs.TabPane>
        <Tabs.TabPane key="groups" title="用户组">
          {subject && (
            <MemberAssignments
              key={subject}
              subject={subject}
              assignments={data?.assignments && assignments}
              kind="用户组"
              lists={data?.groups || []}
              saveMembers={saveGroup}
              people={data?.people || []}
              version={data?.version}
              canAssign={canAssignGroups}
              reload={reload}
            />
          )}
        </Tabs.TabPane>
        {data?.roles && data.assignments && (
          <Tabs.TabPane key="roles" title="角色">
            {subject && (
              <MemberAssignments
                key={subject}
                kind="角色"
                subject={subject}
                lists={roles.map((role) => ({
                  id: role.roleId,
                  name: role.name,
                  disabled: role.status !== 'ACTIVE',
                  description:
                    role.status === 'ACTIVE'
                      ? '直接分配的管理员'
                      : '角色已停用 · 直接分配的管理员',
                  administrators: membersByRole.get(role.roleId) || [],
                }))}
                assignments={assignments}
                people={data.people}
                version={data.version}
                canAssign={!!ops['roles.assign']}
                saveMembers={saveRoleMembers}
                reload={reload}
              />
            )}
          </Tabs.TabPane>
        )}
      </Tabs>
      {groupPerson && (
        <AssignUserGroups
          person={groupPerson}
          canAssign={canAssignGroups}
          groups={data?.groups || []}
          reload={reload}
          onClose={() => setGroupUsername(undefined)}
        />
      )}
      <Modal
        visible={!!dialog}
        title={
          dialog?.kind === 'add'
            ? '添加平台管理员'
            : dialog?.kind === 'roles'
            ? '分配平台角色'
            : dialog?.kind === 'status'
            ? dialog.person.active
              ? '停用账号'
              : '启用账号'
            : '移出管理员名单'
        }
        confirmLoading={busy}
        focusLock
        cancelButtonProps={{ autoFocus: dialog?.kind === 'status' }}
        okText={
          dialog?.kind === 'status'
            ? dialog.person.active
              ? '确认停用'
              : '确认启用'
            : undefined
        }
        okButtonProps={{
          disabled:
            !data ||
            (dialog?.kind === 'add' && !user) ||
            (dialog?.kind === 'status' && !ops['identity.users.write']) ||
            (dialog?.kind === 'roles' && bindingVersion === undefined),
        }}
        onCancel={() => {
          if (!busy) setDialog(undefined);
        }}
        onOk={save}
      >
        {failure && <Alert type="error" content={failure} />}
        {dialog?.kind === 'add' && (
          <Space direction="vertical" style={{ width: '100%' }}>
            <PersonPicker
              path="/administrators/candidates"
              value={user || undefined}
              onChange={setUser}
            />
            <Input
              placeholder="运营显示姓名（选填）"
              aria-label="运营显示姓名"
              value={name}
              onChange={setName}
            />
          </Space>
        )}
        {dialog?.kind === 'status' && (
          <p>
            {dialog.person.displayName}（{dialog.person.username}）
            {dialog.person.active
              ? '停用后将无法使用此账号登录。'
              : '启用后可重新使用此账号登录。'}
          </p>
        )}
        {dialog?.kind === 'remove' && (
          <p>
            移出 {dialog.person.displayName}（{dialog.person.username}
            ）将解除其全部平台角色、用户组归属、部门归属及负责人职务。个人账号和租户成员关系保留。
          </p>
        )}
        {dialog?.kind === 'roles' && (
          <>
            <p>
              {dialog.person.displayName}（{dialog.person.username}）
            </p>
            <Checkbox.Group
              aria-label="平台角色"
              value={selected}
              onChange={(values) => setSelected(values.map(String))}
              options={roles
                .filter((r) => r.status === 'ACTIVE')
                .map((r) => ({ label: r.name, value: r.roleId }))}
            />
          </>
        )}
      </Modal>
    </Card>
  );
}
