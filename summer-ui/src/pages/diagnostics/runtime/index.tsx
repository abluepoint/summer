import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Descriptions, Space, Spin, Table, Tag, Typography } from '@arco-design/web-react';
import useLocale from '@/utils/useLocale';
import { getRuntimeDiagnostics, RuntimeDiagnostics, RuntimeDiagnosticsError, RuntimeModuleMetrics } from './api';

function statusColor(status: string) {
  if (status === 'UP' || status === 'AVAILABLE') return 'green';
  if (['NOT_CONNECTED', 'NOT_ENABLED', 'NOT_APPLIED'].includes(status)) return 'gray';
  return ['DOWN', 'VALIDATION_FAILED', 'UNAVAILABLE'].includes(status) ? 'red' : 'orange';
}

export default function RuntimeDiagnosticsPage() {
  const t = useLocale();
  const [data, setData] = useState<RuntimeDiagnostics | null>(null);
  const [error, setError] = useState<RuntimeDiagnosticsError | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(() => {
    setLoading(true);
    getRuntimeDiagnostics()
      .then((result) => { setData(result); setError(null); })
      .catch((failure) => {
        setError(failure instanceof RuntimeDiagnosticsError ? failure : new RuntimeDiagnosticsError(0, '', '', ''));
        setData(null);
      })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => load(), [load]);

  if (loading && !data) return <Spin />;
  if (error) {
    return <Alert type="error" title={error.status === 403 ? t['diagnostics.runtime.forbidden'] : t['diagnostics.runtime.error']} content={<Space direction="vertical"><Typography.Text>{error.message || t['diagnostics.runtime.error']}</Typography.Text>{error.requestId && <Typography.Text>requestId: {error.requestId}</Typography.Text>}{error.traceId && <Typography.Text>traceId: {error.traceId}</Typography.Text>}<Button size="mini" onClick={load}>{t['diagnostics.runtime.retry']}</Button></Space>} />;
  }
  if (!data) return null;

  const http = data.metrics.http;
  const number = (value: number | null | undefined, digits = 0) =>
    typeof value === 'number' && Number.isFinite(value) ? value.toFixed(digits) : '—';
  const moduleCard = (name: string, module: RuntimeModuleMetrics | undefined, fields: string[]) => (
    <Card key={name} title={t[`diagnostics.runtime.metrics.${name}`]} size="small">
      <Tag color={statusColor(module?.status || 'NOT_CONNECTED')}>
        {t[`diagnostics.runtime.status.${module?.status || 'NOT_CONNECTED'}`] || module?.status}
      </Tag>
      <Descriptions column={1} style={{ marginTop: 12 }} data={fields.map((field) => ({
        label: t[`diagnostics.runtime.metric.${name}.${field}`] || t[`diagnostics.runtime.metric.${field}`], value: number(module?.values[field]),
      }))} />
      <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
        {t[`diagnostics.runtime.window.${name}`]}
      </Typography.Paragraph>
      {name === 'audit' && module?.checkedAt && <Typography.Text type="secondary">{module.checkedAt}</Typography.Text>}
    </Card>
  );

  return <Card title={t['diagnostics.runtime.title']} extra={<Button size="small" loading={loading} onClick={load}>{t['diagnostics.runtime.refresh']}</Button>}>
    <Descriptions column={1} border data={[
      { label: t['diagnostics.runtime.requestId'], value: data.requestId },
      { label: 'traceId', value: data.traceId || '—' },
      { label: t['diagnostics.runtime.checkedAt'], value: data.checkedAt },
      { label: t['diagnostics.runtime.meterCount'], value: String(data.metrics.registeredMeters) },
    ]} />
    <Table
      rowKey="name"
      style={{ marginTop: 20 }}
      pagination={false}
      columns={[
        { title: t['diagnostics.runtime.component'], dataIndex: 'name', render: (value: string) => t[`diagnostics.runtime.component.${value}`] || value },
        { title: t['diagnostics.runtime.status'], dataIndex: 'status', render: (value: string) => <Tag color={statusColor(value)}>{t[`diagnostics.runtime.status.${value}`] || value}</Tag> },
      ]}
      data={data.components}
    />
    <Card title={t['diagnostics.runtime.metrics.http']} size="small" style={{ marginTop: 20 }}>
      <Descriptions column={2} data={[
        { label: t['diagnostics.runtime.metric.requests'], value: number(http?.requests) },
        { label: t['diagnostics.runtime.metric.meanLatencyMs'], value: `${number(http?.meanLatencyMs, 2)} ms` },
        { label: t['diagnostics.runtime.metric.errorRate'], value: http?.errorRate == null ? '—' : `${number(http.errorRate * 100, 2)}%` },
        { label: t['diagnostics.runtime.metric.clientErrors'], value: number(http?.clientErrors) },
        { label: t['diagnostics.runtime.metric.serverErrors'], value: number(http?.serverErrors) },
        { label: t['diagnostics.runtime.metric.processStartedAt'], value: http?.processStartedAt || '—' },
      ]} />
      <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>{t['diagnostics.runtime.window.http']}</Typography.Paragraph>
    </Card>
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 16, marginTop: 16 }}>
      {moduleCard('audit', data.metrics.audit, ['success', 'failure', 'total'])}
      {moduleCard('authentication', data.metrics.authentication, ['attempts'])}
      {moduleCard('cache', data.metrics.cache, ['requests', 'hits', 'loads', 'redisFailures', 'localEntries'])}
      {moduleCard('realtime', data.metrics.realtime, ['connections'])}
    </div>
    <Alert type="info" content={t['diagnostics.runtime.sanitized']} style={{ marginTop: 20 }} />
  </Card>;
}
