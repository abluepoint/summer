import type { ApiFailure } from '@/api/types';
import type { MenuManifestItem, VisibleMenuItem } from '@/menu/manifest';
export type MenuError = Pick<ApiFailure, 'status' | 'code' | 'message' | 'requestId'>;
export type MenuOverride = Pick<VisibleMenuItem, 'enabled' | 'parentMenuId' | 'sortOrder' | 'capabilityCode'>;
export type PlatformManifest = { manifestVersion: number; items: MenuManifestItem[] };
