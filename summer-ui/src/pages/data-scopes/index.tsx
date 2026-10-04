import React, { useEffect, useState } from 'react';
import { Alert, Button, Card, Checkbox, Message, Radio, Space, Table, Typography } from '@arco-design/web-react';
import useLocale from '@/utils/useLocale';
import { DataScopeApiError, DataScopePolicy, DataScopeRule, listDataScopes, saveDataScope } from './api';

const enterpriseId = 'ent-acme';
const resources = ['system-parameters', 'dictionaries'];
const choices: DataScopeRule['type'][] = ['TENANT', 'ORGANIZATION', 'DEPARTMENT', 'ORGANIZATION_TREE', 'SELF', 'CREATOR', 'OWNER'];
export default function DataScopesPage() {
  const t = useLocale(); const [policies, setPolicies] = useState<DataScopePolicy[]>([]); const [error, setError] = useState<DataScopeApiError | null>(null); const [draft, setDraft] = useState<Record<string, DataScopeRule['type'][]>>({}); const [operator, setOperator] = useState<'AND' | 'OR'>('AND');
  const load = () => listDataScopes(enterpriseId).then((items) => { setPolicies(items); setDraft(Object.fromEntries(items.map((item) => [item.resource, item.rules.map((rule) => rule.type)]))); setError(null); }).catch(setError);
  useEffect(() => { load(); }, []);
  const save = (resource: string) => saveDataScope(enterpriseId, resource, operator, (draft[resource] || []).map((type) => ({ type }))).then(() => { Message.success(t['dataScopes.saved']); load(); }).catch(setError);
  return <Card title={t['dataScopes.title']} extra={<Button onClick={load}>{t['dataScopes.refresh']}</Button>}>
    {error && <Alert type="error" content={error.message || t['dataScopes.error']} style={{ marginBottom: 16 }} />}
    <Typography.Paragraph>{t['dataScopes.description']}</Typography.Paragraph>
    <Radio.Group type="button" value={operator} onChange={setOperator}><Radio value="AND">AND</Radio><Radio value="OR">OR</Radio></Radio.Group>
    <Table<DataScopePolicy> rowKey="resource" style={{ marginTop: 16 }} data={resources.map((resource) => policies.find((item) => item.resource === resource) || ({ resource, enterpriseId, operator, rules: [], version: 0, updatedAt: '' } as DataScopePolicy))} columns={[{ title: t['dataScopes.resource'], dataIndex: 'resource' }, { title: t['dataScopes.rules'], render: (_, row) => <Checkbox.Group value={draft[row.resource] || []} onChange={(values) => setDraft((current) => ({ ...current, [row.resource]: values as DataScopeRule['type'][] }))}>{choices.map((type) => <Checkbox key={type} value={type}>{type}</Checkbox>)}</Checkbox.Group> }, { title: t['dataScopes.operation'], render: (_, row) => <Button type="primary" size="small" disabled={!(draft[row.resource] || []).length} onClick={() => save(row.resource)}>{t['dataScopes.save']}</Button> }]} />
  </Card>;
}
