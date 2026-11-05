import { useInterfaceOperations } from '@/auth/useInterfaceOperations';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Select, Table, Tag, Typography } from '@arco-design/web-react';
import useLocale from '@/utils/useLocale';
import useActiveEnterprise from '../data-scopes/useActiveEnterprise';
import { AuthorizationChangeEvent, listAuthorizationChangeEvents, retryAuthorizationChangeEvent } from './api';

type EventError = { message?: string; requestId?: string };

export default function AuthorizationEventsPage() {
  const tenant = useActiveEnterprise();
  return <><Select aria-label="活动租户" placeholder="选择活动租户" value={tenant.enterpriseId || undefined} options={tenant.options} onChange={tenant.change} disabled={tenant.busy} style={{ width: 240, marginBottom: 16 }} />
    {tenant.enterpriseId ? <AuthorizationEventsContent key={tenant.enterpriseId} enterpriseId={tenant.enterpriseId} onBusyChange={tenant.setBusy} /> : tenant.ready && <Alert type="info" content="请选择一个活动成员租户；没有可用租户时不会读取授权事件。" />}</>;
}
function AuthorizationEventsContent({ enterpriseId, onBusyChange }: { enterpriseId: string; onBusyChange: (value: boolean) => void }) {
  const t = useLocale();
  const operations = useInterfaceOperations({ kind: 'ENTERPRISE', id: enterpriseId }, ['authorization.events.retry']);
  const canRetry = operations['authorization.events.retry'] === true;
  const [items, setItems] = useState<AuthorizationChangeEvent[]>([]);
  const [error, setError] = useState<EventError | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const alive = useRef(true);
  const writing = useRef(false);
  const generation = useRef(0);
  useEffect(() => () => { alive.current = false; onBusyChange(false); }, [onBusyChange]);
  const load = useCallback(async () => {
    const request = ++generation.current;
    setLoading(true); setItems([]);
    try {
      const page = await listAuthorizationChangeEvents(enterpriseId);
      if (!alive.current || request !== generation.current) return;
      setItems(page.items); setError(null);
    } catch (failure) {
      if (!alive.current || request !== generation.current) return;
      setItems([]); setError(failure as EventError);
    } finally { if (alive.current && request === generation.current) setLoading(false); }
  }, [enterpriseId]);
  useEffect(() => { load(); }, [load]);
  const retry = async (eventId: string) => {
    if (!canRetry || writing.current) return;
    writing.current = true; setSaving(true); onBusyChange(true);
    const request = ++generation.current;
    try {
      await retryAuthorizationChangeEvent(enterpriseId, eventId);
      if (alive.current && request === generation.current) await load();
    } catch (failure) {
      if (alive.current && request === generation.current) { setItems([]); setError(failure as EventError); }
    } finally {
      writing.current = false;
      if (alive.current) { setSaving(false); onBusyChange(false); }
    }
  };
  const title = t['authorizationEvents.title'] || 'Authorization changes';
  return <Card title={title} extra={<Button size="small" loading={loading} disabled={saving} onClick={load}>{t['authorizationEvents.refresh'] || 'Refresh'}</Button>}>
    <Typography.Paragraph>当前租户：{enterpriseId}</Typography.Paragraph>
    {error && <Alert type="error" content={`${error.message || '授权事件读取或重试失败'}${error.requestId ? ` (${error.requestId})` : ''}`} style={{ marginBottom: 16 }} />}
    <Table<AuthorizationChangeEvent> rowKey="eventId" loading={loading} data={items} columns={[
      { title: t['authorizationEvents.type'] || 'Event', dataIndex: 'eventType' },
      { title: t['authorizationEvents.resource'] || 'Resource', render: (_, row) => `${row.resourceType}/${row.resourceId}` },
      { title: t['authorizationEvents.version'] || 'Version', dataIndex: 'version' },
      { title: t['authorizationEvents.status'] || 'Status', dataIndex: 'status', render: (value) => <Tag color={value === 'PUBLISHED' ? 'green' : value === 'DEAD_LETTER' ? 'red' : 'orange'}>{value}</Tag> },
      { title: t['authorizationEvents.operation'] || 'Operation', render: (_, row) => <Button size="small" disabled={!canRetry || saving} onClick={() => retry(row.eventId)}>{t['authorizationEvents.retry'] || 'Retry'}</Button> },
    ]} />
  </Card>;
}
