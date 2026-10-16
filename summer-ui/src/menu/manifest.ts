import { AuthParams } from '@/utils/authentication';

export type ManifestRoute = AuthParams & {
  name: string;
  key: string;
  iconKey?: string;
  ignore?: boolean;
  children?: ManifestRoute[];
  capabilityCode?: string;
};

export type MenuManifestItem = {
  menuId: string;
  parentMenuId?: string;
  path: string;
  titleKey: string;
  iconKey: string;
  sortOrder: number;
  capabilityCode?: string;
};

function capabilityCode(route: ManifestRoute): string | undefined {
  if (route.capabilityCode) return route.capabilityCode;
  const permission = route.requiredPermissions?.[0];
  if (!permission || typeof permission.resource !== 'string') return undefined;
  const action = permission.actions?.[0];
  return action ? `${permission.resource}.${action}` : permission.resource;
}

/** Converts local route declarations into the server-safe menu contract. */
export function createMenuManifest(routeTree: ManifestRoute[]): { manifestVersion: number; items: MenuManifestItem[] } {
  const items: MenuManifestItem[] = [];
  function visit(routes: ManifestRoute[], parentMenuId?: string) {
    routes.forEach((route, index) => {
      if (route.ignore) return;
      items.push({
        menuId: route.key,
        ...(parentMenuId ? { parentMenuId } : {}),
        path: `/${route.key}`,
        titleKey: route.name,
        iconKey: route.iconKey || route.key,
        sortOrder: items.length * 10 + index,
        ...(capabilityCode(route) ? { capabilityCode: capabilityCode(route) } : {}),
      });
      if (route.children?.length) visit(route.children, route.key);
    });
  }
  visit(routeTree);
  return { manifestVersion: 7, items };
}
