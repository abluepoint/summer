import React, { useRef, useState } from 'react';
import axios from 'axios';
import {
  Alert,
  Breadcrumb,
  Button,
  Input,
  Message,
  Modal,
  Space,
  Table,
} from '@arco-design/web-react';
import { useInterfaceOperations } from '@/auth/useInterfaceOperations';
import { call, Failure } from '../../permissions/api';
import { base } from '../directory';

export type Entry = {
  name: string;
  folder: boolean;
  size: number;
  createdBy?: string | null;
  createdAt?: string | null;
};

type UploadItem = {
  name: string;
  status: '等待上传' | '上传中' | '已上传' | '上传失败';
  error?: string;
};

function uploadError(error: Partial<Failure>) {
  let reason = error.message;
  if (error.status === 413) reason = '文件超过服务器允许的上传大小';
  else if (error.status === 0)
    reason = '未收到服务器响应，请检查网络并刷新目录确认上传结果';
  if (!reason)
    reason = error.status
      ? `上传请求失败（HTTP ${error.status}）`
      : '上传请求失败，请重试';
  return error.requestId
    ? reason + '（请求 ID：' + error.requestId + '）'
    : reason;
}

export default function Folder({
  id,
  folder,
  navigate,
  data,
  error,
  reload,
}: {
  data?: Entry[];
  error?: string;
  reload: () => void;
  id: string;
  folder: string;
  navigate: (folder: string) => void;
}) {
  const path = '/files/' + encodeURIComponent(id);
  const query = folder ? '?folder=' + encodeURIComponent(folder) : '';
  const ops = useInterfaceOperations({ kind: 'PLATFORM' }, [
    'files.folder',
    'files.upload',
    'files.download',
    'files.delete',
  ]);
  const picker = useRef<HTMLInputElement>(null);
  const [uploads, setUploads] = useState<UploadItem[]>([]);
  const [name, setName] = useState('');
  const [dialog, setDialog] = useState<
    'create' | 'deleteFile' | 'deleteFolder'
  >();
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState('');
  const segments = folder ? folder.split('/') : [];
  const removing = dialog === 'deleteFile' || dialog === 'deleteFolder';
  const entryType = dialog === 'deleteFolder' ? '文件夹' : '文件';
  const child = (name: string) => (folder ? folder + '/' + name : name);

  async function perform(action: () => Promise<void>) {
    if (busy) return;
    setBusy(true);
    setFailure('');
    try {
      await action();
    } catch (e) {
      if (removing) {
        const reason =
          e.message ||
          (e.status ? 'HTTP ' + e.status : '未收到服务器确认，请刷新目录核对');
        setFailure('删除失败：' + reason);
      } else {
        setFailure(e.message || '操作失败，请重试');
      }
    } finally {
      setBusy(false);
    }
  }

  function confirm() {
    return perform(async () => {
      if (removing) {
        const result = await call<{ deleted: boolean }>(
          base,
          path +
            '/entries?key=' +
            encodeURIComponent(
              child(name) + (dialog === 'deleteFolder' ? '/' : '')
            ),
          'DELETE'
        );
        if (!result?.deleted)
          throw new Error('服务器未确认删除，请刷新目录核对');
        Message.success('已删除' + entryType + '：' + name);
      } else {
        const params = new URLSearchParams({ name });
        if (folder) params.set('folder', folder);
        await call(base, path + '/folders?' + params, 'POST');
      }
      setDialog(undefined);
      reload();
    });
  }

  function upload(files: File[]) {
    if (!files.length || busy) return;
    setUploads(files.map((file) => ({ name: file.name, status: '等待上传' })));
    const update = (
      index: number,
      status: UploadItem['status'],
      error?: string
    ) =>
      setUploads((items) =>
        items.map((item, i) =>
          i === index ? { ...item, status, error } : item
        )
      );

    return perform(async () => {
      for (let i = 0; i < files.length; i++) {
        const file = files[i];
        update(i, '上传中');
        const form = new FormData();
        form.append('file', file);
        try {
          const result = await call<{ uploaded: boolean }>(
            base,
            path + '/upload' + query,
            'POST',
            form
          );
          if (result?.uploaded !== true)
            throw new Error('服务器未确认上传，请刷新目录核对');
          update(i, '已上传');
        } catch (error) {
          const reason = uploadError(error);
          update(i, '上传失败', reason);
          Message.error({
            id: 'platform-file-upload-error',
            content: file.name + ' 上传失败：' + reason,
          });
        }
      }
      reload();
    });
  }

  function download(name: string) {
    return perform(async () => {
      const response = await axios.get(base + path + '/download', {
        params: { key: child(name) },
        responseType: 'blob',
      });
      const url = URL.createObjectURL(response.data);
      const link = document.createElement('a');
      link.href = url;
      link.download = name;
      link.click();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    });
  }

  return (
    <>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: 8,
        }}
      >
        <nav aria-label="文件目录">
          <Breadcrumb separator="/">
            {['根目录', ...segments].map((label, index) => {
              const target = segments.slice(0, index).join('/');
              return (
                <Breadcrumb.Item key={target} style={{ paddingInline: 0 }}>
                  {target === folder ? (
                    <span aria-current="location">{label}</span>
                  ) : (
                    <Button
                      type="text"
                      size="mini"
                      style={{ fontSize: 'inherit', paddingInline: 2 }}
                      disabled={busy}
                      onClick={() => navigate(target)}
                    >
                      {label}
                    </Button>
                  )}
                </Breadcrumb.Item>
              );
            })}
          </Breadcrumb>
        </nav>
        <Space
          wrap
          style={{ marginInlineStart: 'auto', justifyContent: 'flex-end' }}
        >
          <Button disabled={busy} onClick={reload}>
            刷新
          </Button>
          {ops['files.folder'] && (
            <Button
              disabled={!data || busy}
              onClick={() => {
                setFailure('');
                setName('');
                setDialog('create');
              }}
            >
              新建文件夹
            </Button>
          )}
          {ops['files.upload'] && (
            <Button
              type="primary"
              disabled={!data || busy}
              onClick={() => picker.current?.click()}
            >
              上传文件
            </Button>
          )}
        </Space>
      </div>
      {/* Keep the native picker mounted while focus refreshes toolbar permissions. */}
      <input
        hidden
        type="file"
        multiple
        ref={picker}
        aria-label="选择上传文件"
        onChange={(event) => {
          const files = Array.from(event.target.files || []);
          event.target.value = '';
          upload(files);
        }}
      />
      {uploads.length > 0 && (
        <section aria-label="上传列表" aria-live="polite">
          <strong>本次上传</strong>
          <ul>
            {uploads.map((item, i) => (
              <li key={i}>
                {item.error ? (
                  <div role="alert">
                    <Alert
                      type="error"
                      content={item.name + ' · 上传失败：' + item.error}
                    />
                  </div>
                ) : (
                  item.name + ' · ' + item.status
                )}
              </li>
            ))}
          </ul>
        </section>
      )}
      {(error || (failure && !dialog)) && (
        <Alert type="error" content={error || failure} />
      )}
      <Table
        rowKey={(row) => (row.folder ? 'folder:' : 'file:') + row.name}
        data={data || []}
        loading={busy || (!data && !error)}
        columns={[
          {
            title: '名称',
            render: (_, row: Entry) =>
              row.folder ? (
                <Button type="text" onClick={() => navigate(child(row.name))}>
                  {row.name}
                </Button>
              ) : (
                row.name
              ),
          },
          {
            title: '类型',
            render: (_, row: Entry) => (row.folder ? '文件夹' : '文件'),
          },
          {
            title: '大小',
            render: (_, row: Entry) =>
              row.folder ? '—' : row.size.toLocaleString() + ' B',
          },
          {
            title: '创建人',
            render: (_, row: Entry) => row.createdBy || '未记录',
          },
          {
            title: '创建时间',
            render: (_, row: Entry) =>
              row.createdAt ? (
                <time dateTime={row.createdAt}>
                  {new Date(row.createdAt).toLocaleString('zh-CN', {
                    hour12: false,
                  })}
                </time>
              ) : (
                '未记录'
              ),
          },
          {
            title: '操作',
            render: (_, row: Entry) => (
              <Space>
                {!row.folder && ops['files.download'] && (
                  <Button disabled={busy} onClick={() => download(row.name)}>
                    下载
                  </Button>
                )}
                {ops['files.delete'] && (
                  <Button
                    status="danger"
                    disabled={busy}
                    onClick={() => {
                      setFailure('');
                      setName(row.name);
                      setDialog(row.folder ? 'deleteFolder' : 'deleteFile');
                    }}
                  >
                    删除
                  </Button>
                )}
              </Space>
            ),
          },
        ]}
      />
      <Modal
        title={removing ? '删除' + entryType : '新建文件夹'}
        visible={!!dialog}
        okText={removing ? '确认删除' : '确定'}
        onOk={confirm}
        onCancel={() => {
          if (!busy) {
            setDialog(undefined);
            setFailure('');
          }
        }}
        okButtonProps={{
          disabled: !name.trim() || (removing && !ops['files.delete']),
          status: removing ? 'danger' : undefined,
        }}
        confirmLoading={busy}
      >
        {removing ? (
          <p>
            确认删除{entryType}「{child(name)}」？
            {dialog === 'deleteFolder' && '只能删除空文件夹。'}
          </p>
        ) : (
          <>
            <label htmlFor="folder-name">文件夹名称</label>
            <Input id="folder-name" value={name} onChange={setName} />
          </>
        )}
        {failure && <Alert type="error" content={failure} />}
      </Modal>
    </>
  );
}
