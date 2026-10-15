import { MenuManifest } from './api';
import { UserPermission } from '@/utils/authentication';

/** A manifest write failure must not prevent an ordinary user reading their own grants. */
export async function loadMenuPermissions(
  sync: () => Promise<unknown>,
  read: () => Promise<MenuManifest>,
  onVisible?: (items: MenuManifest['items']) => void
): Promise<UserPermission> {
  try {
    await sync();
  } catch {
    // Only a platform administrator can publish the frontend manifest.
  }
  try {
    const visible = await read();
    onVisible?.(visible.items);
    const permissions: UserPermission = {};
    visible.items.forEach((item) => {
      const actions = item.actions || [];
      if (!actions.length) return;
      permissions[item.menuId] = actions;
      if (item.capabilityCode) {
        const separator = item.capabilityCode.lastIndexOf('.');
        const resource =
          separator > 0
            ? item.capabilityCode.slice(0, separator)
            : item.capabilityCode;
        permissions[resource] = Array.from(
          new Set([...(permissions[resource] || []), ...actions])
        );
      }
    });
    return permissions;
  } catch {
    onVisible?.([]);
    return {};
  }
}
