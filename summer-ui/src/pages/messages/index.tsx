import React, { useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Input, Message, Select, Space, Table, Tag, Typography } from '@arco-design/web-react';
import useLocale from '@/utils/useLocale';
import useActiveEnterprise from '@/pages/data-scopes/useActiveEnterprise';
import { getAuthenticatedUser, oidcManager } from '@/auth/oidc';
import { hasEnterpriseCapability } from '@/auth/businessCapabilities';
import { useOperationHints } from '@/auth/interfaceOperations';
import { useInterfaceOperations } from '@/auth/useInterfaceOperations';
import type { User } from 'oidc-client-ts';
import { acknowledgeMessage, connectRealtime, listMessages, RealtimeError, RealtimeMessage, RealtimeSocket } from './api';

export default function MessagesPage() {
  const tenant = useActiveEnterprise();
  return <><Select aria-label="活动租户" placeholder="选择活动租户" value={tenant.enterpriseId || undefined}
    options={tenant.options} onChange={tenant.change} disabled={tenant.busy} style={{ width: 280, marginBottom: 16 }} />
    {tenant.enterpriseId && <MessagesBody key={tenant.enterpriseId} enterpriseId={tenant.enterpriseId}
      onDirtyChange={tenant.setDirty} />}</>;
}

function MessagesBody({ enterpriseId, onDirtyChange }: { enterpriseId: string; onDirtyChange: (dirty: boolean) => void }) {
  const t = useLocale();
  const [items, setItems] = useState<RealtimeMessage[]>([]);
  const [text, setText] = useState('');
  const [error, setError] = useState<RealtimeError | null>(null);
  const [state, setState] = useState<'CONNECTING' | 'OPEN' | 'RECONNECTING' | 'CLOSED'>('CONNECTING');
  const [legacyCanWrite, setLegacyCanWrite] = useState(false);
  const [legacyCanRead, setLegacyCanRead] = useState(false);
  const { engineMode } = useOperationHints();
  const operations = useInterfaceOperations({ kind: 'ENTERPRISE', id: enterpriseId }, ['messages.send', 'messages.ack'], engineMode === 'MANAGED');
  const canWrite = engineMode === 'LEGACY' ? legacyCanWrite && legacyCanRead : engineMode === 'MANAGED' && operations['messages.send'] === true;
  const canAcknowledge = engineMode === 'LEGACY' ? legacyCanWrite : engineMode === 'MANAGED' && operations['messages.ack'] === true;
  const [sending, setSending] = useState(false);
  const pending = useRef<{ id: string; text: string }>();
  const socket = useRef<RealtimeSocket>();
  const merge = (current: RealtimeMessage[], received: RealtimeMessage[]) =>
    Array.from(new Map([...current, ...received].map(item => [item.messageId, item])).values())
      .sort((first, second) => first.sequence - second.sequence);
  const refresh = () => listMessages(enterpriseId).then((page) => { setItems(current => merge(current, page.items)); setError(null); }).catch(setError);
  useEffect(() => { onDirtyChange(Boolean(text)); }, [text, onDirtyChange]);
  useEffect(() => {
    let alive = true;
    const update = (user: User | null) => { if (alive) {
      setLegacyCanWrite(hasEnterpriseCapability(user, enterpriseId, 'enterprise.messages.write') && user?.scopes.includes('enterprise.messages.write') === true);
      setLegacyCanRead(hasEnterpriseCapability(user, enterpriseId, 'enterprise.messages.read') && user?.scopes.includes('enterprise.messages.read') === true);
    } };
    getAuthenticatedUser().then(update).catch(() => update(null));
    const unload = () => update(null);
    oidcManager.events.addUserLoaded(update); oidcManager.events.addUserUnloaded(unload);
    return () => { alive = false; oidcManager.events.removeUserLoaded(update); oidcManager.events.removeUserUnloaded(unload); };
  }, [enterpriseId]);
  useEffect(() => {
    let alive = true;
    listMessages(enterpriseId).then((page) => {
      if (!alive) return;
      setItems(page.items);
      socket.current = connectRealtime(enterpriseId, page.latestSequence, (event) => {
        if (!alive) return;
        if (event.type === 'message' && event.message) setItems(current => merge(current, [event.message]));
        if (event.type === 'error') {
          setSending(false);
          setError({ status: 0, code: event.code || 'SUMMER_MESSAGE_INVALID', message: `消息未确认，请检查连接或输入后重试。${event.code || ''}`, requestId: '' });
        }
        const confirmedId = event.type === 'message' ? event.message?.clientMessageId : event.type === 'ack' ? event.clientMessageId : undefined;
        if (pending.current && confirmedId === pending.current.id) {
          const confirmed = pending.current.text;
          pending.current = undefined; setSending(false); setError(null);
          setText(current => current.trim() === confirmed ? '' : current);
        }
      }, setState);
    }).catch(e => { if (alive) setError(e); });
    return () => { alive = false; socket.current?.close(); };
  }, [enterpriseId]);
  const send = () => {
    const trimmed = text.trim();
    if (!trimmed) { Message.warning(t['messages.required']); return; }
    if (!canWrite || sending) return;
    if (new TextEncoder().encode(JSON.stringify({ text: trimmed })).length > 64 * 1024) {
      setError({ status: 400, code: 'SUMMER_MESSAGE_INVALID', message: '消息内容超过 64 KiB，请缩短后重试。', requestId: '' }); return;
    }
    const id = pending.current?.text === trimmed ? pending.current.id : `ui-${Array.from(crypto.getRandomValues(new Uint8Array(16)), value => value.toString(16).padStart(2, '0')).join('')}`;
    pending.current = { id, text: trimmed };
    if (socket.current?.send({ type: 'send', clientMessageId: id, payload: { text: trimmed } })) { setSending(true); setError(null); }
    else setError({ status: 0, code: 'SUMMER_MESSAGE_CONNECTION_LOST', message: '连接尚未就绪，消息已保留，请稍后重试。', requestId: '' });
  };
  return <Card title={t['messages.title']} extra={<Space><Tag color={state === 'OPEN' ? 'green' : 'orange'}>{state}</Tag><Button onClick={refresh}>{t['messages.refresh']}</Button></Space>}>
    {error && <Alert type="error" content={`${error.message} ${error.requestId ? `(${error.requestId})` : ''}`} style={{ marginBottom: 16 }} />}
    {canWrite && <Space style={{ marginBottom: 16 }}><Input value={text} onChange={setText} placeholder={t['messages.placeholder']} onPressEnter={send} /><Button type="primary" loading={sending} disabled={state !== 'OPEN'} onClick={send}>{t['messages.send']}</Button></Space>}
    <Typography.Text type="secondary" style={{ display: 'block', marginBottom: 12 }}>{t['messages.description']}</Typography.Text>
    <Table<RealtimeMessage> rowKey="messageId" data={items} pagination={{ pageSize: 10 }} columns={[
      { title: t['messages.sequence'], dataIndex: 'sequence' },
      { title: t['messages.sender'], dataIndex: 'sender' },
      { title: t['messages.payload'], render: (_, item) => String(item.payload?.text || '') },
      { title: t['messages.status'], dataIndex: 'status' },
      { title: t['messages.actions'], render: (_, item) => item.status === 'ACKED' || !canAcknowledge ? null : <Button size="small" onClick={() => acknowledgeMessage(enterpriseId, item.messageId).then((updated) => setItems((current) => current.map((entry) => entry.messageId === updated.messageId ? updated : entry))).catch(setError)}>{t['messages.ack']}</Button> },
    ]} />
  </Card>;
}
