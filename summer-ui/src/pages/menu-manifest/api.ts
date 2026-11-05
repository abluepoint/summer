import axios from 'axios';
import { readEnvelope, readFailure } from '@/pages/customer/contract.mjs';
import { MenuManifest, VisibleMenuItem } from '@/menu/api';
import { MenuManifestItem } from '@/menu/manifest';
export type MenuError = {
  status: number;
  code: string;
  message: string;
  requestId: string;
};
export type MenuOverride = Pick<
  VisibleMenuItem,
  'enabled' | 'parentMenuId' | 'sortOrder'
>;
export type PlatformManifest = {
  manifestVersion: number;
  items: MenuManifestItem[];
};
async function request<T>(
  call: () => Promise<{ data: unknown; status: number }>
): Promise<T> {
  try {
    const response = await call();
    return readEnvelope(response.data, response.status) as T;
  } catch (error) {
    if (!axios.isAxiosError(error)) throw error;
    throw readFailure(
      error.response?.data,
      error.response?.status || 0,
      String(error.response?.headers?.['x-request-id'] || '')
    ) as MenuError;
  }
}
async function requireManaged(base: string) {
  const marker = await request<MenuManifest>(() =>
    axios.get(`${base}/authorization/navigation`, { expectedAccessProbe: true })
  );
  if (marker.engineMode === 'LEGACY')
    throw {
      status: 409,
      code: 'DOMAIN_NOT_MANAGED',
      message: '当前授权域尚未启用新权限管理，菜单配置暂不可编辑。',
      requestId: '',
    } as MenuError;
  if (marker.engineMode !== 'MANAGED')
    throw {
      status: 503,
      code: 'UNKNOWN_ENGINE_MODE',
      message: '无法确认当前授权模式，请稍后重试。',
      requestId: '',
    } as MenuError;
}
export async function getManagedManifest(enterprise: string) {
  const base = `/api/v1/enterprises/${encodeURIComponent(enterprise)}`;
  await requireManaged(base);
  return request<MenuManifest>(() =>
    axios.get(`${base}/authorization/menu-layout`)
  );
}
export async function getPlatformManifest() {
  const base = '/api/v1/platform';
  await requireManaged(base);
  return request<MenuManifest>(() =>
    axios.get(`${base}/authorization/menu-manifest`, {
      expectedAccessProbe: true,
    })
  );
}
export const saveOverride = (
  enterprise: string,
  menu: string,
  body: MenuOverride,
  version: number,
  key: string
) =>
  request<VisibleMenuItem>(() =>
    axios.patch(
      `/api/v1/enterprises/${encodeURIComponent(
        enterprise
      )}/authorization/menu-layout`,
      body,
      {
        params: { menuId: menu },
        headers: { 'If-Match': `"${version}"`, 'Idempotency-Key': key },
      }
    )
  );
export const savePlatformManifest = (
  body: PlatformManifest,
  version: number,
  key: string
) =>
  request<MenuManifest>(() =>
    axios.put('/api/v1/platform/authorization/menu-manifest', body, {
      headers: { 'If-Match': `"${version}"`, 'Idempotency-Key': key },
    })
  );
