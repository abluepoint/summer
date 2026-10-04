import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Spin, Table, Tag } from '@arco-design/web-react';
import useLocale from '@/utils/useLocale';
import {
  InterfaceResource,
  InterfaceResourceApiError,
  listInterfaceResources,
} from './api';

function typeColor(type: InterfaceResource['resourceType']) {
  if (type === 'BUSINESS') return 'blue';
  if (type === 'PROTOCOL') return 'orange';
  if (type === 'INFRASTRUCTURE') return 'green';
  return 'gray';
}

export default function InterfaceResourcesPage() {
  const t = useLocale();
  const [items, setItems] = useState<InterfaceResource[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<InterfaceResourceApiError | null>(null);

  const load = useCallback(() => {
    setLoading(true);
    listInterfaceResources()
      .then((catalog) => {
        setItems(catalog.items);
        setError(null);
      })
      .catch((failure) => {
        setError(
          failure instanceof InterfaceResourceApiError
            ? failure
            : new InterfaceResourceApiError(0, '', '', '')
        );
      })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => load(), [load]);

  if (loading && !items.length) return <Spin />;
  if (error && !items.length) {
    return (
      <Alert
        type="error"
        title={
          error.status === 403
            ? t['interfaceResources.forbidden']
            : t['interfaceResources.error']
        }
        content={error.message || t['interfaceResources.error']}
      />
    );
  }

  return (
    <Card
      title={t['interfaceResources.title']}
      extra={
        <Button size="small" loading={loading} onClick={load}>
          {t['interfaceResources.refresh']}
        </Button>
      }
    >
      {error && (
        <Alert
          type="error"
          content={error.message || t['interfaceResources.error']}
          style={{ marginBottom: 16 }}
        />
      )}
      <Table<InterfaceResource>
        rowKey={(record) => `${record.httpMethod}:${record.normalizedPath}`}
        loading={loading}
        data={items}
        pagination={{ pageSize: 20 }}
        columns={[
          { title: t['interfaceResources.method'], dataIndex: 'httpMethod' },
          { title: t['interfaceResources.path'], dataIndex: 'normalizedPath' },
          { title: t['interfaceResources.handler'], dataIndex: 'handler' },
          { title: t['interfaceResources.module'], dataIndex: 'module' },
          {
            title: t['interfaceResources.type'],
            dataIndex: 'resourceType',
            render: (value) => (
              <Tag color={typeColor(value)}>{value}</Tag>
            ),
          },
          { title: t['interfaceResources.permission'], dataIndex: 'permissionCode' },
          { title: t['interfaceResources.sensitivity'], dataIndex: 'sensitiveLevel' },
          {
            title: t['interfaceResources.configurable'],
            dataIndex: 'configurable',
            render: (value) =>
              value
                ? t['interfaceResources.yes']
                : t['interfaceResources.no'],
          },
        ]}
      />
    </Card>
  );
}
