import axios from 'axios';
import { readEnvelope, readFailure } from '@/pages/customer/contract.mjs';
import { MenuManifest, VisibleMenuItem } from '@/menu/api';
import { MenuManifestItem } from '@/menu/manifest';
export type MenuError = { status: number; code: string; message: string; requestId: string };
export type MenuOverride = Pick<VisibleMenuItem, 'enabled' | 'parentMenuId' | 'sortOrder' | 'capabilityCode'>;
export type PlatformManifest = { manifestVersion: number; items: MenuManifestItem[] };
async function request<T>(call: () => Promise<{ data: unknown; status: number }>): Promise<T> {
  try { const response = await call(); return readEnvelope(response.data, response.status) as T; }
  catch (error) {
    if (!axios.isAxiosError(error)) throw error;
    throw readFailure(error.response?.data, error.response?.status || 0, String(error.response?.headers?.['x-request-id'] || '')) as MenuError;
  }
}
export const getManagedManifest = (enterprise: string) => request<MenuManifest>(() => axios.get(`/api/v1/enterprises/${encodeURIComponent(enterprise)}/menu-manifest/management`));
export const getPlatformManifest = () => request<MenuManifest>(() => axios.get('/api/v1/platform/menu-manifest'));
export const saveOverride = (enterprise: string, menu: string, body: MenuOverride, version: number, key: string) => request<VisibleMenuItem>(() => axios.patch(`/api/v1/enterprises/${encodeURIComponent(enterprise)}/menu-manifest`, body, { params: { menuId: menu }, headers: { 'If-Match': `"${version}"`, 'Idempotency-Key': key } }));
export const savePlatformManifest = (body: PlatformManifest, key: string) => request<MenuManifest>(() => axios.put('/api/v1/platform/menu-manifest', body, { headers: { 'Idempotency-Key': key } }));
