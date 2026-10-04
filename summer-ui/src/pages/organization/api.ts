import axios from 'axios';
import { readEnvelope, readFailure } from '../customer/contract.mjs';

export type OrganizationUnit = { id: string; enterpriseId: string; parentId: string | null; unitType: string; name: string; code: string; memberCount: number; children: OrganizationUnit[] };
export type OrganizationTree = { enterpriseId: string; revision: string; items: OrganizationUnit[] };
export type OrganizationMember = { memberId: string; displayName: string; email: string; status: string; primary: boolean };
export type OrganizationApiError = { status: number; code: string; message: string; requestId: string };

async function request<T>(call: () => Promise<{ data: unknown; status: number }>): Promise<T> {
  try { return readEnvelope((await call()).data, 200) as T; }
  catch (error) {
    if (!axios.isAxiosError(error)) throw error;
    const failure = readFailure(error.response?.data, error.response?.status || 0, String(error.response?.headers?.['x-request-id'] || ''));
    throw failure as OrganizationApiError;
  }
}

export const getOrganizationTree = (enterpriseId: string) => request<OrganizationTree>(() => axios.get(`/api/v1/enterprises/${encodeURIComponent(enterpriseId)}/org-tree`));
export const selectOrganizationContext = (enterpriseId: string, organizationId: string | null) => request<{ enterpriseId: string; organizationId: string | null }>(() => axios.put(`/api/v1/enterprises/${encodeURIComponent(enterpriseId)}/context`, { organizationId }));
export const getOrganizationMembers = (enterpriseId: string, organizationId: string) => request<{ items: OrganizationMember[] }>(() => axios.get(`/api/v1/enterprises/${encodeURIComponent(enterpriseId)}/org-units/${encodeURIComponent(organizationId)}/members`));
