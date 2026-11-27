import React, { useMemo, useState } from 'react';
import { Input, Pagination, Table, Tag } from '@arco-design/web-react';
import { DictionaryOption } from '../api';
import styles from './detail.module.less';

export function matchesDictionaryItem(item: DictionaryOption, query: string) {
  const term = query.trim().toLowerCase();
  return [item.value, item.label, item.description || ''].some((text) =>
    text.toLowerCase().includes(term)
  );
}

// Private to dictionary details: effective, history and publication preview.
export default function DictionaryItemsTable({
  items,
  orderLabel = '默认顺序',
  memberships,
}: {
  items: DictionaryOption[];
  orderLabel?: string;
  memberships?: Map<string, string[]>;
}) {
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const filtered = useMemo(
    () => items.filter((item) => matchesDictionaryItem(item, query)),
    [items, query]
  );
  const current = Math.min(
    page,
    Math.max(1, Math.ceil(filtered.length / pageSize))
  );
  return (
    <>
      <div className={styles.itemToolbar}>
        <div className={styles.itemSearch}>
          <Input.Search
            aria-label="搜索字典值"
            placeholder="搜索字典值、显示名称或说明"
            allowClear
            value={query}
            onChange={(text) => {
              setQuery(text);
              setPage(1);
            }}
          />
        </div>
        <span className={styles.saveHint}>
          {query.trim()
            ? `找到 ${filtered.length} 项，共 ${items.length} 项`
            : `共 ${items.length} 项`}
        </span>
      </div>
      <Table
        rowKey="value"
        pagination={false}
        scroll={{ x: 640 }}
        data={filtered.slice((current - 1) * pageSize, current * pageSize)}
        noDataElement={
          query.trim() ? '没有匹配的字典值，请调整搜索条件。' : '暂无字典值'
        }
        columns={[
          { title: '字典值', dataIndex: 'value' },
          { title: '显示名称', dataIndex: 'label' },
          {
            title: '说明',
            dataIndex: 'description',
            render: (description) => description || '—',
          },
          ...(memberships
            ? [
                {
                  title: '所属字典组及组内顺序',
                  dataIndex: 'value',
                  key: 'memberships',
                  render: (value: string) =>
                    memberships.get(value)?.join('、') || '—',
                },
              ]
            : []),
          { title: orderLabel, dataIndex: 'sortOrder', width: 100 },
          {
            title: '状态',
            width: 100,
            render: (_, item) => (
              <Tag color={item.enabled ? 'green' : 'gray'}>
                {item.enabled ? '启用' : '停用'}
              </Tag>
            ),
          },
        ]}
      />
      <div
        className={styles.itemPagination}
        role="navigation"
        aria-label="字典值分页"
      >
        <Pagination
          current={current}
          pageSize={pageSize}
          total={filtered.length}
          sizeCanChange
          sizeOptions={[10, 20, 50, 100]}
          showTotal={(total) => `共 ${total} 项`}
          onChange={setPage}
          onPageSizeChange={(size) => {
            setPageSize(size);
            setPage(1);
          }}
        />
      </div>
    </>
  );
}
