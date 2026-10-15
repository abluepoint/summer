import axios from 'axios';
import { readEnvelope, readFailure } from '@/pages/customer/contract.mjs';
import { MenuManifestItem } from './manifest';

export type VisibleMenuItem = MenuManifestItem & {
  enabled: boolean;
  actions: string[];
  version: number;
};
export type MenuManifest = { manifestVersion: number; items: VisibleMenuItem[] };

async function request<T>(call: () => Promise<{ data: unknown; status: number }>): Promise<T> {
  try {
    return readEnvelope((await call()).data, 200) as T;
  } catch (error) {
    if (!axios.isAxiosError(error)) throw error;
    throw readFailure(error.response?.data, error.response?.status || 0, String(error.response?.headers?.['x-request-id'] || ''));
  }
}

export function syncMenuManifest(manifest: { manifestVersion: number; items: MenuManifestItem[] }) {
  return request<MenuManifest>(() => axios.put('/api/v1/platform/menu-manifest', manifest, {
    headers: { 'Idempotency-Key': `ui-menu-manifest-${manifest.manifestVersion}` },
  }));
}

export function getVisibleMenuManifest(enterpriseId: string) {
  // A stalled read must expire the previous UI grants and allow the next refresh.
  return request<MenuManifest>(() => axios.get(`/api/v1/enterprises/${encodeURIComponent(enterpriseId)}/menu-manifest`, { timeout: 10000 }));
}

export function updateMenuManifestOverride(enterpriseId: string, item: string, patch: Partial<Pick<VisibleMenuItem, 'enabled' | 'parentMenuId' | 'sortOrder' | 'capabilityCode'>>, version: number) {
  return request<VisibleMenuItem>(() => axios.patch(`/api/v1/enterprises/${encodeURIComponent(enterpriseId)}/menu-manifest/${encodeURIComponent(item)}`, patch, {
    headers: { 'If-Match': `"${version}"`, 'Idempotency-Key': `ui-menu-override-${item}-${Date.now()}` },
  }));
}
