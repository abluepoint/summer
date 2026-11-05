import axios from 'axios';
import { AuthorizationDomain } from '@/auth/interfaceOperations';
import { readEnvelope, readFailure } from '../customer/contract.mjs';

export type ExplanationQuery = {
  subject: string;
  resourceType: 'INTERFACE' | 'MENU' | 'DATA';
  resourceId: string;
  action?: string;
  recordId?: string;
};
export type AuthorizationExplanation = {
  subject: string;
  domain: { kind: string; id: string; key: string };
  resource: { type: string; id: string; source: string };
  action?: string;
  allowed: boolean;
  reason: string;
  revision: number;
  requestId: string;
  provenance: 'CURRENT_PERSISTED_FACTS';
  evaluationBoundary: 'PERSISTED_RESOURCE_AND_MANAGEMENT_GRANTS';
  credentialDelegation: 'NOT_EVALUATED';
  managementQualification: {
    required: boolean;
    allowed: boolean;
    grants: { permissionId: string; permissionName: string; roleId: string; roleName: string }[];
  };
  matchedPermissions: {
    permissionId: string;
    name: string;
    accessMode: string;
    scopePreset?: string;
    roles: { roleId: string; name: string }[];
  }[];
};

export async function explainAuthorization(domain: AuthorizationDomain, query: ExplanationQuery) {
  try {
    const base = domain.kind === 'PLATFORM' ? '/api/v1/platform'
      : `/api/v1/enterprises/${encodeURIComponent(domain.id)}`;
    const response = await axios.get(
      `${base}/authorization/explanation`,
      { params: query }
    );
    return readEnvelope(response.data, 200) as AuthorizationExplanation;
  } catch (error) {
    if (!axios.isAxiosError(error)) throw error;
    throw readFailure(error.response?.data, error.response?.status || 0,
      String(error.response?.headers?.['x-request-id'] || ''));
  }
}
