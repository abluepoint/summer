import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Drawer, Form, Input, InputNumber, Select, Space, Switch, Table, Tag } from '@arco-design/web-react';
import useLocale from '@/utils/useLocale';
import { createParameter, listParameters, Parameter, ParameterApiError, ParameterListQuery, ParameterPage, setParameterStatus, updateParameter } from './api';

const Item = Form.Item;
const empty = { key: '', type: 'STRING' as Parameter['type'], value: '', required: false, min: undefined as number | undefined, max: undefined as number | undefined, pattern: '', public: false, description: '' };

export default function SystemParametersPage() {
  const t = useLocale();
  const [page, setPage] = useState<ParameterPage | null>(null); const [loading, setLoading] = useState(false); const [error, setError] = useState<ParameterApiError | null>(null);
  const [editor, setEditor] = useState<Parameter | null | false>(false); const [form, setForm] = useState(empty); const [saving, setSaving] = useState(false);
  const [query, setQuery] = useState<ParameterListQuery>({ page: 0, size: 20, sort: 'key', direction: 'asc', projection: 'detail' });
  const load = useCallback(() => { setLoading(true); setError(null); listParameters(query).then(setPage).catch((e) => setError(e)).finally(() => setLoading(false)); }, [query]);
  useEffect(() => load(), [load]);
  function open(parameter?: Parameter) { setError(null); setEditor(parameter || null); setForm(parameter ? { ...empty, ...parameter, value: typeof parameter.value === 'object' ? JSON.stringify(parameter.value) : String(parameter.value ?? '') } : empty); }
  function valueForType() { if (form.type === 'BOOLEAN') return form.value === true || form.value === 'true'; if (form.type === 'INTEGER') return Number(form.value); if (form.type === 'DECIMAL') return Number(form.value); return form.value; }
  function save() { setSaving(true); const payload = { ...form, value: valueForType(), publicValue: form.public, ...(editor ? { version: editor.version } : {}) }; const call = editor ? updateParameter(editor.key, payload) : createParameter(payload); call.then(() => { setEditor(false); load(); }).catch((e) => setError(e)).finally(() => setSaving(false)); }
  const updateQuery = (change: Partial<ParameterListQuery>) => setQuery({ ...query, ...change, page: 0 });
  return <Card title={t['systemParameters.title']} extra={<Button type="primary" onClick={() => open()}>{t['systemParameters.create']}</Button>}>
    <Space wrap style={{ marginBottom: 16 }}>
      <Input value={query.key || ''} onChange={(key) => updateQuery({ key: key || undefined })} onPressEnter={load} placeholder={t['systemParameters.key']} style={{ width: 180 }} />
      <Select allowClear value={query.type} onChange={(type) => updateQuery({ type })} placeholder={t['systemParameters.type']} style={{ width: 130 }}>
        {['STRING', 'INTEGER', 'DECIMAL', 'BOOLEAN'].map((type) => <Select.Option key={type} value={type}>{type}</Select.Option>)}
      </Select>
      <Select allowClear value={query.status} onChange={(status) => updateQuery({ status })} placeholder={t['systemParameters.status']} style={{ width: 130 }}>
        <Select.Option value="ACTIVE">ACTIVE</Select.Option><Select.Option value="INACTIVE">INACTIVE</Select.Option>
      </Select>
      <Select allowClear value={query.public === undefined ? undefined : String(query.public)} onChange={(value) => updateQuery({ public: value === undefined ? undefined : value === 'true' })} placeholder="public" style={{ width: 110 }}>
        <Select.Option value="true">public</Select.Option><Select.Option value="false">private</Select.Option>
      </Select>
      <Select value={query.sort} onChange={(sort) => updateQuery({ sort })} style={{ width: 130 }}>
        {['key', 'type', 'status', 'createdAt', 'updatedAt', 'version'].map((sort) => <Select.Option key={sort} value={sort}>{sort}</Select.Option>)}
      </Select>
      <Select value={query.direction} onChange={(direction) => updateQuery({ direction })} style={{ width: 100 }}>
        <Select.Option value="asc">asc</Select.Option><Select.Option value="desc">desc</Select.Option>
      </Select>
      <Select value={query.projection} onChange={(projection) => updateQuery({ projection })} style={{ width: 110 }}>
        <Select.Option value="detail">detail</Select.Option><Select.Option value="summary">summary</Select.Option>
      </Select>
    </Space>
    {error && <Alert type="error" content={`${error.message || t['systemParameters.error']}${error.requestId ? ` (${error.requestId})` : ''}`} style={{ marginBottom: 16 }} />}
    <Table<Parameter> rowKey="key" loading={loading} data={(page?.items || []) as Parameter[]} pagination={{ current: (page?.page || 0) + 1, pageSize: page?.size || query.size, total: page?.totalElements || 0, onChange: (current, size) => setQuery({ ...query, page: current - 1, size }) }} columns={[
      { title: t['systemParameters.key'], dataIndex: 'key' }, { title: t['systemParameters.type'], dataIndex: 'type' }, { title: t['systemParameters.value'], dataIndex: 'value', render: (v) => String(v) },
      { title: t['systemParameters.status'], dataIndex: 'status', render: (v) => <Tag color={v === 'ACTIVE' ? 'green' : 'gray'}>{v}</Tag> }, { title: t['systemParameters.version'], dataIndex: 'version' },
      { title: t['systemParameters.actions'], render: (_, row) => <Space><Button size="small" onClick={() => open(row)}>{t['systemParameters.edit']}</Button><Button size="small" onClick={() => setParameterStatus(row.key, row.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE').then(load).catch(setError)}>{row.status === 'ACTIVE' ? t['systemParameters.disable'] : t['systemParameters.enable']}</Button></Space> },
    ]} />
    <Drawer title={editor ? t['systemParameters.edit'] : t['systemParameters.create']} visible={editor !== false} onCancel={() => setEditor(false)} onOk={save} okButtonProps={{ loading: saving }} width={520}>
      <Form layout="vertical"><Item label={t['systemParameters.key']}><Input value={form.key} disabled={Boolean(editor)} onChange={(v) => setForm({ ...form, key: v })} /></Item>
        <Item label={t['systemParameters.type']}><Select value={form.type} disabled={Boolean(editor)} onChange={(v) => setForm({ ...form, type: v as Parameter['type'] })}><Select.Option value="STRING">STRING</Select.Option><Select.Option value="INTEGER">INTEGER</Select.Option><Select.Option value="DECIMAL">DECIMAL</Select.Option><Select.Option value="BOOLEAN">BOOLEAN</Select.Option></Select></Item>
        <Item label={t['systemParameters.value']}><Input value={String(form.value)} onChange={(v) => setForm({ ...form, value: v })} /></Item>
        <Space><Item label={t['systemParameters.required']}><Switch checked={form.required} onChange={(v) => setForm({ ...form, required: v })} /></Item><Item label={t['systemParameters.public']}><Switch checked={form.public} onChange={(v) => setForm({ ...form, public: v })} /></Item></Space>
        <Space><Item label={t['systemParameters.min']}><InputNumber value={form.min} onChange={(v) => setForm({ ...form, min: v })} /></Item><Item label={t['systemParameters.max']}><InputNumber value={form.max} onChange={(v) => setForm({ ...form, max: v })} /></Item></Space>
        <Item label={t['systemParameters.description']}><Input.TextArea value={form.description} onChange={(v) => setForm({ ...form, description: v })} /></Item>
      </Form>
    </Drawer>
  </Card>;
}
