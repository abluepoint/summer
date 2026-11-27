import { activeEnterpriseSelection } from '@/hooks/useActiveEnterprise';
import { requestMenuRefresh } from '@/menu/refresh';
declare module 'axios' {
  interface AxiosRequestConfig {
    expectedAccessProbe?: boolean;
    menuContextVersion?: number;
  }
}
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
    config.menuContextVersion =
      activeEnterpriseSelection.getSnapshot().contextVersion;
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
      const method = (response.config.method || 'get').toUpperCase();
      const path = (response.config.url || '').split('?')[0];
      if (
        ['POST', 'PUT', 'PATCH', 'DELETE'].includes(method) &&
        /^\/api\/v1\/(platform|enterprises\/[^/]+)\/(permissions(?:\/|$)|permission-groups(?:\/|$)|roles(?:\/|$)|users\/[^/]+\/roles$|authorization\/menu-(manifest|layout)$)/.test(
          path
        ) &&
        typeof response.config.menuContextVersion === 'number'
      ) {
        requestMenuRefresh(response.config.menuContextVersion);
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
      } else if (
        failure === 'forbidden' &&
        !error.config?.expectedAccessProbe
      ) {
        Message.error(authText('auth.forbidden'));
        if (typeof error.config?.menuContextVersion === 'number')
          requestMenuRefresh(error.config.menuContextVersion);
      } else if (failure === 'network') {
        Message.error(authText('auth.networkFailure'));
      }
      // Every failed call is returned to its caller. In particular, writes are
      // never replayed after the redirect and require an explicit new submit.
      return Promise.reject(error);
    }
  );
}
