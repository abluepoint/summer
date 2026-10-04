import React, { useEffect, useState } from 'react';
import { Alert, Card, Spin, Table, Tag } from '@arco-design/web-react';
import { listSecurityKeys, SecurityKey } from './api';

export default function SecurityKeysPage() {
  const [items, setItems] = useState<SecurityKey[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  useEffect(() => {
    listSecurityKeys().then(setItems).catch((failure) => setError(failure?.response?.data?.error?.message || 'Unable to load signing keys')).finally(() => setLoading(false));
  }, []);
  if (loading) return <Spin />;
  if (error) return <Alert type="error" content={error} />;
  return <Card title="Signing keys">
    <Table<SecurityKey> rowKey="kid" data={items} columns={[
      { title: 'Source', dataIndex: 'source' },
      { title: 'kid', dataIndex: 'kid' },
      { title: 'Algorithm', dataIndex: 'algorithm' },
      { title: 'Status', dataIndex: 'status', render: (value) => <Tag color="green">{value}</Tag> },
      { title: 'Health', dataIndex: 'health' },
    ]} />
  </Card>;
}
