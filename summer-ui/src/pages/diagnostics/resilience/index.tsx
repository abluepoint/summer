import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Descriptions, Space, Spin, Table, Tag, Typography } from '@arco-design/web-react';
import useLocale from '@/utils/useLocale';
import { getResilienceDiagnostics, ResilienceDiagnostics, ResilienceDiagnosticsError } from './api';

export default function ResilienceDiagnosticsPage() {
  const t = useLocale();
  const [data, setData] = useState<ResilienceDiagnostics | null>(null);
  const [error, setError] = useState<ResilienceDiagnosticsError | null>(null);
  const [loading, setLoading] = useState(true);
  const load = useCallback(() => {
    setLoading(true);
    getResilienceDiagnostics().then((value) => { setData(value); setError(null); })
      .catch((failure) => setError(failure instanceof ResilienceDiagnosticsError ? failure : new ResilienceDiagnosticsError(0, '', '', '')))
      .finally(() => setLoading(false));
  }, []);
  useEffect(() => load(), [load]);
  if (loading && !data) return <Spin />;
  if (error) return <Alert type="error" title={error.status === 403 ? t['diagnostics.resilience.forbidden'] : t['diagnostics.resilience.error']} content={<Space direction="vertical"><Typography.Text>{error.message}</Typography.Text>{error.requestId && <Typography.Text>requestId: {error.requestId}</Typography.Text>}<Button size="mini" onClick={load}>{t['diagnostics.resilience.retry']}</Button></Space>} />;
  if (!data) return null;
  return <Card title={t['diagnostics.resilience.title']} extra={<Button size="small" loading={loading} onClick={load}>{t['diagnostics.resilience.refresh']}</Button>}>
    <Descriptions column={1} border data={[{ label: t['diagnostics.resilience.requestId'], value: data.requestId }, { label: t['diagnostics.resilience.checkedAt'], value: data.checkedAt }]} />
    <Table rowKey="name" style={{ marginTop: 20 }} pagination={false} columns={[
      { title: t['diagnostics.resilience.operation'], dataIndex: 'name' },
      { title: t['diagnostics.resilience.circuit'], dataIndex: 'circuitBreaker', render: (value: string) => <Tag color={value === 'CLOSED' ? 'green' : value === 'OPEN' ? 'red' : 'orange'}>{value}</Tag> },
      { title: t['diagnostics.resilience.calls'], dataIndex: 'calls' },
      { title: t['diagnostics.resilience.retry'], dataIndex: 'retryAttempts' },
      { title: t['diagnostics.resilience.failures'], dataIndex: 'failures' },
      { title: t['diagnostics.resilience.fallbacks'], dataIndex: 'fallbacks' },
      { title: t['diagnostics.resilience.rejected'], dataIndex: 'rejected' },
      { title: t['diagnostics.resilience.timedOut'], dataIndex: 'timedOut' },
    ]} data={data.operations} />
  </Card>;
}
