import axios from 'axios';
import { readEnvelope, readFailure } from '@/pages/customer/contract.mjs';
import { MenuManifestItem } from './manifest';

export type VisibleMenuItem = MenuManifestItem & {
  enabled: boolean;
  actions: string[];
  version: number;
};
export type MenuManifest = {
  manifestVersion: number;
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
      headers: {
        'Idempotency-Key': `ui-menu-manifest-${manifest.manifestVersion}`,
      },
    })
  );
}

export function getVisibleMenuManifest(enterpriseId: string) {
  // A stalled read must expire the previous UI grants and allow the next refresh.
  return request<MenuManifest>(() =>
    axios.get(
      `/api/v1/enterprises/${encodeURIComponent(enterpriseId)}/menu-manifest`,
      { timeout: 10000 }
    )
  ).catch((failure) => {
    if (failure.status !== 403 && failure.status !== 404) throw failure;
    return getManagedMenuManifest(enterpriseId);
  });
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

/** Only persisted MENU grants produce these two bootstrap management entries. */
export async function getManagedMenuManifest(
  enterpriseId?: string
): Promise<MenuManifest> {
  const domains = [
    '/api/v1/platform',
    ...(enterpriseId
      ? [`/api/v1/enterprises/${encodeURIComponent(enterpriseId)}`]
      : []),
  ];
  const parts = await Promise.all(
    domains.map((base) =>
      request<MenuManifest>(() =>
        axios.get(`${base}/authorization/navigation`, { timeout: 10000 })
      ).catch((failure) => {
        if (
          failure.status === 401 ||
          failure.status === 403 ||
          failure.status === 404
        )
          return { manifestVersion: 0, items: [] };
        throw failure;
      })
    )
  );
  return {
    manifestVersion: Math.max(...parts.map((p) => p.manifestVersion)),
    items: Array.from(
      new Map(
        parts.flatMap((p) => p.items).map((item) => [item.menuId, item])
      ).values()
    ),
  };
}
