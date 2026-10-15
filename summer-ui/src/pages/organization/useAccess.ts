import { useEffect, useState } from 'react';
import { User } from 'oidc-client-ts';
import { getAuthenticatedUser, oidcManager } from '@/auth/oidc';
import { hasEnterpriseCapability } from '@/auth/businessCapabilities';
export default function useOrganizationAccess(enterprise: string) {
  const [access, setAccess] = useState({ orgWrite: false, memberRead: false, memberWrite: false });
  useEffect(() => {
    let alive = true;
    const update = (user: User | null) => {
      if (!alive) return;
      setAccess({
        orgWrite: hasEnterpriseCapability(user, enterprise, 'enterprise.organization.write'),
        memberRead: hasEnterpriseCapability(user, enterprise, 'enterprise.members.read'),
        memberWrite: hasEnterpriseCapability(user, enterprise, 'enterprise.members.write'),
      });
    };
    getAuthenticatedUser().then(update).catch(() => update(null));
    const unload = () => update(null);
    oidcManager.events.addUserLoaded(update); oidcManager.events.addUserUnloaded(unload);
    return () => { alive = false; oidcManager.events.removeUserLoaded(update); oidcManager.events.removeUserUnloaded(unload); };
  }, [enterprise]);
  return access;
}
