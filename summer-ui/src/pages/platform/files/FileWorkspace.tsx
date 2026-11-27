import React, { useCallback, useEffect, useRef, useState } from 'react';
import listStyles from '@/pages/platform/list-scroll.module.less';
import { Tree } from '@arco-design/web-react';
import { IconFolder } from '@arco-design/web-react/icon';
import type { TreeDataType } from '@arco-design/web-react/es/Tree/interface';
import ResizableTreePanel from '@/components/ResizableTreePanel';
import { call } from '../../permissions/api';
import { base } from '../directory';
import Folder, { Entry } from './Folder';
import styles from './style.module.less';

type Listing = { data?: Entry[]; error?: string };

function ancestors(folder: string) {
  const paths = [''];
  for (const part of folder ? folder.split('/') : []) {
    const parent = paths[paths.length - 1];
    paths.push(parent ? parent + '/' + part : part);
  }
  return paths;
}

export default function FileWorkspace({
  id,
  folder,
  navigate,
}: {
  id: string;
  folder: string;
  navigate: (path: string) => void;
}) {
  const [listings, setListings] = useState(new Map<string, Listing>());
  const requests = useRef(new Map<string, number>());
  const [expanded, setExpanded] = useState(() => ancestors(folder));
  const load = useCallback(
    async (path: string, refresh = false) => {
      const previous = requests.current.get(path);
      if (previous && !refresh) return;
      const version = (previous || 0) + 1;
      requests.current.set(path, version);
      let listing: Listing;
      try {
        const query = path ? '?folder=' + encodeURIComponent(path) : '';
        const data = await call<Entry[]>(
          base,
          '/files/' + encodeURIComponent(id) + '/entries' + query
        );
        listing = { data };
      } catch (e) {
        listing = { error: e.message || '读取目录失败，请重试' };
      }
      if (requests.current.get(path) === version) {
        setListings((all) => new Map(all).set(path, listing));
      }
    },
    [id]
  );

  useEffect(() => {
    const paths = ancestors(folder);
    setExpanded((keys) => Array.from(new Set([...keys, ...paths])));
    paths.forEach((path) => load(path, path === folder));
  }, [folder, load]);

  function open(path: string) {
    if (path === folder) {
      setExpanded((keys) => Array.from(new Set([...keys, ...ancestors(path)])));
    } else {
      navigate(path);
    }
  }

  function directory(path: string, title: string): TreeDataType {
    const { data, error } = listings.get(path) || {};
    const folders = data?.filter((entry) => entry.folder);
    const children = expanded.includes(path)
      ? folders?.map(({ name }) =>
          directory(path ? path + '/' + name : name, name)
        )
      : undefined;
    return {
      key: 'dir:' + path,
      title,
      icon: <IconFolder />,
      isLeaf: folders?.length === 0,
      children:
        expanded.includes(path) && !data
          ? [
              {
                key: 'status:' + path,
                title: error || '加载中…',
                disabled: true,
                isLeaf: true,
              },
            ]
          : children,
    };
  }

  const { data, error } = listings.get(folder) || {};
  return (
    <div className={styles.workspace}>
      <ResizableTreePanel id="platform-file-tree" label="目录树">
        <div className={`${styles.tree} ${listStyles.listScroll}`}>
          <Tree
            aria-label="目录树"
            size="small"
            blockNode
            autoExpandParent={false}
            expandedKeys={expanded.map((path) => 'dir:' + path)}
            selectedKeys={['dir:' + folder]}
            treeData={[directory('', '根目录')]}
            loadMore={(node) => load(String(node.key).slice(4), true)}
            onExpand={(keys) => setExpanded(keys.map((key) => key.slice(4)))}
            onSelect={(_, { node }) => {
              const key = String(node.key);
              if (key.startsWith('dir:')) open(key.slice(4));
            }}
          />
        </div>
      </ResizableTreePanel>
      <section
        className={`${styles.list} ${listStyles.listScroll}`}
        aria-label="文件列表"
        tabIndex={0}
      >
        <Folder
          key={folder}
          id={id}
          folder={folder}
          navigate={open}
          data={data}
          error={error}
          reload={() => load(folder, true)}
        />
      </section>
    </div>
  );
}
