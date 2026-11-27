import AppLoading from '@/components/AppLoading';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Empty, Grid, Select, Space, Statistic, Typography } from '@arco-design/web-react';
import useLocale from '@/utils/useLocale';
import useActiveEnterprise from '@/hooks/useActiveEnterprise';
import { getStatistics, Statistics, StatisticsError } from './api';
import locale from './locale';
const { Row, Col } = Grid;

export default function StatisticsPage() {
  const tenant = useActiveEnterprise();
  const t = useLocale(locale);
  return <><Select aria-label={t['statistics.tenant']} placeholder={t['statistics.chooseTenant']} value={tenant.enterpriseId || undefined} options={tenant.options} onChange={tenant.change} style={{ width: 240, marginBottom: 16 }} />
    {tenant.enterpriseId ? <StatisticsContent key={tenant.enterpriseId} enterpriseId={tenant.enterpriseId} /> : tenant.ready ? <Alert type="info" content={t['statistics.chooseTenant']} /> : <AppLoading fullScreen={false} />}</>;
}
function StatisticsContent({ enterpriseId }: { enterpriseId: string }) {
  const t = { ...useLocale(), ...useLocale(locale) };
  const [data, setData] = useState<Statistics | null>(null);
  const [failure, setFailure] = useState<StatisticsError | null>(null);
  const [loading, setLoading] = useState(true);
  const generation = useRef(0);
  const load = useCallback(() => {
    const current = ++generation.current;
    setLoading(true); setData(null); setFailure(null);
    getStatistics(enterpriseId).then((value) => { if (generation.current === current) setData(value); }).catch((error) => {
      if (generation.current === current) setFailure(error instanceof StatisticsError ? error : new StatisticsError(0, '', '', ''));
    }).finally(() => { if (generation.current === current) setLoading(false); });
  }, [enterpriseId]);
  useEffect(() => { load(); return () => { generation.current++; }; }, [load]);
  if (loading) return <AppLoading fullScreen={false} />;
  if (failure) {
    const forbidden = failure.status === 403 || failure.code === 'SUMMER_FORBIDDEN';
    return <Alert type={forbidden ? 'warning' : 'error'} title={forbidden ? t['statistics.forbidden'] : t['statistics.error']} content={<Space direction="vertical"><Typography.Text>{failure.message || (forbidden ? t['statistics.forbidden'] : t['statistics.error'])}</Typography.Text>{failure.requestId && <Typography.Text>requestId: {failure.requestId}</Typography.Text>}<Button size="mini" onClick={load}>{t['statistics.retry']}</Button></Space>} />;
  }
  if (!data) return null;
  const cards: [string, number | null][] = [
    ['statistics.users', data.userCount], ['statistics.roles', data.roleCount], ['statistics.interfaces', data.accessibleInterfaceCount],
    ['statistics.dictionaries', data.dictionaryCount], ['statistics.tasks', data.taskCount], ['statistics.auditEvents', data.auditEventCount],
  ];
  const business = [data.userCount, data.roleCount, data.dictionaryCount, data.taskCount, data.auditEventCount].filter((value) => value !== null);
  const empty = business.length > 0 && business.every((value) => value === 0);
  return <Card title={t['statistics.title']} extra={<Button size="small" onClick={load}>{t['statistics.refresh']}</Button>}>
    <Typography.Paragraph>{t['statistics.window']}: {data.window.from} – {data.window.to}</Typography.Paragraph>
    <Typography.Paragraph>{t['statistics.windowNote']}</Typography.Paragraph>
    <Typography.Paragraph>{t['statistics.snapshot']}: {data.asOf}</Typography.Paragraph>
    {business.length === 0 && <Alert type="warning" content={t['statistics.noMetrics']} />}
    {empty && <Empty description={t['statistics.empty']} />}
    <Row gutter={[16, 16]}>{cards.map(([label, value]) => <Col span={8} key={label}><Card bordered>{value === null ? <Space direction="vertical"><Typography.Text>{t[label]}</Typography.Text><Typography.Text type="secondary">{t['statistics.unavailable']}</Typography.Text></Space> : <Statistic title={t[label]} value={value} />}</Card></Col>)}
      <Col span={8}><Card bordered>{data.taskSuccessRate === null ? <Space direction="vertical"><Typography.Text>{t['statistics.taskSuccessRate']}</Typography.Text><Typography.Text type="secondary">{t['statistics.unavailable']}</Typography.Text></Space> : <Statistic title={t['statistics.taskSuccessRate']} value={`${(data.taskSuccessRate * 100).toFixed(1)}%`} />}</Card></Col>
    </Row>
    <Typography.Paragraph style={{ marginTop: 16 }}>{t['statistics.scopeNote']}</Typography.Paragraph>
    <Typography.Paragraph>{t['statistics.formula']}</Typography.Paragraph>
    <Typography.Paragraph>{t['statistics.interfaceNote']}</Typography.Paragraph>
  </Card>;
}
