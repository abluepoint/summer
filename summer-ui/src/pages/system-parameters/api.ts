import axios from 'axios';
import { businessData } from '@/api/client';
import { SummerApiError } from '@/api/errors';

export type Parameter = {
  id: string; key: string; type: 'STRING' | 'INTEGER' | 'DECIMAL' | 'BOOLEAN'; value: unknown;
  required: boolean; min?: number; max?: number; pattern?: string; public: boolean;
  description?: string; status: 'ACTIVE' | 'INACTIVE'; version?: number;
  revision?: number; state?: 'DRAFT' | 'PUBLISHED' | 'ROLLED_BACK' | 'INACTIVE';
};
export type ParameterSummary = Pick<Parameter, 'id' | 'key' | 'type' | 'status' | 'public'>;
export type ParameterPage = { items: Array<Parameter | ParameterSummary>; page: number; size: number; totalElements: number; totalPages: number; projection: 'summary' | 'detail' };
export type ParameterEvent = {
  eventId: string; parameterKey: string; eventType: string; version: number; requestId?: string;
  status: 'PENDING' | 'PUBLISHED' | 'DEAD_LETTER'; attempts: number; nextAttemptAt: string;
  lastError?: string; createdAt: string; updatedAt: string;
  event: { eventId: string; parameterKey: string; eventType: string; version: number; requestId?: string };
};
export type ParameterEventPage = { items: ParameterEvent[]; totalElements: number };
export type ParameterVersion = { revision: number; state: 'DRAFT' | 'PUBLISHED' | 'ROLLED_BACK' | 'INACTIVE'; parameterKey: string; baseVersion: number; principal: string; createdAt: string; value: unknown; status: 'ACTIVE' | 'INACTIVE'; currentVersion: number };
export type ParameterVersionPage = { items: ParameterVersion[]; currentVersion: number; currentState: ParameterVersion['state'] };
export type ParameterListQuery = {
  page: number; size: number; key?: string; type?: Parameter['type']; status?: Parameter['status'];
  public?: boolean; sort: 'key' | 'type' | 'status' | 'createdAt' | 'updatedAt' | 'version';
  direction: 'asc' | 'desc'; projection: 'summary' | 'detail';
};

export type ParameterViolation = { field: string; message: string };
export class ParameterApiError extends Error {
  status: number; code: string; requestId: string; violations: ParameterViolation[];
  constructor(status: number, code: string, message: string, requestId: string, violations: unknown[] = []) {
    super(message); this.name = 'ParameterApiError'; this.status = status; this.code = code; this.requestId = requestId;
    this.violations = violations.flatMap((violation) => {
      if (!violation || typeof violation !== 'object') return [];
      const { field, message: detail } = violation as Record<string, unknown>;
      return typeof field === 'string' && typeof detail === 'string' ? [{ field, message: detail }] : [];
    });
  }
}

async function request<T>(call: () => Promise<{ data: unknown; status: number }>): Promise<T> {
  try { return await businessData<T>(call); }
  catch (error) {
    if (!(error instanceof SummerApiError)) throw error;
    const failure = error;
    throw new ParameterApiError(failure.status, failure.code, failure.message, failure.requestId, failure.violations);
  }
}

const parameterRoot = (enterpriseId?: string) => enterpriseId ? `/api/v1/enterprises/${encodeURIComponent(enterpriseId)}/system-parameters` : '/api/system-parameters';
const eventRoot = (enterpriseId?: string) => enterpriseId ? `/api/v1/enterprises/${encodeURIComponent(enterpriseId)}/system-parameter-events` : '/api/system-parameter-events';

export const listParameters = (params: ParameterListQuery, enterpriseId?: string) =>
  request<ParameterPage>(() => axios.get(parameterRoot(enterpriseId), { params }));
export const createParameter = (payload: Record<string, unknown>, enterpriseId?: string) =>
  request<Parameter>(() => axios.post(parameterRoot(enterpriseId), payload));
export const updateParameter = (key: string, payload: Record<string, unknown>, enterpriseId?: string) =>
  request<Parameter>(() => axios.patch(`${parameterRoot(enterpriseId)}/${encodeURIComponent(key)}`, payload));
export const setParameterStatus = (key: string, status: 'ACTIVE' | 'INACTIVE', enterpriseId?: string) =>
  request<Parameter>(() => axios.post(`${parameterRoot(enterpriseId)}/${encodeURIComponent(key)}/${status === 'ACTIVE' ? 'enable' : 'disable'}`));
export const listParameterEvents = (key?: string, enterpriseId?: string) =>
  request<ParameterEventPage>(() => axios.get(eventRoot(enterpriseId), { params: key ? { key } : undefined }));
export const retryParameterEvent = (eventId: string, enterpriseId?: string) =>
  request<ParameterEvent>(() => axios.post(`${eventRoot(enterpriseId)}/${encodeURIComponent(eventId)}/retry`));
export const listParameterVersions = (key: string, enterpriseId?: string) =>
  request<ParameterVersionPage>(() => axios.get(`${parameterRoot(enterpriseId)}/${encodeURIComponent(key)}/versions`));
export const saveParameterDraft = (key: string, payload: Record<string, unknown>, enterpriseId?: string) =>
  request<Parameter>(() => axios.post(`${parameterRoot(enterpriseId)}/${encodeURIComponent(key)}/draft`, payload));
export const publishParameterVersion = (key: string, payload: Record<string, unknown>, enterpriseId?: string) =>
  request<Parameter>(() => axios.post(`${parameterRoot(enterpriseId)}/${encodeURIComponent(key)}/publish`, payload));
export const rollbackParameterVersion = (key: string, payload: Record<string, unknown>, enterpriseId?: string) =>
  request<Parameter>(() => axios.post(`${parameterRoot(enterpriseId)}/${encodeURIComponent(key)}/rollback`, payload));
export const deactivateParameterVersion = (key: string, payload: Record<string, unknown>, enterpriseId?: string) =>
  request<Parameter>(() => axios.post(`${parameterRoot(enterpriseId)}/${encodeURIComponent(key)}/deactivate`, payload));

export const saveParameterOverride = (key: string, organization: string, payload: Record<string, unknown>, enterpriseId?: string) =>
  request<{ version: number }>(() => axios.put(`${parameterRoot(enterpriseId)}/${encodeURIComponent(key)}/overrides/${encodeURIComponent(organization)}`, payload));

export function parameterApiForEnterprise(enterpriseId: string) {
  if (!enterpriseId) throw new Error('请选择活动租户');
  return {
    listParameters: (query: ParameterListQuery) => listParameters(query, enterpriseId),
    createParameter: (body: Record<string, unknown>) => createParameter(body, enterpriseId),
    updateParameter: (key: string, body: Record<string, unknown>) => updateParameter(key, body, enterpriseId),
    setParameterStatus: (key: string, status: 'ACTIVE' | 'INACTIVE') => setParameterStatus(key, status, enterpriseId),
    listParameterEvents: (key?: string) => listParameterEvents(key, enterpriseId),
    retryParameterEvent: (id: string) => retryParameterEvent(id, enterpriseId),
    listParameterVersions: (key: string) => listParameterVersions(key, enterpriseId),
    saveParameterDraft: (key: string, body: Record<string, unknown>) => saveParameterDraft(key, body, enterpriseId),
    publishParameterVersion: (key: string, body: Record<string, unknown>) => publishParameterVersion(key, body, enterpriseId),
    rollbackParameterVersion: (key: string, body: Record<string, unknown>) => rollbackParameterVersion(key, body, enterpriseId),
    deactivateParameterVersion: (key: string, body: Record<string, unknown>) => deactivateParameterVersion(key, body, enterpriseId),
    saveParameterOverride: (key: string, scope: string, body: Record<string, unknown>) => saveParameterOverride(key, scope, body, enterpriseId),
  };
}
