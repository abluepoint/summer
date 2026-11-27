import type { VisibleMenuItem } from './api';
import type { IRoute } from '@/routes';

const platformPages = new Set(['roles', 'permissions', 'menu-manifest']);
const platformAliases = new Set(
  Array.from(platformPages, (id) => 'platform/' + id)
);

const platformDictionaryPages = [
  'dictionary/manage',
  'dictionary/groups',
  'dictionary/divisions',
];
export const platformManagementPaths = [
  '/interface-resources',
  ...platformDictionaryPages.map((id) => '/' + id),
];

// These routes can open in a tenant shell using an independent platform menu grant.
// Tenant business routes (including statistics under Dashboard) keep tenant qualification.
const sharedPlatformPages = new Set([
  'interface-resources',
  'platform/administrators',
  'platform/organization',
  'platform/file-repositories',
  'platform/user-groups',
  'platform/files',
  ...platformDictionaryPages,
  'welcome',
  'dashboard/workplace',
  'dashboard/monitor',
  'visualization/data-analysis',
  'visualization/multi-dimension-data-analysis',
  'list/search-table',
  'list/card',
  'form/group',
  'form/step',
  'profile/basic',
  'result/success',
  'result/error',
  'exception/403',
  'exception/404',
  'exception/500',
  'user/info',
  'user/setting',
]);

/** Map authorized menu identities to their local page destinations. Catalog metadata stays intact. */
export function managementNavigation(
  items: VisibleMenuItem[],
  domain: 'PLATFORM' | 'ENTERPRISE',
  clientCode: string
): VisibleMenuItem[] {
  const byId = new Map(items.map((item) => [item.menuId, item]));
  const destination = (id: string) =>
    domain === 'PLATFORM' && platformPages.has(id) ? 'platform/' + id : id;
  const included = (item: VisibleMenuItem) =>
    (item.clientCode || 'web') === clientCode &&
    (!platformAliases.has(item.menuId) ||
      (domain === 'PLATFORM' && item.menuId === 'platform/roles')) &&
    (domain === 'PLATFORM' ||
      (!platformDictionaryPages.includes(item.menuId) &&
        ![
          'permissions',
          'menu-manifest',
          'platform/administrators',
          'platform/organization',
          'platform/file-repositories',
          'platform/user-groups',
          'platform/files',
        ].includes(item.menuId)));
  return items.filter(included).map((item) => {
    let parent = item.parentMenuId;
    const seen = new Set<string>();
    while (parent && !seen.has(parent)) {
      seen.add(parent);
      const ancestor = byId.get(parent);
      if (!ancestor?.enabled || included(ancestor)) break;
      parent = ancestor.parentMenuId;
    }
    const id = destination(item.menuId);
    return {
      ...item,
      menuId: id,
      path: '/' + id,
      parentMenuId: parent ? destination(parent) : undefined,
    };
  });
}

// Add independently authorized platform destinations and their directory ancestry in a tenant shell.
// Sharing a directory across domains must never make its page available in the tenant.
export function withPlatformEntries(
  tenant: VisibleMenuItem[],
  platform: VisibleMenuItem[]
) {
  const result = new Map(tenant.map((item) => [item.menuId, item]));
  const byId = new Map(platform.map((item) => [item.menuId, item]));
  // A platform grant on the workspace directory opts into its individually granted
  // pages. Ancestor-only visibility does not qualify the directory or any child.
  const workspace = byId.get('platform-workspace');
  const inGrantedWorkspace = (item: VisibleMenuItem) => {
    if (
      !workspace?.enabled ||
      workspace.navOnly ||
      workspace.pageEligible !== true
    )
      return false;
    let parent = item.parentMenuId;
    const visited = new Set<string>();
    while (parent && !visited.has(parent)) {
      if (parent === workspace.menuId) return true;
      visited.add(parent);
      parent = byId.get(parent)?.parentMenuId;
    }
    return false;
  };
  for (const page of platform) {
    if (
      (!platformAliases.has(page.menuId) &&
        !sharedPlatformPages.has(page.menuId) &&
        !inGrantedWorkspace(page)) ||
      page.navOnly ||
      page.pageEligible === false
    )
      continue;
    if (result.get(page.menuId)?.pageEligible === true) continue;
    result.set(page.menuId, page);
    let parent = page.parentMenuId;
    while (parent && !result.has(parent)) {
      const ancestor = byId.get(parent);
      if (!ancestor) break;
      result.set(parent, {
        ...ancestor,
        navOnly: true,
        pageEligible: false,
        actions: [],
      });
      parent = ancestor.parentMenuId;
    }
  }
  return Array.from(result.values());
}

export function menuAncestorKeys(routes: IRoute[], key: string): string[] {
  for (const route of routes) {
    if (route.ignore) continue;
    if (route.children?.some((child) => !child.ignore && child.key === key))
      return [route.key];
    const parents = menuAncestorKeys(route.children || [], key);
    if (parents.length) return [route.key, ...parents];
  }
  return [];
}
