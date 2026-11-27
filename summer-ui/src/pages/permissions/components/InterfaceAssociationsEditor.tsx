import React, { useEffect, useRef, useState } from 'react';
import { Alert, Transfer, Space } from '@arco-design/web-react';
import { call, Page, PermissionAssociationProps } from '../api';

type Binding = { resourceIds: string[]; version: number };
export type InterfaceDraft = Binding & { mutationKey: string };
type EditorProps = Pick<
  PermissionAssociationProps,
  'domain' | 'permissionId' | 'onBusyChange' | 'disabled'
> & {
  draft?: InterfaceDraft;
  onChange: (draft?: InterfaceDraft) => void;
};
type Resource = {
  resourceId: string;
  httpMethod: string;
  normalizedPath: string;
  source: string;
  conditions: Record<string, string[]>;
};

function resourceOption(resource: Resource) {
  const conditions = Object.entries(resource.conditions)
    .filter(
      ([key, values]) =>
        key !== 'declaredMethods' &&
        values.length &&
        !(key === 'version' && values.every((value) => value === '[]'))
    )
    .map(([key, values]) => `${key}: ${values.join(', ')}`);
  return {
    key: resource.resourceId,
    value: [
      `${resource.httpMethod} ${resource.normalizedPath}`,
      resource.source,
      ...conditions,
    ].join(' · '),
  };
}

export default function InterfaceAssociationsEditor({
  domain,
  permissionId,
  draft,
  onChange,
  onBusyChange,
  disabled,
}: EditorProps) {
  const base =
    domain.kind === 'PLATFORM'
      ? '/api/v1/platform'
      : `/api/v1/enterprises/${encodeURIComponent(domain.id)}`;
  const [directory, setDirectory] = useState<Resource[]>([]);
  const [binding, setBinding] = useState<Binding>();
  const [checked, setChecked] = useState<string[]>([]);
  const [error, setError] = useState('');
  const generation = useRef(0);
  useEffect(() => {
    const requestScope = generation;
    const current = ++requestScope.current;
    onBusyChange?.(true);
    setBinding(undefined);
    setChecked([]);
    setError('');
    async function loadDirectory() {
      const size = 200;
      const fetchPage = (page: number) =>
        call<Page<Resource>>(
          base,
          `/authorization/interface-directory?page=${page}&size=${size}`
        );
      const first = await fetchPage(0);
      const items = [...first.items];
      for (
        let page = 1;
        current === generation.current && page * size < first.total;
        page++
      ) {
        const next = await fetchPage(page);
        items.push(...next.items);
      }
      return items;
    }
    Promise.all([
      loadDirectory(),
      call<Binding>(
        base,
        `/permissions/${encodeURIComponent(permissionId)}/interfaces`
      ),
    ])
      .then(([resources, binding]) => {
        if (current !== generation.current) return;
        setDirectory(resources);
        setBinding(binding);
      })
      .catch((e) => {
        if (current === generation.current)
          setError(e.message || '接口关联读取失败');
      })
      .finally(() => {
        if (current === generation.current) onBusyChange?.(false);
      });
    return () => {
      requestScope.current++;
    };
  }, [base, permissionId, onBusyChange]);

  const selected = draft?.resourceIds || binding?.resourceIds || [];
  const options = new Map(
    directory.map((resource) => [resource.resourceId, resourceOption(resource)])
  );
  for (const id of selected) {
    if (!options.has(id))
      options.set(id, { key: id, value: `目录中已失效 · ${id}` });
  }

  return (
    <Space direction="vertical" style={{ width: '100%' }}>
      {error && <Alert type="error" content={error} />}
      <Transfer
        dataSource={Array.from(options.values())}
        targetKeys={selected}
        selectedKeys={checked}
        onSelectChange={(left, right) => setChecked([...left, ...right])}
        onChange={(ids, _direction, moved) => {
          if (!binding || disabled) return;
          setChecked((keys) => keys.filter((id) => !moved.includes(id)));
          const original = new Set(binding.resourceIds);
          onChange(
            ids.length === original.size && ids.every((id) => original.has(id))
              ? undefined
              : {
                  resourceIds: ids,
                  version: binding.version,
                  mutationKey: `interface-bind-${Date.now()}-${Math.random()
                    .toString(36)
                    .slice(2)}`,
                }
          );
        }}
        disabled={disabled || !binding}
        titleTexts={['可选接口', '已选接口']}
        showSearch
        searchPlaceholder={['搜索可选接口', '搜索已选接口']}
        filterOption={(input, item) =>
          item.value.toLowerCase().includes(input.trim().toLowerCase())
        }
        render={(item) => <span title={item.value}>{item.value}</span>}
        pagination={{ pageSize: 20, simple: true }}
        listStyle={{ flex: 1, minWidth: 0, height: 320 }}
      />
    </Space>
  );
}
