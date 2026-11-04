import React, { useEffect, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Input,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
} from '@arco-design/web-react';
import { Link, Prompt } from 'react-router-dom';
import useActiveEnterprise from '../../data-scopes/useActiveEnterprise';
import {
  DictionaryDraft,
  DictionaryHistory,
  getDictionaryHistory,
  getDictionaryHistoryDetail,
  changeDictionaryVersion,
  DictionaryDraftItem,
  EffectiveDictionary,
  getDictionaryDraft,
  getDictionaryDraftList,
  getEffectiveDictionary,
  saveDictionaryDraft,
} from '../api';

export default function DictionaryManagementPage() {
  const tenant = useActiveEnterprise();
  const dirty = useRef(false);
  return (
    <>
      <Select
        aria-label="活动租户"
        value={tenant.enterpriseId || undefined}
        options={tenant.options}
        onChange={(id) => {
          if (!dirty.current || window.confirm('有未保存修改，是否放弃？')) {
            dirty.current = false;
            tenant.change(id);
          }
        }}
        style={{ width: 240, marginBottom: 16 }}
      />
      {tenant.enterpriseId ? (
        <Content
          key={tenant.enterpriseId}
          enterpriseId={tenant.enterpriseId}
          onDirty={(value) => {
            dirty.current = value;
          }}
        />
      ) : (
        <Alert content="请选择一个活动成员租户" />
      )}
    </>
  );
}

function Content({
  enterpriseId,
  onDirty,
}: {
  enterpriseId: string;
  onDirty: (value: boolean) => void;
}) {
  const [codes, setCodes] = useState<string[]>([]);
  const [code, setCode] = useState('');
  const [tab, setTab] = useState('effective');
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
  useEffect(
    () => () => {
      alive.current = false;
    },
    []
  );
  useEffect(() => {
    onDirty(dirty || preview || !!pendingAction);
  }, [dirty, preview, pendingAction, onDirty]);
  useEffect(() => {
    const leave = (event: BeforeUnloadEvent) => {
      if (dirty) {
        event.preventDefault();
        event.returnValue = '';
      }
    };
    window.addEventListener('beforeunload', leave);
    return () => window.removeEventListener('beforeunload', leave);
  }, [dirty]);
  useEffect(() => {
    let active = true;
    getDictionaryDraftList(enterpriseId)
      .then((rows) => {
        if (active) {
          setCodes(rows.map((row) => row.code));
          setCode(rows[0]?.code || '');
        }
      })
      .catch((e) => {
        if (active) setError(e.message);
      });
    return () => {
      active = false;
    };
  }, [enterpriseId]);
  useEffect(() => {
    let active = true;
    setValue(undefined);
    setHistory([]);
    setHistorical(undefined);
    setSelectedVersion(undefined);
    setPreview(false);
    setPendingAction(undefined);
    setDraft(undefined);
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
  const readOnlyItems = (items: EffectiveDictionary['items']) => (
    <Table
      rowKey="value"
      pagination={false}
      data={items}
      columns={[
        { title: '值', dataIndex: 'value' },
        { title: '显示名称', dataIndex: 'label' },
        { title: '说明', dataIndex: 'description' },
        { title: '默认顺序', dataIndex: 'sortOrder' },
        {
          title: '状态',
          render: (_, item) => (item.enabled ? '启用' : '停用'),
        },
      ]}
    />
  );
  const proceed = () => !dirty || window.confirm('有未保存修改，是否放弃？');
  const edit = (patch: Partial<DictionaryDraft>) => {
    setDraft((old) => ({ ...old, ...patch }));
    setDirty(true);
  };
  const editItem = (id: string, patch: Partial<DictionaryDraftItem>) =>
    edit({
      items: draft.items.map((i) => (i.id === id ? { ...i, ...patch } : i)),
    });
  const reorder = (from: string, to: string) => {
    if (!from || from === to) return;
    const order = draft.itemOrder.filter((id) => id !== from);
    order.splice(order.indexOf(to), 0, from);
    edit({ itemOrder: order });
  };
  const save = async () => {
    if (!draft || loading || writing.current) return;
    const invalid =
      !draft.name.trim() ||
      draft.name.length > 200 ||
      draft.items.some(
        (i) =>
          !/^[a-z0-9](?:[a-z0-9._-]{0,78}[a-z0-9])?$/.test(i.value) ||
          !i.label.trim() ||
          i.label.length > 200 ||
          (i.description || '').length > 2000
      );
    if (invalid) {
      setError(
        '请检查名称、项值和说明长度；值为1–80个安全字符，说明最多2000字。'
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
        setDirty(false);
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
      title="字典管理"
      extra={<Link to="/dictionary/form">原字典表单</Link>}
    >
      <Prompt when={dirty} message="有未保存修改，是否放弃？" />
      <Select
        aria-label="字典编码"
        disabled={saving || preview || !!pendingAction}
        value={code || undefined}
        options={codes}
        onChange={(next) => {
          if (next === code) return;
          if (proceed()) {
            setDirty(false);
            setLoading(true);
            setCode(next);
          }
        }}
        style={{ width: 280 }}
      />
      {error && <Alert type="error" content={error} />}
      <Tabs
        activeTab={tab}
        onChange={(next) => {
          if (next === tab) return;
          if (!saving && proceed()) {
            setDirty(false);
            setLoading(true);
            setTab(next);
          }
        }}
      >
        <Tabs.TabPane key="effective" title="生效版本">
          <Alert content="基础生效版本（只读）；业务表单根据访问组织应用覆盖。" />
          {value?.status === 'UNPUBLISHED' && <Alert content="尚未发布" />}
          {value?.status === 'PUBLISHED' && (
            <>
              <h3>{value.name}</h3>
              <Tag>基础发布版本 {value.publishedVersion}</Tag>
              <Button
                disabled={loading || saving}
                onClick={() => setPendingAction('disable')}
              >
                停用字典
              </Button>
              <Table
                loading={loading}
                rowKey="value"
                pagination={false}
                data={value.items}
                columns={[
                  { title: '值', dataIndex: 'value' },
                  { title: '显示名称', dataIndex: 'label' },
                  { title: '说明', dataIndex: 'description' },
                  { title: '默认顺序', dataIndex: 'sortOrder' },
                  {
                    title: '状态',
                    render: (_, item) => (item.enabled ? '启用' : '停用'),
                  },
                ]}
              />
            </>
          )}
        </Tabs.TabPane>
        <Tabs.TabPane key="draft" title="草稿">
          <Alert content="保存仅修改草稿，专用发布操作后才影响业务。拖拽行调整默认顺序。" />
          {draft && (
            <Space direction="vertical" style={{ width: '100%' }}>
              <Tag>草稿修订 {draft.draftRevision}</Tag>
              <Input
                aria-label="字典名称"
                value={draft.name}
                maxLength={200}
                disabled={saving || loading}
                onChange={(name) => edit({ name })}
              />
              <Space>
                公开读取
                <Switch
                  aria-label="公开读取"
                  checked={draft.publicReadable}
                  disabled={saving || loading}
                  onChange={(publicReadable) => edit({ publicReadable })}
                />
                启用字典
                <Switch
                  aria-label="启用字典"
                  checked={draft.enabled}
                  disabled={saving || loading}
                  onChange={(enabled) => edit({ enabled })}
                />
              </Space>
              {draft.itemOrder.map((id, index) => {
                const item = draft.items.find((i) => i.id === id);
                return (
                  <Card
                    key={id}
                    size="small"
                    draggable={!saving && !loading}
                    onDragStart={() => {
                      dragged.current = id;
                    }}
                    onDragOver={(e) => e.preventDefault()}
                    onDrop={(e) => {
                      e.preventDefault();
                      if (!saving && !loading) reorder(dragged.current, id);
                    }}
                    title={`↕ ${index + 1}`}
                  >
                    <Space wrap>
                      <Input
                        aria-label={`值 ${index + 1}`}
                        value={item.value}
                        maxLength={80}
                        disabled={saving || loading || item.everPublished}
                        onChange={(value) => editItem(id, { value })}
                      />
                      <Input
                        aria-label={`显示名称 ${index + 1}`}
                        value={item.label}
                        maxLength={200}
                        disabled={saving || loading}
                        onChange={(label) => editItem(id, { label })}
                      />
                      <Switch
                        aria-label={`启用项 ${index + 1}`}
                        checked={item.enabled}
                        disabled={saving || loading}
                        onChange={(enabled) => editItem(id, { enabled })}
                      />
                      <Button
                        disabled={saving || loading || item.everPublished}
                        onClick={() =>
                          edit({
                            items: draft.items.filter((i) => i.id !== id),
                            itemOrder: draft.itemOrder.filter(
                              (key) => key !== id
                            ),
                          })
                        }
                      >
                        删除
                      </Button>
                      {item.everPublished && <Tag>曾发布，值不可改删</Tag>}
                    </Space>
                    <Input.TextArea
                      aria-label={`说明 ${index + 1}`}
                      value={item.description || ''}
                      maxLength={2000}
                      disabled={saving || loading}
                      onChange={(description) => editItem(id, { description })}
                    />
                  </Card>
                );
              })}
              <Space>
                <Button
                  disabled={saving || loading}
                  onClick={() => {
                    const id = `new:${Date.now()}`;
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
                  }}
                >
                  新增项
                </Button>
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
            </Space>
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
              {readOnlyItems(historical.snapshot.items)}
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
            ? '停用当前字典？'
            : `回滚至版本 ${historical?.version}？`
        }
        visible={!!pendingAction}
        confirmLoading={saving}
        onCancel={() => {
          if (!saving) setPendingAction(undefined);
        }}
        onOk={() => transition(pendingAction)}
      >
        {pendingAction === 'disable'
          ? '停用后业务将无法读取此字典。'
          : '完整切换生效内容，草稿保持当前内容。'}
      </Modal>
      <Modal
        title="核对完整草稿后发布"
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
            {readOnlyItems(
              draft.itemOrder.map((id, index) => ({
                ...draft.items.find((i) => i.id === id),
                sortOrder: index,
              }))
            )}
          </>
        )}
      </Modal>
    </Card>
  );
}
