import axios from 'axios';
import { SummerApiError } from '@/api/errors';
import { businessData } from '@/api/client';
import type { ApiFailure } from '@/api/types';

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
  constructor(failure: ApiFailure) {
    super(failure);
    this.name = 'SessionApiError';
  }
}

async function request<T>(call: () => Promise<{ data: unknown; status: number }>): Promise<T> {
  try {
    return await businessData<T>(call);
  } catch (error) {
    if (!(error instanceof SummerApiError)) throw error;
    const failure = error;
    throw new SessionApiError(failure);
  }
}

export function listSessions(principal?: string) {
  return request<SessionPage>(() => axios.get('/api/v1/sessions', principal ? { params: { principal } } : undefined));
}

export function revokeSession(sessionRef: string) {
  return request<{ sessionRef: string; status: string; jwtStatus: string }>(() => axios.delete(`/api/v1/sessions/${encodeURIComponent(sessionRef)}`));
}
