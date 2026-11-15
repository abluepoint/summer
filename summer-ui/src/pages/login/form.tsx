import { Form, Input, Button, Alert } from '@arco-design/web-react';
import { FormInstance } from '@arco-design/web-react/es/Form';
import { IconLock, IconUser } from '@arco-design/web-react/icon';
import React, { useRef, useState } from 'react';
import useLocale from '@/utils/useLocale';
import locale from './locale';
import styles from './style/index.module.less';
import { prepareManualLogin } from '@/auth/oidc';

type CsrfToken = { parameterName: string; token: string };

async function readCsrfToken(): Promise<CsrfToken> {
  const response = await fetch('/csrf', {
    credentials: 'same-origin',
    cache: 'no-store',
  });
  if (!response.ok) throw new Error('CSRF unavailable');
  const data = await response.json();
  if (
    typeof data.parameterName !== 'string' ||
    typeof data.token !== 'string'
  ) {
    throw new Error('Invalid CSRF response');
  }
  return data;
}

export default function LoginForm() {
  const formRef = useRef<FormInstance>();
  const [errorMessage, setErrorMessage] = useState('');
  const [loading, setLoading] = useState(false);
  const t = useLocale(locale);

  async function onSubmitClick() {
    let values: { username: string; password: string };
    try {
      values = await formRef.current.validate();
    } catch {
      return;
    }
    setLoading(true);
    setErrorMessage('');
    try {
      prepareManualLogin();
      const csrf = await readCsrfToken();
      // Native navigation preserves Spring Security's saved authorization request.
      const form = document.createElement('form');
      form.method = 'POST';
      form.action = '/login';
      form.hidden = true;
      for (const [name, value] of [
        ['username', values.username],
        ['password', values.password],
        [csrf.parameterName, csrf.token],
      ]) {
        const input = document.createElement('input');
        input.name = name;
        input.value = value;
        form.appendChild(input);
      }
      document.body.appendChild(form);
      form.submit();
    } catch {
      setErrorMessage(t['login.form.csrfError']);
      setLoading(false);
    }
  }

  return (
    <div className={styles['login-form-wrapper']}>
      <div className={styles['login-form-title']}>{t['login.form.title']}</div>
      {new URLSearchParams(window.location.search).has('error') && (
        <Alert type="error" content={t['login.form.invalidCredentials']} />
      )}
      {new URLSearchParams(window.location.search).has('logout') && (
        <Alert type="success" content={t['login.form.loggedOut']} />
      )}
      {new URLSearchParams(window.location.search).has('logoutError') && (
        <Alert type="error" content={t['login.form.logoutError']} />
      )}
      {errorMessage && <Alert type="error" content={errorMessage} />}
      <Form className={styles['login-form']} layout="vertical" ref={formRef}>
        <Form.Item
          field="username"
          rules={[{ required: true, message: t['login.form.userName.errMsg'] }]}
        >
          <Input
            prefix={<IconUser />}
            autoComplete="username"
            placeholder={t['login.form.userName.placeholder']}
            onPressEnter={onSubmitClick}
          />
        </Form.Item>
        <Form.Item
          field="password"
          rules={[{ required: true, message: t['login.form.password.errMsg'] }]}
        >
          <Input.Password
            prefix={<IconLock />}
            autoComplete="current-password"
            placeholder={t['login.form.password.placeholder']}
            onPressEnter={onSubmitClick}
          />
        </Form.Item>
        <Button type="primary" long onClick={onSubmitClick} loading={loading}>
          {t['login.form.login']}
        </Button>
      </Form>
    </div>
  );
}
