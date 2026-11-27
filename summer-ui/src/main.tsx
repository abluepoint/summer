import { InterfaceOperationHints } from './auth/interfaceOperations';
import { useInterfaceOperations } from './auth/useInterfaceOperations';
import { MENU_REFRESH_EVENT } from './menu/refresh';
import './style/global.less';
import React, { useEffect, useState } from 'react';
import ReactDOM from 'react-dom';
import { createStore } from 'redux';
import { Provider } from 'react-redux';
import {
  Alert,
  Button,
  Card,
  ConfigProvider,
  Message,
  Select,
} from '@arco-design/web-react';
import zhCN from '@arco-design/web-react/es/locale/zh-CN';
import enUS from '@arco-design/web-react/es/locale/en-US';
import { BrowserRouter, Switch, Route } from 'react-router-dom';
import rootReducer from './store';
import PageLayout from './layout';
import AppLoading from './components/AppLoading';
import { GlobalContext } from './context';
import Login from './pages/login';
import OidcCallback from './pages/oauth2/callback';
import {
  authenticationStatus,
  consumeAuthNotice,
  getAuthenticatedUser,
  isAuthRedirecting,
  onAuthenticatedUiClear,
  restartLoginManually,
  startLoginForUnavailable,
} from './auth/oidc';
import { authText, configureBusinessApiAuth } from './auth/requests';
import { getVisibleMenuManifest, getManagedMenuManifest } from './menu/api';
import { loadMenuPermissions } from './menu/permissions';
import { MenuAccessContext } from './menu/access';
import { VisibleMenuItem } from './menu/api';
import useActiveEnterprise, {
  activeEnterpriseSelection,
} from '@/hooks/useActiveEnterprise';
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
  const [authorizationMode, setAuthorizationMode] = useState<'LEGACY' | 'MANAGED' | undefined>();
  const customerOperations = useInterfaceOperations(
    { kind: 'PLATFORM' },
    ['customer.read', 'customer.create', 'customer.update', 'customer.delete'],
    authorizationMode === 'MANAGED'
  );
  const [settledContext, setSettledContext] = useState(-1);
  const t = useLocale();
  const [retryingLogin, setRetryingLogin] = useState(false);
  const retryLogin = async () => {
    setRetryingLogin(true);
    try {
      await restartLoginManually();
    } catch {
      setStatus('failed');
      setSettledContext(activeEnterpriseSelection.getSnapshot().contextVersion);
    } finally {
      setRetryingLogin(false);
    }
  };
  const [status, setStatus] = useState<
    'loading' | 'ready' | 'failed' | 'limited'
  >('loading');
  useEffect(() => {
    if (!tenant.ready) return;
    let alive = true;
    const isCurrent = () =>
      alive &&
      activeEnterpriseSelection.getSnapshot().contextVersion ===
        tenant.contextVersion;
    let stopPermissionRefresh: (() => void) | undefined;
    let visibleSignature = '';
    setStatus('loading');
    setMenuItems([]);
    setAuthorizationMode(undefined);
    getAuthenticatedUser()
      .then(async (user) => {
        if (!isCurrent()) return;
        if (!user) {
          return authenticationStatus()
            .then((reason) =>
              isCurrent()
                ? startLoginForUnavailable(
                    reason === 'expired' ? 'expired' : 'missing',
                    { notify: false }
                  )
                : false
            )
            .then((started) => {
              if (isCurrent() && !started && !isAuthRedirecting()) {
                setStatus('limited');
                setSettledContext(tenant.contextVersion);
              }
            });
        }
        const profile = user.profile;
        if (!tenant.enterpriseId) {
          const navigation = await getManagedMenuManifest();
          if (!isCurrent()) return;
          setMenuItems(navigation.items);
          setAuthorizationMode(navigation.engineMode);
          let refreshingNavigation = false;
          let navigationSignature = JSON.stringify(navigation.items);
          const refreshNavigation = async () => {
            if (
              !isCurrent() ||
              refreshingNavigation ||
              document.visibilityState === 'hidden'
            )
              return;
            refreshingNavigation = true;
            try {
              const next = await getManagedMenuManifest();
              const signature = JSON.stringify(next.items);
              if (isCurrent() && signature !== navigationSignature) {
                navigationSignature = signature;
                setMenuItems(next.items);
                setAuthorizationMode(next.engineMode);
              }
            } catch {
              if (isCurrent()) {
                navigationSignature = '[]';
                setMenuItems([]);
              }
            } finally {
              refreshingNavigation = false;
            }
          };
          const invalidateNavigation = (
            event: CustomEvent<{ contextVersion: number }>
          ) => {
            if (event.detail.contextVersion === tenant.contextVersion)
              refreshNavigation();
          };
          window.addEventListener(MENU_REFRESH_EVENT, invalidateNavigation);
          window.addEventListener('focus', refreshNavigation);
          document.addEventListener('visibilitychange', refreshNavigation);
          stopPermissionRefresh = () => {
            window.removeEventListener(
              MENU_REFRESH_EVENT,
              invalidateNavigation
            );
            window.removeEventListener('focus', refreshNavigation);
            document.removeEventListener('visibilitychange', refreshNavigation);
          };
          store.dispatch({
            type: 'update-userInfo',
            payload: {
              userInfo: {
                subject: profile.sub,
                name: String(
                  profile.name || profile.preferred_username || profile.sub
                ),
                permissions: {},
              },
              userLoading: false,
            },
          });
          setStatus('ready');
          setSettledContext(tenant.contextVersion);
          return;
        }
        const permissions = await loadMenuPermissions(
          () => Promise.resolve(),
          () =>
            tenant.enterpriseId
              ? getVisibleMenuManifest(tenant.enterpriseId)
              : Promise.resolve({ manifestVersion: 0, items: [] }),
          (items, mode) => {
            if (isCurrent()) {
              setAuthorizationMode(mode);
              visibleSignature = JSON.stringify(items);
              setMenuItems(items);
            }
          }
        );
        if (!isCurrent()) return;
        store.dispatch({
          type: 'update-userInfo',
          payload: {
            userInfo: {
              subject: profile.sub,
              name: String(
                profile.name || profile.preferred_username || profile.sub
              ),
              permissions,
            },
            userLoading: false,
          },
        });
        setStatus('ready');
        setSettledContext(tenant.contextVersion);
        // Re-read effective actions when the user returns to the app. Elapsed
        // time alone must not trigger menu-manifest requests.
        let refreshing = false;
        const refreshPermissions = async () => {
          if (
            !isCurrent() ||
            refreshing ||
            document.visibilityState === 'hidden'
          )
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
              (items, mode) => {
                if (isCurrent()) setAuthorizationMode(mode);
                const signature = JSON.stringify(items);
                if (!isCurrent() || signature === visibleSignature) return;
                visibleSignature = signature;
                changed = true;
                setMenuItems(items);
              }
            );
            if (isCurrent() && changed)
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
        const invalidatePermissions = (
          event: CustomEvent<{ contextVersion: number }>
        ) => {
          if (event.detail.contextVersion === tenant.contextVersion)
            refreshPermissions();
        };
        window.addEventListener(MENU_REFRESH_EVENT, invalidatePermissions);
        window.addEventListener('focus', refreshPermissions);
        document.addEventListener('visibilitychange', refreshPermissions);
        stopPermissionRefresh = () => {
          window.removeEventListener(MENU_REFRESH_EVENT, invalidatePermissions);
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
        if (isCurrent()) {
          setStatus('failed');
          setSettledContext(tenant.contextVersion);
        }
      });
    return () => {
      alive = false;
      stopPermissionRefresh?.();
    };
  }, [tenant.ready, tenant.enterpriseId, tenant.contextVersion]);
  // Unmount old tenant pages in this render, before passive-effect cleanup.
  if (!tenant.ready || settledContext !== tenant.contextVersion)
    return <AppLoading />;
  if (status === 'failed' || status === 'limited')
    return (
      <Alert
        type="error"
        content={
          t[status === 'failed' ? 'auth.startFailed' : 'auth.restartLimit']
        }
        action={
          <Button type="primary" loading={retryingLogin} onClick={retryLogin}>
            {t['auth.signInAgain']}
          </Button>
        }
      />
    );
  if (!tenant.enterpriseId && !menuItems.length)
    return (
      <Card title="选择活动租户">
        <Select
          aria-label="活动租户"
          placeholder="选择活动租户"
          options={tenant.options}
          value={undefined}
          onChange={tenant.change}
          disabled={tenant.busy}
          style={{ width: 240 }}
        />
        <Alert
          type="info"
          content={
            tenant.options.length
              ? '请选择一个活动成员租户以加载可用菜单。'
              : '当前身份没有可用的活动成员租户。'
          }
        />
      </Card>
    );
  return status === 'ready' ? (
    <MenuAccessContext.Provider value={menuItems}>
      <InterfaceOperationHints.Provider value={{ engineMode: authorizationMode, actions: customerOperations }}>
        <PageLayout key={tenant.contextVersion} />
      </InterfaceOperationHints.Provider>
    </MenuAccessContext.Provider>
  ) : (
    <AppLoading />
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
          Spin: {
            size: 28,
          },
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
