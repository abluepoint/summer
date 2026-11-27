import React, { useState } from 'react';
import { Alert, Button, Card, Select, Space } from '@arco-design/web-react';
import { useDirectory } from '../directory';
import FileWorkspace from './FileWorkspace';

type Choice = { id: string; name: string };
const locationKey = 'platform.files.location';

export default function Files() {
  const { data, error, reload } = useDirectory<Choice[]>('/files');
  const [selection, setSelection] = useState(
    () => new URLSearchParams(sessionStorage.getItem(locationKey) || '')
  );
  const selected = data?.find((repo) => repo.id === selection.get('id'));
  const id = selected?.id || data?.[0]?.id;
  const folder = selected ? selection.get('folder') || '' : '';

  function open(id: string, folder: string) {
    const next = new URLSearchParams({ id, folder });
    sessionStorage.setItem(locationKey, next.toString());
    setSelection(next);
  }
  return (
    <Card title="文件管理器">
      <Space direction="vertical" style={{ width: '100%' }}>
        <Space>
          <span>文件存储库</span>
          <Select
            aria-label="文件存储库"
            placeholder="选择文件存储库"
            value={id || undefined}
            style={{ width: 260 }}
            options={(data || []).map((repo) => ({
              label: repo.name,
              value: repo.id,
            }))}
            loading={!data && !error}
            onChange={(value) => open(value, '')}
          />
        </Space>
        {error && (
          <Alert
            type="error"
            content={error}
            action={<Button onClick={reload}>重试</Button>}
          />
        )}
        {data?.length === 0 && (
          <Alert content="尚无文件存储库，请先在「文件存储库」中登记。" />
        )}
        {id && (
          <FileWorkspace
            key={id}
            id={id}
            folder={folder}
            navigate={(folder) => open(id, folder)}
          />
        )}
      </Space>
    </Card>
  );
}
