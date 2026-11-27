// Synthetic transport/identity, real React 17 + Arco + shared selection. Not native acceptance.
import React from 'react';
import ReactDOM from 'react-dom';
import axios from 'axios';
import { Select } from '@arco-design/web-react';
import { User } from 'oidc-client-ts';
import { oidcManager } from '../src/auth/oidc';
import { GlobalContext } from '../src/context';
import useActiveEnterprise from '../src/hooks/useActiveEnterprise';
import Roles from '../src/pages/roles';
import '@arco-design/web-react/dist/css/arco.css';
const w = window as any;
const f = w.fixture = { requests: [], hold: '', pending: [], deny: false, conflict: false, version: 4 };
const row = (tenant: string) => ({ roleId: 'role-'+tenant, enterpriseId: tenant, name: 'Role '+tenant, code: 'role-'+tenant, status: 'ACTIVE', capabilityIds: [], version: 1 });
axios.defaults.adapter = async (config) => {
  const url = config.url || '', method = config.method || 'get';
  const tenant = url.match(/\/enterprises\/([^/]+)/)?.[1] || config.params?.enterpriseId || '';
  f.requests.push({ url, method, tenant, body: config.data ? JSON.parse(config.data) : null, ifMatch: config.headers?.['If-Match'] });
  const response = (data: unknown) => ({ data: { success: true, error: null, requestId: 'fixture-ok', data }, status: 200, headers: {}, config });
  const failed = () => ({ isAxiosError: true, response: { status: 403, data: { success: false, error: { code: 'SUMMER_FORBIDDEN', message: 'Denied' }, requestId: 'fixture-denied' }, headers: {} } });
  if (method === 'put' && f.conflict) { f.conflict = false; f.version = 9; throw { isAxiosError: true, response: { status: 412, data: { success: false, error: { code: 'SUMMER_VERSION_CONFLICT', message: 'Stale' }, requestId: 'fixture-conflict' }, headers: {} } }; }
  let data: any;
  if (url.endsWith('/interface-resources')) data = { items: [{ httpMethod: 'GET', normalizedPath: '/api/customers', permissionCode: 'GET:/api/customers', resourceType: 'BUSINESS', configurable: true }] };
  else if (url.includes('/users/')) data = { username: 'alice', enterpriseId: tenant, roleIds: ['role-'+tenant], deniedCapabilityCodes: [], version: method === 'put' ? ++f.version : f.version };
  else data = method === 'get' ? [row(tenant)] : { ...row(tenant), name: JSON.parse(config.data).name };
  if (f.hold && url.includes(f.hold)) {
    f.hold = '';
    return new Promise((resolve, reject) => f.pending.push({ resolve: () => resolve(response(data)), reject: () => reject(failed()) }));
  }
  if (f.deny && !url.endsWith('/interface-resources')) throw failed();
  return response(data);
};
function Shell() {
  const tenant = useActiveEnterprise();
  return <><Select aria-label="fixture-global-enterprise" value={tenant.enterpriseId || undefined} options={tenant.options} onChange={tenant.change} disabled={tenant.busy} />
    <Roles /></>;
}
async function start() {
  const payload = btoa(JSON.stringify({ enterprise_memberships: ['ent-acme','tenant-b'].map(enterpriseId => ({ enterpriseId, status: 'ACTIVE' })), active_enterprise_id: 'tenant-b' })).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
  await oidcManager.storeUser(new User({ access_token: 'synthetic.'+payload+'.fixture', token_type: 'Bearer', expires_at: Math.floor(Date.now()/1000)+600, profile: { sub: 'fixture', iss: location.origin, aud: 'summer-ui', exp: 0, iat: 0 } }));
  ReactDOM.render(<GlobalContext.Provider value={{lang:'zh-CN'}}><Shell /></GlobalContext.Provider>, document.getElementById('root'));
}
start();
