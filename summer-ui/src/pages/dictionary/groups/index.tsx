import React, { useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
} from '@arco-design/web-react';
import {
  DictionaryType,
  getDictionaryDraftList,
  getDictionaryDraft,
  getEffectiveDictionary,
} from '../api';
import DictionaryDetail from '../manage/detail';

type GroupRow = {
  id: string;
  code: string;
  name: string;
  enabled: boolean;
  count: number;
  dictionaryCode: string;
  dictionaryName: string;
  version?: number;
  publication: '已发布' | '已停用' | '当前未生效' | '未发布';
};

export default function DictionaryGroupsPage() {
  const [rows, setRows] = useState<GroupRow[]>([]);
  const [dictionaries, setDictionaries] = useState<DictionaryType[]>([]);
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(1);
  const [reload, setReload] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [creating, setCreating] = useState(false);
  const [parent, setParent] = useState<string>();
  const [selected, setSelected] = useState<{
    code: string;
    groupId?: string;
    tab: string;
    create?: boolean;
  }>();
  useEffect(() => {
    if (selected) return;
    let active = true;
    setLoading(true);
    setError('');
    setRows([]);
    setDictionaries([]);
    async function load() {
      const list = await getDictionaryDraftList();
      const result: GroupRow[][] = new Array(list.length);
      let cursor = 0;
      // The existing API exposes groups per dictionary; bound concurrent reads.
      await Promise.all(
        Array.from({ length: Math.min(4, list.length) }, async () => {
          while (active && cursor < list.length) {
            const index = cursor++;
            const dictionary = list[index];
            const draft = await getDictionaryDraft(dictionary.code);
            const effective = dictionary.published
              ? await getEffectiveDictionary(dictionary.code)
              : undefined;
            const published =
              effective?.status === 'PUBLISHED' ? effective.groups : [];
            const publishedIds = new Set(published.map((group) => group.id));
            result[index] = [
              ...published.map((group) => ({
                ...group,
                count: group.memberValues.length,
                dictionaryCode: dictionary.code,
                dictionaryName: effective.name || dictionary.name,
                version: effective.publishedVersion,
                publication: '已发布' as const,
              })),
              ...draft.groups
                .filter((group) => !publishedIds.has(group.id))
                .map(
                  (group): GroupRow => ({
                    ...group,
                    count: group.memberIds.length,
                    dictionaryCode: dictionary.code,
                    dictionaryName: draft.name,
                    publication: !group.everPublished
                      ? '未发布'
                      : dictionary.published
                      ? '当前未生效'
                      : '已停用',
                  })
                ),
            ];
          }
        })
      );
      if (active) {
        setRows([].concat(...result));
        setDictionaries(list);
      }
    }
    load()
      .catch((e) => {
        if (active)
          setError(`${e.message}${e.requestId ? ` (${e.requestId})` : ''}`);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [reload, selected]);
  if (selected)
    return (
      <DictionaryDetail
        groupsOnly
        code={selected.code}
        initialTab={selected.tab}
        initialGroupId={selected.groupId}
        createGroup={selected.create}
        onBack={() => setSelected(undefined)}
      />
    );
  const filtered = rows.filter((row) =>
    `${row.code} ${row.name} ${row.dictionaryCode} ${row.dictionaryName}`
      .toLowerCase()
      .includes(query.trim().toLowerCase())
  );
  return (
    <Card
      title="字典组管理"
      extra={
        <Button
          type="primary"
          disabled={loading || !!error || !dictionaries.length}
          onClick={() => {
            setParent(undefined);
            setCreating(true);
          }}
        >
          新建字典组
        </Button>
      }
    >
      <Space wrap style={{ marginBottom: 16 }}>
        <Input.Search
          aria-label="搜索字典组"
          placeholder="搜索字典组名称、编码或所属字典"
          allowClear
          value={query}
          onChange={(value) => {
            setQuery(value);
            setPage(1);
          }}
          style={{ width: 280, maxWidth: '100%' }}
        />
        <Button
          loading={loading}
          onClick={() => setReload((value) => value + 1)}
        >
          刷新列表
        </Button>
      </Space>
      {error && (
        <Alert type="error" content={error} style={{ marginBottom: 16 }} />
      )}
      <div
        role="region"
        aria-label="字典组列表"
        tabIndex={0}
        style={{ overflowX: 'auto' }}
      >
        <Table<GroupRow>
          rowKey={(row) => `${row.dictionaryCode}:${row.id}`}
          loading={loading}
          data={filtered}
          style={{ minWidth: 900 }}
          noDataElement={
            error
              ? '字典组加载失败，请重试'
              : query
              ? '没有匹配的字典组'
              : '暂无字典组，点击“新建字典组”开始创建'
          }
          pagination={{
            current: Math.min(
              page,
              Math.max(1, Math.ceil(filtered.length / 10))
            ),
            pageSize: 10,
            total: filtered.length,
            onChange: setPage,
          }}
          columns={[
            { title: '字典组名称', dataIndex: 'name' },
            { title: '字典组编码', dataIndex: 'code' },
            {
              title: '所属字典',
              render: (_, row) =>
                `${row.dictionaryName} (${row.dictionaryCode})`,
            },
            {
              title: '组状态',
              render: (_, row) => (
                <Tag color={row.enabled ? 'green' : 'gray'}>
                  {row.enabled ? '启用' : '停用'}
                </Tag>
              ),
            },
            { title: '成员数', dataIndex: 'count' },
            {
              title: '发布状态',
              render: (_, row) => (
                <Tag color={row.version ? 'green' : 'gray'}>
                  {row.publication}
                </Tag>
              ),
            },
            {
              title: '当前生效版本',
              render: (_, row) => row.version || '—',
            },
            {
              title: '操作',
              render: (_, row) => (
                <Button
                  type="text"
                  onClick={() =>
                    setSelected({
                      code: row.dictionaryCode,
                      groupId: row.id,
                      tab: row.version ? 'effective' : 'draft',
                    })
                  }
                >
                  {row.version ? '详情' : '编辑'}
                </Button>
              ),
            },
          ]}
        />
      </div>
      <Modal
        title="新建字典组"
        visible={creating}
        okText="开始编辑"
        cancelText="取消"
        okButtonProps={{ disabled: !parent }}
        onCancel={() => setCreating(false)}
        onOk={() => {
          if (parent) {
            setCreating(false);
            setSelected({ code: parent, tab: 'draft', create: true });
          }
        }}
      >
        <p>选择新组所属的字典，随后填写组名称、编码及成员。</p>
        <Select
          aria-label="新组所属字典"
          placeholder="选择所属字典"
          showSearch
          value={parent}
          onChange={setParent}
          options={dictionaries.map((dictionary) => ({
            value: dictionary.code,
            label: `${dictionary.name} (${dictionary.code})`,
          }))}
        />
      </Modal>
    </Card>
  );
}
