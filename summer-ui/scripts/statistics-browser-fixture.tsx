// Synthetic identity and HTTP component checks; real login acceptance is separate.
import React from 'react';
import ReactDOM from 'react-dom';
import { User } from 'oidc-client-ts';
import { oidcManager } from '../src/auth/oidc';
import { GlobalContext } from '../src/context';
import StatisticsPage from '../src/pages/statistics';
import '@arco-design/web-react/dist/css/arco.css';
async function start() {
  const claims = { enterprise_memberships: ['ent-acme', 'tenant-b'].map((enterpriseId) => ({ enterpriseId, status: 'ACTIVE' })), active_enterprise_id: 'tenant-b' };
  const payload = btoa(JSON.stringify(claims)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
  await oidcManager.storeUser(new User({ access_token: 'synthetic.' + payload + '.fixture', token_type: 'Bearer', expires_at: Math.floor(Date.now() / 1000) + 600, profile: { sub: 'fixture', iss: location.origin, aud: 'summer-ui', exp: 0, iat: 0 } }));
  ReactDOM.render(<GlobalContext.Provider value={{ lang: 'zh-CN' }}><StatisticsPage /></GlobalContext.Provider>, document.getElementById('root'));
}
start();
