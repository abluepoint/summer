import axios from 'axios';
import { readEnvelope, readFailure } from '../customer/contract.mjs';

export type AuditEvent = {
  eventId: string;
  tenantId: string;
  organizationId: string | null;
  principal: string;
  resource: string;
  action: string;
  result: 'SUCCESS' | 'FAILURE';
  requestId: string | null;
  detail: Record<string, unknown>;
  occurredAt: string;
};

export type AuditPage = {
  items: AuditEvent[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export class AuditApiError extends Error {
  status: number;
  code: string;
  requestId: string;

  constructor(status: number, code: string, message: string, requestId: string) {
    super(message || `HTTP ${status || 'network'}`);
    this.name = 'AuditApiError';
    this.status = status;
    this.code = code;
    this.requestId = requestId;
  }
}

async function request<T>(call: () => Promise<{ data: unknown; status: number }>) {
  try {
    const response = await call();
    return readEnvelope(response.data, response.status) as T;
  } catch (error) {
    if (!axios.isAxiosError(error)) throw error;
    const status = error.response?.status || 0;
    const failure = readFailure(
      error.response?.data,
      status,
      String(error.response?.headers?.['x-request-id'] || '')
    );
    throw new AuditApiError(
      failure.status,
      failure.code,
      failure.message,
      failure.requestId
    );
  }
}

export function listAuditEvents(params: {
  page: number;
  size: number;
  principal?: string;
  resource?: string;
  action?: string;
  result?: string;
  requestId?: string;
}) {
  return request<AuditPage>(() => axios.get('/api/audit-events', { params }));
}

export function getAuditEvent(eventId: string) {
  return request<AuditEvent>(() => axios.get(`/api/audit-events/${encodeURIComponent(eventId)}`));
}

export async function exportAuditEvents(params: {
  principal?: string;
  resource?: string;
  action?: string;
  result?: string;
  requestId?: string;
}) {
  try {
    const response = await axios.get('/api/audit-events/export', { params, responseType: 'blob' });
    return response.data as Blob;
  } catch (error) {
    if (!axios.isAxiosError(error)) throw error;
    const status = error.response?.status || 0;
    const failure = readFailure(
      error.response?.data,
      status,
      String(error.response?.headers?.['x-request-id'] || '')
    );
    throw new AuditApiError(failure.status, failure.code, failure.message, failure.requestId);
  }
}
