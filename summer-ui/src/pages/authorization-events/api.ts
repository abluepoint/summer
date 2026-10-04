import axios from 'axios';
import { readEnvelope, readFailure } from '../customer/contract.mjs';

export type AuthorizationChangeEvent = {
  eventId: string;
  enterpriseId: string;
  resourceType: string;
  resourceId: string;
  eventType: string;
  version: number;
  requestId: string | null;
  status: 'PENDING' | 'PUBLISHED' | 'DEAD_LETTER';
  attempts: number;
  lastError: string | null;
  createdAt: string;
};

async function request<T>(call: () => Promise<{ data: unknown; status: number }>): Promise<T> {
  try { return readEnvelope((await call()).data, 200) as T; }
  catch (error) {
    if (!axios.isAxiosError(error)) throw error;
    throw readFailure(error.response?.data, error.response?.status || 0, String(error.response?.headers?.['x-request-id'] || ''));
  }
}

export const listAuthorizationChangeEvents = (enterpriseId: string) => request<{ items: AuthorizationChangeEvent[]; totalElements: number }>(() => axios.get('/api/authorization-events', { params: { enterpriseId } }));
export const retryAuthorizationChangeEvent = (enterpriseId: string, eventId: string) => request<AuthorizationChangeEvent>(() => axios.post(`/api/authorization-events/${encodeURIComponent(eventId)}/retry`, null, { params: { enterpriseId } }));
