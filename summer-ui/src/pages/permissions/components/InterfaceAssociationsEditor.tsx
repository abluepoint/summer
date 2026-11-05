import React, { useEffect, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Checkbox,
  Input,
  Pagination,
  Space,
  Typography,
} from '@arco-design/web-react';
import { call, Page, PermissionAssociationProps } from '../api';

type Binding = { resourceIds: string[]; version: number };
type Resource = {
  resourceId: string;
  httpMethod: string;
  normalizedPath: string;
  source: string;
  conditions: Record<string, string[]>;
};

export default function InterfaceAssociationsEditor({
  domain,
  permissionId,
  version,
  onSaved,
  onDirtyChange,
  onBusyChange,
  disabled,
}: PermissionAssociationProps) {
  const base =
    domain.kind === 'PLATFORM'
      ? '/api/v1/platform'
      : `/api/v1/enterprises/${encodeURIComponent(domain.id)}`;
  const [directory, setDirectory] = useState<Page<Resource>>();
  const [selected, setSelected] = useState<string[]>([]);
  const [etag, setEtag] = useState<number>();
  const [page, setPage] = useState(0);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [query, setQuery] = useState('');
  const generation = useRef(0);
  const mutation = useRef<{ fingerprint: string; key: string }>();
  const pageGeneration = useRef(0);
  const baseline = useRef('[]');
  const callbacks = useRef({ onDirtyChange, onBusyChange });
  callbacks.current = { onDirtyChange, onBusyChange };
  useEffect(() => {
    callbacks.current.onDirtyChange?.(
      JSON.stringify([...selected].sort()) !== baseline.current
    );
  }, [selected]);
  useEffect(() => {
    callbacks.current.onBusyChange?.(busy);
  }, [busy]);
  useEffect(
    () => () => {
      callbacks.current.onDirtyChange?.(false);
      callbacks.current.onBusyChange?.(false);
    },
    []
  );

  useEffect(() => {
    const requestScope = generation;
    const pageScope = pageGeneration;
    const current = ++requestScope.current;
    ++pageScope.current;
    baseline.current = '[]';
    setDirectory(undefined);
    setSelected([]);
    setEtag(undefined);
    setPage(0);
    setError('');
    setBusy(true);
    setQuery('');
    Promise.all([
      call<Page<Resource>>(base, '/authorization/interface-directory?size=50'),
      call<Binding>(
        base,
        `/permissions/${encodeURIComponent(permissionId)}/interfaces`
      ),
    ])
      .then(([resources, binding]) => {
        if (current !== generation.current) return;
        setDirectory(resources);
        baseline.current = JSON.stringify([...binding.resourceIds].sort());
        setSelected(binding.resourceIds);
        setEtag(binding.version);
      })
      .catch((e) => {
        if (current === generation.current)
          setError(e.message || '接口关联读取失败');
      })
      .finally(() => {
        if (current === generation.current) setBusy(false);
      });
    return () => {
      requestScope.current++;
      pageScope.current++;
    };
  }, [base, permissionId, version]);

  async function changePage(next: number) {
    if (busy) return;
    const current = generation.current;
    const request = ++pageGeneration.current;
    setBusy(true);
    setError('');
    try {
      const resources = await call<Page<Resource>>(
        base,
        `/authorization/interface-directory?page=${next}&size=50`
      );
      if (current !== generation.current || request !== pageGeneration.current)
        return;
      setDirectory(resources);
      setPage(next);
    } catch (e) {
      if (current === generation.current)
        setError(e.message || '接口目录读取失败');
    } finally {
      if (current === generation.current && request === pageGeneration.current)
        setBusy(false);
    }
  }

  async function save() {
    if (busy || disabled || etag === undefined) return;
    const current = generation.current;
    const fingerprint = JSON.stringify([
      base,
      permissionId,
      etag,
      [...selected].sort(),
    ]);
    if (mutation.current?.fingerprint !== fingerprint)
      mutation.current = {
        fingerprint,
        key: `interface-bind-${Date.now()}-${Math.random()
          .toString(36)
          .slice(2)}`,
      };
    setBusy(true);
    setError('');
    try {
      const result = await call<Binding>(
        base,
        `/permissions/${encodeURIComponent(permissionId)}/interfaces`,
        'PUT',
        { resourceIds: selected },
        etag,
        mutation.current.key
      );
      if (current !== generation.current) return;
      baseline.current = JSON.stringify([...result.resourceIds].sort());
      setSelected(result.resourceIds);
      setEtag(result.version);
      mutation.current = undefined;
      callbacks.current.onDirtyChange?.(false);
      onSaved();
    } catch (e) {
      if (current === generation.current)
        setError(e.message || '接口关联保存失败');
    } finally {
      if (current === generation.current) setBusy(false);
    }
  }

  const visible = (directory?.items || []).filter((resource) =>
    `${resource.httpMethod} ${resource.normalizedPath}`
      .toLowerCase()
      .includes(query.toLowerCase())
  );
  return (
    <Space direction="vertical" style={{ width: '100%' }}>
      {error && <Alert type="error" content={error} />}
      <Typography.Text>
        已选择 {selected.length} 项接口。翻页保留选择；失效接口可在下方移除。
      </Typography.Text>
      <Input
        aria-label="筛选本页接口"
        placeholder="筛选本页方法或路径"
        value={query}
        onChange={setQuery}
      />
      <div style={{ maxHeight: 320, overflowY: 'auto', width: '100%' }}>
        {visible.map((resource) => (
          <Checkbox
            key={resource.resourceId}
            style={{
              display: 'flex',
              marginBottom: 10,
              overflowWrap: 'anywhere',
            }}
            checked={selected.includes(resource.resourceId)}
            disabled={busy || disabled || etag === undefined}
            onChange={(checked) =>
              setSelected((ids) =>
                checked
                  ? Array.from(new Set([...ids, resource.resourceId]))
                  : ids.filter((id) => id !== resource.resourceId)
              )
            }
          >
            {resource.httpMethod} {resource.normalizedPath} · {resource.source}
            <Typography.Text type="secondary">
              {' '}
              {Object.entries(resource.conditions)
                .filter(
                  ([key, values]) =>
                    key !== 'declaredMethods' &&
                    values.length &&
                    !(
                      key === 'version' &&
                      values.every((value) => value === '[]')
                    )
                )
                .map(([key, values]) => `${key}: ${values.join(', ')}`)
                .join(' · ')}
            </Typography.Text>
          </Checkbox>
        ))}
      </div>
      <Pagination
        current={page + 1}
        pageSize={50}
        total={directory?.total || 0}
        onChange={(value) => changePage(value - 1)}
      />
      <details>
        <summary>已选资源（点击移除）</summary>
        {selected.map((id) => (
          <Button
            key={id}
            disabled={busy || disabled}
            onClick={() =>
              setSelected((ids) => ids.filter((value) => value !== id))
            }
          >
            {id} ×
          </Button>
        ))}
      </details>
      <Button
        onClick={save}
        loading={busy}
        disabled={disabled || etag === undefined}
      >
        保存接口关联
      </Button>
    </Space>
  );
}
