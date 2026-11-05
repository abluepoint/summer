import axios from 'axios';
import { readEnvelope, readFailure } from '@/pages/customer/contract.mjs';
import { MenuManifestItem } from './manifest';

export type VisibleMenuItem = MenuManifestItem & {
  enabled: boolean;
  navOnly?: boolean;
  pageEligible?: boolean;
  actions: string[];
  version: number;
};
export type MenuManifest = {
  manifestVersion: number;
  engineMode?: 'LEGACY' | 'MANAGED';
  items: VisibleMenuItem[];
};

async function request<T>(
  call: () => Promise<{ data: unknown; status: number }>
): Promise<T> {
  try {
    return readEnvelope((await call()).data, 200) as T;
  } catch (error) {
    if (!axios.isAxiosError(error)) throw error;
    throw readFailure(
      error.response?.data,
      error.response?.status || 0,
      String(error.response?.headers?.['x-request-id'] || '')
    );
  }
}

export function syncMenuManifest(manifest: {
  manifestVersion: number;
  items: MenuManifestItem[];
}) {
  return request<MenuManifest>(() =>
    axios.put('/api/v1/platform/menu-manifest', manifest, {
      expectedAccessProbe: true,
      headers: {
        'Idempotency-Key': `ui-menu-manifest-${manifest.manifestVersion}`,
      },
    })
  );
}

export async function getVisibleMenuManifest(enterpriseId: string) {
  const base = `/api/v1/enterprises/${encodeURIComponent(enterpriseId)}`;
  const current = await request<MenuManifest>(() =>
    axios.get(`${base}/authorization/navigation`, { timeout: 10000 })
  );
  if (current.engineMode === 'MANAGED') return current;
  if (current.engineMode !== 'LEGACY')
    throw new Error('Unknown navigation authorization mode');
  // Only an explicit authoritative LEGACY response permits this bounded compatibility read.
  const legacy = await request<MenuManifest>(() =>
    axios.get(`${base}/menu-manifest`, { timeout: 10000 })
  );
  return { ...legacy, engineMode: 'LEGACY' as const };
}

export function updateMenuManifestOverride(
  enterpriseId: string,
  item: string,
  patch: Partial<
    Pick<
      VisibleMenuItem,
      'enabled' | 'parentMenuId' | 'sortOrder' | 'capabilityCode'
    >
  >,
  version: number
) {
  return request<VisibleMenuItem>(() =>
    axios.patch(
      `/api/v1/enterprises/${encodeURIComponent(
        enterpriseId
      )}/menu-manifest/${encodeURIComponent(item)}`,
      patch,
      {
        headers: {
          'If-Match': `"${version}"`,
          'Idempotency-Key': `ui-menu-override-${item}-${Date.now()}`,
        },
      }
    )
  );
}

/** The platform shell has its own explicit domain, distinct from enterprise ID platform. */
export async function getManagedMenuManifest(
  enterpriseId?: string
): Promise<MenuManifest> {
  if (enterpriseId) return getVisibleMenuManifest(enterpriseId);
  const current = await request<MenuManifest>(() =>
    axios.get('/api/v1/platform/authorization/navigation', { timeout: 10000 })
  );
  if (current.engineMode === 'LEGACY') return { ...current, items: [] };
  if (current.engineMode !== 'MANAGED')
    throw new Error('Unknown navigation authorization mode');
  return current;
}
