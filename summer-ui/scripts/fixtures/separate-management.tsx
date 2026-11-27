// Synthetic identity/transport; real React pages and routing.
import React from 'react';
import ReactDOM from 'react-dom';
import { BrowserRouter, Link, Route, Switch } from 'react-router-dom';
import { User } from 'oidc-client-ts';
import { oidcManager } from '../../src/auth/oidc';
import { GlobalContext } from '../../src/context';
import Divisions from '../../src/pages/dictionary/divisions';
import Roles from '../../src/pages/roles';
import Menus from '../../src/pages/menu-manifest';
import PlatformRoles from '../../src/pages/platform/roles';
import PlatformPermissions from '../../src/pages/platform/permissions';
import PlatformMenus from '../../src/pages/platform/menu-manifest';
import '@arco-design/web-react/dist/css/arco.css';
import Administrators from '../../src/pages/platform/administrators';
import Organization from '../../src/pages/platform/organization';
import FileRepositories from '../../src/pages/platform/file-repositories';
import Files from '../../src/pages/platform/files';
const pages = {
  'platform/files': Files,
  'platform/file-repositories': FileRepositories,
  'platform/administrators': Administrators,
  'platform/organization': Organization,
  'dictionary/divisions': Divisions,
  roles: Roles,
  'menu-manifest': Menus,
  'platform/roles': PlatformRoles,
  'platform/permissions': PlatformPermissions,
  'platform/menu-manifest': PlatformMenus,
};
async function start() {
  const empty = new URLSearchParams(location.search).has('empty');
  await oidcManager.storeUser(
    new User({
      access_token: 'synthetic-separated-management',
      token_type: 'Bearer',
      expires_at: Math.floor(Date.now() / 1000) + 600,
      profile: {
        sub: 'fixture',
        iss: location.origin,
        aud: 'summer-ui',
        exp: 0,
        iat: 0,
        enterprise_memberships: empty
          ? []
          : [{ enterpriseId: 'tenant-a', status: 'ACTIVE' }],
        active_enterprise_id: empty ? undefined : 'tenant-a',
      },
    })
  );
  ReactDOM.render(
    <GlobalContext.Provider value={{ lang: 'zh-CN' }}>
      <BrowserRouter>
        <div style={{ padding: 24 }}>
          <nav
            aria-label="测试入口"
            style={{ display: 'flex', flexWrap: 'wrap' }}
          >
            {Object.keys(pages).map((key) => (
              <Link
                key={key}
                to={'/__separate/' + key}
                style={{ marginRight: 12 }}
              >
                {key}
              </Link>
            ))}
          </nav>
          <Switch>
            {Object.entries(pages).map(([key, Page]) => (
              <Route
                exact
                key={key}
                path={'/__separate/' + key}
                component={Page}
              />
            ))}
          </Switch>
        </div>
      </BrowserRouter>
    </GlobalContext.Provider>,
    document.getElementById('root')
  );
}
start();
