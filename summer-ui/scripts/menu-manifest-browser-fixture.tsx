// UI interaction fixture: synthetic permissions and HTTP responses, no real login.
import React from 'react';
import ReactDOM from 'react-dom';
import { Provider } from 'react-redux';
import { createStore } from 'redux';
import { BrowserRouter, Link } from 'react-router-dom';
import { User } from 'oidc-client-ts';
import { oidcManager } from '../src/auth/oidc';
import { GlobalContext } from '../src/context';
import { MenuAccessContext } from '../src/menu/access';
import { managementNavigation } from '../src/menu/managementNavigation';
import MenuManifestPage from '../src/pages/menu-manifest';
import '@arco-design/web-react/dist/css/arco.css';
const readOnly = new URLSearchParams(location.search).has('readOnly');
const permissions = readOnly ? {} : { 'enterprise.menu': ['read', 'write'], 'platform.menu': ['write'] };
const role = { menuId: 'roles', titleKey:'menu.roles', path:'/roles', enabled:true, sortOrder:0, actions:[], version:1, iconKey:'' };
const visible = [...managementNavigation([role], 'PLATFORM', 'web'), ...managementNavigation([role], 'ENTERPRISE', 'web')];
const store = createStore(() => ({ userInfo: { permissions } }));
async function render() {
await oidcManager.storeUser(new User({ access_token: 'synthetic-menu-fixture', token_type: 'Bearer', expires_at: Math.floor(Date.now() / 1000) + 600, profile: { sub: 'menu-fixture', iss: location.origin, aud: 'summer-ui', exp: 0, iat: 0, enterprise_memberships: [{ enterpriseId: 'ent-acme', status: 'ACTIVE' }], active_enterprise_id: 'ent-acme' } }));
ReactDOM.render(<Provider store={store}><GlobalContext.Provider value={{ lang: 'zh-CN' }}><MenuAccessContext.Provider value={visible}><BrowserRouter><Link to="/__menu/away">离开测试页</Link>{location.pathname.endsWith('/away') ? <div>已离开</div> : <MenuManifestPage />}</BrowserRouter></MenuAccessContext.Provider></GlobalContext.Provider></Provider>, document.getElementById('root'));

}
render();
