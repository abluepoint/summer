import React, { useEffect, useState } from 'react';
import { Alert, Card, Select, Table, Tabs, Tag } from '@arco-design/web-react';
import { Link } from 'react-router-dom';
import useActiveEnterprise from '../../data-scopes/useActiveEnterprise';
import { EffectiveDictionary, getDictionaryDraftList, getEffectiveDictionary } from '../api';

export default function DictionaryManagementPage() {
  const tenant = useActiveEnterprise();
  return <>
    <Select aria-label="活动租户" value={tenant.enterpriseId || undefined} options={tenant.options} onChange={tenant.change} style={{ width: 240, marginBottom: 16 }} />
    {tenant.enterpriseId ? <EffectiveContent key={tenant.enterpriseId} enterpriseId={tenant.enterpriseId} /> : <Alert content="请选择一个活动成员租户" />}
  </>;
}

function EffectiveContent({ enterpriseId }: { enterpriseId: string }) {
  const [codes, setCodes] = useState<string[]>([]);
  const [code, setCode] = useState('');
  const [value, setValue] = useState<EffectiveDictionary>();
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    let active = true;
    getDictionaryDraftList(enterpriseId).then((rows) => {
      if (active) { setCodes(rows.map((row) => row.code)); setCode(rows[0]?.code || ''); }
    }).catch((e) => { if (active) setError(`${e.message}${e.requestId ? ` (${e.requestId})` : ''}`); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [enterpriseId]);
  useEffect(() => {
    let active = true;
    setValue(undefined); setError('');
    if (code) {
      setLoading(true);
      getEffectiveDictionary(enterpriseId, code).then((result) => { if (active) setValue(result); })
        .catch((e) => { if (active) setError(`${e.message}${e.requestId ? ` (${e.requestId})` : ''}`); })
        .finally(() => { if (active) setLoading(false); });
    }
    return () => { active = false; };
  }, [enterpriseId, code]);
  return <Card title="字典管理" extra={<Link to="/dictionary/form">原字典表单</Link>}>
    <Select aria-label="字典编码" value={code || undefined} options={codes} onChange={setCode} style={{ width: 280 }} />
    <Tabs defaultActiveTab="effective"><Tabs.TabPane key="effective" title="生效版本">
      <Alert content="基础生效版本（只读）；业务表单根据访问组织应用覆盖。" />
      {error && <Alert type="error" content={error} />}
      {!loading && !codes.length && !error && <Alert content="暂无字典" />}
      {value?.status === 'UNPUBLISHED' && <Alert content="尚未发布" />}
      {value?.status === 'PUBLISHED' && <>
        <h3>{value.name}</h3><Tag>基础发布版本 {value.publishedVersion}</Tag>
        <Table loading={loading} rowKey="value" pagination={false} data={value.items} columns={[
          { title: '值', dataIndex: 'value' }, { title: '显示名称', dataIndex: 'label' },
          { title: '默认顺序', dataIndex: 'sortOrder' },
          { title: '状态', render: (_, item) => item.enabled ? '启用' : '停用' },
        ]} />
      </>}
    </Tabs.TabPane></Tabs>
  </Card>;
}
