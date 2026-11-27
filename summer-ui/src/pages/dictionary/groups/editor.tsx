import React, { useEffect, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Input,
  Message,
  Tooltip,
  Modal,
  Space,
  Switch,
  Table,
  Tag,
} from '@arco-design/web-react';
import {
  DictionaryDraft,
  DictionaryDraftGroup,
  DictionaryOption,
  DictionarySnapshotGroup,
} from '../api';
import DictionaryItemsTable, {
  matchesDictionaryItem,
} from '../manage/items-table';
import { IconDragDotVertical } from '@arco-design/web-react/icon';
import styles from '../manage/detail.module.less';

export function newDictionaryGroup(): DictionaryDraftGroup {
  return {
    id: `new:${Array.from(
      window.crypto.getRandomValues(new Uint8Array(16)),
      (byte) => byte.toString(16).padStart(2, '0')
    ).join('')}`,
    code: '',
    name: '',
    enabled: true,
    everPublished: false,
    memberIds: [],
  };
}

export function DictionaryGroupsTable({
  groups,
  items,
}: {
  groups: DictionarySnapshotGroup[];
  items: DictionaryOption[];
}) {
  const byValue = new Map(items.map((item) => [item.value, item]));
  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      {!groups.length && <Alert content="当前版本暂无该字典组。" />}
      {groups.map((group) => {
        const missing = group.memberValues.filter(
          (value) => !byValue.has(value)
        );
        const members = group.memberValues.flatMap((value, position) => {
          const item = byValue.get(value);
          return item
            ? [
                {
                  ...item,
                  sortOrder: group.memberSortOrders?.[value] ?? position + 1,
                },
              ]
            : [];
        });
        return (
          <Card key={group.id} title={group.name}>
            <p style={{ overflowWrap: 'anywhere' }}>
              组编码：{group.code} · {group.enabled ? '启用' : '停用'} ·{' '}
              {group.memberValues.length} 个成员
            </p>
            <Alert
              content="下方字典值引用自所属字典，名称、说明和状态沿用当前查看版本的字典值。组内仅维护引用关系及顺序，移除引用不会删除字典值。"
              style={{ marginBottom: 16 }}
            />
            {missing.length > 0 && (
              <Alert
                type="error"
                content={`当前版本存在无法解析的字典值引用：${missing.join(
                  '、'
                )}`}
              />
            )}
            <h3>引用的字典值</h3>
            <DictionaryItemsTable items={members} orderLabel="组内顺序" />
          </Card>
        );
      })}
    </Space>
  );
}

export default function DictionaryGroupsEditor({
  draft,
  disabled,
  onChange,
  focusedId,
}: {
  focusedId?: string;
  draft: DictionaryDraft;
  disabled: boolean;
  onChange: (groups: DictionaryDraftGroup[]) => void;
}) {
  const update = (id: string, patch: Partial<DictionaryDraftGroup>) =>
    onChange(
      draft.groups.map((group) =>
        group.id === id ? { ...group, ...patch } : group
      )
    );
  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Alert content="字典组引用所属字典的字典值，不复制字典值内容；名称、说明和状态请在字典管理中修改。移除引用不会删除字典值，同一字典值可被多个组引用；已发布组的编码不可修改，也不可删除，可改为停用。新成员保存时按字典顺序追加，保存后可调整组内顺序。" />
      <p id="group-code-help">
        组编码必填：3–80位小写字母、数字或连字符，以字母开头、字母或数字结尾，在当前字典内唯一。
      </p>
      {!focusedId && (
        <Button
          disabled={disabled}
          onClick={() => onChange([...draft.groups, newDictionaryGroup()])}
        >
          新增字典组
        </Button>
      )}
      {!draft.groups.length && (
        <Alert content="暂无字典组，点击“新增字典组”开始编辑。" />
      )}
      {draft.groups
        .filter((group) => !focusedId || group.id === focusedId)
        .map((group, index) => (
          <Card
            key={group.id}
            title={`字典组 ${index + 1}`}
            extra={
              <Button
                status="danger"
                disabled={disabled || group.everPublished}
                onClick={() =>
                  onChange(
                    draft.groups.filter((entry) => entry.id !== group.id)
                  )
                }
              >
                删除字典组
              </Button>
            }
          >
            <Space direction="vertical" size={12} style={{ width: '100%' }}>
              <label htmlFor={`group-code-${group.id}`}>组编码（必填）</label>
              <Input
                id={`group-code-${group.id}`}
                aria-describedby="group-code-help"
                value={group.code}
                maxLength={80}
                autoCorrect="off"
                autoCapitalize="none"
                spellCheck={false}
                disabled={disabled || group.everPublished}
                onChange={(code) => update(group.id, { code })}
              />
              <label htmlFor={`group-name-${group.id}`}>组名称（必填）</label>
              <Input
                id={`group-name-${group.id}`}
                value={group.name}
                maxLength={200}
                disabled={disabled}
                onChange={(name) => update(group.id, { name })}
              />
              <Space>
                <span>组状态</span>
                <Switch
                  aria-label={`启用字典组 ${index + 1}`}
                  checked={group.enabled}
                  checkedText="启用"
                  uncheckedText="停用"
                  disabled={disabled}
                  onChange={(enabled) => update(group.id, { enabled })}
                />
              </Space>
              <GroupMembersTable
                group={group}
                draft={draft}
                disabled={disabled}
                onChange={(memberIds, memberSortOrders) =>
                  update(group.id, {
                    memberIds,
                    ...(memberSortOrders ? { memberSortOrders } : {}),
                  })
                }
              />
            </Space>
          </Card>
        ))}
    </Space>
  );
}

function GroupOrderInput({
  id,
  position,
  rank,
  disabled,
  onMove,
}: {
  id: string;
  position: number;
  rank: number;
  disabled: boolean;
  onMove: (position: number) => void;
}) {
  const [text, setText] = useState(String(rank));
  useEffect(() => setText(String(rank)), [rank, disabled]);
  return (
    <Input
      id={id}
      aria-label={`组内顺序 ${position}`}
      inputMode="numeric"
      pattern="[0-9]+"
      aria-describedby="group-order-help"
      value={text}
      disabled={disabled}
      style={{ width: 110 }}
      onChange={setText}
      onPressEnter={(event) => event.currentTarget.blur()}
      onBlur={() => {
        if (disabled) return;
        const next = Number(text);
        if (
          !/^\d+$/.test(text) ||
          !Number.isSafeInteger(next) ||
          next < 1 ||
          next > 2147483647
        ) {
          Message.warning('顺序编号须为 1–2147483647 的整数，可不连续');
          setText(String(rank));
          return;
        }
        onMove(next);
      }}
    />
  );
}

function GroupMembersTable({
  group,
  draft,
  disabled,
  onChange,
}: {
  group: DictionaryDraftGroup;
  draft: DictionaryDraft;
  disabled: boolean;
  onChange: (ids: string[], ranks?: Record<string, number>) => void;
}) {
  const [page, setPage] = useState(1);
  const [adding, setAdding] = useState(false);
  const [checked, setChecked] = useState<string[]>([]);
  const [search, setSearch] = useState('');
  const [pickerPage, setPickerPage] = useState(1);
  const members = new Set(group.memberIds);
  const candidates = draft.items.filter(
    (item) => !members.has(item.id) && matchesDictionaryItem(item, search)
  );
  const [dropTarget, setDropTarget] = useState('');
  const [announcement, setAnnouncement] = useState('');
  const dragged = useRef('');
  const focusAfterMove = useRef<string>();
  const saved = useRef({
    revision: draft.draftRevision,
    ids: new Set(group.memberIds),
  });
  if (saved.current.revision !== draft.draftRevision)
    saved.current = {
      revision: draft.draftRevision,
      ids: new Set(group.memberIds),
    };
  const pendingMembers =
    group.id.startsWith('new:') ||
    group.memberIds.some((id) => !saved.current.ids.has(id));
  const sortingDisabled = disabled || pendingMembers;
  const current = Math.min(
    page,
    Math.max(1, Math.ceil(group.memberIds.length / 10))
  );
  const controlId = (kind: string, id: string) => `${kind}-${group.id}-${id}`;
  useEffect(() => {
    if (focusAfterMove.current) {
      document.getElementById(focusAfterMove.current)?.focus();
      focusAfterMove.current = undefined;
    }
  }, [group.memberIds, current]);
  const ranks = Object.fromEntries(
    group.memberIds.map((id, index) => [
      id,
      group.memberSortOrders?.[id] ?? index + 1,
    ])
  );
  const changeRank = (id: string, rank: number) => {
    if (
      sortingDisabled ||
      !Number.isInteger(rank) ||
      rank < 1 ||
      rank > 2147483647 ||
      ranks[id] === rank
    )
      return;
    const nextRanks = { ...ranks, [id]: rank };
    const memberIds = [...group.memberIds].sort(
      (a, b) => nextRanks[a] - nextRanks[b] || (a < b ? -1 : a > b ? 1 : 0)
    );
    focusAfterMove.current = controlId('group-order', id);
    setPage(Math.floor(memberIds.indexOf(id) / 10) + 1);
    setAnnouncement(`顺序编号已改为 ${rank}`);
    onChange(memberIds, nextRanks);
  };
  const move = (id: string, position: number, kind: string) => {
    if (
      sortingDisabled ||
      !Number.isInteger(position) ||
      position < 1 ||
      position > group.memberIds.length
    )
      return;
    const from = group.memberIds.indexOf(id);
    if (from < 0 || from === position - 1) return;
    const memberIds = [...group.memberIds];
    memberIds.splice(from, 1);
    memberIds.splice(position - 1, 0, id);
    const slots = group.memberIds.map((id) => ranks[id]).sort((a, b) => a - b);
    for (let i = 1; i < slots.length; i++)
      slots[i] = Math.max(slots[i], slots[i - 1] + 1);
    if (slots.some((rank) => rank > 2147483647)) {
      Message.warning('顺序编号已达上限，请先调小编号再拖动');
      return;
    }
    focusAfterMove.current = controlId(kind, id);
    setPage(Math.ceil(position / 10));
    setAnnouncement(
      `字典值 ${
        draft.items.find((item) => item.id === id)?.value || id
      } 已移至第 ${position} 项`
    );
    onChange(
      memberIds,
      Object.fromEntries(memberIds.map((id, index) => [id, slots[index]]))
    );
  };
  return (
    <>
      <Space>
        <h3>引用的字典值</h3>
        <Button
          disabled={disabled}
          onClick={() => {
            setChecked([]);
            setSearch('');
            setPickerPage(1);
            setAdding(true);
          }}
        >
          添加字典值
        </Button>
      </Space>
      <Modal
        title="添加字典值"
        visible={adding}
        okText={`添加（${checked.length}）`}
        cancelText="取消"
        okButtonProps={{ disabled: disabled || !checked.length }}
        style={{ width: 680, maxWidth: 'calc(100vw - 32px)' }}
        onCancel={() => setAdding(false)}
        onOk={() => {
          const selected = new Set(checked);
          onChange([
            ...group.memberIds,
            ...draft.itemOrder.filter(
              (id) => selected.has(id) && !members.has(id)
            ),
          ]);
          setAdding(false);
        }}
      >
        <Input.Search
          aria-label="搜索可添加的字典值"
          placeholder="搜索字典值、显示名称或说明"
          allowClear
          value={search}
          onChange={(value) => {
            setSearch(value);
            setPickerPage(1);
          }}
        />
        <div
          role="region"
          aria-label="可添加的字典值"
          tabIndex={0}
          style={{ overflowX: 'auto' }}
        >
          <Table
            rowKey="id"
            size="small"
            data={candidates}
            style={{ minWidth: 420 }}
            noDataElement={search ? '没有匹配的字典值' : '没有可添加的字典值'}
            rowSelection={{
              type: 'checkbox',
              selectedRowKeys: checked,
              checkCrossPage: true,
              preserveSelectedRowKeys: true,
              checkboxProps: (item) => ({
                'aria-label': `选择 ${item.label}（${item.value}）`,
              }),
              onChange: (keys) => setChecked(keys.map(String)),
            }}
            pagination={{
              current: pickerPage,
              pageSize: 10,
              onChange: setPickerPage,
            }}
            columns={[
              { title: '字典值', dataIndex: 'value' },
              { title: '显示名称', dataIndex: 'label' },
              { title: '说明', dataIndex: 'description' },
              {
                title: '状态',
                render: (_, item) => (item.enabled ? '启用' : '停用'),
              },
            ]}
          />
        </div>
      </Modal>
      <p id="group-order-help" className={styles.editorHelp}>
        {pendingMembers
          ? '新组或新增引用请先保存草稿，再调整组内顺序。'
          : '拖动手柄调整顺序，也可聚焦手柄后按 ↑ / ↓。顺序编号可不连续（如10、20、50），按 Enter 或移出输入框后按编号排序并显示目标页；相同编号采用固定顺序。'}
      </p>
      <span
        role="status"
        style={{
          position: 'absolute',
          width: 1,
          height: 1,
          overflow: 'hidden',
          clipPath: 'inset(50%)',
          whiteSpace: 'nowrap',
        }}
      >
        {announcement}
      </span>
      <Table
        rowKey="id"
        size="small"
        scroll={{ x: 900 }}
        pagination={{
          current,
          pageSize: 10,
          total: group.memberIds.length,
          onChange: (value) => {
            setPage(value);
            dragged.current = '';
            setDropTarget('');
          },
        }}
        data={group.memberIds.map((id, position) => ({
          ...draft.items.find((item) => item.id === id),
          position: position + 1,
        }))}
        rowClassName={(item) =>
          item.id === dropTarget ? styles.dropTarget : ''
        }
        onRow={(item) => ({
          'aria-label': `组内项 ${item.position}`,
          onDragOver: (event) => {
            if (sortingDisabled || !dragged.current) return;
            event.preventDefault();
            event.dataTransfer.dropEffect = 'move';
            setDropTarget(item.id);
          },
          onDrop: (event) => {
            event.preventDefault();
            move(dragged.current, item.position, 'group-handle');
            dragged.current = '';
            setDropTarget('');
          },
        })}
        columns={[
          {
            title: '组内顺序',
            width: 174,
            render: (_, item) => (
              <div className={styles.orderCell}>
                <Tooltip content="拖拽排序，也可聚焦后按 ↑ / ↓ 键调整">
                  <Button
                    id={controlId('group-handle', item.id)}
                    className={styles.dragHandle}
                    type="text"
                    size="mini"
                    icon={<IconDragDotVertical />}
                    aria-label={`排序组内第 ${item.position} 项`}
                    disabled={sortingDisabled}
                    draggable={!sortingDisabled}
                    onDragStart={(event) => {
                      dragged.current = item.id;
                      event.dataTransfer.effectAllowed = 'move';
                      event.dataTransfer.setData('text/plain', item.id);
                    }}
                    onDragEnd={() => {
                      dragged.current = '';
                      setDropTarget('');
                    }}
                    onKeyDown={(event) => {
                      if (event.key !== 'ArrowUp' && event.key !== 'ArrowDown')
                        return;
                      event.preventDefault();
                      move(
                        item.id,
                        item.position + (event.key === 'ArrowUp' ? -1 : 1),
                        'group-handle'
                      );
                    }}
                  />
                </Tooltip>
                <GroupOrderInput
                  id={controlId('group-order', item.id)}
                  position={item.position}
                  rank={ranks[item.id]}
                  disabled={sortingDisabled}
                  onMove={(rank) => changeRank(item.id, rank)}
                />
              </div>
            ),
          },
          { title: '字典值', dataIndex: 'value' },
          { title: '显示名称', dataIndex: 'label' },
          { title: '说明', render: (_, item) => item.description || '—' },
          {
            title: '状态',
            render: (_, item) => (
              <Tag color={item.enabled ? 'green' : 'gray'}>
                {item.enabled ? '启用' : '停用'}
              </Tag>
            ),
          },
          {
            title: '操作',
            render: (_, item) => (
              <Button
                size="small"
                disabled={disabled}
                onClick={() =>
                  onChange(group.memberIds.filter((id) => id !== item.id))
                }
              >
                移除引用
              </Button>
            ),
          },
        ]}
      />
    </>
  );
}
