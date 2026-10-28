import React, { useEffect, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  Modal,
  Message,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from '@arco-design/web-react';
import useLocale from '@/utils/useLocale';
import {
  createOAuthClient,
  listOAuthClients,
  listOAuthConsents,
  OAuthClient,
  OAuthConsent,
  revokeOAuthConsent,
  setOAuthClientStatus,
  updateOAuthClient,
} from './api';
import { SummerApiError } from '@/api/errors';
import locale from './locale';

export default function OAuthClientsPage() {
  const t = { ...useLocale(), ...useLocale(locale) };
  const [clients, setClients] = useState<OAuthClient[]>([]);
  const [consents, setConsents] = useState<OAuthConsent[]>([]);
  const [error, setError] = useState('');
  const [visible, setVisible] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editing, setEditing] = useState<OAuthClient | null>(null);
  const [formError, setFormError] = useState('');
  const [busy, setBusy] = useState<string[]>([]);
  const savingRef = useRef(false);
  const busyRef = useRef(new Set<string>());
  const initialValues = useRef('');
  const [form] = Form.useForm();
  const failureText = (failure: unknown) =>
    failure instanceof SummerApiError
      ? `${failure.message}${
          failure.requestId ? ` (${failure.requestId})` : ''
        }`
      : t['oauthClients.error'];
  const open = (client: OAuthClient | null) => {
    setEditing(client);
    setFormError('');
    form.resetFields();
    const value = client
      ? {
          clientId: client.clientId,
          clientName: client.clientName,
          redirectUris: client.redirectUris.join('\n'),
          scopes: client.scopes.join(' '),
        }
      : {};
    form.setFieldsValue(value);
    initialValues.current = JSON.stringify(form.getFieldsValue());
    setVisible(true);
  };
  const cancel = () => {
    if (savingRef.current) return;
    if (JSON.stringify(form.getFieldsValue()) !== initialValues.current) {
      Modal.confirm({
        title: t['oauthClients.discard'],
        content: t['oauthClients.discardDetail'],
        okText: t['oauthClients.discardOk'],
        cancelText: t['oauthClients.cancel'],
        onOk: () => setVisible(false),
      });
    } else setVisible(false);
  };
  const run = async (key: string, action: () => Promise<void>) => {
    if (busyRef.current.has(key)) return;
    busyRef.current.add(key);
    setBusy(Array.from(busyRef.current));
    try {
      await action();
      await load();
    } catch (failure) {
      setError(failureText(failure));
    } finally {
      busyRef.current.delete(key);
      setBusy(Array.from(busyRef.current));
    }
  };
  const load = async () => {
    try {
      setError('');
      const [nextClients, nextConsents] = await Promise.all([
        listOAuthClients(),
        listOAuthConsents(),
      ]);
      setClients(nextClients);
      setConsents(nextConsents);
    } catch (failure) {
      setError(failureText(failure));
    }
  };
  useEffect(() => {
    void load();
  }, []);
  const save = async () => {
    if (savingRef.current) return;
    savingRef.current = true;
    let value;
    try {
      value = await form.validate();
    } catch {
      savingRef.current = false;
      return;
    }
    setSaving(true);
    setFormError('');
    try {
      const metadata = {
        clientName: value.clientName.trim(),
        redirectUris: value.redirectUris
          .split('\n')
          .map((v: string) => v.trim())
          .filter(Boolean),
        scopes: value.scopes
          .split(/[,\s]+/)
          .map((v: string) => v.trim())
          .filter(Boolean),
      };
      if (editing)
        await updateOAuthClient(editing.clientId, {
          ...metadata,
          version: editing.version,
        });
      else
        await createOAuthClient({
          ...metadata,
          clientId: value.clientId.trim(),
          publicClient: true,
        });
      Message.success(
        t[editing ? 'oauthClients.saved' : 'oauthClients.created']
      );
      setVisible(false);
      form.resetFields();
      await load();
    } catch (failure) {
      setFormError(
        `${failureText(failure)}${
          failure instanceof SummerApiError && failure.status === 412
            ? ` — ${t['oauthClients.conflict']}`
            : ''
        }`
      );
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  };
  const changeStatus = (client: OAuthClient, enabled: boolean) =>
    run(client.clientId, async () => {
      await setOAuthClientStatus(
        client.clientId,
        enabled ? 'ACTIVE' : 'DISABLED',
        client.version
      );
      Message.success(t['oauthClients.saved']);
    });
  const revoke = (consent: OAuthConsent) =>
    run(`consent:${consent.clientId}:${consent.principalName}`, async () => {
      await revokeOAuthConsent(consent.clientId, consent.principalName);
      Message.success(t['oauthClients.revoked']);
    });
  return (
    <Card
      title={t['oauthClients.title']}
      extra={
        <Button type="primary" onClick={() => open(null)}>
          {t['oauthClients.create']}
        </Button>
      }
    >
      {error && (
        <Alert type="error" content={error} style={{ marginBottom: 16 }} />
      )}
      <Typography.Paragraph>
        {t['oauthClients.description']}
      </Typography.Paragraph>
      <Table<OAuthClient>
        rowKey="clientId"
        data={clients}
        columns={[
          { title: t['oauthClients.clientId'], dataIndex: 'clientId' },
          { title: t['oauthClients.name'], dataIndex: 'clientName' },
          {
            title: t['oauthClients.redirectUris'],
            dataIndex: 'redirectUris',
            render: (v) => v.join(', '),
          },
          {
            title: t['oauthClients.scopes'],
            dataIndex: 'scopes',
            render: (v) => v.join(', '),
          },
          {
            title: t['oauthClients.status'],
            dataIndex: 'status',
            render: (v) => (
              <Tag color={v === 'ACTIVE' ? 'green' : 'gray'}>{v}</Tag>
            ),
          },
          {
            title: t['oauthClients.actions'],
            render: (_, client) => (
              <Space>
                <Button
                  size="small"
                  disabled={busy.includes(client.clientId)}
                  onClick={() => open(client)}
                >
                  {t['oauthClients.edit']}
                </Button>
                <Switch
                  aria-label={`${client.clientId} ${t['oauthClients.status']}`}
                  loading={busy.includes(client.clientId)}
                  checked={client.status === 'ACTIVE'}
                  onChange={(checked) => void changeStatus(client, checked)}
                />
              </Space>
            ),
          },
        ]}
        pagination={false}
      />
      <Typography.Title heading={6} style={{ marginTop: 24 }}>
        {t['oauthClients.consents']}
      </Typography.Title>
      <Table<OAuthConsent>
        rowKey={(v) => `${v.clientId}:${v.principalName}`}
        data={consents}
        columns={[
          { title: t['oauthClients.clientId'], dataIndex: 'clientId' },
          { title: t['oauthClients.principal'], dataIndex: 'principalName' },
          {
            title: t['oauthClients.authorities'],
            dataIndex: 'authorities',
            render: (v) => v.join(', '),
          },
          {
            title: t['oauthClients.actions'],
            render: (_, consent) => (
              <Button
                size="small"
                loading={busy.includes(
                  `consent:${consent.clientId}:${consent.principalName}`
                )}
                onClick={() => void revoke(consent)}
              >
                {t['oauthClients.revoke']}
              </Button>
            ),
          },
        ]}
        pagination={false}
      />
      <Modal
        title={t[editing ? 'oauthClients.edit' : 'oauthClients.create']}
        visible={visible}
        confirmLoading={saving}
        okText={t['oauthClients.save']}
        cancelText={t['oauthClients.cancel']}
        cancelButtonProps={{ disabled: saving }}
        maskClosable={!saving}
        escToExit={!saving}
        closable={!saving}
        onOk={() => void save()}
        onCancel={cancel}
      >
        {formError && (
          <Alert
            type="error"
            content={formError}
            style={{ marginBottom: 16 }}
          />
        )}
        <Form form={form} layout="vertical">
          <Form.Item
            field="clientId"
            label={t['oauthClients.clientId']}
            rules={[{ required: true }]}
          >
            <Input disabled={!!editing} />
          </Form.Item>
          <Form.Item
            field="clientName"
            label={t['oauthClients.name']}
            rules={[{ required: true }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            field="redirectUris"
            label={t['oauthClients.redirectUris']}
            rules={[{ required: true }]}
          >
            <Input.TextArea placeholder={t['oauthClients.uriLines']} />
          </Form.Item>
          <Form.Item
            field="scopes"
            label={t['oauthClients.scopes']}
            rules={[{ required: true }]}
          >
            <Input placeholder={t['oauthClients.scopeWords']} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
