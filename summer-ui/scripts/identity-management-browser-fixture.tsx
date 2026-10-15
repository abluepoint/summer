// Synthetic identity + HTTP component regression, never real login acceptance.
import React from 'react';
import ReactDOM from 'react-dom';
import { BrowserRouter, Link, Route, Switch } from 'react-router-dom';
import { User } from 'oidc-client-ts';
import { oidcManager } from '../src/auth/oidc';
import { GlobalContext } from '../src/context';
import AuthUsersPage from '../src/pages/auth-users';
import OrganizationPage from '../src/pages/organization';
import '@arco-design/web-react/dist/css/arco.css';
async function start() {
  const readOnly = new URLSearchParams(location.search).has('readOnly');
  const capabilities = ['enterprise.members.read', 'enterprise.organization.read', ...readOnly ? [] : ['enterprise.members.write', 'enterprise.organization.write']];
  const parameters = new URLSearchParams(location.search);
  const foreignGrant = parameters.has('foreignGrant');
  const claims = {
    enterprise_memberships: ['ent-acme', 'tenant-a', 'tenant-b'].map((enterpriseId) => ({ enterpriseId, status: enterpriseId === 'ent-acme' && parameters.has('inactiveSample') ? 'DISABLED' : 'ACTIVE' })),
    active_enterprise_id: 'tenant-a',
    enterprise_capabilities: { 'ent-acme': foreignGrant ? [] : capabilities, 'tenant-a': foreignGrant ? [] : capabilities, 'tenant-b': capabilities },
  };
  // Synthetic access-token payload mirrors signed business claims; OAuth scopes stay protocol/customer-only.
  const payload = btoa(unescape(encodeURIComponent(JSON.stringify(claims)))).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
  await oidcManager.storeUser(new User({ access_token: 'synthetic.' + payload + '.fixture', token_type: 'Bearer', scope: 'openid profile customer.read customer.write', expires_at: Math.floor(Date.now() / 1000) + 600, profile: { sub: 'ui-fixture', iss: location.origin, aud: 'summer-ui', exp: 0, iat: 0 } }));
  ReactDOM.render(<GlobalContext.Provider value={{ lang: 'zh-CN' }}><BrowserRouter><Link to="/__identity/away">离开测试页</Link><Switch><Route path="/__identity/users" component={AuthUsersPage} /><Route path="/__identity/organization" component={OrganizationPage} /><Route><div>已离开</div></Route></Switch></BrowserRouter></GlobalContext.Provider>, document.getElementById('root'));
}
start();
