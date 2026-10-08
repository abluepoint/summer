import React, { useEffect, useRef, useState } from 'react';
import { useHistory } from 'react-router-dom';
import { isAuthRedirecting } from '@/auth/oidc';
import { Alert, Button, Card, Input, Message, Modal, Radio, Space, Tag, Typography } from '@arco-design/web-react';
import useLocale from '@/utils/useLocale';
import { DataScopeApiError, DataScopeCondition, DataScopePolicy, DataScopePreview, listDataScopes, previewDataScope, saveDataScope, saveDataScopeBinding } from './api';

const enterpriseId = 'ent-acme';
export default function DataScopesPage() {
  const t = useLocale();
  const history = useHistory();
  const originalTrees = useRef<Record<string, string>>({});
  const [policies, setPolicies] = useState<DataScopePolicy[]>([]);
  const [trees, setTrees] = useState<Record<string, string>>({});
  const [error, setError] = useState<DataScopeApiError | null>(null);
  const [subject, setSubject] = useState('');
  const [organizationId, setOrganizationId] = useState('');
  const [preview, setPreview] = useState<DataScopePreview | null>(null);
  const [bindingKind, setBindingKind] = useState<'ROLE' | 'USER'>('USER');
  const [bindingSubject, setBindingSubject] = useState('');
  const load = () => listDataScopes(enterpriseId).then((items) => {
    setPolicies(items);
    const nextTrees = Object.fromEntries(items.map((item) => [item.resource, JSON.stringify(!item.rules.length && item.conditions ? item.conditions : { operator: item.operator, children: [
      ...item.rules.map((r) => ({ ...r, field: r.field || r.type })),
      ...(item.conditions ? [item.conditions] : []),
    ] }, null, 2)]));
    originalTrees.current = nextTrees;
    setTrees(nextTrees);
    setError(null);
  }).catch((failure) => { setPolicies([]); setPreview(null); setError(failure); });
  useEffect(() => { load(); }, []);
  const dirty = Object.keys(trees).some((resource) => trees[resource] !== originalTrees.current[resource]);
  useEffect(() => {
    if (!dirty) return undefined;
    const unblock = history.block(() => isAuthRedirecting() ? undefined : '条件树有未保存修改，确认离开？');
    const beforeUnload = (event: BeforeUnloadEvent) => { if (!isAuthRedirecting()) { event.preventDefault(); event.returnValue = ''; } };
    window.addEventListener('beforeunload', beforeUnload);
    return () => { unblock(); window.removeEventListener('beforeunload', beforeUnload); };
  }, [dirty, history]);
  const save = (resource: string, binding = false) => {
    try {
      const conditions = JSON.parse(trees[resource]) as DataScopeCondition;
      const call = binding
        ? saveDataScopeBinding(enterpriseId, resource, bindingKind, bindingSubject, conditions)
        : saveDataScope(enterpriseId, resource, 'AND', [], conditions);
      call.then((saved) => { Message.success(t['dataScopes.saved']); setPreview(null); if (!binding) { originalTrees.current = { ...originalTrees.current, [resource]: trees[resource] }; setPolicies((current) => current.map((policy) => policy.resource === resource ? saved as DataScopePolicy : policy)); } }).catch(setError);
    } catch { Message.error('条件树必须是有效 JSON'); }
  };
  const runPreview = (resource: string) => {
    setPreview(null);
    setError(null);
    previewDataScope(enterpriseId, resource, subject, organizationId || undefined)
      .then(setPreview).catch((failure) => { setPreview(null); setError(failure); });
  };
  const refresh = () => dirty ? Modal.confirm({ title: '刷新会放弃未保存的条件树，是否继续？', okText: '放弃修改', cancelText: '继续编辑', onOk: load }) : load();
  return <Card title={t['dataScopes.title']} extra={<Button onClick={refresh}>{t['dataScopes.refresh']}</Button>}>
    {error && <Alert type="error" content={`${error.status === 403 ? '无权管理数据范围' : error.message}${error.requestId ? ` (${error.requestId})` : ''}`} />}
    <Typography.Paragraph>资源策略限定总范围，角色允许取并集，用户拒绝优先。预览读取已保存的角色绑定和用户拒绝。</Typography.Paragraph>
    <Space wrap><Radio.Group value={bindingKind} onChange={setBindingKind}><Radio value="USER">用户拒绝</Radio><Radio value="ROLE">角色允许</Radio></Radio.Group><Input placeholder="用户主体 / 角色 ID" value={bindingSubject} onChange={setBindingSubject} /></Space>
    {policies.map((policy) => <Card key={policy.resource} size="small" title={`${policy.resource} · AND/OR 条件树 · v${policy.version}`} style={{ marginTop: 16 }}>
      <Typography.Paragraph>字段：TENANT、ORGANIZATION、ORGANIZATION_TREE、DEPARTMENT、SELF、CREATOR、OWNER；比较：EQ、NE、IN、NOT_IN；effect：ALLOW / DENY。children 可嵌套 AND / OR。</Typography.Paragraph>
      <Input.TextArea aria-label={`${policy.resource} condition tree`} value={trees[policy.resource] || ''} onChange={(value) => setTrees((current) => ({ ...current, [policy.resource]: value }))} autoSize={{ minRows: 5, maxRows: 16 }} />
      <Space style={{ marginTop: 8 }}><Button onClick={() => save(policy.resource)}>保存资源策略</Button><Button disabled={!bindingSubject.trim()} onClick={() => save(policy.resource, true)}>保存主体范围</Button><Button onClick={() => runPreview(policy.resource)}>{t['dataScopes.preview']}</Button></Space>
    </Card>)}
    <Space style={{ marginTop: 18 }}><Input placeholder={t['dataScopes.subject']} value={subject} onChange={setSubject} /><Input placeholder={t['dataScopes.organization']} value={organizationId} onChange={setOrganizationId} /></Space>
    {preview && <Card size="small" title={t['dataScopes.previewTitle']} style={{ marginTop: 18 }}><Space><Tag color={preview.allowed ? 'green' : 'red'}>{preview.decision}</Tag><span>{preview.reasonCode}</span><span>v{preview.policyVersion}</span></Space><Typography.Paragraph>{preview.explanation}</Typography.Paragraph><pre>{JSON.stringify(preview.effectiveScope, null, 2)}</pre></Card>}
  </Card>;
}
