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
  const t = useLocale();
  const [status, setStatus] = useState<
    'loading' | 'ready' | 'failed' | 'limited'
  >('loading');
  useEffect(() => {
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
          () => getVisibleMenuManifest('ent-acme')
        );
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
        const notice = consumeAuthNotice();
        if (notice === 'expired') Message.info(authText('auth.expired'));
        if (notice === 'missing') Message.info(authText('auth.missing'));
        if (notice === 'unauthorized')
          Message.info(authText('auth.reauthenticated'));
      })
      .catch(() => setStatus('failed'));
  }, []);
  if (status === 'failed')
    return <Alert type="error" content={t['auth.startFailed']} />;
  if (status === 'limited')
    return <Alert type="error" content={t['auth.restartLimit']} />;
  return status === 'ready' ? <PageLayout /> : <Spin />;
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
