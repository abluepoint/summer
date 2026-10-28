import { businessData as request } from '@/api/client';
import axios from 'axios';

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

export const listAuthorizationChangeEvents = (enterpriseId: string) => request<{ items: AuthorizationChangeEvent[]; totalElements: number }>(() => axios.get('/api/authorization-events', { params: { enterpriseId } }));
export const retryAuthorizationChangeEvent = (enterpriseId: string, eventId: string) => request<AuthorizationChangeEvent>(() => axios.post(`/api/authorization-events/${encodeURIComponent(eventId)}/retry`, null, { params: { enterpriseId } }));
