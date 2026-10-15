import axios from 'axios';
import { readEnvelope, readFailure } from '../customer/contract.mjs';

export type AuthorizationRole = {
  roleId: string;
  enterpriseId: string;
  name: string;
  code: string;
  status: string;
  capabilityIds: string[];
  version: number;
};
export type AuthorizationResource = {
  httpMethod: string;
  normalizedPath: string;
  permissionCode: string;
  resourceType: string;
  configurable: boolean;
};
export type UserRoleAssignment = {
  username: string;
  enterpriseId: string;
  roleIds: string[];
  deniedCapabilityCodes: string[];
  version: number;
};
export type AuthorizationApiError = { status: number; code: string; message: string; requestId: string };

async function request<T>(call: () => Promise<{ data: unknown; status: number }>): Promise<T> {
  try { return readEnvelope((await call()).data, 200) as T; }
  catch (error) {
    if (!axios.isAxiosError(error)) throw error;
    throw readFailure(error.response?.data, error.response?.status || 0, String(error.response?.headers?.['x-request-id'] || '')) as AuthorizationApiError;
  }
}

export const listRoles = (enterpriseId: string) => request<AuthorizationRole[]>(() => axios.get(`/api/v1/enterprises/${encodeURIComponent(enterpriseId)}/roles`));
export const listAuthorizationResources = () => request<{ items: AuthorizationResource[] }>(() => axios.get('/api/v1/platform/interface-resources'));
export const createRole = (enterpriseId: string, name: string, code: string, capabilityIds: string[]) => request<AuthorizationRole>(() => axios.post(`/api/v1/enterprises/${encodeURIComponent(enterpriseId)}/roles`, { name, code, capabilityIds }, { headers: { 'Idempotency-Key': `ui-role-${Date.now()}` } }));
export const getUserRoleAssignment = (enterpriseId: string, username: string) => request<UserRoleAssignment>(() => axios.get(`/api/v1/enterprises/${encodeURIComponent(enterpriseId)}/users/${encodeURIComponent(username)}/roles`)).catch((error: AuthorizationApiError) => {
  if (error.status === 409 && error.code === 'SUMMER_AUTHORIZATION_CONFLICT') return null;
  throw error;
});
export const assignUserRoles = (enterpriseId: string, username: string, roleIds: string[], deniedCapabilityCodes: string[], version: number) => request<UserRoleAssignment>(() => axios.put(`/api/v1/enterprises/${encodeURIComponent(enterpriseId)}/users/${encodeURIComponent(username)}/roles`, { roleIds, deniedCapabilityCodes }, { headers: { 'If-Match': `"${version}"`, 'Idempotency-Key': `ui-assignment-${Date.now()}` } }));
