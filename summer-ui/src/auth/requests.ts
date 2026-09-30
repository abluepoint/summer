import axios from 'axios';
import { Message } from '@arco-design/web-react';
import locale from '@/locale';
import {
  authenticationStatus,
  getAccessTokenFromMemory,
  isAuthRedirecting,
  restartLoginAfterUnauthorized,
  startLoginForUnavailable,
} from './oidc';
import {
  classifyBusinessFailure,
  completeAuthenticatedRequest,
  isBusinessApi,
} from './recovery.mjs';

export function authText(key: string) {
  const language =
    localStorage.getItem('arco-lang') === 'en-US' ? 'en-US' : 'zh-CN';
  return locale[language][key];
}

class AuthenticationUnavailable extends Error {}

let configured = false;

export function configureBusinessApiAuth() {
  if (configured) return;
  configured = true;

  axios.interceptors.request.use(async (config) => {
    if (!isBusinessApi(config.url)) return config;
    const token = await getAccessTokenFromMemory();
    if (!token) {
      const reason = await authenticationStatus();
      try {
        const started = await startLoginForUnavailable(
          reason === 'expired' ? 'expired' : 'missing'
        );
        if (!started && !isAuthRedirecting())
          Message.error(authText('auth.restartLimit'));
      } catch {
        Message.error(authText('auth.startFailed'));
      }
      throw new AuthenticationUnavailable(
        authText(reason === 'expired' ? 'auth.expired' : 'auth.missing')
      );
    }
    config.headers = { ...config.headers, Authorization: `Bearer ${token}` };
    return config;
  });

  axios.interceptors.response.use(
    (response) => {
      if (isBusinessApi(response.config.url) && !isAuthRedirecting()) {
        completeAuthenticatedRequest(sessionStorage);
      }
      return response;
    },
    async (error) => {
      if (error instanceof AuthenticationUnavailable)
        return Promise.reject(error);
      if (!isBusinessApi(error.config?.url)) return Promise.reject(error);
      const failure = classifyBusinessFailure(
        error.response?.status,
        Boolean(error.response)
      );
      if (failure === 'unauthorized') {
        try {
          const started = await restartLoginAfterUnauthorized();
          if (!started && !isAuthRedirecting())
            Message.error(authText('auth.restartLimit'));
        } catch {
          Message.error(authText('auth.startFailed'));
        }
      } else if (failure === 'forbidden') {
        Message.error(authText('auth.forbidden'));
      } else if (failure === 'network') {
        Message.error(authText('auth.networkFailure'));
      }
      // Every failed call is returned to its caller. In particular, writes are
      // never replayed after the redirect and require an explicit new submit.
      return Promise.reject(error);
    }
  );
}
