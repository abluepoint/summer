import { call } from '../../permissions/api';
import { assignUserRoles } from '../../roles/api';
import { base } from '../directory';
import type { MembershipChange } from './MemberAssignments';

export type RoleAssignmentRow = {
  username: string;
  roleId: string | null;
  version: number;
};

export async function saveRoleMembers(change: MembershipChange) {
  const roster = await call<{ assignments?: RoleAssignmentRow[] }>(
    base,
    '/administrators'
  );
  if (!roster.assignments) throw new Error('无权分配平台角色');
  const add = change.kind === 'add';
  const usernames =
    change.kind === 'add' ? change.selected : [change.person.username];
  const roleId = change.list.id;
  let completed = 0;
  for (const username of usernames) {
    const rows = roster.assignments.filter((row) => row.username === username);
    const current = rows.flatMap((row) => (row.roleId ? [row.roleId] : []));
    if (add !== current.includes(roleId)) {
      const next = add
        ? [...current, roleId]
        : current.filter((id) => id !== roleId);
      try {
        await assignUserRoles(base, username, next, rows[0]?.version ?? 0);
      } catch (e) {
        const reason =
          e.status === 409 || e.status === 412
            ? '角色分配已变化，请核对后重试'
            : e.message || '保存失败，请重试';
        throw new Error(
          (completed ? `已完成 ${completed} 人。` : '') +
            username +
            '：' +
            reason
        );
      }
    }
    completed++;
  }
}
