import React, { useMemo, useState } from 'react';
import { Button, Input } from '@arco-design/web-react';
import ResizableTreePanel from '@/components/ResizableTreePanel';
import { Group } from '../api';
import styles from './permission-group-tree.module.less';

type Branch = { group: Group; children: Branch[] };

function branches(groups: Group[], query: string) {
  const byParent = new Map<string | null, Group[]>();
  for (const group of groups) {
    const siblings = byParent.get(group.parentGroupId);
    if (siblings) siblings.push(group);
    else byParent.set(group.parentGroupId, [group]);
  }
  const parentIds: string[] = [];
  function visit(parent: string | null): Branch[] {
    return (byParent.get(parent) || []).flatMap((group) => {
      const children = visit(group.groupId);
      if (children.length) parentIds.push(group.groupId);
      return children.length || group.name.toLowerCase().includes(query)
        ? [{ group, children }]
        : [];
    });
  }
  const tree = visit(null);
  return { tree, parentIds };
}

type Props = {
  groups: Group[];
  selected?: Group;
  onSelect: (id?: string) => void;
  renderGroupCheckbox?: (group: Group) => React.ReactNode;
  management?: {
    onCreate: () => void;
    onEdit: (group: Group) => void;
    onDelete: (group: Group) => void;
    canCreate: boolean;
    canEdit: boolean;
    canDelete: boolean;
  };
  loading: boolean;
};

export default function PermissionGroupTree({
  groups,
  selected,
  onSelect,
  renderGroupCheckbox,
  management,
  loading,
}: Props) {
  const [search, setSearch] = useState('');
  const [collapsed, setCollapsed] = useState<string[]>([]);
  const [searchCollapsed, setSearchCollapsed] = useState<string[]>([]);
  const query = search.trim().toLowerCase();
  const { tree, parentIds } = useMemo(
    () => branches(groups, query),
    [groups, query]
  );
  const closed = query ? searchCollapsed : collapsed;
  const setClosed = query ? setSearchCollapsed : setCollapsed;

  const closedIds = useMemo(() => new Set(closed), [closed]);

  const matchPattern = useMemo(
    () =>
      query
        ? new RegExp(
            '(' + query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + ')',
            'ig'
          )
        : undefined,
    [query]
  );

  function rows(nodes: Branch[]) {
    return (
      <ul className={styles.branch}>
        {nodes.map(({ group, children }) => {
          const id = group.groupId;
          const expanded = !closedIds.has(id);
          return (
            <li key={id}>
              <div
                className={styles.row}
                data-selected={selected?.groupId === id}
              >
                {renderGroupCheckbox?.(group)}
                <button
                  type="button"
                  className={styles.name}
                  aria-label={group.name}
                  aria-pressed={selected?.groupId === id}
                  title={group.name}
                  onClick={() => onSelect(id)}
                >
                  {matchPattern
                    ? group.name
                        .split(matchPattern)
                        .map((part, index) =>
                          index % 2 ? <mark key={index}>{part}</mark> : part
                        )
                    : group.name}
                </button>
                {children.length > 0 && (
                  <button
                    type="button"
                    className={styles.toggle}
                    aria-label={`${expanded ? '收起' : '展开'}${group.name}`}
                    aria-expanded={expanded}
                    onClick={() =>
                      setClosed((old) =>
                        expanded
                          ? [...old, id]
                          : old.filter((key) => key !== id)
                      )
                    }
                  >
                    {expanded ? '收起' : '展开'}
                  </button>
                )}
              </div>
              {expanded && children.length > 0 && rows(children)}
            </li>
          );
        })}
      </ul>
    );
  }

  return (
    <ResizableTreePanel id="permission-group-panel" label="权限组">
      <header className={styles.header}>
        <strong>权限组</strong>
        {management && (
          <Button
            type="text"
            size="small"
            onClick={management.onCreate}
            disabled={!management.canCreate}
          >
            {selected ? '新建子组' : '新建组'}
          </Button>
        )}
      </header>
      <Input
        allowClear
        aria-label="搜索权限组"
        placeholder="搜索权限组"
        value={search}
        onChange={(value) => {
          setSearch(value);
          setSearchCollapsed([]);
        }}
      />
      <div className={styles.treeActions}>
        <Button
          type="text"
          size="mini"
          aria-controls="permission-group-tree"
          disabled={!parentIds.some((id) => closedIds.has(id))}
          onClick={() => setClosed([])}
        >
          全部展开
        </Button>
        <Button
          type="text"
          size="mini"
          aria-controls="permission-group-tree"
          disabled={!parentIds.some((id) => !closedIds.has(id))}
          onClick={() => setClosed(parentIds)}
        >
          全部收起
        </Button>
      </div>
      <div id="permission-group-tree" className={styles.tree}>
        <button
          type="button"
          className={styles.all}
          aria-pressed={!selected}
          onClick={() => onSelect()}
        >
          全部权限
        </button>
        {tree.length > 0 && (
          <div className={styles.rootBranches}>{rows(tree)}</div>
        )}
        {!tree.length && (
          <div className={styles.empty} role="status">
            {loading
              ? '正在加载权限组…'
              : query
              ? '未找到匹配的权限组'
              : '暂无权限组'}
          </div>
        )}
      </div>
      {selected && management && (
        <footer className={styles.footer}>
          <span title={selected.name}>{selected.name}</span>
          <Button
            size="mini"
            disabled={!management.canEdit}
            onClick={() => management.onEdit(selected)}
          >
            编辑组
          </Button>
          <Button
            size="mini"
            status="danger"
            disabled={!management.canDelete}
            onClick={() => management.onDelete(selected)}
          >
            删除组
          </Button>
        </footer>
      )}
    </ResizableTreePanel>
  );
}
