// Test-only consumer harness; never registered as an application route.
import AppLoading from '@/components/AppLoading';
import React, { useEffect, useRef, useState } from 'react';
import { useHistory } from 'react-router-dom';
import { isAuthRedirecting } from '@/auth/oidc';
import {
  Alert,
  Button,
  Card,
  Form,
  Select,
  Tag,
  Input,
  Table,
  Descriptions,
} from '@arco-design/web-react';
import useLocale from '@/utils/useLocale';

import { DictionarySelect } from '@/components/DictionarySelect';
import {
  DictionaryCascade,
  DictionaryCascadeLevel,
} from '@/pages/dictionary/cascade';
import {
  DictionaryNameDisplay,
  dictionaryName,
  useDictionaryNames,
} from '@/pages/dictionary/names';

import useActiveEnterprise from '@/hooks/useActiveEnterprise';
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
          onDirtyChange={tenant.setDirty}
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
  onDirtyChange,
}: {
  onDirtyChange: (value: boolean) => void;
}) {
  const t = useLocale();
  const history = useHistory();
  const [value, setValue] = useState('');
  const [cascadeValues, setCascadeValues] = useState<string[]>(['', '', '']);
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
  const cascadeDirty = useRef(false);
  useEffect(
    () => onDirtyChange(cascadeDirty.current),
    [cascadeValues, onDirtyChange]
  );
  useEffect(() => {
    if (!cascadeDirty.current) return undefined;
    const unblock = history.block(() =>
      isAuthRedirecting() ? undefined : '字典表单有未保存修改，确认离开？'
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
  }, [cascadeValues, history]);

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
      {loading && <AppLoading fullScreen={false} />}
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
        {version !== undefined && (
          <div data-testid="dictionary-version">
            {t['dictionary.version']}: {version}
          </div>
        )}
      </Form>
    </Card>
  );
}
