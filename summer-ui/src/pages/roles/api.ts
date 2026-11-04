import { call, Group, Page, Permission } from '../permissions/api';
export type AuthorizationApiError = {
  status: number;
  message: string;
  requestId?: string;
};
export type AuthorizationRole = {
  roleId: string;
  name: string;
  code: string;
  status: string;
  permissionIds: string[];
  version: number;
};
export type UserRoleAssignment = {
  username: string;
  roleIds: string[];
  sources: {
    permissionId: string;
    permissionName: string;
    roleId: string;
    roleName: string;
  }[];
  version: number;
};
const key = () =>
  `ui-roles-${Array.from(crypto.getRandomValues(new Uint32Array(4)))
    .map((n) => n.toString(16))
    .join('')}`;
export const listRoles = (d: string) => pages<AuthorizationRole>(d, '/roles');
export const createRole = (
  d: string,
  name: string,
  code: string,
  permissionIds: string[]
) =>
  call<AuthorizationRole>(
    d,
    '/roles',
    'POST',
    { name, code, permissionIds },
    undefined,
    key()
  );
export const saveRolePermissions = (
  d: string,
  role: AuthorizationRole,
  permissionIds: string[]
) =>
  call<AuthorizationRole>(
    d,
    `/roles/${encodeURIComponent(role.roleId)}/permissions`,
    'PUT',
    { permissionIds },
    role.version,
    key()
  );
export const getUserRoleAssignment = (d: string, name: string) =>
  call<UserRoleAssignment>(d, `/users/${encodeURIComponent(name)}/roles`);
export const assignUserRoles = (
  d: string,
  name: string,
  roleIds: string[],
  version: number
) =>
  call<UserRoleAssignment>(
    d,
    `/users/${encodeURIComponent(name)}/roles`,
    'PUT',
    { roleIds },
    version,
    key()
  );
async function pages<T>(d: string, path: string): Promise<T[]> {
  const items: T[] = [];
  for (let page = 1; ; page++) {
    const result = await call<Page<T>>(d, `${path}?page=${page}&size=200`);
    items.push(...result.items);
    if (items.length >= result.total) return items;
    if (!result.items.length) throw new Error('权限目录发生变化，请刷新后重试');
  }
}
export const listPermissionChoices = (d: string) =>
  pages<Permission>(d, '/permissions');
export const listPermissionGroups = (d: string) =>
  pages<Group>(d, '/permission-groups');
