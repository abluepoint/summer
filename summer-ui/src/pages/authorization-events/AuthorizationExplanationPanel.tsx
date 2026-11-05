import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Button, Card, Input, Select, Space, Table, Tag, Typography } from '@arco-design/web-react';
import { oidcManager } from '@/auth/oidc';
import { MENU_REFRESH_EVENT } from '@/menu/refresh';
import { AuthorizationDomain } from '@/auth/interfaceOperations';
import { AuthorizationExplanation, ExplanationQuery, explainAuthorization } from './explanation';

type Failure = { message?: string; requestId?: string };

export default function AuthorizationExplanationPanel({ domain, disabled = false, onBusyChange }: {
  domain: AuthorizationDomain;
  disabled?: boolean;
  onBusyChange: (value: boolean) => void;
}) {
  const kind = domain.kind;
  const id = domain.kind === 'ENTERPRISE' ? domain.id : 'platform';
  const targetDomain = useMemo<AuthorizationDomain>(() => kind === 'PLATFORM' ? { kind } : { kind, id }, [kind, id]);
  const [query, setQuery] = useState<ExplanationQuery>({ subject: '', resourceType: 'INTERFACE', resourceId: '' });
  const [result, setResult] = useState<AuthorizationExplanation | null>(null);
  const [error, setError] = useState<Failure | null>(null);
  const [busy, setBusy] = useState(false);
  const generation = useRef(0);
  const alive = useRef(true);
  const inFlight = useRef<number | null>(null);
  const latest = useRef<ExplanationQuery | null>(null);
  const load = useCallback(async (input: ExplanationQuery) => {
    if (disabled || inFlight.current !== null) return;
    const request = ++generation.current;
    inFlight.current = request;
    latest.current = input;
    setResult(null); setError(null); setBusy(true); onBusyChange(true);
    try {
      const value = await explainAuthorization(targetDomain, input);
      if (alive.current && request === generation.current) setResult(value);
    } catch (failure) {
      if (alive.current && request === generation.current) setError(failure as Failure);
    } finally {
      if (inFlight.current === request) inFlight.current = null;
      if (alive.current && request === generation.current) { setBusy(false); onBusyChange(false); }
    }
  }, [targetDomain, disabled, onBusyChange]);
  useEffect(() => {
    alive.current = true;
    const clear = () => { generation.current++; inFlight.current = null; latest.current = null; setResult(null); setError(null); setBusy(false); onBusyChange(false); };
    const refresh = () => { if (latest.current) load(latest.current); };
    const visible = () => { if (document.visibilityState === 'visible') refresh(); };
    window.addEventListener('focus', refresh);
    window.addEventListener(MENU_REFRESH_EVENT, refresh);
    document.addEventListener('visibilitychange', visible);
    oidcManager.events.addUserLoaded(clear);
    oidcManager.events.addUserUnloaded(clear);
    return () => {
      alive.current = false; generation.current++; inFlight.current = null; onBusyChange(false);
      window.removeEventListener('focus', refresh);
      window.removeEventListener(MENU_REFRESH_EVENT, refresh);
      document.removeEventListener('visibilitychange', visible);
      oidcManager.events.removeUserLoaded(clear);
      oidcManager.events.removeUserUnloaded(clear);
    };
  }, [load, onBusyChange]);
  const edit = (patch: Partial<ExplanationQuery>) => {
    generation.current++; latest.current = null; setResult(null); setError(null);
    setQuery(current => ({ ...current, ...patch }));
  };
  const canQuery = query.subject.trim() && query.resourceId.trim()
    && (query.resourceType !== 'DATA' || query.action && query.recordId?.trim());
  return <Card title="授权解释" style={{ marginTop: 16 }}>
    <Typography.Paragraph>管理域：{kind === 'PLATFORM' ? '平台' : `企业 ${id}`}。查看该域内当前持久授权；实际调用还受客户端委托范围及业务条件约束。数据预览只显示结果，不返回业务正文。</Typography.Paragraph>
    <Space wrap>
      <Input aria-label="解释目标主体" placeholder="目标主体" value={query.subject} maxLength={128} disabled={busy || disabled} onChange={subject => edit({ subject })} />
      <Select aria-label="解释资源类型" value={query.resourceType} disabled={busy || disabled} style={{ width: 140 }} options={['INTERFACE', 'MENU', 'DATA']} onChange={resourceType => {
        generation.current++; latest.current = null; setResult(null); setError(null);
        setQuery({ subject: query.subject, resourceType, resourceId: '' });
      }} />
      <Input aria-label="解释资源身份" placeholder="资源身份" value={query.resourceId} maxLength={160} disabled={busy || disabled} onChange={resourceId => edit({ resourceId })} />
      {query.resourceType === 'DATA' && <>
        <Select aria-label="解释数据动作" placeholder="数据动作" value={query.action} disabled={busy || disabled} style={{ width: 130 }} options={['read', 'create', 'update', 'delete', 'export']} onChange={action => edit({ action })} />
        <Input aria-label="解释目标记录" placeholder="记录身份" value={query.recordId || ''} maxLength={256} disabled={busy || disabled} onChange={recordId => edit({ recordId })} />
      </>}
      <Button type="primary" loading={busy} disabled={!canQuery || busy || disabled} onClick={() => load(query)}>查看授权依据</Button>
    </Space>
    {error && <Alert type="error" style={{ marginTop: 16 }} content={`${error.message || '授权解释读取失败'}${error.requestId ? ` (${error.requestId})` : ''}`} />}
    {result && <div aria-live="polite">
      <Typography.Paragraph style={{ marginTop: 16 }}>
        主体：{result.subject} · 可信域：{result.domain.key} · 资源：{result.resource.type}/{result.resource.id} · 来源：{result.resource.source}
      </Typography.Paragraph>
      <Typography.Paragraph><Tag color={result.allowed ? 'green' : 'red'}>{result.allowed ? '允许' : '拒绝'}</Tag> {result.reason} · revision {result.revision} · 请求 {result.requestId}</Typography.Paragraph>
      {result.managementQualification.required && <Typography.Paragraph>
        管理角色资格：{result.managementQualification.allowed ? '具备' : '缺少'}。
        {result.managementQualification.grants.map(grant => `${grant.roleName} (${grant.roleId}) → ${grant.permissionName} (${grant.permissionId})`).join('、')}
      </Typography.Paragraph>}
      <Table rowKey="permissionId" pagination={false} data={result.matchedPermissions} columns={[
        { title: '命中权限', render: (_, row) => `${row.name} (${row.permissionId})` },
        { title: '访问方式', dataIndex: 'accessMode' },
        { title: '角色来源', render: (_, row) => row.roles.length ? row.roles.map(role => `${role.name} (${role.roleId})`).join('、') : result.managementQualification.required ? '资源访问方式独立于管理角色资格（见上方）' : '此访问方式无需角色授予' },
        { title: '数据范围', render: (_, row) => row.scopePreset || '—' },
      ]} />
    </div>}
  </Card>;
}
