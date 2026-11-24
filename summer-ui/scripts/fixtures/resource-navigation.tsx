// Real layout, routing and pages; synthetic identity and intercepted HTTP in the runner.
import React, { useEffect, useState } from 'react';
import ReactDOM from 'react-dom';
import { BrowserRouter } from 'react-router-dom';
import { Provider } from 'react-redux';
import { createStore } from 'redux';
import { User } from 'oidc-client-ts';
import { oidcManager } from '../../src/auth/oidc';
import { GlobalContext } from '../../src/context';
import { MenuAccessContext } from '../../src/menu/access';
import {
  getVisibleMenuManifest,
  getManagedMenuManifest,
  VisibleMenuItem,
} from '../../src/menu/api';
import reducer from '../../src/store';
import PageLayout from '../../src/layout';
import '@arco-design/web-react/dist/css/arco.css';
import '../../src/style/global.less';

const platformOnly =
  new URLSearchParams(location.search).get('grants') === 'platform';
function App() {
  const [items, setItems] = useState<VisibleMenuItem[]>([]);
  useEffect(() => {
    (platformOnly
      ? getManagedMenuManifest()
      : getVisibleMenuManifest('tenant-a')
    ).then((manifest) => setItems(manifest.items));
  }, []);
  return (
    <MenuAccessContext.Provider value={items}>
      <PageLayout />
    </MenuAccessContext.Provider>
  );
}
async function start() {
  await oidcManager.storeUser(
    new User({
      access_token: 'synthetic-resource-navigation',
      token_type: 'Bearer',
      expires_at: Math.floor(Date.now() / 1000) + 600,
      profile: {
        sub: 'navigation-fixture',
        iss: location.origin,
        aud: 'summer-ui',
        exp: 0,
        iat: 0,
        enterprise_memberships: platformOnly
          ? []
          : [{ enterpriseId: 'tenant-a', status: 'ACTIVE' }],
        active_enterprise_id: platformOnly ? undefined : 'tenant-a',
      },
    })
  );
  ReactDOM.render(
    <Provider store={createStore(reducer)}>
      <GlobalContext.Provider value={{ lang: 'zh-CN' }}>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </GlobalContext.Provider>
    </Provider>,
    document.getElementById('root')
  );
}
start();
