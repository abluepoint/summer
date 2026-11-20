import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Dropdown,
  Input,
  Menu,
  Message,
  Modal,
  Pagination,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Tooltip,
} from '@arco-design/web-react';
import {
  IconDelete,
  IconDown,
  IconDragDotVertical,
  IconEdit,
  IconLock,
  IconPlus,
  IconStop,
} from '@arco-design/web-react/icon';
import { Prompt } from 'react-router-dom';
import styles from './detail.module.less';
import DictionaryGroupsEditor, {
  DictionaryGroupsTable,
} from '../groups/editor';
import DictionaryItemsTable, { matchesDictionaryItem } from './items-table';
import {
  DictionaryDraft,
  DictionaryHistory,
  getDictionaryHistory,
  getDictionaryHistoryDetail,
  changeDictionaryVersion,
  DictionaryDraftItem,
  EffectiveDictionary,
  getDictionaryDraft,
  getEffectiveDictionary,
  saveDictionaryDraft,
} from '../api';

type OrderedDraftItem = DictionaryDraftItem & { position: number };

export default function DictionaryDetail({
  enterpriseId,
  code,
  initialTab = 'effective',
  groupsOnly = false,
  onBack,
  onDirty,
  onBusy,
}: {
  enterpriseId: string;
  code: string;
  initialTab?: string;
  groupsOnly?: boolean;
  onBack: () => void;
  onDirty: (value: boolean) => void;
  onBusy: (value: boolean) => void;
}) {
  const [tab, setTab] = useState(initialTab);
  const [reload, setReload] = useState(0);
  const [value, setValue] = useState<EffectiveDictionary>();
  const [draft, setDraft] = useState<DictionaryDraft>();
  const [history, setHistory] = useState<DictionaryHistory[]>([]);
  const [historical, setHistorical] = useState<DictionaryHistory>();
  const [preview, setPreview] = useState(false);
  const [pendingAction, setPendingAction] = useState<'disable' | 'rollback'>();
  const [selectedVersion, setSelectedVersion] = useState<number>();
  const [dirty, setDirty] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const alive = useRef(true);
  const writing = useRef(false);
  const dragged = useRef('');
  const [dropTarget, setDropTarget] = useState('');
  const [itemQuery, setItemQuery] = useState('');
  const [itemPage, setItemPage] = useState(1);
  const [itemPageSize, setItemPageSize] = useState(10);
  const [editingMatches, setEditingMatches] = useState<Set<string>>(new Set());
  const orderedItems = useMemo<OrderedDraftItem[]>(() => {
    if (!draft) return [];
    const byId = new Map(draft.items.map((item) => [item.id, item]));
    return draft.itemOrder.map((id, index) => ({
      ...byId.get(id),
      position: index + 1,
    }));
  }, [draft]);
  const isFiltering = !!itemQuery.trim();
  const filteredItems = useMemo(
    () =>
      orderedItems.filter(
        (item) =>
          editingMatches.has(item.id) || matchesDictionaryItem(item, itemQuery)
      ),
    [orderedItems, itemQuery, editingMatches]
  );
  const currentItemPage = Math.min(
    itemPage,
    Math.max(1, Math.ceil(filteredItems.length / itemPageSize))
  );
  const searchItems = (query: string) => {
    setItemQuery(query);
    setEditingMatches(new Set());
    setItemPage(1);
    dragged.current = '';
    setDropTarget('');
  };
  useEffect(
    () => () => {
      alive.current = false;
    },
    []
  );
  useEffect(() => {
    onDirty(dirty || preview || !!pendingAction);
    return () => onDirty(false);
  }, [dirty, preview, pendingAction, onDirty]);
  useEffect(() => {
    onBusy(saving);
    return () => onBusy(false);
  }, [saving, onBusy]);
  useEffect(() => {
    const leave = (event: BeforeUnloadEvent) => {
      if (dirty || saving) {
        event.preventDefault();
        event.returnValue = '';
      }
    };
    window.addEventListener('beforeunload', leave);
    return () => window.removeEventListener('beforeunload', leave);
  }, [dirty, saving]);
  useEffect(() => {
    let active = true;
    setValue(undefined);
    setHistory([]);
    setHistorical(undefined);
    setSelectedVersion(undefined);
    setPreview(false);
    setPendingAction(undefined);
    setDraft(undefined);
    setItemQuery('');
    setEditingMatches(new Set());
    setItemPage(1);
    setError('');
    if (code) {
      setLoading(true);
      Promise.all([
        getEffectiveDictionary(enterpriseId, code),
        getDictionaryDraft(enterpriseId, code),
        getDictionaryHistory(enterpriseId, code),
      ])
        .then(([effective, edit, versions]) => {
          if (active) {
            setValue(effective);
            setDraft(edit);
            setHistory(versions);
          }
        })
        .catch((e) => {
          if (active)
            setError(`${e.message}${e.requestId ? ` (${e.requestId})` : ''}`);
        })
        .finally(() => {
          if (active) setLoading(false);
        });
    }
    return () => {
      active = false;
    };
  }, [enterpriseId, code, tab, reload]);
  useEffect(() => {
    let active = true;
    setHistorical(undefined);
    if (selectedVersion !== undefined && code) {
      getDictionaryHistoryDetail(enterpriseId, code, selectedVersion)
        .then((result) => {
          if (active) setHistorical(result);
        })
        .catch((e) => {
          if (active)
            setError(`${e.message}${e.requestId ? ` (${e.requestId})` : ''}`);
        });
    }
    return () => {
      active = false;
    };
  }, [enterpriseId, code, selectedVersion]);
  const transition = async (action: 'publish' | 'disable' | 'rollback') => {
    if (!draft || !value || loading || writing.current || dirty) return;
    if (action === 'rollback' && !historical) return;
    writing.current = true;
    setSaving(true);
    setError('');
    try {
      await changeDictionaryVersion(enterpriseId, code, action, {
        expectedVersion: value.publishedVersion,
        expectedEffectiveRevision: value.effectiveRevision,
        ...(action === 'publish'
          ? { expectedDraftRevision: draft.draftRevision }
          : {}),
        ...(action === 'rollback' ? { targetVersion: historical.version } : {}),
        idempotencyKey: Array.from(
          window.crypto.getRandomValues(new Uint8Array(16)),
          (byte) => byte.toString(16).padStart(2, '0')
        ).join(''),
      });
      if (alive.current) {
        setPreview(false);
        setPendingAction(undefined);
        setLoading(true);
        setReload((n) => n + 1);
      }
    } catch (e) {
      if (alive.current)
        setError(
          `${
            e.status === 409
              ? '版本或草稿已变化，请刷新后重新核对；当前输入已保留。'
              : e.message
          }${e.requestId ? ` (${e.requestId})` : ''}`
        );
    } finally {
      writing.current = false;
      if (alive.current) setSaving(false);
    }
  };
  const proceed = () => !dirty || window.confirm('有未保存修改，是否放弃？');
  const edit = (patch: Partial<DictionaryDraft>) => {
    setDraft((old) => ({ ...old, ...patch }));
    setDirty(true);
  };
  const editItem = (id: string, patch: Partial<DictionaryDraftItem>) => {
    // Keep a matching row visible while its searchable fields are being edited.
    if (isFiltering)
      setEditingMatches((ids) => (ids.has(id) ? ids : new Set(ids).add(id)));
    edit({
      items: draft.items.map((i) => (i.id === id ? { ...i, ...patch } : i)),
    });
  };
  const reorder = (from: string, to: string) => {
    if (!from || from === to || isFiltering || saving || loading) return;
    const order = [...draft.itemOrder];
    const fromIndex = order.indexOf(from);
    const toIndex = order.indexOf(to);
    if (fromIndex < 0 || toIndex < 0) return;
    order.splice(fromIndex, 1);
    order.splice(toIndex, 0, from);
    edit({ itemOrder: order });
    setItemPage(Math.floor(toIndex / itemPageSize) + 1);
  };
  const save = async () => {
    if (!draft || loading || writing.current) return;
    if (groupsOnly) {
      const codes = new Set<string>();
      const invalid = draft.groups.find((group) => {
        const bad =
          !/^[a-z][a-z0-9-]{1,78}[a-z0-9]$/.test(group.code) ||
          codes.has(group.code) ||
          !group.name.trim() ||
          group.name.length > 200;
        codes.add(group.code);
        return bad;
      });
      if (invalid) {
        setError(
          '请检查字典组：编码须为3–80位小写字母、数字或连字符，以字母开头、字母或数字结尾且不重复；名称必填且不超过200字。'
        );
        document
          .getElementById(
            `group-${
              !invalid.name.trim() || invalid.name.length > 200
                ? 'name'
                : 'code'
            }-${invalid.id}`
          )
          ?.focus();
        return;
      }
    }
    const seen = new Set<string>();
    const invalidItem = orderedItems.find((item) => {
      const invalid =
        seen.has(item.value) ||
        !/^[a-z0-9](?:[a-z0-9._-]{0,78}[a-z0-9])?$/.test(item.value) ||
        !item.label.trim() ||
        item.label.length > 200 ||
        (item.description || '').length > 2000;
      seen.add(item.value);
      return invalid;
    });
    if (!draft.name.trim() || draft.name.length > 200 || invalidItem) {
      if (invalidItem) {
        searchItems('');
        setItemPage(Math.floor((invalidItem.position - 1) / itemPageSize) + 1);
      }
      setError(
        '请检查字典名称、字典值、显示名称和说明；字典值仅支持1–80位小写字母、数字、点、下划线和连字符，首尾须为字母或数字，且不可重复。'
      );
      return;
    }
    writing.current = true;
    setSaving(true);
    setError('');
    try {
      const result = await saveDictionaryDraft(enterpriseId, draft);
      if (alive.current) {
        setDraft(result);
        setEditingMatches(new Set());
        setDirty(false);
        Message.success(
          `${groupsOnly ? '字典组' : '字典值'}已保存为草稿，发布后生效`
        );
      }
    } catch (e) {
      if (alive.current)
        setError(
          `${
            e.status === 409
              ? '草稿已被其他客户端修改，请刷新后处理；当前编辑内容已保留。'
              : e.status === 403
              ? '无权保存这些修改'
              : e.message
          }${e.requestId ? ` (${e.requestId})` : ''}`
        );
    } finally {
      writing.current = false;
      if (alive.current) setSaving(false);
    }
  };
  return (
    <Card
      title={`${groupsOnly ? '字典组管理' : '字典详情'} · ${code}`}
      extra={
        <Button
          disabled={saving || preview || !!pendingAction}
          onClick={() => {
            if (proceed()) onBack();
          }}
        >
          返回字典列表
        </Button>
      }
    >
      <Prompt
        when={dirty || saving}
        message={() => (saving ? false : '有未保存修改，是否放弃？')}
      />
      {error && <Alert type="error" content={error} />}
      <Tabs
        activeTab={tab}
        onChange={(next) => {
          if (next === tab) return;
          if (!saving && !preview && !pendingAction && proceed()) {
            setDirty(false);
            setLoading(true);
            setTab(next);
          }
        }}
      >
        <Tabs.TabPane key="effective" title="生效版本">
          <div className={styles.detailHeader}>
            <div className={styles.summary}>
              <h2 className={styles.title}>
                {value?.name || draft?.name || code}
              </h2>
              {value && (
                <Space size={8} wrap>
                  <Tag color={value.status === 'PUBLISHED' ? 'green' : 'gray'}>
                    {value.status === 'PUBLISHED' ? '已发布' : '未发布'}
                  </Tag>
                  {value.status === 'PUBLISHED' && (
                    <Tag>发布版本 v{value.publishedVersion}</Tag>
                  )}
                </Space>
              )}
            </div>
            <Space size={12} wrap>
              <Button
                type="primary"
                icon={<IconEdit />}
                disabled={loading || saving || !draft}
                onClick={() => setTab('draft')}
              >
                {groupsOnly ? '编辑字典组' : '编辑字典值'}
              </Button>
              {!groupsOnly && value?.status === 'PUBLISHED' && (
                <Dropdown
                  trigger="click"
                  position="br"
                  disabled={loading || saving || !!pendingAction}
                  droplist={
                    <Menu
                      onClickMenuItem={(key) => {
                        if (key === 'disable' && !loading && !saving) {
                          setError('');
                          setPendingAction('disable');
                        }
                      }}
                    >
                      <Menu.Item key="disable" className={styles.dangerAction}>
                        <IconStop /> 停用字典
                      </Menu.Item>
                    </Menu>
                  }
                >
                  <Button disabled={loading || saving || !!pendingAction}>
                    更多操作 <IconDown />
                  </Button>
                </Dropdown>
              )}
            </Space>
          </div>
          <Descriptions
            className={styles.metadata}
            column={{ xs: 1, sm: 2, lg: 3 }}
            size="small"
            data={[
              { label: '字典编码', value: code },
              {
                label: '读取方式',
                value:
                  value?.status === 'PUBLISHED'
                    ? value.publicReadable
                      ? '公开读取'
                      : '授权读取'
                    : '—',
              },
              {
                label: '字典值数量',
                value:
                  value?.status === 'PUBLISHED'
                    ? `${value.items.length} 项`
                    : '—',
              },
            ]}
          />
          {value?.status === 'PUBLISHED' && (
            <Alert
              className={styles.hint}
              content={
                groupsOnly
                  ? '当前展示生效字典组。修改需先保存草稿，再随所属字典整体发布生效。'
                  : '当前展示生效版本。修改字典值需先保存草稿，再发布生效。'
              }
            />
          )}
          {value && value.status !== 'PUBLISHED' && (
            <Alert
              content={
                value.status === 'UNPUBLISHED'
                  ? groupsOnly
                    ? '尚未发布，请编辑字典组并随字典发布。'
                    : '尚未发布，请编辑字典值并发布。'
                  : '字典已停用，可在草稿中编辑后重新发布。'
              }
            />
          )}
          {value?.status === 'PUBLISHED' && (
            <>
              <h3 className={styles.sectionTitle}>
                {groupsOnly ? '字典组' : '字典值'}
              </h3>
              {groupsOnly ? (
                <DictionaryGroupsTable
                  groups={value.groups}
                  items={value.items}
                />
              ) : (
                <DictionaryItemsTable items={value.items} />
              )}
            </>
          )}
        </Tabs.TabPane>
        <Tabs.TabPane key="draft" title="草稿">
          <Alert
            className={styles.hint}
            content="修改先保存为草稿，发布后才会生效。"
          />
          {draft && (
            <>
              {groupsOnly ? (
                <DictionaryGroupsEditor
                  draft={draft}
                  disabled={saving || loading}
                  onChange={(groups) => edit({ groups })}
                />
              ) : (
                <>
                  <div className={styles.editorSectionHeader}>
                    <h3 className={styles.editorTitle}>基本信息</h3>
                    <Tag>草稿修订 {draft.draftRevision}</Tag>
                  </div>
                  <div className={styles.draftMetadata}>
                    <div className={styles.nameField}>
                      <label
                        className={styles.fieldLabel}
                        htmlFor="dictionary-name"
                      >
                        字典名称
                      </label>
                      <Input
                        id="dictionary-name"
                        aria-label="字典名称"
                        value={draft.name}
                        maxLength={200}
                        disabled={saving || loading}
                        onChange={(name) => edit({ name })}
                      />
                    </div>
                    <div>
                      <div className={styles.fieldLabel}>公开读取</div>
                      <Switch
                        aria-label="公开读取"
                        checked={draft.publicReadable}
                        checkedText="是"
                        uncheckedText="否"
                        disabled={saving || loading}
                        onChange={(publicReadable) => edit({ publicReadable })}
                      />
                    </div>
                    <div>
                      <div className={styles.fieldLabel}>字典状态</div>
                      <Switch
                        aria-label="启用字典"
                        checked={draft.enabled}
                        checkedText="启用"
                        uncheckedText="停用"
                        disabled={saving || loading}
                        onChange={(enabled) => edit({ enabled })}
                      />
                    </div>
                  </div>
                  <div className={styles.editorSectionHeader}>
                    <div>
                      <Space size={8}>
                        <h3 className={styles.editorTitle}>字典值</h3>
                        <Tag>{draft.items.length} 项</Tag>
                      </Space>
                      <p className={styles.editorHelp}>
                        {isFiltering
                          ? '搜索结果中暂不支持排序，清空搜索后可调整顺序。'
                          : '拖拽左侧手柄或使用方向键调整顺序；已发布的值不可修改或删除。'}
                      </p>
                    </div>
                    <Button
                      icon={<IconPlus />}
                      disabled={saving || loading}
                      onClick={() => {
                        const id = `new:${Array.from(
                          window.crypto.getRandomValues(new Uint8Array(16)),
                          (byte) => byte.toString(16).padStart(2, '0')
                        ).join('')}`;
                        edit({
                          items: [
                            ...draft.items,
                            {
                              id,
                              value: '',
                              label: '',
                              description: '',
                              enabled: true,
                              everPublished: false,
                              sortOrder: draft.items.length,
                            },
                          ],
                          itemOrder: [...draft.itemOrder, id],
                        });
                        searchItems('');
                        setItemPage(
                          Math.floor(draft.items.length / itemPageSize) + 1
                        );
                      }}
                    >
                      新增字典值
                    </Button>
                  </div>
                  <div className={styles.itemToolbar}>
                    <div className={styles.itemSearch}>
                      <Input.Search
                        aria-label="搜索草稿字典值"
                        placeholder="搜索字典值、显示名称或说明"
                        allowClear
                        value={itemQuery}
                        disabled={saving || loading}
                        onChange={searchItems}
                      />
                    </div>
                    <span className={styles.saveHint}>
                      {isFiltering
                        ? `找到 ${filteredItems.length} 项，共 ${draft.items.length} 项`
                        : '切换分页或搜索会保留未保存的修改'}
                    </span>
                  </div>
                  <Table<OrderedDraftItem>
                    className={styles.valueTable}
                    rowKey="id"
                    size="middle"
                    border={{ wrapper: true }}
                    pagination={false}
                    scroll={{ x: 960 }}
                    data={filteredItems.slice(
                      (currentItemPage - 1) * itemPageSize,
                      currentItemPage * itemPageSize
                    )}
                    noDataElement={
                      isFiltering
                        ? '没有匹配的字典值，请调整搜索条件。'
                        : '暂无字典值，点击“新增字典值”开始编辑。'
                    }
                    rowClassName={(item) =>
                      item.id === dropTarget ? styles.dropTarget : ''
                    }
                    onRow={(item) => ({
                      'aria-label': `默认项 ${item.position}`,
                      onDragOver: (event) => {
                        if (
                          !dragged.current ||
                          saving ||
                          loading ||
                          isFiltering
                        )
                          return;
                        event.preventDefault();
                        event.dataTransfer.dropEffect = 'move';
                        setDropTarget(item.id);
                      },
                      onDrop: (event) => {
                        event.preventDefault();
                        if (!saving && !loading)
                          reorder(dragged.current, item.id);
                        dragged.current = '';
                        setDropTarget('');
                      },
                    })}
                    columns={[
                      {
                        title: '排序',
                        width: 88,
                        render: (_, item) => (
                          <div className={styles.orderCell}>
                            <Tooltip content="拖拽排序，也可聚焦后按 ↑ / ↓ 键调整">
                              <Button
                                className={styles.dragHandle}
                                type="text"
                                size="mini"
                                icon={<IconDragDotVertical />}
                                aria-label={`排序第 ${item.position} 项`}
                                disabled={saving || loading || isFiltering}
                                draggable={!saving && !loading && !isFiltering}
                                onDragStart={(event) => {
                                  dragged.current = item.id;
                                  event.dataTransfer.effectAllowed = 'move';
                                  event.dataTransfer.setData(
                                    'text/plain',
                                    item.id
                                  );
                                }}
                                onDragEnd={() => {
                                  dragged.current = '';
                                  setDropTarget('');
                                }}
                                onKeyDown={(event) => {
                                  if (
                                    event.key !== 'ArrowUp' &&
                                    event.key !== 'ArrowDown'
                                  )
                                    return;
                                  event.preventDefault();
                                  const target =
                                    draft.itemOrder[
                                      item.position -
                                        1 +
                                        (event.key === 'ArrowUp' ? -1 : 1)
                                    ];
                                  if (target) reorder(item.id, target);
                                }}
                              />
                            </Tooltip>
                            <span>{item.position}</span>
                          </div>
                        ),
                      },
                      {
                        title: '字典值',
                        width: 190,
                        render: (_, item) => (
                          <Input
                            aria-label={`值 ${item.position}`}
                            placeholder="如 standard"
                            value={item.value}
                            prefix={
                              item.everPublished ? <IconLock /> : undefined
                            }
                            maxLength={80}
                            disabled={saving || loading || item.everPublished}
                            onChange={(value) => editItem(item.id, { value })}
                          />
                        ),
                      },
                      {
                        title: '显示名称',
                        width: 190,
                        render: (_, item) => (
                          <Input
                            aria-label={`显示名称 ${item.position}`}
                            placeholder="如 普通客户"
                            value={item.label}
                            maxLength={200}
                            disabled={saving || loading}
                            onChange={(label) => editItem(item.id, { label })}
                          />
                        ),
                      },
                      {
                        title: '说明（选填）',
                        render: (_, item) => (
                          <Input.TextArea
                            aria-label={`说明 ${item.position}`}
                            placeholder="补充说明"
                            value={item.description || ''}
                            autoSize={{ minRows: 1, maxRows: 3 }}
                            maxLength={2000}
                            disabled={saving || loading}
                            onChange={(description) =>
                              editItem(item.id, { description })
                            }
                          />
                        ),
                      },
                      {
                        title: '状态',
                        width: 100,
                        render: (_, item) => (
                          <Switch
                            aria-label={`启用项 ${item.position}`}
                            checked={item.enabled}
                            checkedText="启用"
                            uncheckedText="停用"
                            disabled={saving || loading}
                            onChange={(enabled) =>
                              editItem(item.id, { enabled })
                            }
                          />
                        ),
                      },
                      {
                        title: '操作',
                        width: 100,
                        fixed: 'right',
                        render: (_, item) => (
                          <Button
                            type="text"
                            status="danger"
                            size="small"
                            icon={<IconDelete />}
                            disabled={saving || loading || item.everPublished}
                            onClick={() =>
                              edit({
                                items: draft.items.filter(
                                  (i) => i.id !== item.id
                                ),
                                itemOrder: draft.itemOrder.filter(
                                  (id) => id !== item.id
                                ),
                              })
                            }
                          >
                            删除
                          </Button>
                        ),
                      },
                    ]}
                  />
                  <div
                    className={styles.itemPagination}
                    role="navigation"
                    aria-label="草稿字典值分页"
                  >
                    <Pagination
                      current={currentItemPage}
                      pageSize={itemPageSize}
                      total={filteredItems.length}
                      disabled={saving || loading}
                      sizeCanChange
                      sizeOptions={[10, 20, 50, 100]}
                      showTotal={(total) => `共 ${total} 项`}
                      onChange={(page) => {
                        setItemPage(page);
                        dragged.current = '';
                        setDropTarget('');
                      }}
                      onPageSizeChange={(size) => {
                        setItemPageSize(size);
                        setItemPage(1);
                      }}
                    />
                  </div>
                </>
              )}
              <div className={styles.editorFooter}>
                <span className={styles.saveHint} role="status">
                  {dirty ? '有未保存的修改' : '暂无未保存修改'}
                </span>
                <Space wrap>
                  <Button
                    type="primary"
                    disabled={!dirty || loading}
                    loading={saving}
                    onClick={save}
                  >
                    保存草稿
                  </Button>
                  <Button
                    disabled={dirty || saving || loading}
                    onClick={() => setPreview(true)}
                  >
                    核对并发布
                  </Button>
                  <Button
                    type="text"
                    disabled={saving || loading}
                    onClick={() => {
                      if (proceed()) {
                        setDirty(false);
                        setLoading(true);
                        setReload((revision) => revision + 1);
                      }
                    }}
                  >
                    刷新草稿
                  </Button>
                </Space>
              </div>
            </>
          )}
        </Tabs.TabPane>
        <Tabs.TabPane key="history" title="历史版本">
          <Alert content="历史发布内容（只读），回滚保留当前草稿。" />
          <Select
            aria-label="历史版本号"
            value={selectedVersion}
            disabled={saving || loading}
            options={history.map((row) => ({
              value: row.version,
              label: `版本 ${row.version} · ${row.createdAt}`,
            }))}
            onChange={setSelectedVersion}
          />
          {historical?.snapshot && (
            <>
              <h3>{historical.snapshot.name}</h3>
              <p>
                公开读取：{historical.snapshot.publicReadable ? '是' : '否'}
                ；启用字典：{historical.snapshot.enabled ? '是' : '否'}
              </p>
              {groupsOnly ? (
                <DictionaryGroupsTable
                  groups={historical.snapshot.groups}
                  items={historical.snapshot.items}
                />
              ) : (
                <DictionaryItemsTable
                  key={historical.version}
                  items={historical.snapshot.items}
                />
              )}
              <Button
                disabled={
                  saving ||
                  loading ||
                  historical.version === value?.publishedVersion
                }
                onClick={() => setPendingAction('rollback')}
              >
                回滚此版本
              </Button>
            </>
          )}
        </Tabs.TabPane>
      </Tabs>
      <Modal
        title={
          pendingAction === 'disable'
            ? `停用字典“${value?.name || code}”？`
            : `回滚至版本 ${historical?.version}？`
        }
        visible={!!pendingAction}
        confirmLoading={saving}
        okText={pendingAction === 'disable' ? '确认停用' : '确认回滚'}
        cancelText="取消"
        okButtonProps={{
          status: pendingAction === 'disable' ? 'danger' : undefined,
        }}
        cancelButtonProps={{ disabled: saving }}
        maskClosable={false}
        onCancel={() => {
          if (!saving) setPendingAction(undefined);
        }}
        onOk={() => transition(pendingAction)}
      >
        {pendingAction === 'disable'
          ? `停用后，业务将无法读取字典 ${code}，使用该字典的选项与名称展示可能受到影响。草稿和历史版本会保留。`
          : '完整切换生效内容，草稿保持当前内容。'}
        {error && (
          <Alert type="error" content={error} style={{ marginTop: 16 }} />
        )}
      </Modal>
      <Modal
        title="核对完整草稿后发布"
        style={{ width: 800, maxWidth: 'calc(100vw - 48px)' }}
        visible={preview}
        onCancel={() => {
          if (!saving) setPreview(false);
        }}
        confirmLoading={saving}
        onOk={() => transition('publish')}
        okText="确认发布"
      >
        {draft && (
          <>
            <h3>{draft.name}</h3>
            <p>
              草稿修订 {draft.draftRevision}；当前发布版本{' '}
              {value?.publishedVersion}；生效修订 {value?.effectiveRevision}
            </p>
            <p>
              公开读取：{draft.publicReadable ? '是' : '否'}；启用字典：
              {draft.enabled ? '是' : '否'}
            </p>
            {groupsOnly && (
              <Alert content="字典组随所属字典整体发布，以下字典值及所有字典组将一同生效。" />
            )}
            {groupsOnly && (
              <DictionaryGroupsTable
                groups={draft.groups.map((group) => ({
                  ...group,
                  memberValues: group.memberIds.map(
                    (id) =>
                      draft.items.find((item) => item.id === id)?.value || id
                  ),
                }))}
                items={draft.items}
              />
            )}
            <DictionaryItemsTable
              items={orderedItems.map((item) => ({
                ...item,
                sortOrder: item.position - 1,
              }))}
            />
          </>
        )}
      </Modal>
    </Card>
  );
}
