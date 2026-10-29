import type { ApiFailure } from '@/api/types';
import type { MenuManifestItem } from '@/menu/manifest';
import type { VisibleMenuItem } from '@/menu/api';
export type MenuError = Pick<ApiFailure, 'status' | 'code' | 'message' | 'requestId'>;
export type MenuOverride = Pick<VisibleMenuItem, 'enabled' | 'parentMenuId' | 'sortOrder' | 'capabilityCode'>;
export type PlatformManifest = { manifestVersion: number; items: MenuManifestItem[] };
