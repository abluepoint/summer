import React, { useState } from 'react';
import listStyles from '@/pages/platform/list-scroll.module.less';
import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
} from '@arco-design/web-react';
import { useInterfaceOperations } from '@/auth/useInterfaceOperations';
import { call } from '../../permissions/api';
import { base, useDirectory } from '../directory';

const path = '/file-repositories';
type StorageType = 'LOCAL' | 'S3';
type Repository = {
  id: string;
  name: string;
  path: string;
  type: StorageType;
  state: 'ONLINE' | 'OFFLINE';
  blobCount: number | null;
  totalSize: number | null;
  availableSpace: number | null;
};

function bytes(value: number | null) {
  if (value == null) return '—';
  const units = ['B', 'KiB', 'MiB', 'GiB', 'TiB', 'PiB'];
  let index = 0;
  while (value >= 1024 && index < units.length - 1) {
    value /= 1024;
    index++;
  }
  return (
    value.toLocaleString('zh-CN', { maximumFractionDigits: 2 }) +
    ' ' +
    units[index]
  );
}

export default function FileRepositories() {
  const { data, error, reload } = useDirectory<Repository[]>(path);
  const ops = useInterfaceOperations({ kind: 'PLATFORM' }, [
    'fileRepositories.create',
    'fileRepositories.remove',
    'fileRepositories.credentials',
  ]);
  const [dialog, setDialog] = useState<'create' | Repository>();
  const [name, setName] = useState('');
  const [type, setType] = useState<StorageType>('LOCAL');
  const [location, setLocation] = useState('');
  const [editingCredentials, setEditingCredentials] = useState(false);
  const [accessKeyId, setAccessKeyId] = useState('');
  const [secretAccessKey, setSecretAccessKey] = useState('');
  const needsCredentials =
    editingCredentials || (dialog === 'create' && type === 'S3');
  function clearCredentials() {
    setAccessKeyId('');
    setSecretAccessKey('');
  }
  function close() {
    clearCredentials();
    setEditingCredentials(false);
    setDialog(undefined);
  }
  const [failure, setFailure] = useState('');
  const [busy, setBusy] = useState(false);

  function open(target: 'create' | Repository, editKeys = false) {
    clearCredentials();
    setEditingCredentials(editKeys);
    setFailure('');
    setName('');
    setLocation('');
    setType('LOCAL');
    setDialog(target);
  }

  async function save() {
    if (!dialog || busy) return;
    setBusy(true);
    setFailure('');
    try {
      if (dialog === 'create') {
        await call(base, path, 'POST', {
          name: name.trim(),
          type,
          path: location.trim(),
          ...(needsCredentials
            ? { accessKeyId: accessKeyId.trim(), secretAccessKey }
            : {}),
        });
      } else if (editingCredentials) {
        await call(
          base,
          path + '/' + encodeURIComponent(dialog.id) + '/credentials',
          'PUT',
          { accessKeyId: accessKeyId.trim(), secretAccessKey }
        );
      } else {
        await call(base, path + '/' + encodeURIComponent(dialog.id), 'DELETE');
      }
      close();
      reload();
    } catch (e) {
      setFailure(e.message || '操作失败，请重试');
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card
      title="文件存储库"
      extra={
        <Space>
          <Button onClick={reload}>刷新</Button>
          {ops['fileRepositories.create'] && (
            <Button type="primary" onClick={() => open('create')}>
              添加存储库
            </Button>
          )}
        </Space>
      }
    >
      {error && <Alert type="error" content={error} />}
      <div
        role="region"
        aria-label="文件存储库列表"
        tabIndex={0}
        className={listStyles.listScroll}
        style={{ overflowX: 'auto' }}
      >
        <Table
          rowKey="id"
          data={data || []}
          loading={!data && !error}
          style={{ minWidth: 950 }}
          columns={[
            {
              title: '名字',
              dataIndex: 'name',
              sorter: (a, b) => a.name.localeCompare(b.name, 'zh-CN'),
            },
            { title: '路径', dataIndex: 'path' },
            {
              title: '类型',
              dataIndex: 'type',
              render: (v) => (v === 'LOCAL' ? '本地文件' : 'S3'),
            },
            {
              title: '状态',
              dataIndex: 'state',
              render: (v) => (
                <Tag color={v === 'ONLINE' ? 'green' : 'red'}>
                  {v === 'ONLINE' ? '在线' : '不可用'}
                </Tag>
              ),
            },
            {
              title: 'Blob 数量',
              dataIndex: 'blobCount',
              render: (v) => v ?? '—',
            },
            { title: '总大小', dataIndex: 'totalSize', render: bytes },
            {
              title: '可用空间',
              dataIndex: 'availableSpace',
              render: (v, row) => (row.type === 'S3' ? '未提供' : bytes(v)),
            },
            ...(ops['fileRepositories.remove'] ||
            ops['fileRepositories.credentials']
              ? [
                  {
                    title: '操作',
                    render: (_, row) => (
                      <Space>
                        {row.type === 'S3' &&
                          ops['fileRepositories.credentials'] && (
                            <Button type="text" onClick={() => open(row, true)}>
                              更换密钥
                            </Button>
                          )}
                        {ops['fileRepositories.remove'] && (
                          <Button
                            type="text"
                            status="danger"
                            onClick={() => open(row)}
                          >
                            移除
                          </Button>
                        )}
                      </Space>
                    ),
                  },
                ]
              : []),
          ]}
        />
      </div>
      <Modal
        visible={!!dialog}
        title={
          dialog === 'create'
            ? '添加文件存储库'
            : editingCredentials
            ? '更换 S3 密钥 · ' + (dialog?.name || '')
            : '移除文件存储库'
        }
        onCancel={() => !busy && close()}
        onOk={save}
        confirmLoading={busy}
        okButtonProps={{
          disabled:
            (dialog === 'create' && (!name.trim() || !location.trim())) ||
            (needsCredentials &&
              (!accessKeyId.trim() || !secretAccessKey.trim())),
        }}
      >
        {dialog === 'create' || editingCredentials ? (
          <Form layout="vertical">
            {dialog === 'create' && (
              <>
                <Form.Item label="名字" required>
                  <Input
                    aria-label="名字"
                    value={name}
                    maxLength={100}
                    onChange={setName}
                  />
                </Form.Item>
                <Form.Item label="类型" required>
                  <Select
                    aria-label="类型"
                    value={type}
                    onChange={(v) => {
                      clearCredentials();
                      setType(v);
                      setLocation('');
                    }}
                    options={[
                      { label: '本地文件', value: 'LOCAL' },
                      { label: 'S3', value: 'S3' },
                    ]}
                  />
                </Form.Item>
                <Form.Item
                  label={
                    type === 'LOCAL'
                      ? '根目录下的子目录名'
                      : '已有 S3 存储桶名称'
                  }
                  required
                >
                  <Input
                    aria-label="存储位置"
                    value={location}
                    onChange={setLocation}
                    maxLength={type === 'LOCAL' ? 128 : 63}
                  />
                </Form.Item>
              </>
            )}
            {needsCredentials && (
              <>
                <Form.Item
                  label={<span id="s3-access-key-label">Access Key ID</span>}
                  required
                >
                  <Input
                    aria-labelledby="s3-access-key-label"
                    value={accessKeyId}
                    onChange={setAccessKeyId}
                    autoCapitalize="none"
                    autoCorrect="off"
                    spellCheck={false}
                    maxLength={256}
                    required
                  />
                </Form.Item>
                <Form.Item
                  label={
                    <span id="s3-secret-key-label">Secret Access Key</span>
                  }
                  required
                >
                  <Input.Password
                    aria-labelledby="s3-secret-key-label"
                    value={secretAccessKey}
                    onChange={setSecretAccessKey}
                    autoComplete="new-password"
                    maxLength={512}
                    required
                  />
                </Form.Item>
              </>
            )}
          </Form>
        ) : (
          dialog && <p>移除“{dialog.name}”的登记？已存储的文件会保留。</p>
        )}
        {failure && <Alert type="error" content={failure} />}
      </Modal>
    </Card>
  );
}
