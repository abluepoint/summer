import React from 'react';
import { Group, Permission } from '../api';
import treeStyles from './permission-group-tree.module.less';

export function indexGroupPermissions(
  groups: Group[],
  permissions: Permission[]
) {
  const index = new Map(groups.map((g) => [g.groupId, [] as Permission[]]));
  const parents = new Map(groups.map((g) => [g.groupId, g.parentGroupId]));
  for (const permission of permissions) {
    const seen = new Set<string>();
    let id: string | null = permission.groupId;
    while (id && !seen.has(id)) {
      index.get(id)?.push(permission);
      seen.add(id);
      id = parents.get(id) || null;
    }
  }
  return index;
}

export function GroupPermissionCheckbox({
  group,
  items,
  selectedIds,
  disabled,
  onSelect,
}: {
  group: Group;
  items: Permission[];
  selectedIds: Set<string>;
  disabled: boolean;
  onSelect: (ids: string[], checked: boolean) => void;
}) {
  const count = items.filter((p) => selectedIds.has(p.permissionId)).length;
  const label = `全选${group.name}及子组权限`;
  return (
    <label className={treeStyles.groupCheck} title={label}>
      <input
        type="checkbox"
        aria-label={label}
        disabled={disabled || items.length === 0}
        checked={items.length > 0 && count === items.length}
        ref={(input) => {
          if (input) input.indeterminate = count > 0 && count < items.length;
        }}
        onChange={(event) =>
          onSelect(
            items.map((p) => p.permissionId),
            event.target.checked
          )
        }
      />
    </label>
  );
}
