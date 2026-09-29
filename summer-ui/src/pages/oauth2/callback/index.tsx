import React from 'react';
import { Card, Typography } from '@arco-design/web-react';
import useLocale from '@/utils/useLocale';

export default function OidcCallbackPlaceholder() {
  const locale = useLocale();

  return (
    <Card title={locale['oauth2.callback.title']}>
      <Typography.Paragraph>
        {locale['oauth2.callback.description']}
      </Typography.Paragraph>
    </Card>
  );
}
