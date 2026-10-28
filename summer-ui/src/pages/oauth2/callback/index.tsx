import React, { useEffect, useState } from 'react';
import { Alert, Button } from '@arco-design/web-react';
import { useHistory } from 'react-router-dom';
import { completeLoginCallback, clearTemporaryAuthState } from '@/auth/oidc';
import useLocale from '@/utils/useLocale';
import AppLoading from '@/components/AppLoading';

export default function OidcCallback() {
  const t = useLocale();
  const history = useHistory();
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const callbackUrl = window.location.href;
    completeLoginCallback(callbackUrl)
      .then(({ returnTo }) => {
        window.history.replaceState(null, '', '/oauth2/callback');
        history.replace(returnTo);
      })
      .catch(() => {
        window.history.replaceState(null, '', '/oauth2/callback');
        clearTemporaryAuthState();
        setFailed(true);
      });
  }, [history]);

  if (!failed) return <AppLoading />;
  return (
    <Alert
      type="error"
      content={t['oauth2.callback.failed']}
      action={
        <Button onClick={() => window.location.replace('/login')}>
          {t['oauth2.callback.backToLogin']}
        </Button>
      }
    />
  );
}
