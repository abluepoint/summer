import { createContext, useContext } from 'react';
import { VisibleMenuItem } from './api';
import type { IRoute } from '@/routes';

export const MenuAccessContext = createContext<VisibleMenuItem[]>([]);
export function useMenuItems() {
  return useContext(MenuAccessContext);
}
export function customerActions(
  items: VisibleMenuItem[],
  menuId: string
): string[] {
  return Array.from(
    new Set(
      items
        .filter(
          (item) =>
            item.enabled &&
            (item.menuId === menuId ||
              (menuId === 'customer/detail' &&
                ['customer/drawer', 'customer/modal', 'customer/page'].includes(
                  item.menuId
                )))
        )
        .flatMap((item) => item.actions || [])
    )
  );
}
export function useCustomerActions(menuId: string) {
  return customerActions(useMenuItems(), menuId);
}

/** Resolve safe server menu metadata against local routes only. Missing items fail closed. */
export function resolveMenuRoutes(
  local: IRoute[],
  items: VisibleMenuItem[]
): IRoute[] {
  const known = new Map<string, IRoute>();
  const visit = (routes: IRoute[]) =>
    routes.forEach((route) => {
      known.set(route.key, route);
      if (route.children) visit(route.children);
    });
  visit(local);
  const visible = new Map(
    items
      .filter((item) => item.enabled && known.has(item.menuId))
      .map((item) => [item.menuId, item])
  );
  const nodes = new Map<string, IRoute>();
  const ordered = Array.from(visible.values()).sort(
    (a, b) => a.sortOrder - b.sortOrder || a.menuId.localeCompare(b.menuId)
  );
  for (const item of ordered) {
    let parent = item.parentMenuId;
    const seen = new Set([item.menuId]);
    let valid = true;
    while (parent) {
      if (!visible.has(parent) || seen.has(parent)) {
        valid = false;
        break;
      }
      seen.add(parent);
      parent = visible.get(parent).parentMenuId;
    }
    if (!valid) continue;
    const source = known.get(item.menuId);
    nodes.set(item.menuId, {
      ...source,
      exact: Boolean(source.children),
      name: item.titleKey,
      iconKey: item.iconKey,
      children: source.children ? [] : undefined,
    });
  }
  const roots: IRoute[] = [];
  for (const item of ordered) {
    const node = nodes.get(item.menuId);
    if (!node) continue;
    if (item.parentMenuId) {
      const parent = nodes.get(item.parentMenuId);
      if (parent) (parent.children ||= []).push(node);
    } else roots.push(node);
  }
  // Detail pages have no menu row; they inherit access from a visible customer screen.
  const detail = known.get('customer/detail');
  if (detail && customerActions(items, 'customer/detail').includes('read'))
    roots.push({ ...detail });
  return roots;
}
export function customerRouteAllowed(
  path: string,
  items: VisibleMenuItem[]
): boolean {
  const actions = customerActions(items, 'customer/page');
  if (/^\/customer\/page\/create(?:\/|$)/.test(path))
    return actions.includes('create');
  if (/^\/customer\/page\/edit(?:\/|$)/.test(path))
    return actions.includes('update');
  return true;
}
