import React, { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Drawer,
  Form,
  Input,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
} from '@arco-design/web-react';
import useLocale from '@/utils/useLocale';
import { AuditApiError, AuditEvent, AuditPage, getAuditEvent, listAuditEvents } from './api';

const FormItem = Form.Item;

export default function AuditPageScreen() {
  const t = useLocale();
  const [filters, setFilters] = useState({ principal: '', resource: '', action: '', result: '', requestId: '' });
  const [page, setPage] = useState<AuditPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<AuditApiError | null>(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [detail, setDetail] = useState<AuditEvent | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  const load = useCallback(() => {
    setLoading(true);
    listAuditEvents({ page: currentPage - 1, size: 20, ...filters })
      .then((result) => { setPage(result); setError(null); })
      .catch((failure) => setError(failure instanceof AuditApiError ? failure : new AuditApiError(0, '', '', '')))
      .finally(() => setLoading(false));
  }, [currentPage, filters]);

  useEffect(() => load(), [load]);

  function openDetail(eventId: string) {
    setDetailLoading(true);
    getAuditEvent(eventId)
      .then(setDetail)
      .catch((failure) => {
        const message = failure instanceof AuditApiError ? failure.message : t['audit.error'];
        // Details are read-only; keep the list available when one row fails.
        window.console.error(message);
      })
      .finally(() => setDetailLoading(false));
  }

  if (loading && !page) return <Spin />;
  if (error && !page) {
    return <Alert type="error" title={error.status === 403 ? t['audit.forbidden'] : t['audit.error']} content={error.message || t['audit.error']} />;
  }

  return (
    <Card title={t['audit.title']} extra={<Button size="small" loading={loading} onClick={load}>{t['audit.refresh']}</Button>}>
      <Form layout="inline" style={{ marginBottom: 20 }}>
        <FormItem field="principal" label={t['audit.principal']}>
          <Input value={filters.principal} onChange={(value) => setFilters({ ...filters, principal: value })} />
        </FormItem>
        <FormItem field="resource" label={t['audit.resource']}>
          <Input value={filters.resource} onChange={(value) => setFilters({ ...filters, resource: value })} />
        </FormItem>
        <FormItem field="action" label={t['audit.action']}>
          <Input value={filters.action} onChange={(value) => setFilters({ ...filters, action: value })} />
        </FormItem>
        <FormItem field="result" label={t['audit.result']}>
          <Select value={filters.result} onChange={(value) => setFilters({ ...filters, result: value || '' })} allowClear style={{ width: 140 }}>
            <Select.Option value="SUCCESS">{t['audit.success']}</Select.Option>
            <Select.Option value="FAILURE">{t['audit.failure']}</Select.Option>
          </Select>
        </FormItem>
        <FormItem field="requestId" label={t['audit.requestId']}>
          <Input value={filters.requestId} onChange={(value) => setFilters({ ...filters, requestId: value })} />
        </FormItem>
        <Button type="primary" onClick={() => { setCurrentPage(1); load(); }}>{t['audit.search']}</Button>
      </Form>
      {error && <Alert type="error" content={error.message || t['audit.error']} style={{ marginBottom: 16 }} />}
      <Table<AuditEvent>
        rowKey="eventId"
        loading={loading}
        data={page?.items || []}
        pagination={{ current: currentPage, pageSize: page?.size || 20, total: page?.totalElements || 0, onChange: setCurrentPage }}
        columns={[
          { title: t['audit.time'], dataIndex: 'occurredAt' },
          { title: t['audit.principal'], dataIndex: 'principal' },
          { title: t['audit.resource'], dataIndex: 'resource' },
          { title: t['audit.action'], dataIndex: 'action' },
          { title: t['audit.result'], dataIndex: 'result', render: (value) => <Tag color={value === 'SUCCESS' ? 'green' : 'red'}>{value === 'SUCCESS' ? t['audit.success'] : t['audit.failure']}</Tag> },
          { title: t['audit.requestId'], dataIndex: 'requestId', render: (value) => value || '—' },
          { title: t['audit.details'], render: (_, record) => <Button type="text" size="small" onClick={() => openDetail(record.eventId)}>{t['audit.view']}</Button> },
        ]}
      />
      <Drawer title={t['audit.details']} visible={Boolean(detail) || detailLoading} onCancel={() => setDetail(null)} width={560} footer={null}>
        {detailLoading ? <Spin /> : detail && <Space direction="vertical" size="medium" style={{ width: '100%' }}>
          <Typography.Text>eventId: {detail.eventId}</Typography.Text>
          <Typography.Text>tenantId: {detail.tenantId}</Typography.Text>
          <Typography.Text>{t['audit.principal']}: {detail.principal}</Typography.Text>
          <Typography.Text>{t['audit.resource']}: {detail.resource}</Typography.Text>
          <Typography.Text>{t['audit.action']}: {detail.action}</Typography.Text>
          <Typography.Text>{t['audit.requestId']}: {detail.requestId || '—'}</Typography.Text>
          <Typography.Text>{t['audit.time']}: {detail.occurredAt}</Typography.Text>
          <Typography.Text>{t['audit.detailPayload']}: {JSON.stringify(detail.detail)}</Typography.Text>
        </Space>}
      </Drawer>
    </Card>
  );
}
