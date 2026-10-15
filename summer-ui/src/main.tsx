import './style/global.less';
import React, { useEffect, useState } from 'react';
import ReactDOM from 'react-dom';
import { createStore } from 'redux';
import { Provider } from 'react-redux';
import { Alert, ConfigProvider, Message, Spin } from '@arco-design/web-react';
import zhCN from '@arco-design/web-react/es/locale/zh-CN';
import enUS from '@arco-design/web-react/es/locale/en-US';
import { BrowserRouter, Switch, Route } from 'react-router-dom';
import rootReducer from './store';
import PageLayout from './layout';
import { GlobalContext } from './context';
import Login from './pages/login';
import OidcCallback from './pages/oauth2/callback';
import {
  authenticationStatus,
  consumeAuthNotice,
  getAuthenticatedUser,
  isAuthRedirecting,
  onAuthenticatedUiClear,
  startLoginForUnavailable,
} from './auth/oidc';
import { authText, configureBusinessApiAuth } from './auth/requests';
import { menuManifest } from './routes';
import { getVisibleMenuManifest, syncMenuManifest } from './menu/api';
import { loadMenuPermissions } from './menu/permissions';
import { MenuAccessContext } from './menu/access';
import { VisibleMenuItem } from './menu/api';
import useActiveEnterprise from './pages/data-scopes/useActiveEnterprise';
import useLocale from './utils/useLocale';
import changeTheme from './utils/changeTheme';
import useStorage from './utils/useStorage';
import './mock';

const store = createStore(rootReducer);
onAuthenticatedUiClear(() => {
  store.dispatch({
    type: 'update-userInfo',
    payload: { userInfo: { permissions: {} }, userLoading: false },
  });
});
configureBusinessApiAuth();

function AuthenticatedLayout() {
  const tenant = useActiveEnterprise();
  const [menuItems, setMenuItems] = useState<VisibleMenuItem[]>([]);
  const t = useLocale();
  const [status, setStatus] = useState<
    'loading' | 'ready' | 'failed' | 'limited'
  >('loading');
  useEffect(() => {
    if (!tenant.ready) return;
    let alive = true;
    let stopPermissionRefresh: (() => void) | undefined;
    let visibleSignature = '';
    setStatus('loading');
    setMenuItems([]);
    getAuthenticatedUser()
      .then(async (user) => {
        if (!user) {
          return authenticationStatus()
            .then((reason) =>
              startLoginForUnavailable(
                reason === 'expired' ? 'expired' : 'missing',
                { notify: false }
              )
            )
            .then((started) => {
              if (!started && !isAuthRedirecting()) setStatus('limited');
            });
        }
        const profile = user.profile;
        const permissions = await loadMenuPermissions(
          () => syncMenuManifest(menuManifest),
          () =>
            tenant.enterpriseId
              ? getVisibleMenuManifest(tenant.enterpriseId)
              : Promise.resolve({ manifestVersion: 0, items: [] }),
          (items) => {
            if (alive) {
              visibleSignature = JSON.stringify(items);
              setMenuItems(items);
            }
          }
        );
        if (!alive) return;
        store.dispatch({
          type: 'update-userInfo',
          payload: {
            userInfo: {
              name: String(
                profile.name || profile.preferred_username || profile.sub
              ),
              permissions,
            },
            userLoading: false,
          },
        });
        setStatus('ready');
        // Notifications can be lost. Re-read the server's effective actions while
        // this page is visible, including when returning from a background tab.
        let refreshing = false;
        const refreshPermissions = async () => {
          if (!alive || refreshing || document.visibilityState === 'hidden')
            return;
          refreshing = true;
          try {
            let changed = false;
            const nextPermissions = await loadMenuPermissions(
              () => Promise.resolve(),
              () =>
                tenant.enterpriseId
                  ? getVisibleMenuManifest(tenant.enterpriseId)
                  : Promise.resolve({ manifestVersion: 0, items: [] }),
              (items) => {
                const signature = JSON.stringify(items);
                if (!alive || signature === visibleSignature) return;
                visibleSignature = signature;
                changed = true;
                setMenuItems(items);
              }
            );
            if (alive && changed)
              store.dispatch({
                type: 'update-userInfo',
                payload: {
                  userInfo: {
                    ...store.getState().userInfo,
                    permissions: nextPermissions,
                  },
                  userLoading: false,
                },
              });
          } finally {
            refreshing = false;
          }
        };
        const interval = window.setInterval(refreshPermissions, 10000);
        window.addEventListener('focus', refreshPermissions);
        document.addEventListener('visibilitychange', refreshPermissions);
        stopPermissionRefresh = () => {
          window.clearInterval(interval);
          window.removeEventListener('focus', refreshPermissions);
          document.removeEventListener('visibilitychange', refreshPermissions);
        };
        const notice = consumeAuthNotice();
        if (notice === 'expired') Message.info(authText('auth.expired'));
        if (notice === 'missing') Message.info(authText('auth.missing'));
        if (notice === 'unauthorized')
          Message.info(authText('auth.reauthenticated'));
      })
      .catch(() => {
        if (alive) setStatus('failed');
      });
    return () => {
      alive = false;
      stopPermissionRefresh?.();
    };
  }, [tenant.ready, tenant.enterpriseId]);
  if (status === 'failed')
    return <Alert type="error" content={t['auth.startFailed']} />;
  if (status === 'limited')
    return <Alert type="error" content={t['auth.restartLimit']} />;
  return status === 'ready' ? (
    <MenuAccessContext.Provider value={menuItems}>
      <PageLayout />
    </MenuAccessContext.Provider>
  ) : (
    <Spin />
  );
}

function Index() {
  const [lang, setLang] = useStorage('arco-lang', 'zh-CN');
  const [theme, setTheme] = useStorage('arco-theme', 'light');

  function getArcoLocale() {
    switch (lang) {
      case 'zh-CN':
        return zhCN;
      case 'en-US':
        return enUS;
      default:
        return zhCN;
    }
  }

  useEffect(() => {
    changeTheme(theme);
  }, [theme]);

  const contextValue = {
    lang,
    setLang,
    theme,
    setTheme,
  };

  return (
    <BrowserRouter>
      <ConfigProvider
        locale={getArcoLocale()}
        componentConfig={{
          Card: {
            bordered: false,
          },
          List: {
            bordered: false,
          },
          Table: {
            border: false,
          },
        }}
      >
        <Provider store={store}>
          <GlobalContext.Provider value={contextValue}>
            <Switch>
              <Route path="/login" component={Login} />
              <Route path="/oauth2/callback" component={OidcCallback} />
              <Route path="/" component={AuthenticatedLayout} />
            </Switch>
          </GlobalContext.Provider>
        </Provider>
      </ConfigProvider>
    </BrowserRouter>
  );
}

ReactDOM.render(<Index />, document.getElementById('root'));
