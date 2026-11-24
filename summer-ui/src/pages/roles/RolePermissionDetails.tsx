import React, { useState } from 'react';
import { Input, Table } from '@arco-design/web-react';
import { Group, Permission } from '../permissions/api';
import { AuthorizationRole } from './api';
import treeStyles from '../permissions/components/permission-group-tree.module.less';

export default function RolePermissionDetails({
  role,
  groups,
  selected,
  permissions,
}: {
  role: AuthorizationRole;
  groups: Group[];
  selected?: Group;
  permissions: Permission[];
}) {
  const [search, setSearch] = useState('');
  const label = selected ? selected.name : '全部权限';
  const groupNames = new Map(groups.map((g) => [g.groupId, g.name]));
  const known = new Map(permissions.map((p) => [p.permissionId, p]));
  const historical = new Map(
    role.legacyPermissions?.map((p) => [p.permissionId, p.name])
  );
  const ids = selected
    ? permissions.map((p) => p.permissionId)
    : role.permissionIds;
  const rows = ids.map((id) => {
    const p = known.get(id);
    return {
      id,
      name: p?.name || historical.get(id) || '目录信息不可用',
      code: p?.code || id,
      group: p ? groupNames.get(p.groupId) || p.groupId || '—' : '—',
      type: p
        ? { INTERFACE: '接口', MENU: '菜单', DATA: '数据' }[p.type] || p.type
        : '—',
      status: p
        ? p.status === 'ACTIVE'
          ? '启用'
          : '停用'
        : historical.has(id)
        ? '历史授权'
        : '—',
    };
  });
  const query = search.trim().toLowerCase();
  const data = rows.filter(
    (r) =>
      r.name.toLowerCase().includes(query) ||
      r.code.toLowerCase().includes(query)
  );
  return (
    <div>
      <h3 className={treeStyles.tableHeading}>
        {label}（{rows.length}）
      </h3>
      <Input
        aria-label="搜索已授权限"
        placeholder="搜索权限名称或编码"
        allowClear
        value={search}
        onChange={setSearch}
        style={{ maxWidth: 320, marginBottom: 12 }}
      />
      <Table
        key={JSON.stringify([selected?.groupId, query])}
        rowKey="id"
        data={data}
        scroll={{ x: 580 }}
        pagination={{ pageSize: 10, showTotal: true, hideOnSinglePage: true }}
        noDataElement={query ? '没有匹配的权限' : '该角色暂无已授权限'}
        columns={[
          { title: '权限名称', dataIndex: 'name' },
          { title: '编码 / 标识', dataIndex: 'code' },
          { title: '所属组', dataIndex: 'group' },
          { title: '类型', dataIndex: 'type' },
          { title: '状态', dataIndex: 'status' },
        ]}
      />
    </div>
  );
}
