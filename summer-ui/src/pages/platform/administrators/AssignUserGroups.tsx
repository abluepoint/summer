import React, { useState } from 'react';
import {
  Alert,
  Checkbox,
  Empty,
  Input,
  Modal,
  Space,
} from '@arco-design/web-react';
import { call } from '../../permissions/api';
import { base, Person, RosterGroup, saveGroupMembers } from '../directory';

export default function AssignUserGroups({
  person,
  canAssign,
  groups,
  reload,
  onClose,
}: {
  person: Person;
  canAssign: boolean;
  groups: RosterGroup[];
  reload: () => void;
  onClose: () => void;
}) {
  const [search, setSearch] = useState('');
  const [changes, setChanges] = useState<Record<string, boolean>>({});
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState('');
  const { username } = person;
  const pending = groups.filter(
    (g) =>
      changes[g.id] !== undefined &&
      changes[g.id] !== g.administrators.includes(username)
  );
  const filtered = groups.filter((g) =>
    g.name.toLowerCase().includes(search.trim().toLowerCase())
  );

  async function save() {
    if (busy || !canAssign || !pending.length) return;
    setBusy(true);
    setFailure('');
    let saved = 0;
    try {
      for (const { id } of pending) {
        // Each write replaces one group's membership and consumes its directory version.
        const roster = await call<{ groups: RosterGroup[]; version: number }>(
          base,
          '/administrators'
        );
        const group = roster.groups.find((g) => g.id === id);
        if (!group && changes[id]) throw new Error('用户组已删除');
        if (group && changes[id] !== group.administrators.includes(username)) {
          const members = changes[id]
            ? [...group.administrators, username]
            : group.administrators.filter((member) => member !== username);
          await saveGroupMembers(id, members, roster.version);
        }
        saved++;
      }
      onClose();
    } catch (e) {
      const remaining = Object.fromEntries(
        pending.slice(saved).map((g) => [g.id, changes[g.id]])
      );
      setChanges(remaining);
      const prefix = saved ? `已完成 ${saved} 个用户组的更改。` : '';
      setFailure(prefix + (e.message || '保存失败，请重试'));
    } finally {
      reload();
      setBusy(false);
    }
  }

  return (
    <Modal
      visible
      focusLock
      title="分配用户组"
      okText="保存"
      confirmLoading={busy}
      okButtonProps={{ disabled: !canAssign || !pending.length }}
      onOk={save}
      onCancel={() => !busy && onClose()}
    >
      <Space direction="vertical" style={{ width: '100%' }}>
        <span>
          {person.displayName}（{username}）
        </span>
        <Input.Search
          aria-label="搜索可分配用户组"
          placeholder="搜索用户组名称"
          allowClear
          value={search}
          onChange={setSearch}
        />
        <div
          role="group"
          aria-label="选择所属用户组"
          style={{ maxHeight: 320, overflowY: 'auto' }}
        >
          <Space direction="vertical">
            {filtered.map((g) => {
              const member = g.administrators.includes(username);
              return (
                <Checkbox
                  key={g.id}
                  checked={changes[g.id] ?? member}
                  disabled={!canAssign || busy || (!person.active && !member)}
                  onChange={(checked) =>
                    setChanges({ ...changes, [g.id]: checked })
                  }
                >
                  {g.name}
                </Checkbox>
              );
            })}
          </Space>
          {!filtered.length && (
            <Empty
              description={groups.length ? '没有匹配的用户组' : '暂无用户组'}
            />
          )}
        </div>
        {failure && <Alert type="error" content={failure} />}
      </Space>
    </Modal>
  );
}
