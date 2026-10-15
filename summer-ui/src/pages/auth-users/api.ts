import axios from 'axios';
import { readEnvelope, readFailure } from '../customer/contract.mjs';
export type AuthUser = { username: string; role: 'ADMIN' | 'USER'; active: boolean };
export type UserFailure = { status: number; requestId: string };
export type CreateUser = { username: string; password: string; role: AuthUser['role'] };
async function request<T>(call: () => Promise<{ data: unknown; status: number }>): Promise<T> {
  try { const response = await call(); return readEnvelope(response.data, response.status) as T; }
  catch (error) {
    const response = axios.isAxiosError(error) ? error.response : undefined;
    const failure = response ? readFailure(response.data, response.status, String(response.headers?.['x-request-id'] || '')) : error as UserFailure;
    // Validation errors can include rejected passwords. Retain metadata only.
    throw { status: failure?.status || 0, requestId: failure?.requestId || '' } as UserFailure;
  }
}
export const listUsers = () => request<AuthUser[]>(() => axios.get('/api/users'));
export const createUser = (body: CreateUser) => request<AuthUser>(() => axios.post('/api/users', body));
export const setUserActive = (username: string, active: boolean) => request<AuthUser>(() => axios.patch(`/api/users/${encodeURIComponent(username)}`, { active }));
