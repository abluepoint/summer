import { useCallback, useEffect, useRef, useState } from 'react';
import { unstable_batchedUpdates } from 'react-dom';
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
type Snapshot = { options: Choice[]; enterpriseId: string; ready: boolean; dirty: boolean; busy: boolean; contextVersion: number };
type Guard = { dirty: boolean; busy: boolean };
let snapshot: Snapshot = { options: [], enterpriseId: '', ready: false, dirty: false, busy: false, contextVersion: 0 };
const listeners = new Set<() => void>();
const guards = new Map<symbol, Guard>();
let identity = '';
let lastClaim = '';
let identityRead = 0;
let stopIdentity: (() => void) | undefined;

function publish(next: Partial<Snapshot>) {
  snapshot = { ...snapshot, ...next };
  // OIDC events also arrive outside React event handlers in React 17.
  unstable_batchedUpdates(() => listeners.forEach((listener) => listener()));
}
function updateIdentity(user: User | null) {
  if (user?.expired) user = null;
  const next = choicesFor(user);
  const nextIdentity = user ? `${user.profile.iss || ''}:${user.profile.sub}` : '';
  const sameIdentity = Boolean(identity) && identity === nextIdentity;
  const selected = sameIdentity && next.selected === lastClaim && next.options.some((option) => option.value === snapshot.enterpriseId)
    ? snapshot.enterpriseId : next.selected;
  identity = nextIdentity;
  lastClaim = next.selected;
  guards.clear();
  publish({ options: next.options, enterpriseId: selected, ready: true, dirty: false, busy: false, contextVersion: snapshot.contextVersion + 1 });
}
function setGuard(owner: symbol, value: Partial<Guard>) {
  guards.set(owner, { dirty: false, busy: false, ...guards.get(owner), ...value });
  const values = Array.from(guards.values());
  const dirty = values.some((guard) => guard.dirty), busy = values.some((guard) => guard.busy);
  if (dirty !== snapshot.dirty || busy !== snapshot.busy) publish({ dirty, busy });
}
function releaseGuard(owner: symbol) {
  guards.delete(owner);
  const values = Array.from(guards.values());
  const dirty = values.some((guard) => guard.dirty), busy = values.some((guard) => guard.busy);
  if (dirty !== snapshot.dirty || busy !== snapshot.busy) publish({ dirty, busy });
}

/** In-memory UI selection only. It never changes JWT claims or grants API access. */
export const activeEnterpriseSelection = {
  getSnapshot: () => snapshot,
  subscribe(listener: () => void) {
    listeners.add(listener);
    if (!stopIdentity) {
      const loaded = (user: User) => { identityRead++; updateIdentity(user); };
      const unloaded = () => { identityRead++; updateIdentity(null); };
      oidcManager.events.addUserLoaded(loaded);
      oidcManager.events.addUserUnloaded(unloaded);
      stopIdentity = () => {
        oidcManager.events.removeUserLoaded(loaded);
        oidcManager.events.removeUserUnloaded(unloaded);
      };
      const read = ++identityRead;
      getAuthenticatedUser().then((user) => { if (read === identityRead) updateIdentity(user); })
        .catch(() => { if (read === identityRead) updateIdentity(null); });
    }
    return () => {
      listeners.delete(listener);
      if (!listeners.size) {
        stopIdentity?.(); stopIdentity = undefined; identityRead++;
        identity = ''; lastClaim = ''; guards.clear();
        snapshot = { options: [], enterpriseId: '', ready: false, dirty: false, busy: false, contextVersion: snapshot.contextVersion + 1 };
      }
    };
  },
  setGuard,
  releaseGuard,
  change(value: string) {
    if (snapshot.busy || value === snapshot.enterpriseId || !snapshot.options.some((option) => option.value === value)) return;
    const version = snapshot.contextVersion;
    const apply = () => {
      if (version !== snapshot.contextVersion || snapshot.busy || !snapshot.options.some((option) => option.value === value)) return;
      guards.clear();
      publish({ enterpriseId: value, dirty: false, busy: false, contextVersion: version + 1 });
    };
    if (snapshot.dirty) Modal.confirm({ title: '切换租户会放弃未保存修改，是否继续？', okText: '放弃修改', cancelText: '继续编辑', onOk: apply });
    else apply();
  },
};

export default function useActiveEnterprise() {
  const [current, setCurrent] = useState(activeEnterpriseSelection.getSnapshot);
  const owner = useRef(Symbol('enterprise-consumer'));
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    const update = () => setCurrent(activeEnterpriseSelection.getSnapshot());
    const unsubscribe = activeEnterpriseSelection.subscribe(update);
    update();
    const guardOwner = owner.current;
    return () => { mounted.current = false; unsubscribe(); releaseGuard(guardOwner); };
  }, []);
  const setDirty = useCallback((dirty: boolean) => { if (mounted.current) setGuard(owner.current, { dirty }); }, []);
  const setBusy = useCallback((busy: boolean) => { if (mounted.current) setGuard(owner.current, { busy }); }, []);
  return { ...current, setDirty, setBusy, change: activeEnterpriseSelection.change };
}
