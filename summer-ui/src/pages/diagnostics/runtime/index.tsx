import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Descriptions, Space, Spin, Table, Tag, Typography } from '@arco-design/web-react';
import useLocale from '@/utils/useLocale';
import { getRuntimeDiagnostics, RuntimeDiagnostics, RuntimeDiagnosticsError } from './api';

function statusColor(status: string) {
  if (status === 'UP') return 'green';
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
    return <Alert type="error" title={error.status === 403 ? t['diagnostics.runtime.forbidden'] : t['diagnostics.runtime.error']} content={<Space direction="vertical"><Typography.Text>{error.message || t['diagnostics.runtime.error']}</Typography.Text>{error.requestId && <Typography.Text>requestId: {error.requestId}</Typography.Text>}<Button size="mini" onClick={load}>{t['diagnostics.runtime.retry']}</Button></Space>} />;
  }
  if (!data) return null;

  return <Card title={t['diagnostics.runtime.title']} extra={<Button size="small" loading={loading} onClick={load}>{t['diagnostics.runtime.refresh']}</Button>}>
    <Descriptions column={1} border data={[
      { label: t['diagnostics.runtime.requestId'], value: data.requestId },
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
    <Alert type="info" content={t['diagnostics.runtime.sanitized']} style={{ marginTop: 20 }} />
  </Card>;
}
