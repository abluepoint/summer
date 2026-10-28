import type { ApiFailure } from '@/api/types';
import { businessData as request } from '@/api/client';
import axios from 'axios';

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
export type AuthorizationApiError = Pick<ApiFailure, 'status' | 'code' | 'message' | 'requestId'>;

export const listRoles = (enterpriseId: string) => request<AuthorizationRole[]>(() => axios.get(`/api/v1/enterprises/${encodeURIComponent(enterpriseId)}/roles`));
export const listAuthorizationResources = () => request<{ items: AuthorizationResource[] }>(() => axios.get('/api/v1/platform/interface-resources'));
export const createRole = (enterpriseId: string, name: string, code: string, capabilityIds: string[]) => request<AuthorizationRole>(() => axios.post(`/api/v1/enterprises/${encodeURIComponent(enterpriseId)}/roles`, { name, code, capabilityIds }, { headers: { 'Idempotency-Key': `ui-role-${Date.now()}` } }));
export const getUserRoleAssignment = (enterpriseId: string, username: string) => request<UserRoleAssignment>(() => axios.get(`/api/v1/enterprises/${encodeURIComponent(enterpriseId)}/users/${encodeURIComponent(username)}/roles`)).catch((error: AuthorizationApiError) => {
  if (error.status === 409 && error.code === 'SUMMER_AUTHORIZATION_CONFLICT') return null;
  throw error;
});
export const assignUserRoles = (enterpriseId: string, username: string, roleIds: string[], deniedCapabilityCodes: string[], version: number) => request<UserRoleAssignment>(() => axios.put(`/api/v1/enterprises/${encodeURIComponent(enterpriseId)}/users/${encodeURIComponent(username)}/roles`, { roleIds, deniedCapabilityCodes }, { headers: { 'If-Match': `"${version}"`, 'Idempotency-Key': `ui-assignment-${Date.now()}` } }));
