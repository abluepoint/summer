import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Card, Empty, Grid, Space, Spin, Statistic, Typography } from '@arco-design/web-react';
import useLocale from '@/utils/useLocale';
import { getStatistics, Statistics, StatisticsError } from './api';

const enterpriseId = 'ent-acme';
const { Row, Col } = Grid;

function isEmpty(value: Statistics) {
  return [value.userCount, value.roleCount, value.interfaceCount, value.dictionaryCount, value.taskCount, value.auditEventCount].every((item) => item === 0);
}

export default function StatisticsPage() {
  const t = useLocale();
  const [data, setData] = useState<Statistics | null>(null);
  const [failure, setFailure] = useState<StatisticsError | null>(null);
  const [loading, setLoading] = useState(true);
  const load = useCallback(() => {
    setLoading(true);
    getStatistics(enterpriseId).then((value) => { setData(value); setFailure(null); }).catch((error) => {
      setData(null);
      setFailure(error instanceof StatisticsError ? error : new StatisticsError(0, '', '', ''));
    }).finally(() => setLoading(false));
  }, []);
  useEffect(() => load(), [load]);

  const cards = useMemo(() => data ? [
    ['statistics.users', data.userCount],
    ['statistics.roles', data.roleCount],
    ['statistics.interfaces', data.accessibleInterfaceCount],
    ['statistics.dictionaries', data.dictionaryCount],
    ['statistics.tasks', data.taskCount],
    ['statistics.auditEvents', data.auditEventCount],
  ] : [], [data]);

  if (loading && !data) return <Spin />;
  if (failure) {
    const forbidden = failure.status === 403 || failure.code === 'SUMMER_FORBIDDEN';
    return <Alert type={forbidden ? 'warning' : 'error'} title={forbidden ? t['statistics.forbidden'] : t['statistics.error']} content={<Space direction="vertical"><Typography.Text>{failure.message || (forbidden ? t['statistics.forbidden'] : t['statistics.error'])}</Typography.Text>{failure.requestId && <Typography.Text>requestId: {failure.requestId}</Typography.Text>}<Button size="mini" onClick={load}>{t['statistics.retry']}</Button></Space>} />;
  }
  if (!data) return null;

  return <Card title={t['statistics.title']} extra={<Button size="small" loading={loading} onClick={load}>{t['statistics.refresh']}</Button>}>
    <Typography.Paragraph>{t['statistics.window']}: {data.window.from} – {data.window.to}</Typography.Paragraph>
    {isEmpty(data) ? <Empty description={t['statistics.empty']} /> : <Row gutter={[16, 16]}>{cards.map(([label, value]) => <Col span={8} key={label}><Card bordered><Statistic title={t[label]} value={value} /></Card></Col>)}<Col span={8}><Card bordered><Statistic title={t['statistics.taskSuccessRate']} value={`${(data.taskSuccessRate * 100).toFixed(1)}%`} /></Card></Col></Row>}
  </Card>;
}
