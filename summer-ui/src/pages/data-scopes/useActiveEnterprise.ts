import { useEffect, useState } from 'react';
import { Modal } from '@arco-design/web-react';
import { User } from 'oidc-client-ts';
import { getAuthenticatedUser, oidcManager } from '@/auth/oidc';

type Choice = { value: string; label: string };
// Claims only populate the selector; every request remains server-authorized.
function choicesFor(user: User | null) {
  let access: Record<string, unknown> = {};
  if (user) {
    try { access = JSON.parse(decodeURIComponent(Array.from(atob(user.access_token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))).map((char) => `%${char.charCodeAt(0).toString(16).padStart(2, '0')}`).join(''))); } catch { /* Opaque access token: use authenticated profile metadata. */ }
  }
  const claims = { ...(user?.profile || {}), ...access };
  const memberships = Array.isArray(claims.enterprise_memberships) ? claims.enterprise_memberships : [];
  const options: Choice[] = [];
  memberships.forEach((membership) => {
    if (!membership || typeof membership !== 'object') return;
    const id = membership.enterpriseId || membership.enterprise_id;
    if (typeof id !== 'string' || !id || String(membership.status || membership.membership_status).toUpperCase() !== 'ACTIVE' || options.some((option) => option.value === id)) return;
    options.push({ value: id, label: id });
  });
  const active = typeof claims.active_enterprise_id === 'string' ? claims.active_enterprise_id : undefined;
  const selected = options.find((option) => option.value === active)?.value || (options.length === 1 ? options[0].value : '');
  return { options, selected };
}
export default function useActiveEnterprise() {
  const [options, setOptions] = useState<Choice[]>([]);
  const [enterpriseId, select] = useState('');
  const [ready, setReady] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    let alive = true;
    const update = (user: User | null) => { if (!alive) return; const next = choicesFor(user); setOptions(next.options); select((current) => next.options.some((option) => option.value === current) ? current : next.selected); setReady(true); };
    getAuthenticatedUser().then(update).catch(() => update(null));
    oidcManager.events.addUserLoaded(update);
    const unload = () => update(null);
    oidcManager.events.addUserUnloaded(unload);
    return () => { alive = false; oidcManager.events.removeUserLoaded(update); oidcManager.events.removeUserUnloaded(unload); };
  }, []);
  const change = (value: string) => {
    if (busy || value === enterpriseId || !options.some((option) => option.value === value)) return;
    const apply = () => { setDirty(false); select(value); };
    if (dirty) Modal.confirm({ title: '切换租户会放弃未保存修改，是否继续？', okText: '放弃修改', cancelText: '继续编辑', onOk: apply });
    else apply();
  };
  return { options, enterpriseId, ready, dirty, busy, setDirty, setBusy, change };
}
