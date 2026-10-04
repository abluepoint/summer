import axios from 'axios';
import { SummerApiError } from '../customer/api';
import { readEnvelope, readFailure } from '../customer/contract.mjs';

export type SessionItem = {
  sessionRef: string;
  principal: string;
  clientId: string;
  status: 'ACTIVE' | 'REVOKED';
  createdAt: string;
  lastSeenAt: string;
  expiresAt: string;
  current: boolean;
};

export type SessionPage = { availability: 'AVAILABLE' | 'UNAVAILABLE'; items: SessionItem[] };

export class SessionApiError extends SummerApiError {
  constructor(failure: { status: number; code: string; message: string; violations: unknown[]; requestId: string; timestamp: string }) {
    super(failure);
    this.name = 'SessionApiError';
  }
}

async function request<T>(call: () => Promise<{ data: unknown; status: number }>): Promise<T> {
  try {
    return readEnvelope((await call()).data, 200) as T;
  } catch (error) {
    if (error instanceof SummerApiError) throw error;
    if (!axios.isAxiosError(error)) throw error;
    const status = error.response?.status || 0;
    const failure = readFailure(error.response?.data, status, String(error.response?.headers?.['x-request-id'] || ''));
    throw new SessionApiError(failure);
  }
}

export function listSessions(principal?: string) {
  return request<SessionPage>(() => axios.get('/api/v1/sessions', principal ? { params: { principal } } : undefined));
}

export function revokeSession(sessionRef: string) {
  return request<{ sessionRef: string; status: string; jwtStatus: string }>(() => axios.delete(`/api/v1/sessions/${encodeURIComponent(sessionRef)}`));
}
