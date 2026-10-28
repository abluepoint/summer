import type { AuthUser, UserFailure, CreateUser } from './types';
export type { AuthUser, UserFailure, CreateUser } from './types';
import axios from 'axios';
import { businessData } from '@/api/client';
async function request<T>(call: () => Promise<{ data: unknown; status: number }>): Promise<T> {
  try { return await businessData<T>(call); }
  catch (error) {
    const failure = error as UserFailure;
    // Validation errors can include rejected passwords. Retain metadata only.
    throw { status: failure?.status || 0, requestId: failure?.requestId || '' } as UserFailure;
  }
}
export const listUsers = () => request<AuthUser[]>(() => axios.get('/api/users'));
export const createUser = (body: CreateUser) => request<AuthUser>(() => axios.post('/api/users', body));
export const setUserActive = (username: string, active: boolean) => request<AuthUser>(() => axios.patch(`/api/users/${encodeURIComponent(username)}`, { active }));
