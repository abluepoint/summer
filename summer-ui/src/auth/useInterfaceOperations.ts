import { useEffect, useState } from 'react';
import { oidcManager } from './oidc';
import { AuthorizationDomain, qualifyOperations } from './interfaceOperations';

/** Refresh on context/auth/focus changes; never retain a hint from a previous domain. */
export function useInterfaceOperations(
  domain: AuthorizationDomain,
  actions: string[],
  enabled = true
) {
  const kind = domain.kind;
  const id = domain.kind === 'ENTERPRISE' ? domain.id : 'platform';
  const actionKey = actions.join(',');
  const key = `${kind}:${id}:${actionKey}:${enabled}`;
  const [state, setState] = useState<{
    key: string;
    actions: Record<string, boolean>;
  }>({ key: '', actions: {} });
  useEffect(() => {
    let generation = 0;
    let alive = true;
    const clear = () => {
      generation++;
      if (alive) setState({ key, actions: {} });
    };
    const load = () => {
      const request = ++generation;
      setState({ key, actions: {} });
      if (!enabled || !id || !actionKey) return;
      const target: AuthorizationDomain =
        kind === 'PLATFORM' ? { kind } : { kind, id };
      qualifyOperations(target, actionKey.split(','))
        .then((values) => {
          if (alive && request === generation)
            setState({ key, actions: values });
        })
        .catch(() => {
          if (alive && request === generation) setState({ key, actions: {} });
        });
    };
    const visible = () => {
      if (document.visibilityState === 'visible') load();
    };
    load();
    window.addEventListener('focus', load);
    document.addEventListener('visibilitychange', visible);
    oidcManager.events.addUserLoaded(load);
    oidcManager.events.addUserUnloaded(clear);
    return () => {
      alive = false;
      generation++;
      window.removeEventListener('focus', load);
      document.removeEventListener('visibilitychange', visible);
      oidcManager.events.removeUserLoaded(load);
      oidcManager.events.removeUserUnloaded(clear);
    };
  }, [kind, id, actionKey, key, enabled]);
  return state.key === key ? state.actions : {};
}
