import React, { useState } from 'react';
import listStyles from '@/pages/platform/list-scroll.module.less';
import {
  Alert,
  Button,
  Input,
  Modal,
  Select,
  Space,
  Table,
} from '@arco-design/web-react';
import { IconStar, IconStarFill } from '@arco-design/web-react/icon';
import styles from './style.module.less';
import { Person, RosterGroup } from '../directory';

export type MemberList = Omit<RosterGroup, 'description'> & {
  description?: string;
  disabled?: boolean;
};
export type MembershipChange = { list: MemberList; version: number } & (
  | { kind: 'add'; selected: string[] }
  | { kind: 'remove'; person: Person }
);

function MemberOption({
  pinAction,
  ...option
}: React.ComponentProps<typeof Select.Option> & {
  pinAction?: React.ReactNode;
}) {
  return (
    <div className={styles.groupOption}>
      <Select.Option {...option} />
      {pinAction}
    </div>
  );
}

export default function MemberAssignments({
  lists,
  kind,
  saveMembers,
  subject,
  assignments,
  people,
  version,
  canAssign,
  reload,
}: {
  lists: MemberList[];
  kind: '用户组' | '角色';
  saveMembers: (change: MembershipChange) => Promise<unknown>;
  subject: string;
  assignments?: Map<string, string[]>;
  people: Person[];
  version?: number;
  canAssign: boolean;
  reload: () => void;
}) {
  const storageKey =
    'platform.administrators.' +
    (kind === '角色' ? 'visibleRoles.' : 'visibleGroups.') +
    subject;
  const [visibleIds, setVisibleIds] = useState<string[] | undefined>(() => {
    try {
      const saved = JSON.parse(localStorage.getItem(storageKey));
      if (Array.isArray(saved) && saved.every((id) => typeof id === 'string'))
        return saved;
    } catch {
      /* Unavailable storage or an old preference uses the default buttons. */
    }
    return undefined;
  });
  const [activeId, setActiveId] = useState<string>();
  const [preferenceError, setPreferenceError] = useState('');
  const [draft, setDraft] = useState<MembershipChange>();
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(1);
  const [failure, setFailure] = useState('');
  const [busy, setBusy] = useState(false);
  const shown = visibleIds
    ? lists.filter((g) => visibleIds.includes(g.id))
    : lists.slice(0, 3);
  const activeList = lists.find((g) => g.id === activeId) || shown[0];
  function toggleShown(list: MemberList) {
    const ids = shown.map((g) => g.id);
    const next = ids.includes(list.id)
      ? ids.filter((id) => id !== list.id)
      : [...ids, list.id];
    try {
      localStorage.setItem(storageKey, JSON.stringify(next));
      setVisibleIds(next);
      if (activeList) setActiveId(activeList.id);
      setPreferenceError('');
    } catch {
      setPreferenceError('浏览器未能保存展示配置，请重试');
    }
  }
  const members = new Set(activeList?.administrators);
  const memberColumns = [
    { title: '姓名', dataIndex: 'displayName' },
    { title: '账号', dataIndex: 'username' },
    {
      title: '账号状态',
      render: (_, p: Person) => (p.active ? '启用' : '停用'),
    },
  ];
  function open(list: MemberList, person?: Person) {
    if (version === undefined) return;
    setDraft(
      person
        ? { kind: 'remove', list, version, person }
        : { kind: 'add', list, version, selected: [] }
    );
    setPage(1);
    setSearch('');
    setFailure('');
  }
  const draftMembers = new Set(
    (kind === '角色' &&
      lists.find((list) => list.id === draft?.list.id)?.administrators) ||
      draft?.list.administrators ||
      []
  );
  const selected =
    draft?.kind === 'add'
      ? draft.selected.filter((name) => !draftMembers.has(name))
      : [];
  async function save() {
    if (
      !draft ||
      busy ||
      !canAssign ||
      (draft.kind === 'add' && !selected.length)
    )
      return;
    setBusy(true);
    setFailure('');
    try {
      await saveMembers(draft.kind === 'add' ? { ...draft, selected } : draft);
      setDraft(undefined);
      reload();
    } catch (e) {
      reload();
      if (e.status === 409 || e.status === 412) {
        setFailure('名单已变化，请关闭弹窗后重新操作');
      } else {
        setFailure(e.message || '更新成员失败，请重试');
      }
    } finally {
      setBusy(false);
    }
  }
  const term = search.trim().toLowerCase();
  const candidates =
    draft?.kind === 'add'
      ? people.filter(
          (p) =>
            p.active &&
            !draftMembers.has(p.username) &&
            (p.username + ' ' + p.displayName).toLowerCase().includes(term)
        )
      : [];
  return (
    <>
      <div className={styles.toolbar} role="group" aria-label={`按${kind}筛选`}>
        <Select
          className={styles.groupSearch}
          aria-label={`搜索${kind}`}
          placeholder={`搜索并选择${kind}`}
          showSearch
          value={activeList?.id}
          onChange={setActiveId}
          filterOption={(input, option) =>
            option.props.children
              .toLowerCase()
              .includes(input.trim().toLowerCase())
          }
          notFoundContent={`没有匹配的${kind}`}
        >
          {lists.map((g) => {
            const pinned = shown.includes(g);
            const pinLabel = (pinned ? '取消常显：' : '设为常显：') + g.name;
            return (
              <MemberOption
                key={g.id}
                value={g.id}
                isSelectOption
                pinAction={
                  <Button
                    aria-label={pinLabel}
                    title={pinLabel}
                    aria-pressed={pinned}
                    type="text"
                    size="small"
                    icon={pinned ? <IconStarFill /> : <IconStar />}
                    onMouseDown={(event) => event.preventDefault()}
                    onClick={() => toggleShown(g)}
                  />
                }
              >
                {g.name}
              </MemberOption>
            );
          })}
        </Select>
        <Button.Group
          className={styles.groups}
          {...{ role: 'group', 'aria-label': '常显' + kind }}
        >
          {shown.map((g) => (
            <Button
              key={g.id}
              type={activeList?.id === g.id ? 'primary' : 'secondary'}
              aria-pressed={activeList?.id === g.id}
              onClick={() => setActiveId(g.id)}
            >
              {g.name}
            </Button>
          ))}
        </Button.Group>
        <Button.Group
          className={styles.actions}
          {...{ role: 'group', 'aria-label': '成员名单操作' }}
        >
          {canAssign && (
            <Button
              disabled={!activeList || activeList.disabled}
              onClick={() => open(activeList)}
            >
              分配管理员
            </Button>
          )}
          <Button onClick={reload}>刷新</Button>
        </Button.Group>
      </div>
      {preferenceError && <Alert type="error" content={preferenceError} />}
      {activeList?.description && <p>{activeList.description}</p>}
      <Table
        className={listStyles.listScroll}
        key={activeList?.id}
        rowKey="username"
        data={people.filter((p) => members.has(p.username))}
        noDataElement={
          !lists.length
            ? '暂无' + kind
            : activeList
            ? '暂无管理员'
            : '请选择' + kind
        }
        pagination={{ pageSize: 20, showTotal: true }}
        scroll={{ x: 760 }}
        columns={[
          ...memberColumns,
          ...(assignments
            ? [
                {
                  title: '分配角色',
                  render: (_, p: Person) =>
                    assignments.get(p.username)?.join('、') || '—',
                },
              ]
            : []),
          ...(canAssign && activeList
            ? [
                {
                  title: '操作',
                  render: (_, person: Person) => (
                    <Button
                      status="danger"
                      disabled={kind === '角色' && !person.active}
                      onClick={() => open(activeList, person)}
                    >
                      移出{kind}
                    </Button>
                  ),
                },
              ]
            : []),
        ]}
      />
      <Modal
        visible={!!draft}
        title={
          (draft?.kind === 'remove' ? '移出' + kind + ' · ' : '分配管理员 · ') +
          (draft?.list.name || '')
        }
        okText={draft?.kind === 'remove' ? '确认移出' : '确认分配'}
        cancelButtonProps={{ autoFocus: draft?.kind === 'remove' }}
        okButtonProps={{
          disabled: !canAssign || (draft?.kind === 'add' && !selected.length),
          status: draft?.kind === 'remove' ? 'danger' : 'default',
        }}
        confirmLoading={busy}
        onCancel={() => !busy && setDraft(undefined)}
        onOk={save}
      >
        {draft?.kind === 'remove' && (
          <p>
            确认将 {draft.person.displayName}（{draft.person.username}）移出“
            {draft.list.name}”{kind === '角色' ? '的直接分配名单' : ''}？
          </p>
        )}
        {draft?.kind === 'add' && (
          <Space direction="vertical" style={{ width: '100%' }}>
            <Input.Search
              aria-label="搜索候选管理员"
              placeholder="搜索姓名或账号"
              allowClear
              value={search}
              onChange={(v) => {
                setSearch(v);
                setPage(1);
              }}
            />
            <span role="status">已选 {selected.length} 人</span>
            <Table
              rowKey="username"
              size="small"
              data={candidates}
              noDataElement={term ? '没有匹配的管理员' : '暂无可分配的管理员'}
              rowSelection={{
                type: 'checkbox',
                selectedRowKeys: selected,
                checkCrossPage: true,
                preserveSelectedRowKeys: true,
                checkboxProps: (p) => ({
                  disabled: busy,
                  'aria-label': '选择 ' + p.username,
                }),
                onChange: (keys) =>
                  setDraft({ ...draft, selected: keys.map(String) }),
              }}
              pagination={{ current: page, pageSize: 10, onChange: setPage }}
              columns={memberColumns}
            />
          </Space>
        )}
        {failure && <Alert type="error" content={failure} />}
      </Modal>
    </>
  );
}
