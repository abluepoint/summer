import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Message, Space, Table, Tag, Typography } from '@arco-design/web-react';
import useLocale from '@/utils/useLocale';
import { listSessions, revokeSession, SessionApiError, SessionItem } from './api';

export default function SessionsPage() {
  const t = useLocale();
  const [items, setItems] = useState<SessionItem[]>([]);
  const [error, setError] = useState<SessionApiError | null>(null);
  const [loading, setLoading] = useState(false);
  const load = useCallback(() => {
    setLoading(true);
    listSessions()
      .then((page) => { setItems(page.items); setError(null); })
      .catch((failure) => setError(failure instanceof SessionApiError ? failure : null))
      .finally(() => setLoading(false));
  }, []);
  useEffect(() => { load(); }, [load]);
  const revoke = (item: SessionItem) => {
    setLoading(true);
    revokeSession(item.sessionRef)
      .then(() => { Message.success(t['sessions.revoked']); return listSessions(); })
      .then((page) => { setItems(page.items); setError(null); })
      .catch((failure) => setError(failure instanceof SessionApiError ? failure : null))
      .finally(() => setLoading(false));
  };
  return (
    <Card title={t['sessions.title']} extra={<Button size="small" loading={loading} onClick={load}>{t['sessions.refresh']}</Button>}>
      <Typography.Paragraph>{t['sessions.description']}</Typography.Paragraph>
      {error && <Alert type="error" title={error.code === 'SUMMER_SESSION_STORE_UNAVAILABLE' ? t['sessions.unavailable'] : t['sessions.error']} content={<Space direction="vertical"><span>{error.message || t['sessions.error']}</span>{error.requestId && <span>{t['sessions.requestId']}: {error.requestId}</span>}<Button size="mini" onClick={load}>{t['sessions.retry']}</Button></Space>} style={{ marginBottom: 16 }} />}
      <Table<SessionItem> rowKey="sessionRef" loading={loading} data={items} columns={[
        { title: t['sessions.principal'], dataIndex: 'principal' },
        { title: t['sessions.client'], dataIndex: 'clientId' },
        { title: t['sessions.status'], render: (_, item) => <Tag color={item.status === 'ACTIVE' ? 'green' : 'gray'}>{item.status === 'ACTIVE' && item.current ? t['sessions.current'] : item.status}</Tag> },
        { title: t['sessions.lastSeen'], dataIndex: 'lastSeenAt' },
        { title: t['sessions.expires'], dataIndex: 'expiresAt' },
        { title: t['sessions.actions'], render: (_, item) => item.status === 'ACTIVE' ? <Button size="small" status="danger" onClick={() => revoke(item)}>{t['sessions.revoke']}</Button> : null },
      ]} />
    </Card>
  );
}
