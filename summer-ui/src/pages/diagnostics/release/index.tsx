import React, { useEffect, useState } from 'react';
import { Alert, Button, Card, Descriptions, Space, Spin, Table, Tag, Typography } from '@arco-design/web-react';
import useLocale from '@/utils/useLocale';
import { CheckName, getReleaseReadiness, ReleaseReadiness, ReleaseReadinessError } from './api';
import locale from './locale';

export default function ReleaseReadinessPage() {
  const t = useLocale(locale);
  const [attempt, setAttempt] = useState(0);
  const [data, setData] = useState<ReleaseReadiness | null>(null);
  const [error, setError] = useState<ReleaseReadinessError | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setData(null);
    setError(null);
    getReleaseReadiness().then((result) => {
      if (active) setData(result);
    }).catch((failure) => {
      if (active) setError(failure instanceof ReleaseReadinessError ? failure : new ReleaseReadinessError(0, '', ''));
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [attempt]);

  return <Card title={t.title} extra={<Button loading={loading} onClick={() => setAttempt((value) => value + 1)}>{t.refresh}</Button>}>
    <Space direction="vertical" style={{ width: '100%' }}>
      <Alert type="info" content={t.boundary} />
      {loading && <Spin tip={t.checking} />}
      {!loading && error && <Alert type="error" title={error.status === 403 ? t.forbidden : t.error} content={
        <Space direction="vertical">
          {error.code && <Typography.Text>{error.code}</Typography.Text>}
          {error.requestId && <Typography.Text>requestId: {error.requestId}</Typography.Text>}
          <Button onClick={() => setAttempt((value) => value + 1)}>{t.retry}</Button>
        </Space>
      } />}
      {!loading && data && <>
        <Alert type={data.status === 'READY' ? 'success' : 'warning'} content={data.status === 'READY' ? t.ready : t.notReady} />
        <Descriptions column={1} data={[
          { label: 'requestId', value: data.requestId || '—' },
          { label: t.timestamp, value: data.timestamp || '—' },
        ]} />
        <Table rowKey="name" pagination={false} data={data.checks} columns={[
          { title: t.item, dataIndex: 'name', render: (name: CheckName) => t[name] },
          { title: t.result, dataIndex: 'passed', render: (passed: boolean) => <Tag color={passed ? 'green' : 'red'}>{passed ? t.passed : t.failed}</Tag> },
          { title: t.detail, dataIndex: 'detail' },
        ]} />
      </>}
      <Typography.Paragraph type="secondary">{t.external}</Typography.Paragraph>
    </Space>
  </Card>;
}
