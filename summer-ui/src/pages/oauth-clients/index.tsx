import React, { useEffect, useState } from 'react';
import { Alert, Button, Card, Form, Input, Modal, Message, Space, Switch, Table, Tag, Typography } from '@arco-design/web-react';
import useLocale from '@/utils/useLocale';
import { createOAuthClient, listOAuthClients, listOAuthConsents, OAuthClient, OAuthConsent, revokeOAuthConsent, setOAuthClientStatus } from './api';
import { SummerApiError } from '../customer/api';

export default function OAuthClientsPage() {
  const t = useLocale();
  const [clients, setClients] = useState<OAuthClient[]>([]);
  const [consents, setConsents] = useState<OAuthConsent[]>([]);
  const [error, setError] = useState('');
  const [visible, setVisible] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  const load = async () => {
    try {
      setError('');
      const [nextClients, nextConsents] = await Promise.all([listOAuthClients(), listOAuthConsents()]);
      setClients(nextClients);
      setConsents(nextConsents);
    } catch (failure) {
      setError(failure instanceof SummerApiError ? failure.message : t['oauthClients.error']);
    }
  };
  useEffect(() => { void load(); }, []);
  const create = async () => {
    const value = await form.validate();
    setSaving(true);
    try {
      await createOAuthClient({
        clientId: value.clientId.trim(),
        clientName: value.clientName.trim(),
        publicClient: true,
        redirectUris: value.redirectUris.split('\n').map((v: string) => v.trim()).filter(Boolean),
        scopes: value.scopes.split(/[,\s]+/).map((v: string) => v.trim()).filter(Boolean),
      });
      Message.success(t['oauthClients.created']);
      setVisible(false);
      form.resetFields();
      await load();
    } catch (failure) {
      setError(failure instanceof SummerApiError ? failure.message : t['oauthClients.error']);
    } finally { setSaving(false); }
  };
  const changeStatus = async (client: OAuthClient, enabled: boolean) => {
    try {
      await setOAuthClientStatus(client.clientId, enabled ? 'ACTIVE' : 'DISABLED', client.version);
      Message.success(t['oauthClients.saved']);
      await load();
    } catch (failure) {
      setError(failure instanceof SummerApiError ? failure.message : t['oauthClients.error']);
    }
  };
  const revoke = async (consent: OAuthConsent) => {
    try {
      await revokeOAuthConsent(consent.clientId, consent.principalName);
      Message.success(t['oauthClients.revoked']);
      await load();
    } catch (failure) {
      setError(failure instanceof SummerApiError ? failure.message : t['oauthClients.error']);
    }
  };
  return <Card title={t['oauthClients.title']} extra={<Button type="primary" onClick={() => setVisible(true)}>{t['oauthClients.create']}</Button>}>
    {error && <Alert type="error" content={error} style={{ marginBottom: 16 }} />}
    <Typography.Paragraph>{t['oauthClients.description']}</Typography.Paragraph>
    <Table<OAuthClient> rowKey="clientId" data={clients} columns={[
      { title: t['oauthClients.clientId'], dataIndex: 'clientId' },
      { title: t['oauthClients.name'], dataIndex: 'clientName' },
      { title: t['oauthClients.redirectUris'], dataIndex: 'redirectUris', render: (v) => v.join(', ') },
      { title: t['oauthClients.scopes'], dataIndex: 'scopes', render: (v) => v.join(', ') },
      { title: t['oauthClients.status'], dataIndex: 'status', render: (v) => <Tag color={v === 'ACTIVE' ? 'green' : 'gray'}>{v}</Tag> },
      { title: t['oauthClients.actions'], render: (_, client) => <Switch checked={client.status === 'ACTIVE'} onChange={(checked) => void changeStatus(client, checked)} /> },
    ]} pagination={false} />
    <Typography.Title heading={6} style={{ marginTop: 24 }}>{t['oauthClients.consents']}</Typography.Title>
    <Table<OAuthConsent> rowKey={(v) => `${v.clientId}:${v.principalName}`} data={consents} columns={[
      { title: t['oauthClients.clientId'], dataIndex: 'clientId' },
      { title: t['oauthClients.principal'], dataIndex: 'principalName' },
      { title: t['oauthClients.authorities'], dataIndex: 'authorities', render: (v) => v.join(', ') },
      { title: t['oauthClients.actions'], render: (_, consent) => <Button size="small" onClick={() => void revoke(consent)}>{t['oauthClients.revoke']}</Button> },
    ]} pagination={false} />
    <Modal title={t['oauthClients.create']} visible={visible} confirmLoading={saving} onOk={() => void create()} onCancel={() => setVisible(false)}>
      <Form form={form} layout="vertical">
        <Form.Item field="clientId" label={t['oauthClients.clientId']} rules={[{ required: true }]}><Input /></Form.Item>
        <Form.Item field="clientName" label={t['oauthClients.name']} rules={[{ required: true }]}><Input /></Form.Item>
        <Form.Item field="redirectUris" label={t['oauthClients.redirectUris']} rules={[{ required: true }]}><Input.TextArea placeholder="One URI per line" /></Form.Item>
        <Form.Item field="scopes" label={t['oauthClients.scopes']} rules={[{ required: true }]}><Input placeholder="openid profile" /></Form.Item>
      </Form>
    </Modal>
  </Card>;
}
