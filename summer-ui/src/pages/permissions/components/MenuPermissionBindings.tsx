import { activeEnterpriseSelection } from '@/hooks/useActiveEnterprise';
import { requestMenuRefresh } from '@/menu/refresh';
import React, { useEffect, useRef, useState } from 'react';
import { Alert, Button, Checkbox, Space } from '@arco-design/web-react';
import { call, PermissionAssociationProps } from '../api';

type Binding = { resourceIds: string[]; version: number };
type Menu = { menuId: string; titleKey: string };
/** The relationship ETag is independent of the parent permission's version. */
export default function MenuPermissionBindings({
  domain,
  permissionId,
  version,
  onSaved,
  onDirtyChange,
  onBusyChange,
  disabled = false,
}: PermissionAssociationProps) {
  const base =
    domain.kind === 'PLATFORM'
      ? '/api/v1/platform'
      : `/api/v1/enterprises/${encodeURIComponent(domain.id)}`;
  const [menus, setMenus] = useState<Menu[]>([]);
  const [selected, setSelected] = useState<string[]>([]);
  const baseline = useRef('[]');
  const [etag, setEtag] = useState<number>();
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const generation = useRef(0);
  const contextVersion = activeEnterpriseSelection.getSnapshot().contextVersion;
  const mutation = useRef<{ fingerprint: string; key: string }>();
  useEffect(() => {
    const current = ++generation.current;
    baseline.current = '[]';
    mutation.current = undefined;
    setMenus([]);
    setSelected([]);
    setEtag(undefined);
    setError('');
    setBusy(true);
    Promise.all([
      call<{ items: Menu[] }>(base, '/authorization/menu-directory'),
      call<Binding>(
        base,
        `/permissions/${encodeURIComponent(permissionId)}/menus`
      ),
    ])
      .then(([directory, binding]) => {
        if (
          current !== generation.current ||
          contextVersion !==
            activeEnterpriseSelection.getSnapshot().contextVersion
        )
          return;
        setMenus([
          ...directory.items,
          ...binding.resourceIds
            .filter((id) => !directory.items.some((item) => item.menuId === id))
            .map((menuId) => ({
              menuId,
              titleKey: '不在当前清单（可取消关联）',
            })),
        ]);
        setSelected(binding.resourceIds);
        baseline.current = JSON.stringify([...binding.resourceIds].sort());
        setEtag(binding.version);
      })
      .catch((e) => {
        if (current === generation.current)
          setError(e.message || '菜单关联读取失败');
      })
      .finally(() => {
        if (
          current === generation.current &&
          contextVersion ===
            activeEnterpriseSelection.getSnapshot().contextVersion
        )
          setBusy(false);
      });
    return () => {
      generation.current++;
    };
  }, [base, permissionId, version, contextVersion]);
  useEffect(() => {
    onDirtyChange?.(JSON.stringify([...selected].sort()) !== baseline.current);
  }, [selected, onDirtyChange]);
  useEffect(() => {
    onBusyChange?.(busy);
  }, [busy, onBusyChange]);
  useEffect(
    () => () => {
      onDirtyChange?.(false);
      onBusyChange?.(false);
    },
    [onDirtyChange, onBusyChange]
  );
  async function save() {
    if (disabled || busy || etag === undefined) return;
    const current = generation.current;
    const contextVersion =
      activeEnterpriseSelection.getSnapshot().contextVersion;
    const fingerprint = JSON.stringify([
      base,
      permissionId,
      etag,
      [...selected].sort(),
    ]);
    if (mutation.current?.fingerprint !== fingerprint)
      mutation.current = {
        fingerprint,
        key: `menu-bind-${Date.now()}-${Math.random().toString(36).slice(2)}`,
      };
    setBusy(true);
    setError('');
    try {
      const result = await call<Binding>(
        base,
        `/permissions/${encodeURIComponent(permissionId)}/menus`,
        'PUT',
        { menuIds: selected },
        etag,
        mutation.current.key
      );
      if (
        current !== generation.current ||
        contextVersion !==
          activeEnterpriseSelection.getSnapshot().contextVersion
      )
        return;
      setSelected(result.resourceIds);
      baseline.current = JSON.stringify([...result.resourceIds].sort());
      setEtag(result.version);
      mutation.current = undefined;
      requestMenuRefresh(contextVersion);
      onSaved();
    } catch (e) {
      if (current === generation.current)
        setError(e.message || '菜单关联保存失败');
    } finally {
      if (
        current === generation.current &&
        contextVersion ===
          activeEnterpriseSelection.getSnapshot().contextVersion
      )
        setBusy(false);
    }
  }
  return (
    <Space direction="vertical" style={{ width: '100%' }}>
      {error && <Alert type="error" content={error} />}
      <Checkbox.Group
        value={selected}
        disabled={disabled || busy || etag === undefined}
        onChange={(values) => setSelected(values.map(String))}
        options={menus.map((menu) => ({
          label: `${menu.titleKey} (${menu.menuId})`,
          value: menu.menuId,
        }))}
      />
      <Button
        onClick={save}
        loading={busy}
        disabled={disabled || etag === undefined}
      >
        保存菜单关联
      </Button>
    </Space>
  );
}
