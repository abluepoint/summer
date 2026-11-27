import React, { useState } from 'react';
import listStyles from '@/pages/platform/list-scroll.module.less';
import { Button, Input, Space, Table } from '@arco-design/web-react';
import { AuthorizationRole } from './api';

export type RolePanel = 'create' | 'edit' | 'detail' | 'permissions' | 'delete';

export default function RoleTable({
  platform,
  roles,
  operations,
  busy,
  ready,
  openPanel,
}: {
  platform: boolean;
  roles: AuthorizationRole[];
  operations: Record<string, boolean>;
  busy: boolean;
  ready: boolean;
  openPanel: (panel: RolePanel, row?: AuthorizationRole) => void;
}) {
  const [filter, setFilter] = useState('');
  const actionSize = platform ? undefined : 'mini';
  const query = filter.trim().toLowerCase();
  const visibleRoles = roles.filter((role) =>
    [role.name, role.code].some((value) => value.toLowerCase().includes(query))
  );
  return (
    <>
      <Space wrap style={{ marginBottom: 16 }}>
        <Input
          allowClear
          aria-label="搜索角色"
          placeholder="搜索角色名称或编码"
          value={filter}
          onChange={setFilter}
        />
        <Button
          type="primary"
          disabled={busy || !ready || !operations['roles.create']}
          onClick={() => openPanel('create')}
        >
          新建角色
        </Button>
      </Space>
      <div
        role="region"
        aria-label={platform ? '平台角色列表' : '租户角色列表'}
        tabIndex={0}
        className={platform ? listStyles.listScroll : undefined}
        style={{ overflowX: 'auto' }}
      >
        <Table<AuthorizationRole>
          style={{ minWidth: 720 }}
          loading={busy}
          rowKey="roleId"
          data={visibleRoles}
          pagination={{ pageSize: 10, showTotal: true }}
          columns={[
            {
              title: '角色名称',
              render: (_, r) =>
                r.defaultPlatformAdministrator
                  ? r.name + '（默认平台管理员）'
                  : r.name,
            },
            { title: '角色编码', dataIndex: 'code' },
            {
              title: '状态',
              render: (_, r) => (r.status === 'ACTIVE' ? '启用' : '停用'),
            },
            { title: '权限数', render: (_, r) => r.permissionIds.length },
            {
              title: '操作',
              render: (_, r) => (
                <Space wrap size={4}>
                  <Button
                    size={actionSize}
                    disabled={busy}
                    onClick={() => openPanel('detail', r)}
                  >
                    查看
                  </Button>
                  <Button
                    size={actionSize}
                    disabled={busy || !operations['roles.update']}
                    onClick={() => openPanel('edit', r)}
                  >
                    编辑
                  </Button>
                  <Button
                    size={actionSize}
                    disabled={busy || !operations['roles.permissions.write']}
                    onClick={() => openPanel('permissions', r)}
                  >
                    配置权限
                  </Button>
                  <Button
                    size={actionSize}
                    status="danger"
                    disabled={
                      busy ||
                      (platform
                        ? r.defaultPlatformAdministrator
                        : r.code === 'permission-administrator') ||
                      !operations['roles.delete']
                    }
                    onClick={() => openPanel('delete', r)}
                  >
                    删除
                  </Button>
                </Space>
              ),
            },
          ]}
        />
      </div>
    </>
  );
}
