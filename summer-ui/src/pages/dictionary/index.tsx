import React, { useEffect, useRef, useState } from 'react';
import { useHistory } from 'react-router-dom';
import { isAuthRedirecting } from '@/auth/oidc';
import {
  Alert,
  Button,
  Card,
  Form,
  Select,
  Spin,
  Tag,
  Input,
  InputNumber,
  Table,
  Descriptions,
} from '@arco-design/web-react';
import useLocale from '@/utils/useLocale';
import { saveDictionaryOverride } from './api';

import { DictionarySelect } from './select';
import { DictionaryCascade, DictionaryCascadeLevel } from './cascade';
import {
  DictionaryNameDisplay,
  dictionaryName,
  useDictionaryNames,
} from './names';

import useActiveEnterprise from '../data-scopes/useActiveEnterprise';
const dictionaryCode = 'customer-segment';
const cascadeLevels: DictionaryCascadeLevel[] = [
  { code: dictionaryCode, label: '一级：客户分类' },
  {
    code: 'cascade-second',
    label: '二级：分类分组',
    groups: {
      '1': 'one',
      '01': 'zero-one',
      missing: 'absent',
      disabled: 'disabled',
    },
  },
  {
    code: 'cascade-third',
    label: '三级：业务选项',
    groups: { shared: 'shared', other: 'other' },
  },
];

export default function DictionaryConsumerPage() {
  const tenant = useActiveEnterprise();
  return (
    <>
      <Select
        aria-label="活动租户"
        placeholder="选择活动租户"
        value={tenant.enterpriseId || undefined}
        options={tenant.options}
        onChange={tenant.change}
        disabled={tenant.busy}
        style={{ width: 240, marginBottom: 16 }}
      />
      {tenant.enterpriseId ? (
        <DictionaryContent
          key={tenant.enterpriseId}
          enterpriseId={tenant.enterpriseId}
          onDirtyChange={tenant.setDirty}
          onBusyChange={tenant.setBusy}
        />
      ) : (
        tenant.ready && (
          <Alert
            type="info"
            content="请选择一个活动成员租户；没有可用租户时不会读取配置。"
          />
        )
      )}
    </>
  );
}
function DictionaryContent({
  enterpriseId,
  onDirtyChange,
  onBusyChange,
}: {
  enterpriseId: string;
  onDirtyChange: (value: boolean) => void;
  onBusyChange: (value: boolean) => void;
}) {
  const t = useLocale();
  const history = useHistory();
  const [value, setValue] = useState('');
  const [cascadeValues, setCascadeValues] = useState<string[]>(['', '', '']);
  const [error, setError] = useState('');
  const [groupCode, setGroupCode] = useState('');
  const names = useDictionaryNames(
    dictionaryCode,
    value ? [value] : [],
    groupCode || undefined,
    true
  );
  const options = names.status === 'ready' ? names.choices || [] : [];
  const loading = names.status === 'loading';
  const version = names.identity?.publishedVersion;
  const { streamState, streamError } = names;
  const alive = useRef(true);
  const writing = useRef(false);
  const [saving, setSaving] = useState(false);
  useEffect(
    () => () => {
      alive.current = false;
    },
    []
  );
  useEffect(() => onBusyChange(saving), [saving, onBusyChange]);
  const overrideDirty = useRef(false);
  const cascadeDirty = useRef(false);
  const [overrideOrg, setOverrideOrg] = useState('_tenant');
  const [overrideJson, setOverrideJson] = useState('[]');
  const [overrideVersion, setOverrideVersion] = useState<number | undefined>();
  useEffect(
    () => onDirtyChange(overrideDirty.current || cascadeDirty.current),
    [
      overrideJson,
      overrideOrg,
      overrideVersion,
      error,
      cascadeValues,
      onDirtyChange,
    ]
  );
  useEffect(() => {
    if (!overrideDirty.current && !cascadeDirty.current && !saving)
      return undefined;
    const unblock = history.block(() =>
      isAuthRedirecting()
        ? undefined
        : saving
        ? false
        : '字典表单有未保存修改，确认离开？'
    );
    const beforeUnload = (event: BeforeUnloadEvent) => {
      if (!isAuthRedirecting()) {
        event.preventDefault();
        event.returnValue = '';
      }
    };
    window.addEventListener('beforeunload', beforeUnload);
    return () => {
      unblock();
      window.removeEventListener('beforeunload', beforeUnload);
    };
  }, [
    overrideJson,
    overrideOrg,
    overrideVersion,
    cascadeValues,
    saving,
    history,
  ]);

  return (
    <Card
      title={t['dictionary.title']}
      extra={
        <span>
          <Tag
            color={streamState === 'OPEN' ? 'green' : 'orange'}
            style={{ marginRight: 8 }}
          >
            {streamState}
          </Tag>
          <Button
            size="small"
            disabled={saving}
            loading={loading}
            onClick={() => {
              names.refresh();
            }}
          >
            {t['dictionary.refresh']}
          </Button>
        </span>
      }
    >
      {loading && <Spin />}
      <Form layout="vertical" style={{ maxWidth: 520 }}>
        <Alert
          type="info"
          content="覆盖优先级：组织 > 租户 > 基础值。高优先级覆盖不可访问时不会回退；发布覆盖需要独立授权。"
          style={{ marginBottom: 16 }}
        />
        {names.status === 'ready' && names.identity?.overrideReviewRequired && (
          <Alert
            type="warning"
            content={`覆盖发布修订 ${names.identity.publishedRevision} 包含基础生效版本缺失的引用，请管理员核对覆盖历史并整理；原数组和历史已保留。`}
          />
        )}
        {error && (
          <Alert type="error" content={error} style={{ marginBottom: 16 }} />
        )}
        {streamError && (
          <Alert
            type="warning"
            content={t['dictionary.streamDisconnected']}
            style={{ marginBottom: 16 }}
          />
        )}
        {names.status === 'ready' &&
          names.items.get(value)?.status === 'disabled' && (
            <Alert
              type="warning"
              content={t['dictionary.reselect']}
              style={{ marginBottom: 16 }}
            />
          )}
        <Form.Item label={t['dictionary.segment']}>
          <DictionarySelect
            code={dictionaryCode}
            groupCode={groupCode || undefined}
            value={value}
            onChange={setValue}
          />
        </Form.Item>
        <Form.Item label="字典组编码（留空使用默认顺序）">
          <Input
            aria-label="字典组编码"
            value={groupCode}
            onChange={setGroupCode}
          />
        </Form.Item>
        <Form.Item label="已有业务值（仅展示，不改变选择范围）">
          <Input aria-label="已有业务值" value={value} onChange={setValue} />
        </Form.Item>
        <Descriptions
          title="业务详情"
          data={[
            { label: '原值', value: value || '—' },
            {
              label: '名称',
              value: <DictionaryNameDisplay names={names} value={value} />,
            },
          ]}
        />
        <Table
          rowKey="value"
          pagination={false}
          data={Array.from(
            new Set(
              [...options.map((item) => item.value), value].filter(Boolean)
            )
          ).map((item) => ({ value: item }))}
          columns={[
            { title: '业务值', dataIndex: 'value' },
            {
              title: '当前名称（转换函数）',
              render: (_, row) => dictionaryName(names, row.value),
            },
            {
              title: '当前名称（展示组件）',
              render: (_, row) => (
                <DictionaryNameDisplay names={names} value={row.value} />
              ),
            },
          ]}
        />
        <Form.Item label="跨字典三级联动（页面配置）">
          <DictionaryCascade
            levels={cascadeLevels}
            values={cascadeValues}
            onChange={(next) => {
              cascadeDirty.current = true;
              setCascadeValues(next);
            }}
          />
        </Form.Item>
        <Form.Item label="覆盖范围（_tenant 或组织 ID）">
          <Input
            disabled={saving}
            value={overrideOrg}
            onChange={(v) => {
              overrideDirty.current = true;
              setOverrideOrg(v);
              setOverrideVersion(undefined);
            }}
          />
        </Form.Item>
        <Form.Item label="覆盖版本（新建留空）">
          <InputNumber
            disabled={saving}
            min={0}
            value={overrideVersion}
            onChange={(v) => {
              overrideDirty.current = true;
              setOverrideVersion(v);
            }}
          />
        </Form.Item>
        <Form.Item label="覆盖选项 JSON">
          <Input.TextArea
            disabled={saving}
            aria-label="覆盖选项 JSON"
            value={overrideJson}
            onChange={(text) => {
              overrideDirty.current = true;
              setOverrideJson(text);
            }}
          />
        </Form.Item>
        <Button
          disabled={saving}
          loading={saving}
          onClick={() => {
            if (writing.current) return;
            try {
              const items = JSON.parse(overrideJson);
              writing.current = true;
              setSaving(true);
              saveDictionaryOverride(
                dictionaryCode,
                overrideOrg,
                items,
                overrideVersion,
                enterpriseId
              )
                .then((result) => {
                  if (!alive.current) return;
                  setOverrideVersion(result.version);
                  overrideDirty.current = false;
                  names.refresh();
                })
                .catch((e) => {
                  if (alive.current)
                    setError(
                      `${e.status === 403 ? '无权发布字典覆盖' : e.message}${
                        e.requestId ? ` (${e.requestId})` : ''
                      }`
                    );
                })
                .finally(() => {
                  writing.current = false;
                  if (alive.current) setSaving(false);
                });
            } catch {
              setError('请输入有效选项 JSON');
            }
          }}
        >
          发布字典覆盖
        </Button>
        {version !== undefined && (
          <div data-testid="dictionary-version">
            {t['dictionary.version']}: {version}
          </div>
        )}
      </Form>
    </Card>
  );
}
