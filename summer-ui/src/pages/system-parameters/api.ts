import axios from 'axios';
import { readEnvelope, readFailure } from '../customer/contract.mjs';

export type Parameter = {
  id: string; key: string; type: 'STRING' | 'INTEGER' | 'DECIMAL' | 'BOOLEAN'; value: unknown;
  required: boolean; min?: number; max?: number; pattern?: string; public: boolean;
  description?: string; status: 'ACTIVE' | 'INACTIVE'; version: number;
};
export type ParameterPage = { items: Parameter[]; page: number; size: number; totalElements: number; totalPages: number };

export class ParameterApiError extends Error {
  status: number; code: string; requestId: string;
  constructor(status: number, code: string, message: string, requestId: string) {
    super(message); this.name = 'ParameterApiError'; this.status = status; this.code = code; this.requestId = requestId;
  }
}

async function request<T>(call: () => Promise<{ data: unknown; status: number }>): Promise<T> {
  try { const response = await call(); return readEnvelope(response.data, response.status) as T; }
  catch (error) {
    if (!axios.isAxiosError(error)) throw error;
    const status = error.response?.status || 0;
    const failure = readFailure(error.response?.data, status, String(error.response?.headers?.['x-request-id'] || ''));
    throw new ParameterApiError(failure.status, failure.code, failure.message, failure.requestId);
  }
}

export const listParameters = (params: { page: number; size: number; type?: string; status?: string }) =>
  request<ParameterPage>(() => axios.get('/api/system-parameters', { params }));
export const createParameter = (payload: Record<string, unknown>) =>
  request<Parameter>(() => axios.post('/api/system-parameters', payload));
export const updateParameter = (key: string, payload: Record<string, unknown>) =>
  request<Parameter>(() => axios.patch(`/api/system-parameters/${encodeURIComponent(key)}`, payload));
export const setParameterStatus = (key: string, status: 'ACTIVE' | 'INACTIVE') =>
  request<Parameter>(() => axios.post(`/api/system-parameters/${encodeURIComponent(key)}/${status === 'ACTIVE' ? 'enable' : 'disable'}`));
