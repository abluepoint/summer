import React, { useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Input, Message, Space, Table, Tag, Typography } from '@arco-design/web-react';
import useLocale from '@/utils/useLocale';
import { acknowledgeMessage, connectRealtime, listMessages, RealtimeError, RealtimeMessage, RealtimeSocket } from './api';

const enterpriseId = 'ent-acme';

export default function MessagesPage() {
  const t = useLocale();
  const [items, setItems] = useState<RealtimeMessage[]>([]);
  const [text, setText] = useState('');
  const [error, setError] = useState<RealtimeError | null>(null);
  const [state, setState] = useState<'CONNECTING' | 'OPEN' | 'RECONNECTING' | 'CLOSED'>('CONNECTING');
  const socket = useRef<RealtimeSocket>();
  const refresh = () => listMessages(enterpriseId).then((page) => setItems(page.items)).catch(setError);
  useEffect(() => {
    listMessages(enterpriseId).then((page) => {
      setItems(page.items);
      socket.current = connectRealtime(enterpriseId, page.latestSequence, (event) => {
        if (event.type === 'message' && event.message) setItems((current) => current.some((item) => item.messageId === event.message?.messageId) ? current : [...current, event.message]);
      }, setState);
    }).catch(setError);
    return () => socket.current?.close();
  }, []);
  const send = () => {
    const trimmed = text.trim();
    if (!trimmed) { Message.warning(t['messages.required']); return; }
    socket.current?.send({ type: 'send', clientMessageId: `ui-${Date.now()}`, payload: { text: trimmed } });
    setText('');
  };
  return <Card title={t['messages.title']} extra={<Space><Tag color={state === 'OPEN' ? 'green' : 'orange'}>{state}</Tag><Button onClick={refresh}>{t['messages.refresh']}</Button></Space>}>
    {error && <Alert type="error" content={`${error.message} ${error.requestId ? `(${error.requestId})` : ''}`} style={{ marginBottom: 16 }} />}
    <Space style={{ marginBottom: 16 }}><Input value={text} onChange={setText} placeholder={t['messages.placeholder']} onPressEnter={send} /><Button type="primary" onClick={send}>{t['messages.send']}</Button></Space>
    <Typography.Text type="secondary" style={{ display: 'block', marginBottom: 12 }}>{t['messages.description']}</Typography.Text>
    <Table<RealtimeMessage> rowKey="messageId" data={items} pagination={{ pageSize: 10 }} columns={[
      { title: t['messages.sequence'], dataIndex: 'sequence' },
      { title: t['messages.sender'], dataIndex: 'sender' },
      { title: t['messages.payload'], render: (_, item) => String(item.payload?.text || '') },
      { title: t['messages.status'], dataIndex: 'status' },
      { title: t['messages.actions'], render: (_, item) => item.status === 'ACKED' ? null : <Button size="small" onClick={() => acknowledgeMessage(enterpriseId, item.messageId).then((updated) => setItems((current) => current.map((entry) => entry.messageId === updated.messageId ? updated : entry))).catch(setError)}>{t['messages.ack']}</Button> },
    ]} />
  </Card>;
}
