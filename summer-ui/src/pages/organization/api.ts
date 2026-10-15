import axios from 'axios';
import { readEnvelope, readFailure } from '../customer/contract.mjs';

export type OrganizationUnit = { id: string; enterpriseId: string; parentId: string | null; unitType: 'COMPANY' | 'DEPARTMENT' | 'TEAM'; name: string; code: string; leaderMemberId?: string; description?: string; sortOrder: number; status: 'ACTIVE' | 'DISABLED'; memberCount: number; children: OrganizationUnit[] };
export type OrganizationTree = { enterpriseId: string; revision: string; items: OrganizationUnit[] };
export type OrganizationMember = { memberId: string; displayName: string; email: string; status: string; primary: boolean };
export type OrganizationApiError = { status: number; code: string; message: string; requestId: string };

async function request<T>(call: () => Promise<{ data: unknown; status: number }>): Promise<T> {
  try { const response = await call(); return response.status === 204 ? undefined as T : readEnvelope(response.data, response.status) as T; }
  catch (error) {
    if (!axios.isAxiosError(error)) throw error;
    const failure = readFailure(error.response?.data, error.response?.status || 0, String(error.response?.headers?.['x-request-id'] || ''));
    throw failure as OrganizationApiError;
  }
}

export const getOrganizationTree = (enterpriseId: string) => request<OrganizationTree>(() => axios.get(`/api/v1/enterprises/${encodeURIComponent(enterpriseId)}/org-tree`));
export const getOrganizationContext = (enterpriseId: string) => request<{ enterpriseId: string; organizationId: string | null }>(() => axios.get(`/api/v1/enterprises/${encodeURIComponent(enterpriseId)}/context`));
export const selectOrganizationContext = (enterpriseId: string, organizationId: string | null) => request<{ enterpriseId: string; organizationId: string | null }>(() => axios.put(`/api/v1/enterprises/${encodeURIComponent(enterpriseId)}/context`, { organizationId }));
export const getOrganizationMembers = (enterpriseId: string, organizationId: string) => request<{ items: OrganizationMember[] }>(() => axios.get(`/api/v1/enterprises/${encodeURIComponent(enterpriseId)}/org-units/${encodeURIComponent(organizationId)}/members`));

export type UnitDraft = Pick<OrganizationUnit, 'parentId' | 'unitType' | 'name' | 'code' | 'leaderMemberId' | 'description' | 'sortOrder' | 'status'>;
export type TenantMember = { memberId: string; displayName: string; email: string; status: string };
export type MemberDetail = TenantMember & { enterpriseId: string; revision: string };
export type MemberPage = { items: TenantMember[]; page: number; size: number; total: number; revision: string };
export type AssignmentDraft = { memberIds: string[]; organizationIds: string[]; primaryOrganizationId: string | null };
const root = (enterprise: string) => `/api/v1/enterprises/${encodeURIComponent(enterprise)}`;
const units = (enterprise: string) => `${root(enterprise)}/org-units`;
const headers = (key: string, etag?: string) => ({ 'Idempotency-Key': key, ...(etag ? { 'If-Match': etag } : {}) });
export const getOrganizationDetail = (enterprise: string, id: string) => request<{ unit: OrganizationUnit; etag: string }>(async () => {
  const response = await axios.get(`${units(enterprise)}/${encodeURIComponent(id)}`);
  return { ...response, data: { success: true, data: { unit: readEnvelope(response.data, response.status), etag: String(response.headers.etag || '') } } };
});
export const createOrganization = (enterprise: string, body: UnitDraft, key: string) => request<OrganizationUnit>(() => axios.post(units(enterprise), body, { headers: headers(key) }));
export const updateOrganization = (enterprise: string, id: string, body: UnitDraft, etag: string, key: string) => request<OrganizationUnit>(() => axios.patch(`${units(enterprise)}/${encodeURIComponent(id)}`, body, { headers: headers(key, etag) }));
export const deleteOrganization = (enterprise: string, id: string, etag: string, key: string) => request<void>(() => axios.delete(`${units(enterprise)}/${encodeURIComponent(id)}`, { headers: headers(key, etag) }));
export const assignOrganizations = (enterprise: string, body: AssignmentDraft, revision: string, key: string) => request<unknown>(() => axios.put(`${root(enterprise)}/org-assignments:batch`, body, { headers: headers(key, `"${revision}"`) }));
export const listTenantMembers = (enterprise: string, params: { page: number; size: number; q?: string; status?: string }) => request<MemberPage>(() => axios.get(`${root(enterprise)}/members`, { params }));
export const getTenantMember = (enterprise: string, id: string) => request<MemberDetail>(() => axios.get(`${root(enterprise)}/members/${encodeURIComponent(id)}`));
export const changeMemberStatus = (enterprise: string, id: string, action: 'enable' | 'disable' | 'leave', revision: string, key: string) => request<MemberDetail>(() => axios.post(`${root(enterprise)}/members/${encodeURIComponent(id)}:${action}`, undefined, { headers: headers(key, `"${revision}"`) }));
