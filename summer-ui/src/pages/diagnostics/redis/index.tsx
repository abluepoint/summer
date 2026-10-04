import React, { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Space,
  Spin,
  Tag,
  Typography,
} from '@arco-design/web-react';
import useLocale from '@/utils/useLocale';
import { RedisDiagnostics, RedisDiagnosticsError, getRedisDiagnostics } from './api';

function statusColor(status: RedisDiagnostics['status']) {
  if (status === 'UP') return 'green';
  if (status === 'AUTHENTICATION_FAILED') return 'orange';
  return 'red';
}

function value(value: number | null) {
  return value == null ? '—' : String(value);
}

export default function RedisDiagnosticsPage() {
  const t = useLocale();
  const [data, setData] = useState<RedisDiagnostics | null>(null);
  const [error, setError] = useState<RedisDiagnosticsError | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(() => {
    setLoading(true);
    getRedisDiagnostics()
      .then((result) => {
        setData(result);
        setError(null);
      })
      .catch((failure) => {
        if (failure instanceof RedisDiagnosticsError) setError(failure);
        else setError(new RedisDiagnosticsError(0, '', '', ''));
        setData(null);
      })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => load(), [load]);

  if (loading && !data) return <Spin />;
  if (error) {
    return (
      <Alert
        type="error"
        title={
          error.status === 403
            ? t['diagnostics.redis.forbidden']
            : t['diagnostics.redis.error']
        }
        content={
          <Space direction="vertical">
            <Typography.Text>
              {error.message || t['diagnostics.redis.error']}
            </Typography.Text>
            {error.requestId && (
              <Typography.Text>requestId: {error.requestId}</Typography.Text>
            )}
            <Button size="mini" onClick={load}>
              {t['diagnostics.redis.retry']}
            </Button>
          </Space>
        }
      />
    );
  }
  if (!data) return null;

  return (
    <Card
      title={t['diagnostics.redis.title']}
      extra={
        <Button size="small" loading={loading} onClick={load}>
          {t['diagnostics.redis.refresh']}
        </Button>
      }
    >
      <Descriptions
        column={1}
        border
        data={[
          {
            label: t['diagnostics.redis.status'],
            value: (
              <Tag color={statusColor(data.status)}>
                {t[`diagnostics.redis.status.${data.status}`] || data.status}
              </Tag>
            ),
          },
          { label: t['diagnostics.redis.reason'], value: data.reason || '—' },
          { label: t['diagnostics.redis.message'], value: data.message },
          { label: t['diagnostics.redis.latency'], value: `${data.latencyMs} ms` },
          { label: t['diagnostics.redis.checkedAt'], value: data.checkedAt },
        ]}
      />
      <Descriptions
        column={1}
        border
        title={t['diagnostics.redis.metrics']}
        style={{ marginTop: 20 }}
        data={[
          {
            label: t['diagnostics.redis.uptime'],
            value: value(data.metrics.uptimeSeconds),
          },
          {
            label: t['diagnostics.redis.clients'],
            value: value(data.metrics.connectedClients),
          },
          {
            label: t['diagnostics.redis.memory'],
            value: value(data.metrics.usedMemoryBytes),
          },
          {
            label: t['diagnostics.redis.commands'],
            value: value(data.metrics.totalCommandsProcessed),
          },
        ]}
      />
      <Alert
        type="info"
        content={t['diagnostics.redis.endpointNotice']}
        style={{ marginTop: 20 }}
      />
    </Card>
  );
}
