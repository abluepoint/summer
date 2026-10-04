import React, { useEffect, useState } from 'react';
import {
  Alert,
  Card,
  Spin,
  Table,
  Tag,
  Typography,
} from '@arco-design/web-react';
import useLocale from '@/utils/useLocale';
import { listSecurityKeys, SecurityKey } from './api';
import { SummerApiError } from '../customer/api';

export default function SecurityKeysPage() {
  const t = useLocale();
  const [items, setItems] = useState<SecurityKey[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<SummerApiError>();
  useEffect(() => {
    listSecurityKeys()
      .then(setItems)
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
  }, []);
  if (loading) return <Spin />;
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
      <Table<SecurityKey>
        rowKey="kid"
        data={items}
        columns={[
          { title: t['securityKeys.source'], dataIndex: 'source' },
          { title: t['securityKeys.kid'], dataIndex: 'kid' },
          { title: t['securityKeys.algorithm'], dataIndex: 'algorithm' },
          {
            title: t['securityKeys.status'],
            dataIndex: 'status',
            render: (value) => <Tag color="green">{value}</Tag>,
          },
          { title: t['securityKeys.health'], dataIndex: 'health' },
        ]}
      />
    </Card>
  );
}
