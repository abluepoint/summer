import React, { useEffect, useState } from 'react';
import { Alert, Button, Card, Descriptions, Form, Input, Message, Space, Table, Tag, Typography } from '@arco-design/web-react';
import useLocale from '@/utils/useLocale';
import { disablePlugin, enablePlugin, getCustomerInsights, getPluginDiagnostic, listEnterprisePlugins, listPlugins, Plugin, PluginApiError, PluginDiagnostic, PluginInsights, registerPlugin } from './api';

const enterpriseId = 'ent-acme';

export default function PluginsPage() {
  const t = useLocale();
  const [plugins, setPlugins] = useState<Plugin[]>([]);
  const [insights, setInsights] = useState<PluginInsights | null>(null);
  const [diagnostic, setDiagnostic] = useState<PluginDiagnostic | null>(null);
  const [error, setError] = useState<PluginApiError | null>(null);
  const [loading, setLoading] = useState(false);
  const [form] = Form.useForm();
  const load = () => { setLoading(true); Promise.all([listPlugins(), listEnterprisePlugins(enterpriseId)]).then(([platform, visible]) => { setPlugins(platform.items); setInsights(null); setError(null); if (!visible.items.length) return; }).catch(setError).finally(() => setLoading(false)); };
  useEffect(() => load(), []);
  const register = () => form.validate().then((values) => registerPlugin(values).then(() => { Message.success(t['plugins.registered']); form.resetFields(); load(); }).catch(setError));
  const toggle = (plugin: Plugin) => (plugin.status === 'ENABLED' ? disablePlugin(plugin.pluginId) : enablePlugin(plugin.pluginId)).then(() => { Message.success(t['plugins.saved']); load(); }).catch(setError);
  const inspect = (plugin: Plugin) => getPluginDiagnostic(plugin.pluginId).then(setDiagnostic).catch(setError);
  const readInsights = () => getCustomerInsights(enterpriseId).then(setInsights).catch(setError);
  return <Card title={t['plugins.title']} extra={<Button loading={loading} onClick={load}>{t['plugins.refresh']}</Button>}>
    <Typography.Paragraph>{t['plugins.description']}</Typography.Paragraph>
    {error && <Alert type="error" content={`${error.message || t['plugins.error']}${error.requestId ? ` (${error.requestId})` : ''}`} style={{ marginBottom: 16 }} />}
    <Form form={form} layout="inline" style={{ marginBottom: 20 }}>
      <Form.Item field="pluginId" rules={[{ required: true }]}><Input placeholder={t['plugins.pluginId']} /></Form.Item>
      <Form.Item field="version" rules={[{ required: true }]}><Input placeholder={t['plugins.version']} /></Form.Item>
      <Form.Item field="artifactDigest" rules={[{ required: true }]}><Input placeholder={t['plugins.digest']} /></Form.Item>
      <Button type="primary" onClick={register}>{t['plugins.register']}</Button>
    </Form>
    <Table<Plugin> rowKey="pluginId" loading={loading} data={plugins} pagination={false} columns={[
      { title: t['plugins.pluginId'], dataIndex: 'pluginId' }, { title: t['plugins.version'], dataIndex: 'version' },
      { title: t['plugins.status'], dataIndex: 'status', render: (value) => <Tag color={value === 'ENABLED' ? 'green' : value === 'INCOMPATIBLE' ? 'red' : 'gray'}>{value}</Tag> },
      { title: t['plugins.trusted'], dataIndex: 'trusted', render: (value) => value ? t['plugins.yes'] : t['plugins.no'] },
      { title: t['plugins.actions'], render: (_, plugin) => <Space><Button size="small" onClick={() => toggle(plugin)}>{plugin.status === 'ENABLED' ? t['plugins.disable'] : t['plugins.enable']}</Button><Button size="small" onClick={() => inspect(plugin)}>{t['plugins.diagnostic']}</Button>{plugin.status === 'ENABLED' && <Button size="small" onClick={readInsights}>{t['plugins.readExample']}</Button>}</Space> },
    ]} />
    {diagnostic && <Card title={t['plugins.diagnostic']} style={{ marginTop: 18 }}><Descriptions column={1} data={[{ label: t['plugins.status'], value: diagnostic.status }, { label: t['plugins.compatible'], value: String(diagnostic.compatible) }, { label: t['plugins.diagnostic'], value: diagnostic.diagnostic }, { label: t['plugins.problems'], value: diagnostic.problems.join('; ') || '-' }]} /></Card>}
    {insights && <Card title={t['plugins.exampleTitle']} style={{ marginTop: 18 }}><Descriptions column={1} data={[{ label: t['plugins.activeCustomers'], value: insights.activeCustomerCount }, { label: t['plugins.requestId'], value: insights.requestId }, { label: t['plugins.dictionary'], value: `${insights.dictionary.code} (${insights.dictionary.items.length})` }, { label: t['plugins.configuration'], value: insights.configuration.map((item) => String(item.key)).join(', ') }]} /></Card>}
  </Card>;
}
