import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Checkbox, Table } from '@arco-design/web-react';
import { Group, Permission } from '../api';
import { listPermissionChoices } from '../../roles/api';
import PermissionGroupTree from './PermissionGroupTree';
import {
  GroupPermissionCheckbox,
  indexGroupPermissions,
} from './GroupPermissionCheckbox';
import styles from './permission-group-tree.module.less';

export default function DataInterfacePicker({
  base,
  groups,
  disabled,
  value = [],
  onChange,
}: {
  base: string;
  groups: Group[];
  disabled: boolean;
  value?: string[];
  onChange?: (ids: string[]) => void;
}) {
  const [catalog, setCatalog] = useState<Permission[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [groupId, setGroupId] = useState<string>();
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    setCatalog([]);
    listPermissionChoices(base, 'INTERFACE')
      .then((rows) => {
        if (active) setCatalog(rows);
      })
      .catch((e) => {
        if (active) setError(e.message || '接口权限读取失败');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [base]);
  const index = useMemo(
    () => indexGroupPermissions(groups, catalog),
    [groups, catalog]
  );
  const selected = new Set(value);
  const visible = groupId ? index.get(groupId) || [] : catalog;
  const selectedInGroup = visible.filter((p) =>
    selected.has(p.permissionId)
  ).length;
  const toggle = (ids: string[], checked: boolean) => {
    const next = new Set(value);
    ids.forEach((id) => (checked ? next.add(id) : next.delete(id)));
    onChange?.(Array.from(next));
  };
  return (
    <div>
      {error && <Alert type="error" content={error} />}
      <div className={styles.workspace}>
        <PermissionGroupTree
          groups={groups}
          selected={groups.find((g) => g.groupId === groupId)}
          onSelect={setGroupId}
          loading={loading}
          renderGroupCheckbox={(group) => (
            <GroupPermissionCheckbox
              group={group}
              items={index.get(group.groupId) || []}
              selectedIds={selected}
              disabled={disabled || loading}
              onSelect={toggle}
            />
          )}
        />
        <section className={styles.permissionTables} aria-label="接口权限">
          <div>
            <Checkbox
              disabled={disabled || !visible.length}
              checked={!!visible.length && selectedInGroup === visible.length}
              indeterminate={
                selectedInGroup > 0 && selectedInGroup < visible.length
              }
              onChange={(checked) =>
                toggle(
                  visible.map((p) => p.permissionId),
                  checked
                )
              }
            >
              {groupId ? '全选本组及子组接口权限' : '全选目录中的接口权限'}
            </Checkbox>
            <span>已选 {selected.size} 项接口权限</span>
          </div>
          <Table
            key={groupId || ''}
            rowKey="permissionId"
            data={visible}
            loading={loading}
            pagination={{
              pageSize: 10,
              showTotal: true,
              hideOnSinglePage: true,
            }}
            columns={[
              { title: '接口权限', dataIndex: 'name' },
              { title: '编码', dataIndex: 'code' },
              {
                title: '状态',
                render: (_, p) => (p.status === 'ACTIVE' ? '启用' : '停用'),
              },
              {
                title: '关联',
                render: (_, p) => (
                  <Checkbox
                    aria-label={`关联 ${p.name}`}
                    disabled={disabled}
                    checked={selected.has(p.permissionId)}
                    onChange={(checked) => toggle([p.permissionId], checked)}
                  />
                ),
              },
            ]}
          />
        </section>
      </div>
    </div>
  );
}
