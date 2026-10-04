import React, { useEffect, useState } from 'react';
import { Alert, Button, Card, Checkbox, Input, Message, Radio, Space, Table, Tag, Typography } from '@arco-design/web-react';
import useLocale from '@/utils/useLocale';
import { DataScopeApiError, DataScopePolicy, DataScopePreview, DataScopeRule, listDataScopes, previewDataScope, saveDataScope } from './api';

const enterpriseId = 'ent-acme';
const resources = ['system-parameters', 'dictionaries'];
const choices: DataScopeRule['type'][] = ['TENANT', 'ORGANIZATION', 'DEPARTMENT', 'ORGANIZATION_TREE', 'SELF', 'CREATOR', 'OWNER'];
export default function DataScopesPage() {
  const t = useLocale(); const [policies, setPolicies] = useState<DataScopePolicy[]>([]); const [error, setError] = useState<DataScopeApiError | null>(null); const [draft, setDraft] = useState<Record<string, DataScopeRule['type'][]>>({}); const [operator, setOperator] = useState<'AND' | 'OR'>('AND'); const [subject, setSubject] = useState(''); const [organizationId, setOrganizationId] = useState(''); const [preview, setPreview] = useState<DataScopePreview | null>(null);
  const load = () => listDataScopes(enterpriseId).then((items) => { setPolicies(items); setDraft(Object.fromEntries(items.map((item) => [item.resource, item.rules.map((rule) => rule.type)]))); setError(null); }).catch(setError);
  useEffect(() => { load(); }, []);
  const save = (resource: string) => {
    const selected = draft[resource] || [];
    const children = selected.map((field) => ({ field, operator: 'EQ' as const, value: ['ORGANIZATION', 'DEPARTMENT', 'ORGANIZATION_TREE'].includes(field) ? organizationId : subject })).filter((node) => String(node.value || '').trim());
    return saveDataScope(enterpriseId, resource, operator, selected.map((type) => ({ type })), children.length ? { operator, children } : undefined).then(() => { Message.success(t['dataScopes.saved']); load(); }).catch(setError);
  };
  const previewScope = (resource: string) => previewDataScope(enterpriseId, resource, subject, organizationId || undefined).then(setPreview).catch(setError);
  return <Card title={t['dataScopes.title']} extra={<Button onClick={load}>{t['dataScopes.refresh']}</Button>}>
    {error && <Alert type="error" content={error.message || t['dataScopes.error']} style={{ marginBottom: 16 }} />}
    <Typography.Paragraph>{t['dataScopes.description']}</Typography.Paragraph>
    <Radio.Group type="button" value={operator} onChange={setOperator}><Radio value="AND">AND</Radio><Radio value="OR">OR</Radio></Radio.Group>
    <Table<DataScopePolicy> rowKey="resource" style={{ marginTop: 16 }} data={resources.map((resource) => policies.find((item) => item.resource === resource) || ({ resource, enterpriseId, operator, rules: [], version: 0, updatedAt: '' } as DataScopePolicy))} columns={[{ title: t['dataScopes.resource'], dataIndex: 'resource' }, { title: t['dataScopes.rules'], render: (_, row) => <Checkbox.Group value={draft[row.resource] || []} onChange={(values) => setDraft((current) => ({ ...current, [row.resource]: values as DataScopeRule['type'][] }))}>{choices.map((type) => <Checkbox key={type} value={type}>{type}</Checkbox>)}</Checkbox.Group> }, { title: t['dataScopes.operation'], render: (_, row) => <Space><Button type="primary" size="small" disabled={!(draft[row.resource] || []).length} onClick={() => save(row.resource)}>{t['dataScopes.save']}</Button><Button size="small" onClick={() => previewScope(row.resource)}>{t['dataScopes.preview']}</Button></Space> }]} />
    <Space style={{ marginTop: 18 }}><Input placeholder={t['dataScopes.subject']} value={subject} onChange={setSubject} /><Input placeholder={t['dataScopes.organization']} value={organizationId} onChange={setOrganizationId} /></Space>
    {preview && <Card size="small" title={t['dataScopes.previewTitle']} style={{ marginTop: 18 }}><Space><Tag color={preview.allowed ? 'green' : 'red'}>{preview.decision}</Tag><span>{preview.explanation}</span><span>v{preview.policyVersion}</span></Space><Typography.Paragraph>{preview.matchedConditions.join(', ') || t['dataScopes.noMatch']}</Typography.Paragraph></Card>}
  </Card>;
}
