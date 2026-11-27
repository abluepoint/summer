import axios from 'axios';
import { menuClientCode } from '@/menu/client';
import { readEnvelope, readFailure } from '@/pages/customer/contract.mjs';
import { MenuManifest } from '@/menu/api';
import { MenuManifestItem } from '@/menu/manifest';
export type MenuError = {
  status: number;
  code: string;
  message: string;
  requestId: string;
};
export type PlatformManifest = {
  manifestVersion: number;
  items: (MenuManifestItem & { enabled?: boolean })[];
  layoutVersions?: Record<string, number>;
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
async function currentMode(base: string) {
  const marker = await request<MenuManifest>(() =>
    axios.get(`${base}/authorization/navigation`, {
      params: { clientCode: menuClientCode },
      expectedAccessProbe: true,
    })
  );
  if (marker.engineMode !== 'MANAGED' && marker.engineMode !== 'LEGACY')
    throw {
      status: 503,
      code: 'UNKNOWN_ENGINE_MODE',
      message: '无法确认当前授权模式，请稍后重试。',
      requestId: '',
    } as MenuError;
  return marker.engineMode;
}
export async function getPlatformManifest() {
  const base = '/api/v1/platform';
  const mode = await currentMode(base);
  return request<MenuManifest>(() =>
    axios.get(
      `${base}${
        mode === 'MANAGED' ? '/authorization/menu-manifest' : '/menu-manifest'
      }`,
      {
        expectedAccessProbe: true,
      }
    )
  );
}
export const savePlatformManifest = async (
  body: PlatformManifest,
  version: number,
  key: string
) => {
  const base = '/api/v1/platform';
  const mode = await currentMode(base);
  if (body.layoutVersions && mode !== 'MANAGED')
    throw {
      status: 409,
      message: '当前授权模式不支持菜单编辑',
      code: 'DOMAIN_NOT_MANAGED',
      requestId: '',
    } as MenuError;
  return request<MenuManifest>(() =>
    axios.put(
      `${base}${
        mode === 'MANAGED' ? '/authorization/menu-manifest' : '/menu-manifest'
      }`,
      body,
      {
        headers: { 'If-Match': `"${version}"`, 'Idempotency-Key': key },
      }
    )
  );
};
