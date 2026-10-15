// Component interaction fixture with synthetic identity and HTTP responses; not a real-login acceptance test.
import React from 'react';
import ReactDOM from 'react-dom';
import { BrowserRouter, Link, Route, Switch } from 'react-router-dom';
import { User } from 'oidc-client-ts';
import { oidcManager } from '../src/auth/oidc';
import { GlobalContext } from '../src/context';
import TasksPage from '../src/pages/tasks';
import '@arco-design/web-react/dist/css/arco.css';
async function render() {
  await oidcManager.storeUser(new User({ access_token: 'synthetic-task-fixture', token_type: 'Bearer',
    expires_at: Math.floor(Date.now() / 1000) + 600,
    profile: { sub: 'task-fixture', iss: location.origin, aud: 'summer-ui', exp: 0, iat: 0,
      enterprise_memberships: [{ enterpriseId: 'ent-acme', status: 'ACTIVE' }, { enterpriseId: 'ent-north', status: 'ACTIVE' }],
      active_enterprise_id: 'ent-acme' } }));
  ReactDOM.render(<GlobalContext.Provider value={{ lang: 'zh-CN' }}><BrowserRouter>
    <Link to="/__tasks/away">离开任务页</Link><Switch><Route path="/__tasks/page"><TasksPage /></Route><Route><div>已离开</div></Route></Switch>
  </BrowserRouter></GlobalContext.Provider>, document.getElementById('root'));
}
render();
