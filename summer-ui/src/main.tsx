import './style/global.less';
import React, { useEffect, useState } from 'react';
import ReactDOM from 'react-dom';
import { createStore } from 'redux';
import { Provider } from 'react-redux';
import { Alert, ConfigProvider, Spin } from '@arco-design/web-react';
import zhCN from '@arco-design/web-react/es/locale/zh-CN';
import enUS from '@arco-design/web-react/es/locale/en-US';
import { BrowserRouter, Switch, Route } from 'react-router-dom';
import rootReducer from './store';
import PageLayout from './layout';
import { GlobalContext } from './context';
import Login from './pages/login';
import OidcCallback from './pages/oauth2/callback';
import { getAuthenticatedUser, startLogin } from './auth/oidc';
import { generatePermission } from './routes';
import useLocale from './utils/useLocale';
import changeTheme from './utils/changeTheme';
import useStorage from './utils/useStorage';
import './mock';

const store = createStore(rootReducer);

function AuthenticatedLayout() {
  const t = useLocale();
  const [status, setStatus] = useState<'loading' | 'ready' | 'failed'>(
    'loading'
  );
  useEffect(() => {
    getAuthenticatedUser()
      .then((user) => {
        if (!user) return startLogin();
        const profile = user.profile;
        store.dispatch({
          type: 'update-userInfo',
          payload: {
            userInfo: {
              name: String(
                profile.name || profile.preferred_username || profile.sub
              ),
              permissions: generatePermission('admin'),
            },
            userLoading: false,
          },
        });
        setStatus('ready');
      })
      .catch(() => setStatus('failed'));
  }, []);
  if (status === 'failed')
    return <Alert type="error" content={t['auth.startFailed']} />;
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
