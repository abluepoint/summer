import React from 'react';
import {
  Alert,
  Button,
  Card,
  Input,
  Select,
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

export function DictionaryGroupsTable({
  groups,
  items,
}: {
  groups: DictionarySnapshotGroup[];
  items: DictionaryOption[];
}) {
  return (
    <Table
      rowKey="id"
      data={groups}
      scroll={{ x: 640 }}
      pagination={{ pageSize: 10 }}
      columns={[
        { title: '组编码', dataIndex: 'code' },
        { title: '组名称', dataIndex: 'name' },
        {
          title: '状态',
          render: (_, group) => (
            <Tag color={group.enabled ? 'green' : 'gray'}>
              {group.enabled ? '启用' : '停用'}
            </Tag>
          ),
        },
        {
          title: '成员（按组内顺序）',
          render: (_, group) =>
            group.memberValues
              .map(
                (value) =>
                  items.find((item) => item.value === value)?.label || value
              )
              .join('、') || '暂无成员',
        },
      ]}
    />
  );
}

export default function DictionaryGroupsEditor({
  draft,
  disabled,
  onChange,
}: {
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
      <Alert content="字典组是当前字典值的子集。同一字典值可加入多个组；已发布组的编码不可修改，也不可删除，可改为停用。新成员保存时按字典顺序追加，保存后可调整组内顺序。" />
      <p id="group-code-help">
        组编码必填：3–80位小写字母、数字或连字符，以字母开头、字母或数字结尾，在当前字典内唯一。
      </p>
      <Button
        disabled={disabled}
        onClick={() =>
          onChange([
            ...draft.groups,
            {
              id: `new:${Array.from(
                window.crypto.getRandomValues(new Uint8Array(16)),
                (byte) => byte.toString(16).padStart(2, '0')
              ).join('')}`,
              code: '',
              name: '',
              enabled: true,
              everPublished: false,
              memberIds: [],
            },
          ])
        }
      >
        新增字典组
      </Button>
      {!draft.groups.length && (
        <Alert content="暂无字典组，点击“新增字典组”开始编辑。" />
      )}
      {draft.groups.map((group, index) => (
        <Card
          key={group.id}
          title={`字典组 ${index + 1}`}
          extra={
            <Button
              status="danger"
              disabled={disabled || group.everPublished}
              onClick={() =>
                onChange(draft.groups.filter((entry) => entry.id !== group.id))
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
            <span id={`group-members-${group.id}`}>组成员</span>
            <Select
              aria-labelledby={`group-members-${group.id}`}
              mode="multiple"
              allowClear
              showSearch
              disabled={disabled}
              value={group.memberIds}
              options={draft.itemOrder.map((id) => {
                const item = draft.items.find((entry) => entry.id === id);
                return {
                  value: id,
                  label: `${item.label} (${item.value})${
                    item.enabled ? '' : ' · 已停用'
                  }`,
                };
              })}
              onChange={(memberIds) => update(group.id, { memberIds })}
            />
            <Table
              rowKey="id"
              size="small"
              scroll={{ x: 480 }}
              pagination={{ pageSize: 10 }}
              data={group.memberIds.map((id, position) => ({
                ...draft.items.find((item) => item.id === id),
                position,
              }))}
              columns={[
                { title: '组内顺序', render: (_, item) => item.position + 1 },
                { title: '字典值', dataIndex: 'value' },
                { title: '显示名称', dataIndex: 'label' },
                {
                  title: '排序',
                  render: (_, item) => (
                    <Space>
                      {[-1, 1].map((delta) => (
                        <Button
                          key={delta}
                          size="small"
                          disabled={
                            disabled ||
                            group.id.startsWith('new:') ||
                            item.position + delta < 0 ||
                            item.position + delta >= group.memberIds.length
                          }
                          onClick={() => {
                            const memberIds = [...group.memberIds];
                            memberIds.splice(item.position, 1);
                            memberIds.splice(item.position + delta, 0, item.id);
                            update(group.id, { memberIds });
                          }}
                        >
                          {delta < 0 ? '上移' : '下移'}
                        </Button>
                      ))}
                    </Space>
                  ),
                },
              ]}
            />
          </Space>
        </Card>
      ))}
    </Space>
  );
}
