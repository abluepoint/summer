import type { ApiFailure } from '@/api/types';
import { businessData as request } from '@/api/client';
import axios from 'axios';

export type DataScopeField = 'TENANT' | 'ORGANIZATION' | 'DEPARTMENT' | 'ORGANIZATION_TREE' | 'SELF' | 'CREATOR' | 'OWNER';
export type DataScopeRule = { type: DataScopeField; field?: DataScopeField; operator?: 'EQ' | 'IN' | 'NE' | 'NOT_IN' | 'CONTAINS'; value?: unknown; effect?: 'ALLOW' | 'DENY'; children?: DataScopeRule[] };
export type DataScopeCondition = { field?: DataScopeField; operator?: 'AND' | 'OR' | 'EQ' | 'NE' | 'IN' | 'NOT_IN'; value?: unknown; effect?: 'ALLOW' | 'DENY'; children?: DataScopeCondition[] };
export type DataScopePolicy = { resource: string; enterpriseId: string; operator: 'AND' | 'OR'; rules: DataScopeRule[]; conditions?: DataScopeCondition | null; version: number; updatedAt: string; checksum?: string };
export type DataScopePreview = { enterpriseId: string; resource: string; subject: string; allowed: boolean; decision: 'ALLOW' | 'DENY'; reasonCode: string; explanation: string; matchedConditions: string[]; deniedConditions: string[]; policyVersion: number; checksum: string; effectiveScope: Record<string, unknown> };
export type DataScopeApiError = Pick<ApiFailure, 'status' | 'code' | 'message' | 'requestId'>;
export const listDataScopes = (enterpriseId: string) => request<DataScopePolicy[]>(() => axios.get(`/api/v1/enterprises/${encodeURIComponent(enterpriseId)}/data-scopes`));
export const saveDataScope = (enterpriseId: string, resource: string, operator: 'AND' | 'OR', rules: DataScopeRule[], conditions?: DataScopeCondition) => request<DataScopePolicy>(() => axios.put(`/api/v1/enterprises/${encodeURIComponent(enterpriseId)}/data-scopes/${encodeURIComponent(resource)}`, { operator, rules, conditions }));
export const previewDataScope = (enterpriseId: string, resource: string, subject: string, organizationId?: string) => request<DataScopePreview>(() => axios.post(`/api/v1/enterprises/${encodeURIComponent(enterpriseId)}/data-scopes/${encodeURIComponent(resource)}:preview`, { subject, organizationId }));
export const listDataScopeAudit = (enterpriseId: string, resource: string) => request<Array<{ action: string; version: number; checksum: string; occurredAt: string }>>(() => axios.get(`/api/v1/enterprises/${encodeURIComponent(enterpriseId)}/data-scopes/${encodeURIComponent(resource)}/audit`));

export const saveDataScopeBinding = (enterpriseId: string, resource: string, kind: 'ROLE' | 'USER', subjectId: string, conditions: DataScopeCondition) => request<unknown>(() => axios.put(`/api/v1/enterprises/${encodeURIComponent(enterpriseId)}/data-scopes/${encodeURIComponent(resource)}/bindings/${kind}/${encodeURIComponent(subjectId)}`, { operator: 'AND', rules: [], conditions }));
