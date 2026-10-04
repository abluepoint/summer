import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Table, Tag } from '@arco-design/web-react';
import useLocale from '@/utils/useLocale';
import { AuthorizationChangeEvent, listAuthorizationChangeEvents, retryAuthorizationChangeEvent } from './api';

const enterpriseId = 'ent-acme';

export default function AuthorizationEventsPage() {
  const t = useLocale();
  const [items, setItems] = useState<AuthorizationChangeEvent[]>([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const load = useCallback(() => {
    setLoading(true);
    listAuthorizationChangeEvents(enterpriseId).then((page) => { setItems(page.items); setError(''); }).catch((failure) => setError(failure instanceof Error ? failure.message : String(failure))).finally(() => setLoading(false));
  }, []);
  useEffect(() => { load(); }, [load]);
  const title = t['authorizationEvents.title'] || 'Authorization changes';
  return <Card title={title} extra={<Button size="small" loading={loading} onClick={load}>{t['authorizationEvents.refresh'] || 'Refresh'}</Button>}>
    {error && <Alert type="error" content={error} style={{ marginBottom: 16 }} />}
    <Table<AuthorizationChangeEvent> rowKey="eventId" loading={loading} data={items} columns={[
      { title: t['authorizationEvents.type'] || 'Event', dataIndex: 'eventType' },
      { title: t['authorizationEvents.resource'] || 'Resource', render: (_, row) => `${row.resourceType}/${row.resourceId}` },
      { title: t['authorizationEvents.version'] || 'Version', dataIndex: 'version' },
      { title: t['authorizationEvents.status'] || 'Status', dataIndex: 'status', render: (value) => <Tag color={value === 'PUBLISHED' ? 'green' : value === 'DEAD_LETTER' ? 'red' : 'orange'}>{value}</Tag> },
      { title: t['authorizationEvents.operation'] || 'Operation', render: (_, row) => <Button size="small" onClick={() => retryAuthorizationChangeEvent(enterpriseId, row.eventId).then(load).catch((failure) => setError(failure instanceof Error ? failure.message : String(failure)))}>{t['authorizationEvents.retry'] || 'Retry'}</Button> },
    ]} />
  </Card>;
}
