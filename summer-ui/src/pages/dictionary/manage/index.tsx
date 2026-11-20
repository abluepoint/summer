import React, { useEffect, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  Modal,
  Space,
  Switch,
  Table,
  Tag,
} from '@arco-design/web-react';
import { Prompt } from 'react-router-dom';
import useActiveEnterprise from '../../data-scopes/useActiveEnterprise';
import {
  CreateDictionary,
  DictionaryType,
  createDictionary,
  getDictionaryDraftList,
} from '../api';
import DictionaryDetail from './detail';

export default function DictionaryManagementPage({
  groupsOnly = false,
}: {
  groupsOnly?: boolean;
}) {
  const tenant = useActiveEnterprise();
  return tenant.enterpriseId ? (
    <DictionaryList
      groupsOnly={groupsOnly}
      key={tenant.enterpriseId}
      enterpriseId={tenant.enterpriseId}
      onDirty={tenant.setDirty}
      onBusy={tenant.setBusy}
    />
  ) : (
    <Alert content="请先在系统导航栏选择活动企业" />
  );
}

function DictionaryList({
  groupsOnly,
  enterpriseId,
  onDirty,
  onBusy,
}: {
  groupsOnly: boolean;
  enterpriseId: string;
  onDirty: (value: boolean) => void;
  onBusy: (value: boolean) => void;
}) {
  const [rows, setRows] = useState<DictionaryType[]>([]);
  const [selected, setSelected] = useState<{ code: string; tab: string }>();
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(1);
  const [reload, setReload] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [creating, setCreating] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [saving, setSaving] = useState(false);
  const [createError, setCreateError] = useState('');
  const [form] = Form.useForm<CreateDictionary>();
  const writing = useRef(false);
  const alive = useRef(true);
  useEffect(
    () => () => {
      alive.current = false;
    },
    []
  );
  useEffect(() => {
    if (selected) return;
    onDirty(dirty);
    return () => onDirty(false);
  }, [dirty, selected, onDirty]);
  useEffect(() => {
    if (selected) return;
    onBusy(saving);
    return () => onBusy(false);
  }, [saving, selected, onBusy]);
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
    if (selected) return;
    let active = true;
    setLoading(true);
    setError('');
    getDictionaryDraftList(enterpriseId)
      .then((result) => {
        if (active) setRows(result);
      })
      .catch((e) => {
        if (active) {
          setRows([]);
          setError(`${e.message}${e.requestId ? ` (${e.requestId})` : ''}`);
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [enterpriseId, reload, selected]);

  const create = async () => {
    if (writing.current) return;
    let input: CreateDictionary;
    try {
      input = await form.validate();
    } catch {
      return;
    }
    if (writing.current || !alive.current) return;
    writing.current = true;
    setSaving(true);
    setCreateError('');
    try {
      const result = await createDictionary(enterpriseId, {
        ...input,
        name: input.name.trim(),
      });
      if (alive.current) {
        setCreating(false);
        setDirty(false);
        setSelected({ code: result.code, tab: 'draft' });
      }
    } catch (e) {
      if (alive.current)
        setCreateError(
          `${
            e.status === 409
              ? '字典编码已存在，请更换编码后重试。'
              : e.status === 403
              ? '无权新建字典，请联系管理员。'
              : e.message
          }${e.requestId ? ` (${e.requestId})` : ''}`
        );
    } finally {
      writing.current = false;
      if (alive.current) {
        setSaving(false);
        onBusy(false);
      }
    }
  };

  if (selected)
    return (
      <DictionaryDetail
        groupsOnly={groupsOnly}
        enterpriseId={enterpriseId}
        code={selected.code}
        initialTab={selected.tab}
        onBack={() => setSelected(undefined)}
        onDirty={onDirty}
        onBusy={onBusy}
      />
    );

  const filtered = rows.filter((row) =>
    `${row.code} ${row.name}`.toLowerCase().includes(query.trim().toLowerCase())
  );
  return (
    <Card
      title={groupsOnly ? '字典组管理' : '字典管理'}
      extra={
        !groupsOnly && (
          <Button
            type="primary"
            onClick={() => {
              form.resetFields();
              setCreateError('');
              setDirty(false);
              setCreating(true);
            }}
          >
            新建字典
          </Button>
        )
      }
    >
      <Prompt
        when={dirty || saving}
        message={() => (saving ? false : '有未保存修改，是否放弃？')}
      />
      <Space style={{ marginBottom: 16 }}>
        <Input.Search
          aria-label="搜索字典"
          placeholder="搜索字典名称或编码"
          allowClear
          value={query}
          onChange={(value) => {
            setQuery(value);
            setPage(1);
          }}
          style={{ width: 280 }}
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
      <Table<DictionaryType>
        rowKey="code"
        loading={loading}
        data={filtered}
        noDataElement={
          error
            ? '字典列表加载失败，请重试'
            : query
            ? '没有匹配的字典'
            : groupsOnly
            ? '暂无字典，请先在字典管理中创建字典'
            : '暂无字典，点击“新建字典”开始创建'
        }
        pagination={{
          current: page,
          pageSize: 10,
          total: filtered.length,
          onChange: setPage,
        }}
        columns={[
          { title: '字典名称', dataIndex: 'name' },
          { title: '字典编码', dataIndex: 'code' },
          {
            title: '发布状态',
            render: (_, row) => (
              <Tag color={row.published ? 'green' : 'gray'}>
                {row.published
                  ? '已发布'
                  : row.publishedVersion
                  ? '已停用'
                  : '未发布'}
              </Tag>
            ),
          },
          {
            title: '当前发布版本',
            render: (_, row) => row.publishedVersion || '—',
          },
          {
            title: '操作',
            render: (_, row) => (
              <Button
                type="text"
                onClick={() =>
                  setSelected({ code: row.code, tab: 'effective' })
                }
              >
                {groupsOnly ? '管理字典组' : '详情'}
              </Button>
            ),
          },
        ]}
      />
      <Modal
        title="新建字典"
        visible={creating}
        confirmLoading={saving}
        okText="创建并编辑字典值"
        cancelText="取消"
        maskClosable={false}
        onOk={create}
        onCancel={() => {
          if (
            !saving &&
            (!dirty || window.confirm('有未保存修改，是否放弃？'))
          ) {
            setCreating(false);
            setDirty(false);
          }
        }}
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{ publicReadable: false, enabled: true }}
          onValuesChange={() => setDirty(true)}
        >
          <Form.Item
            label="字典名称"
            field="name"
            rules={[
              { required: true, message: '请输入字典名称' },
              {
                validator: (value, callback) => {
                  if (!value?.trim()) callback('字典名称不能为空白');
                },
              },
              { maxLength: 200, message: '字典名称最多200个字符' },
            ]}
          >
            <Input
              aria-label="新字典名称"
              maxLength={200}
              disabled={saving}
              placeholder="例如：客户类型"
            />
          </Form.Item>
          <Form.Item
            label="字典编码"
            field="code"
            extra="3–80位小写字母、数字或连字符，以字母开头、字母或数字结尾。创建后不可修改。"
            rules={[
              { required: true, message: '请输入字典编码' },
              {
                match: /^[a-z][a-z0-9-]{1,78}[a-z0-9]$/,
                message: '请输入符合规则的字典编码',
              },
            ]}
          >
            <Input
              aria-label="新字典编码"
              maxLength={80}
              disabled={saving}
              placeholder="例如：customer-type"
            />
          </Form.Item>
          <Form.Item
            label="公开读取"
            field="publicReadable"
            triggerPropName="checked"
          >
            <Switch aria-label="新字典公开读取" disabled={saving} />
          </Form.Item>
          <Form.Item label="启用字典" field="enabled" triggerPropName="checked">
            <Switch aria-label="新字典启用" disabled={saving} />
          </Form.Item>
        </Form>
        <Alert content="创建后进入字典详情维护字典值，保存草稿并发布后供业务使用。" />
        {createError && (
          <Alert type="error" content={createError} style={{ marginTop: 16 }} />
        )}
      </Modal>
    </Card>
  );
}
