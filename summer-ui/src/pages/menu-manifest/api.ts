import type { MenuError, MenuOverride, PlatformManifest } from './types';
export type { MenuError, MenuOverride, PlatformManifest } from './types';
import { businessData as request } from '@/api/client';
import axios from 'axios';
import { MenuManifest, VisibleMenuItem } from '@/menu/api';
export const getManagedManifest = (enterprise: string) => request<MenuManifest>(() => axios.get(`/api/v1/enterprises/${encodeURIComponent(enterprise)}/menu-manifest/management`));
export const getPlatformManifest = () => request<MenuManifest>(() => axios.get('/api/v1/platform/menu-manifest'));
export const saveOverride = (enterprise: string, menu: string, body: MenuOverride, version: number, key: string) => request<VisibleMenuItem>(() => axios.patch(`/api/v1/enterprises/${encodeURIComponent(enterprise)}/menu-manifest`, body, { params: { menuId: menu }, headers: { 'If-Match': `"${version}"`, 'Idempotency-Key': key } }));
export const savePlatformManifest = (body: PlatformManifest, key: string) => request<MenuManifest>(() => axios.put('/api/v1/platform/menu-manifest', body, { headers: { 'Idempotency-Key': key } }));
