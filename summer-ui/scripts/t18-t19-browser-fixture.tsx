// Component-browser regression fixture. Synthetic identity and HTTP responses
// prove UI behavior only; this fixture is never a real login acceptance result.
import React from 'react';
import ReactDOM from 'react-dom';
import { BrowserRouter, Link, Route, Switch } from 'react-router-dom';
import { User } from 'oidc-client-ts';
import { GlobalContext } from '../src/context';
import { oidcManager } from '../src/auth/oidc';
import DataScopesPage from '../src/pages/data-scopes';
import SystemParametersPage from '../src/pages/system-parameters';
import DictionaryPage from '../src/pages/dictionary';
import '@arco-design/web-react/dist/css/arco.css';

async function render() {
  if (!new URLSearchParams(location.search).has('anonymous')) {
    await oidcManager.storeUser(new User({
      access_token: 'synthetic-component-fixture', token_type: 'Bearer',
      expires_at: Math.floor(Date.now() / 1000) + 600,
      profile: { sub: 'ui-fixture', iss: location.origin, aud: 'summer-ui', exp: 0, iat: 0, enterprise_memberships: new URLSearchParams(location.search).has('multi') ? [{ enterpriseId: 'tenant-a', status: 'ACTIVE' }, { enterpriseId: 'tenant-b', status: 'ACTIVE' }, { enterpriseId: 'inactive', status: 'INACTIVE' }] : [{ enterpriseId: 'ent-acme', status: 'ACTIVE' }], active_enterprise_id: new URLSearchParams(location.search).has('multi') ? 'tenant-a' : 'ent-acme' },
    }));
  }
  ReactDOM.render(
    <GlobalContext.Provider value={{ lang: 'zh-CN' }}>
      <BrowserRouter>
        <Link to="/__t18-t19/away">离开测试页</Link>
        <Switch>
          <Route path="/__t18-t19/data-scopes" component={DataScopesPage} />
          <Route path="/__t18-t19/system-parameters" component={SystemParametersPage} />
          <Route path="/__t18-t19/dictionary" component={DictionaryPage} />
          <Route><div>已离开</div></Route>
        </Switch>
      </BrowserRouter>
    </GlobalContext.Provider>, document.getElementById('root'),
  );
}
render();
