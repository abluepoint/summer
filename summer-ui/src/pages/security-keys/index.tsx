import AppLoading from '@/components/AppLoading';
import React, { useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  InputNumber,
  Message,
  Space,
  Table,
  Tag,
  Typography,
} from '@arco-design/web-react';
import useLocale from '@/utils/useLocale';
import { listSecurityKeyState, rotateSecurityKey, SecurityKey, SecurityKeyRotationSchedule } from './api';
import { SummerApiError } from '../customer/api';

export default function SecurityKeysPage() {
  const t = useLocale();
  const [items, setItems] = useState<SecurityKey[]>([]);
  const [rotation, setRotation] = useState<SecurityKeyRotationSchedule>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<SummerApiError>();
  const [retentionDays, setRetentionDays] = useState(30);
  const [rotating, setRotating] = useState(false);
  const [rotationError, setRotationError] = useState<SummerApiError>();
  const load = () => {
    setLoading(true);
    setError(undefined);
    return listSecurityKeyState()
      .then((state) => { setItems(state.items); setRotation(state.rotation); })
      .catch((failure) => {
        setError(
          failure instanceof SummerApiError
            ? failure
            : new SummerApiError({
                status: 0,
                code: '',
                message: t['securityKeys.error'],
                violations: [],
                requestId: '',
                timestamp: '',
              })
        );
      })
      .finally(() => setLoading(false));
  };
  useEffect(() => {
    void load();
  }, []);
  const rotate = async () => {
    const expectedVersion = Math.max(...items.map((item) => item.version), 0);
    setRotating(true);
    setRotationError(undefined);
    try {
      await rotateSecurityKey(retentionDays, expectedVersion);
      Message.success(t['securityKeys.rotateSuccess']);
      await load();
    } catch (failure) {
      setRotationError(
        failure instanceof SummerApiError
          ? failure
          : new SummerApiError({
              status: 0,
              code: '',
              message: t['securityKeys.rotateError'],
              violations: [],
              requestId: '',
              timestamp: '',
            })
      );
    } finally {
      setRotating(false);
    }
  };
  if (loading) return <AppLoading fullScreen={false} />;
  if (error)
    return (
      <Alert
        type="error"
        title={
          error.status === 403
            ? t['securityKeys.forbidden']
            : t['securityKeys.error']
        }
        content={
          <>
            <Typography.Text>
              {error.message || t['securityKeys.error']}
            </Typography.Text>
            {error.requestId && (
              <Typography.Text> requestId: {error.requestId}</Typography.Text>
            )}
          </>
        }
      />
    );
  return (
    <Card title={t['securityKeys.title']}>
      {rotation && <Alert
        type={rotation.status === 'FAILED' ? 'error' : 'info'}
        content={`${t['securityKeys.automaticStatus']}: ${rotation.status} · ${t['securityKeys.nextRotation']}: ${rotation.nextRotationAt || '—'}${rotation.failureCount ? ` · ${t['securityKeys.failures']}: ${rotation.failureCount}` : ''}`}
        style={{ marginBottom: 16 }}
      />}
      <Space style={{ marginBottom: 16 }}>
        <Typography.Text>{t['securityKeys.retention']}</Typography.Text>
        <InputNumber
          min={1}
          max={365}
          value={retentionDays}
          onChange={(value) => setRetentionDays(Number(value) || 1)}
        />
        <Button
          type="primary"
          loading={rotating}
          disabled={items.length === 0}
          onClick={rotate}
        >
          {t['securityKeys.rotate']}
        </Button>
      </Space>
      {rotationError && (
        <Alert
          type="error"
          title={
            rotationError.status === 412
              ? t['securityKeys.stale']
              : t['securityKeys.rotateError']
          }
          content={
            <>
              <Typography.Text>{rotationError.message}</Typography.Text>
              {rotationError.requestId && (
                <Typography.Text> requestId: {rotationError.requestId}</Typography.Text>
              )}
            </>
          }
          style={{ marginBottom: 16 }}
        />
      )}
      <Table<SecurityKey>
        rowKey="kid"
        data={items}
        columns={[
          { title: t['securityKeys.source'], dataIndex: 'source' },
          { title: t['securityKeys.kid'], dataIndex: 'kid' },
          { title: t['securityKeys.algorithm'], dataIndex: 'algorithm' },
          { title: t['securityKeys.version'], dataIndex: 'version' },
          {
            title: t['securityKeys.status'],
            dataIndex: 'status',
            render: (value) => <Tag color="green">{value}</Tag>,
          },
          { title: t['securityKeys.health'], dataIndex: 'health' },
          { title: t['securityKeys.graceUntil'], dataIndex: 'graceUntil' },
        ]}
      />
    </Card>
  );
}
