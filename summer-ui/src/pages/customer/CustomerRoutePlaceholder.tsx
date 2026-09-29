import React from 'react';
import { Card, Space, Typography } from '@arco-design/web-react';
import useLocale from '@/utils/useLocale';

export type CustomerRouteMode = 'drawer' | 'modal' | 'page';

export default function CustomerRoutePlaceholder({
  mode,
}: {
  mode: CustomerRouteMode;
}) {
  const locale = useLocale();

  return (
    <Card title={locale['customer.baseline.title']}>
      <Typography.Paragraph>
        {locale['customer.baseline.description']}
      </Typography.Paragraph>
      <Space direction="vertical">
        <Typography.Text>
          {locale['customer.baseline.route']}: /customer/{mode}
        </Typography.Text>
        <Typography.Text>
          {locale['customer.baseline.status']}: {locale['customer.baseline.statusValue']}
        </Typography.Text>
      </Space>
    </Card>
  );
}
