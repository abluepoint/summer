import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { useInterfaceOperations } from '@/auth/useInterfaceOperations';
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
} from '@arco-design/web-react';
import { readEnvelope } from '@/pages/customer/contract.mjs';

const endpoint = '/api/v1/platform/administrative-divisions';
const levels = ['', '省级', '地级', '县级', '乡级'];

type Division = {
  id: string;
  parentId: string | null;
  code: string | null;
  level: number;
  name: string;
  updatedAt: string;
  sourceDate: string | null;
  revision: number;
  hasChildren: boolean;
  children?: Division[];
};
type Editor = {
  row?: Division;
  parent?: Division;
  level: number;
  name: string;
  code: string;
};
type Branches = Record<string, Division[]>;

function failure(error: unknown): string {
  if (axios.isAxiosError(error))
    return error.response?.data?.error?.message || error.message;
  if (error && typeof error === 'object' && 'message' in error)
    return String((error as { message: unknown }).message);
  return '区划请求失败';
}

export default function DivisionsPage() {
  const allowed = useInterfaceOperations({ kind: 'PLATFORM' }, [
    'divisions.create',
    'divisions.update',
    'divisions.delete',
  ]);
  const [branches, setBranches] = useState<Branches>({});
  const [expanded, setExpanded] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [listError, setListError] = useState('');
  const [editor, setEditor] = useState<Editor>();
  const [removing, setRemoving] = useState<Division>();
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState('');

  useEffect(() => {
    const abort = new AbortController();
    async function load() {
      const missing = ['', ...expanded].filter((id) => !branches[id]);
      setLoading(missing.length > 0);
      setListError('');
      if (!missing.length) return;
      const next: Branches = {};
      for (const parentId of missing) {
        const response = await axios.get(endpoint, {
          params: parentId ? { parentId } : {},
          signal: abort.signal,
        });
        next[parentId] = readEnvelope(
          response.data,
          response.status
        ) as Division[];
      }
      if (!abort.signal.aborted)
        setBranches((current) => ({ ...current, ...next }));
    }
    load().catch((error) => {
      if (!abort.signal.aborted) {
        setListError(failure(error));
        setLoading(false);
      }
    });
    return () => abort.abort();
  }, [branches, expanded]);

  function tree(parentId: string): Division[] {
    return (branches[parentId] || []).map((row) =>
      row.hasChildren
        ? { ...row, children: expanded.includes(row.id) ? tree(row.id) : [] }
        : row
    );
  }

  function openEditor(parent?: Division, row?: Division) {
    setFormError('');
    setEditor({
      parent,
      row,
      name: row?.name || '',
      code: row?.code || '',
      level: row?.level || (parent ? parent.level + 1 : 1),
    });
  }

  async function save() {
    if (saving) return;
    if (
      editor &&
      (!editor.name.trim() ||
        (!editor.row &&
          !new RegExp(editor.level === 4 ? '^\\d{9}$' : '^\\d{6}$').test(
            editor.code
          )))
    ) {
      setFormError('请填写名称和该层级对应位数的区划代码');
      return;
    }
    setSaving(true);
    setFormError('');
    try {
      let response;
      if (removing) {
        response = await axios.delete(endpoint + '/' + removing.id, {
          params: { revision: removing.revision },
        });
      } else if (editor?.row) {
        response = await axios.put(endpoint + '/' + editor.row.id, {
          name: editor.name.trim(),
          revision: editor.row.revision,
        });
      } else if (editor) {
        response = await axios.post(endpoint, {
          name: editor.name.trim(),
          code: editor.code,
          level: editor.level,
          parentId: editor.parent?.id || null,
        });
      }
      if (response?.data) readEnvelope(response.data, response.status);
      if (editor?.parent) {
        const id = editor.parent.id;
        setExpanded((current) =>
          current.includes(id) ? current : [...current, id]
        );
      } else if (removing) {
        setExpanded((current) => current.filter((id) => id !== removing.id));
      }
      setEditor(undefined);
      setRemoving(undefined);
      setBranches({});
    } catch (error) {
      setFormError(failure(error));
    } finally {
      setSaving(false);
    }
  }

  return (
    <Card
      title="省市区管理"
      extra={
        <Space>
          <Button onClick={() => setBranches({})}>刷新</Button>
          <Button
            type="primary"
            disabled={!allowed['divisions.create']}
            onClick={() => openEditor()}
          >
            新增省级区划
          </Button>
        </Space>
      }
    >
      {listError && <Alert type="error" content={listError} />}
      <div
        role="region"
        aria-label="行政区划树形表格"
        tabIndex={0}
        style={{ overflowX: 'auto' }}
      >
        <Table<Division>
          rowKey="id"
          data={tree('')}
          loading={loading}
          pagination={false}
          expandProps={{ strictTreeData: false }}
          expandedRowKeys={expanded}
          onExpandedRowsChange={(keys) => setExpanded(keys.map(String))}
          style={{ minWidth: 820 }}
          columns={[
            { title: '区划名称', dataIndex: 'name' },
            { title: '区划代码', render: (_, row) => row.code || '资料暂缺' },
            { title: '层级', render: (_, row) => levels[row.level] },
            {
              title: '来源数据截止日',
              render: (_, row) => row.sourceDate || '—',
            },
            {
              title: '数据更新时间',
              render: (_, row) =>
                new Date(row.updatedAt).toLocaleString('zh-CN', {
                  hour12: false,
                }),
            },
            {
              title: '操作',
              render: (_, row) => (
                <Space size="mini">
                  {row.level < 4 && (
                    <Button
                      size="mini"
                      disabled={!allowed['divisions.create']}
                      onClick={() => openEditor(row)}
                    >
                      新增下级
                    </Button>
                  )}
                  <Button
                    size="mini"
                    disabled={!allowed['divisions.update']}
                    onClick={() => openEditor(undefined, row)}
                  >
                    修改
                  </Button>
                  <Button
                    size="mini"
                    status="danger"
                    disabled={row.hasChildren || !allowed['divisions.delete']}
                    onClick={() => {
                      setFormError('');
                      setRemoving(row);
                    }}
                  >
                    删除
                  </Button>
                </Space>
              ),
            },
          ]}
        />
      </div>
      <Modal
        title={removing ? '删除区划' : editor?.row ? '修改区划' : '新增区划'}
        visible={!!editor || !!removing}
        confirmLoading={saving}
        onOk={save}
        onCancel={() => {
          if (!saving) {
            setEditor(undefined);
            setRemoving(undefined);
          }
        }}
      >
        {formError && <Alert type="error" content={formError} />}
        {removing && <p>确定删除“{removing.name}”吗？</p>}
        {editor && (
          <Form layout="vertical" disabled={saving}>
            {editor.parent && (
              <Form.Item label="上级区划">{editor.parent.name}</Form.Item>
            )}
            <Form.Item label="层级">
              <Select
                aria-label="层级"
                value={editor.level}
                disabled={!!editor.row || !editor.parent}
                onChange={(level) => setEditor({ ...editor, level })}
              >
                {levels.map(
                  (label, level) =>
                    level > (editor.parent?.level || 0) && (
                      <Select.Option key={level} value={level}>
                        {label}
                      </Select.Option>
                    )
                )}
              </Select>
            </Form.Item>
            <Form.Item label="区划代码" required={!editor.row}>
              <Input
                aria-label="区划代码"
                value={editor.code}
                disabled={!!editor.row}
                placeholder={editor.level === 4 ? '9 位数字' : '6 位数字'}
                maxLength={editor.level === 4 ? 9 : 6}
                onChange={(code) => setEditor({ ...editor, code })}
              />
            </Form.Item>
            <Form.Item label="区划名称" required>
              <Input
                aria-label="区划名称"
                value={editor.name}
                maxLength={120}
                onChange={(name) => setEditor({ ...editor, name })}
              />
            </Form.Item>
          </Form>
        )}
      </Modal>
    </Card>
  );
}
