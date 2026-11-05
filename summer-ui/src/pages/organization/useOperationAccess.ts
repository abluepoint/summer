import { useMemo } from 'react';
import { useOperationHints } from '@/auth/interfaceOperations';
import { useInterfaceOperations } from '@/auth/useInterfaceOperations';
import useLegacyAccess from './useAccess';

const actions = [
  'organization.create',
  'organization.update',
  'organization.delete',
  'organization.assign',
  'members.read',
  'members.disable',
  'members.enable',
  'members.leave',
];

export default function useOrganizationOperationAccess(enterprise: string) {
  const legacy = useLegacyAccess(enterprise);
  const { engineMode } = useOperationHints();
  const domain = useMemo(
    () => ({ kind: 'ENTERPRISE' as const, id: enterprise }),
    [enterprise]
  );
  const current = useInterfaceOperations(
    domain,
    actions,
    engineMode === 'MANAGED'
  );
  const allowed = (action: string, old: boolean) =>
    engineMode === 'LEGACY'
      ? old
      : engineMode === 'MANAGED' && current[action] === true;
  return {
    orgCreate: allowed('organization.create', legacy.orgWrite),
    orgUpdate: allowed('organization.update', legacy.orgWrite),
    orgDelete: allowed('organization.delete', legacy.orgWrite),
    orgAssign: allowed('organization.assign', legacy.orgWrite),
    memberRead: allowed('members.read', legacy.memberRead),
    memberDisable: allowed('members.disable', legacy.memberWrite),
    memberEnable: allowed('members.enable', legacy.memberWrite),
    memberLeave: allowed('members.leave', legacy.memberWrite),
  };
}
