import axios from 'axios';
import { readEnvelope, readFailure } from '../customer/contract.mjs';

export type DataScopeField = 'TENANT' | 'ORGANIZATION' | 'DEPARTMENT' | 'ORGANIZATION_TREE' | 'SELF' | 'CREATOR' | 'OWNER';
export type DataScopeRule = { type: DataScopeField; field?: DataScopeField; operator?: 'EQ' | 'IN' | 'NE' | 'NOT_IN' | 'CONTAINS'; value?: unknown; effect?: 'ALLOW' | 'DENY'; children?: DataScopeRule[] };
export type DataScopeCondition = { field?: DataScopeField; operator?: 'AND' | 'OR' | 'EQ' | 'NE' | 'IN' | 'NOT_IN'; value?: unknown; effect?: 'ALLOW' | 'DENY'; children?: DataScopeCondition[] };
export type DataScopePolicy = { resource: string; enterpriseId: string; operator: 'AND' | 'OR'; rules: DataScopeRule[]; conditions?: DataScopeCondition | null; version: number; updatedAt: string; checksum?: string };
export type DataScopePreview = { enterpriseId: string; resource: string; subject: string; allowed: boolean; decision: 'ALLOW' | 'DENY'; reasonCode: string; explanation: string; matchedConditions: string[]; deniedConditions: string[]; policyVersion: number; checksum: string; effectiveScope: Record<string, unknown> };
export type DataScopeApiError = { status: number; code: string; message: string; requestId: string };
async function request<T>(call: () => Promise<{ data: unknown; status: number }>): Promise<T> { try { return readEnvelope((await call()).data, 200) as T; } catch (error) { if (!axios.isAxiosError(error)) throw error; throw readFailure(error.response?.data, error.response?.status || 0, String(error.response?.headers?.['x-request-id'] || '')) as DataScopeApiError; } }
export const listDataScopes = (enterpriseId: string) => request<DataScopePolicy[]>(() => axios.get(`/api/v1/enterprises/${encodeURIComponent(enterpriseId)}/data-scopes`));
export const saveDataScope = (enterpriseId: string, resource: string, operator: 'AND' | 'OR', rules: DataScopeRule[], conditions?: DataScopeCondition) => request<DataScopePolicy>(() => axios.put(`/api/v1/enterprises/${encodeURIComponent(enterpriseId)}/data-scopes/${encodeURIComponent(resource)}`, { operator, rules, conditions }));
export const previewDataScope = (enterpriseId: string, resource: string, subject: string, organizationId?: string) => request<DataScopePreview>(() => axios.post(`/api/v1/enterprises/${encodeURIComponent(enterpriseId)}/data-scopes/${encodeURIComponent(resource)}:preview`, { subject, organizationId }));
export const listDataScopeAudit = (enterpriseId: string, resource: string) => request<Array<{ action: string; version: number; checksum: string; occurredAt: string }>>(() => axios.get(`/api/v1/enterprises/${encodeURIComponent(enterpriseId)}/data-scopes/${encodeURIComponent(resource)}/audit`));

export const saveDataScopeBinding = (enterpriseId: string, resource: string, kind: 'ROLE' | 'USER', subjectId: string, conditions: DataScopeCondition) => request<unknown>(() => axios.put(`/api/v1/enterprises/${encodeURIComponent(enterpriseId)}/data-scopes/${encodeURIComponent(resource)}/bindings/${kind}/${encodeURIComponent(subjectId)}`, { operator: 'AND', rules: [], conditions }));
