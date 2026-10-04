import axios from 'axios';
import { readEnvelope, readFailure } from '../customer/contract.mjs';

export type DataScopeRule = { type: 'TENANT' | 'ORGANIZATION' | 'DEPARTMENT' | 'ORGANIZATION_TREE' | 'SELF' | 'CREATOR' | 'OWNER' };
export type DataScopePolicy = { resource: string; enterpriseId: string; operator: 'AND' | 'OR'; rules: DataScopeRule[]; version: number; updatedAt: string };
export type DataScopeApiError = { status: number; code: string; message: string; requestId: string };
async function request<T>(call: () => Promise<{ data: unknown; status: number }>): Promise<T> {
  try { return readEnvelope((await call()).data, 200) as T; }
  catch (error) { if (!axios.isAxiosError(error)) throw error; throw readFailure(error.response?.data, error.response?.status || 0, String(error.response?.headers?.['x-request-id'] || '')) as DataScopeApiError; }
}
export const listDataScopes = (enterpriseId: string) => request<DataScopePolicy[]>(() => axios.get(`/api/v1/enterprises/${encodeURIComponent(enterpriseId)}/data-scopes`));
export const saveDataScope = (enterpriseId: string, resource: string, operator: 'AND' | 'OR', rules: DataScopeRule[]) => request<DataScopePolicy>(() => axios.put(`/api/v1/enterprises/${encodeURIComponent(enterpriseId)}/data-scopes/${encodeURIComponent(resource)}`, { operator, rules }));
