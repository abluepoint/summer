import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Button, Card, Drawer, Form, Input, InputNumber, Modal, Select, Space, Switch, Table, Tag } from '@arco-design/web-react';
import { useHistory } from 'react-router-dom';
import { isAuthRedirecting } from '@/auth/oidc';
import useLocale from '@/utils/useLocale';
import { Parameter, ParameterApiError, ParameterEvent, ParameterListQuery, ParameterPage, ParameterVersion, ParameterVersionPage, parameterApiForEnterprise } from './api';
import useActiveEnterprise from '@/hooks/useActiveEnterprise';

const Item = Form.Item;
const empty = { key: '', type: 'STRING' as Parameter['type'], value: '', required: false, min: undefined as number | undefined, max: undefined as number | undefined, pattern: '', public: false, description: '' };

export default function SystemParametersPage() {
  const tenant = useActiveEnterprise();
  return <><Select aria-label="活动租户" placeholder="选择活动租户" value={tenant.enterpriseId || undefined} options={tenant.options} onChange={tenant.change} disabled={tenant.busy} style={{ width: 240, marginBottom: 16 }} />
    {tenant.enterpriseId ? <SystemParametersContent key={tenant.enterpriseId} enterpriseId={tenant.enterpriseId} onDirtyChange={tenant.setDirty} onBusyChange={tenant.setBusy} /> : tenant.ready && <Alert type="info" content="请选择一个活动成员租户；没有可用租户时不会读取配置。" />}</>;
}
function SystemParametersContent({ enterpriseId, onDirtyChange, onBusyChange }: { enterpriseId: string; onDirtyChange: (value: boolean) => void; onBusyChange: (value: boolean) => void }) {
  const { createParameter, deactivateParameterVersion, listParameterEvents, listParameterVersions, listParameters, publishParameterVersion, retryParameterEvent, rollbackParameterVersion, saveParameterDraft, setParameterStatus, updateParameter, saveParameterOverride } = useMemo(() => parameterApiForEnterprise(enterpriseId), [enterpriseId]);
  const t = useLocale();
  const router = useHistory();
  const editorBaseline = useRef('');
  const alive = useRef(true);
  const writing = useRef(false);
  const historySelection = useRef<string | null>(null);
  useEffect(() => () => { alive.current = false; historySelection.current = null; }, []);
  const listRequest = useRef(0);
  const eventRequest = useRef(0);
  const historyRequest = useRef(0);
  const [page, setPage] = useState<ParameterPage | null>(null); const [events, setEvents] = useState<ParameterEvent[]>([]); const [loading, setLoading] = useState(false); const [error, setError] = useState<ParameterApiError | null>(null);
  const [editor, setEditor] = useState<Parameter | null | false>(false); const [form, setForm] = useState(empty); const [saving, setSaving] = useState(false);
  const [overrideOrg, setOverrideOrg] = useState('_tenant'); const [overrideVersion, setOverrideVersion] = useState<number | undefined>();
  const [history, setHistory] = useState<ParameterVersionPage | null>(null); const [historyKey, setHistoryKey] = useState<string | null>(null);
  const [query, setQuery] = useState<ParameterListQuery>({ page: 0, size: 20, sort: 'key', direction: 'asc', projection: 'detail' });
  const editorState = JSON.stringify({ form, overrideOrg, overrideVersion });
  const dirty = editor !== false && editorState !== editorBaseline.current;
  useEffect(() => onDirtyChange(dirty), [dirty, onDirtyChange]);
  useEffect(() => onBusyChange(saving), [saving, onBusyChange]);
  useEffect(() => {
    if (!dirty && !saving) return undefined;
    const unblock = router.block(() => isAuthRedirecting() ? undefined : saving ? false : '配置有未保存修改，确认离开？');
    const beforeUnload = (event: BeforeUnloadEvent) => { if (!isAuthRedirecting()) { event.preventDefault(); event.returnValue = ''; } };
    window.addEventListener('beforeunload', beforeUnload);
    return () => { unblock(); window.removeEventListener('beforeunload', beforeUnload); };
  }, [dirty, saving, router]);
  const load = useCallback(() => { const request = ++listRequest.current; setLoading(true); setError(null); return listParameters(query).then((result) => { if (request === listRequest.current) setPage(result); }).catch((e) => { if (request !== listRequest.current) return; setPage(null); historyRequest.current++; historySelection.current = null; setHistory(null); setHistoryKey(null); setEditor(false); setForm(empty); setError(e); }).finally(() => { if (request === listRequest.current) setLoading(false); }); }, [query]);
  const loadEvents = useCallback(() => { const request = ++eventRequest.current; setEvents([]); return listParameterEvents().then((result) => { if (request === eventRequest.current) setEvents(result.items); }).catch((e) => { if (request === eventRequest.current) { setEvents([]); setError(e); } }); }, []);
  const loadHistory = useCallback((key: string) => { const request = ++historyRequest.current; historySelection.current = key; setHistory(null); setHistoryKey(key); return listParameterVersions(key).then((result) => { if (request === historyRequest.current) setHistory(result); }).catch((e) => { if (request === historyRequest.current) { historySelection.current = null; setHistory(null); setHistoryKey(null); setError(e); } }); }, []);
  useEffect(() => { load(); loadEvents(); return () => { listRequest.current++; eventRequest.current++; historyRequest.current++; }; }, [load, loadEvents]);
  function open(parameter?: Parameter) { const nextForm = parameter ? { ...empty, ...parameter, value: typeof parameter.value === 'object' ? JSON.stringify(parameter.value) : String(parameter.value ?? '') } : empty; editorBaseline.current = JSON.stringify({ form: nextForm, overrideOrg: '_tenant', overrideVersion: undefined }); setError(null); setOverrideOrg('_tenant'); setOverrideVersion(undefined); setEditor(parameter || null); setForm(nextForm); }
  function closeEditor() { if (saving) return; if (!dirty) { setEditor(false); return; } Modal.confirm({ title: '放弃未保存的配置？', okText: '放弃修改', cancelText: '继续编辑', onOk: () => setEditor(false) }); }
  function valueForType() {
    if (form.type === 'BOOLEAN') {
      if (form.value !== 'true' && form.value !== 'false') throw new Error('布尔值必须是 true 或 false');
      return form.value === 'true';
    }
    if (form.type === 'INTEGER' || form.type === 'DECIMAL') {
      const value = Number(form.value);
      if (!String(form.value).trim() || !Number.isFinite(value) || (form.type === 'INTEGER' && !Number.isSafeInteger(value))) throw new Error(form.type === 'INTEGER' ? '请输入有效的安全整数' : '请输入有效数字');
      return value;
    }
    return form.value;
  }
  function localError(failure: Error) { setError(new ParameterApiError(0, 'INVALID_INPUT', failure.message, '')); }
  function mutableFields() { return { value: valueForType(), required: form.required, min: form.min, max: form.max, pattern: form.pattern || undefined, publicValue: form.public, description: form.description }; }
  function write<T>(call: () => Promise<T>, success: (result: T) => void | Promise<unknown>) {
    if (writing.current) return;
    writing.current = true; setSaving(true); setError(null);
    call().then(async (result) => { if (alive.current) await success(result); }).catch((failure) => { if (alive.current) setError(failure); }).finally(() => { writing.current = false; if (alive.current) setSaving(false); });
  }
  function changeStatus(parameter: Parameter) {
    write(() => parameter.status === 'ACTIVE' ? deactivateParameterVersion(parameter.key, { expectedVersion: parameter.version, idempotencyKey: `ui-disable-${parameter.key}-${Date.now()}` }) : setParameterStatus(parameter.key, 'ACTIVE'), () => Promise.all([
      load(), loadEvents(), historySelection.current === parameter.key ? loadHistory(parameter.key) : Promise.resolve(),
    ]));
  }
  function save() { try { const payload = mutableFields(); write(() => editor ? updateParameter(editor.key, { ...payload, version: editor.version }) : createParameter({ ...payload, key: form.key, type: form.type }), () => { setEditor(false); load(); loadEvents(); }); } catch (failure) { localError(failure); } }
  function saveDraft() { if (!editor) return; const key = editor.key; try { const payload = mutableFields(); write(() => saveParameterDraft(key, { ...payload, baseVersion: editor.version, idempotencyKey: `ui-draft-${Date.now()}` }), () => { editorBaseline.current = editorState; loadHistory(key); loadEvents(); }); } catch (failure) { localError(failure); } }
  function publish(version: ParameterVersion) { if (!historyKey || !history) return; write(() => publishParameterVersion(historyKey, { revision: version.revision, expectedVersion: history.currentVersion, idempotencyKey: `ui-publish-${version.revision}-${Date.now()}` }), () => { load(); loadHistory(historyKey); loadEvents(); }); }
  function rollback(version: ParameterVersion) { if (!historyKey || !history) return; write(() => rollbackParameterVersion(historyKey, { revision: version.revision, expectedVersion: history.currentVersion, idempotencyKey: `ui-rollback-${version.revision}-${Date.now()}` }), () => { load(); loadHistory(historyKey); loadEvents(); }); }
  const updateQuery = (change: Partial<ParameterListQuery>) => setQuery({ ...query, ...change, page: 0 });
  const errorMessage = error ? `${error.status === 403 ? '无权执行此配置操作' : error.message || t['systemParameters.error']}${error.requestId ? ` (${error.requestId})` : ''}` : '';
  const fieldFeedback = (...names: string[]) => {
    const messages = error?.violations?.filter((violation) => names.includes(violation.field)).map((violation) => violation.message);
    return messages?.length ? { validateStatus: 'error' as const, help: messages.join('；') } : {};
  };
  const otherViolations = error?.violations?.filter((violation) => !['key', 'type', 'value', 'required', 'public', 'publicValue', 'min', 'max', 'description'].includes(violation.field));
  return <Card title={t['systemParameters.title']} extra={<Button disabled={saving} type="primary" onClick={() => open()}>{t['systemParameters.create']}</Button>}>
    <Alert type="info" content="覆盖优先级：组织 > 租户 > 基础值。选中的高优先级覆盖不可访问时不会回退；各项写操作由服务端分别授权。" style={{ marginBottom: 16 }} />
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
    {error && editor === false && <Alert type="error" content={errorMessage} style={{ marginBottom: 16 }} />}
    {page?.projection === 'summary' && <Alert type="info" content="当前仅展示摘要；切换到 detail 查看完整配置后，才能编辑或变更状态。" style={{ marginBottom: 16 }} />}
    <Table<Parameter> rowKey="key" loading={loading} data={(page?.items || []) as Parameter[]} pagination={{ current: (page?.page || 0) + 1, pageSize: page?.size || query.size, total: page?.totalElements || 0, onChange: (current, size) => setQuery({ ...query, page: current - 1, size }) }} columns={[
      { title: t['systemParameters.key'], dataIndex: 'key' }, { title: t['systemParameters.type'], dataIndex: 'type' }, { title: t['systemParameters.value'], dataIndex: 'value', render: (v) => v === undefined ? '—' : String(v) },
      { title: t['systemParameters.status'], dataIndex: 'status', render: (v) => <Tag color={v === 'ACTIVE' ? 'green' : 'gray'}>{v}</Tag> }, { title: t['systemParameters.version'], dataIndex: 'version', render: (v) => v === undefined ? '—' : v },
      { title: t['systemParameters.actions'], render: (_, row) => <Space><Button size="small" disabled={saving || page?.projection === 'summary'} onClick={() => open(row)}>{t['systemParameters.edit']}</Button><Button disabled={saving} size="small" onClick={() => loadHistory(row.key)}>{t['systemParameters.history']}</Button><Button size="small" disabled={saving || page?.projection === 'summary'} onClick={() => changeStatus(row)}>{row.status === 'ACTIVE' ? t['systemParameters.disable'] : t['systemParameters.enable']}</Button></Space> },
    ]} />
    <Card title={t['systemParameters.deliveryTitle']} style={{ marginTop: 16 }}>
      <Table<ParameterEvent> rowKey="eventId" data={events} pagination={false} columns={[
        { title: t['systemParameters.key'], dataIndex: 'parameterKey' },
        { title: t['systemParameters.deliveryStatus'], dataIndex: 'status', render: (value) => <Tag color={value === 'PUBLISHED' ? 'green' : value === 'DEAD_LETTER' ? 'red' : 'orange'}>{value}</Tag> },
        { title: t['systemParameters.attempts'], dataIndex: 'attempts' },
        { title: t['systemParameters.requestId'], dataIndex: 'requestId' },
        { title: t['systemParameters.actions'], render: (_, row) => row.status === 'DEAD_LETTER' ? <Button disabled={saving} size="small" onClick={() => write(() => retryParameterEvent(row.eventId), loadEvents)}>{t['systemParameters.retry']}</Button> : null },
      ]} />
    </Card>
    {history && <Card title={`${t['systemParameters.history']} · ${historyKey}`} style={{ marginTop: 16 }}><Table<ParameterVersion> rowKey="revision" data={history.items} pagination={false} columns={[
      { title: t['systemParameters.revision'], dataIndex: 'revision' }, { title: t['systemParameters.releaseState'], dataIndex: 'state' }, { title: t['systemParameters.value'], dataIndex: 'value', render: (v) => String(v) }, { title: t['systemParameters.revisionStatus'], dataIndex: 'status' },
      { title: t['systemParameters.actions'], render: (_, row) => <Space>{row.state === 'DRAFT' && <Button disabled={saving} size="small" type="primary" onClick={() => publish(row)}>{t['systemParameters.publish']}</Button>}{row.state !== 'DRAFT' && <Button disabled={saving} size="small" onClick={() => rollback(row)}>{t['systemParameters.rollback']}</Button>}</Space> },
    ]} /></Card>}
    <Drawer title={editor ? t['systemParameters.edit'] : t['systemParameters.create']} visible={editor !== false} onCancel={closeEditor} onOk={save} okButtonProps={{ loading: saving, disabled: saving }} width={520}>
      {error && <Alert type="error" content={<>{errorMessage}{otherViolations?.map((violation, index) => <div key={index}>{violation.field}: {violation.message}</div>)}</>} style={{ marginBottom: 16 }} />}
      <Form layout="vertical"><Item label={t['systemParameters.key']} {...fieldFeedback('key')}><Input value={form.key} disabled={saving || Boolean(editor)} onChange={(v) => setForm({ ...form, key: v })} /></Item>
        <Item label={t['systemParameters.type']} {...fieldFeedback('type')}><Select value={form.type} disabled={saving || Boolean(editor)} onChange={(v) => setForm({ ...form, type: v as Parameter['type'] })}><Select.Option value="STRING">STRING</Select.Option><Select.Option value="INTEGER">INTEGER</Select.Option><Select.Option value="DECIMAL">DECIMAL</Select.Option><Select.Option value="BOOLEAN">BOOLEAN</Select.Option></Select></Item>
        <Item label={t['systemParameters.value']} {...fieldFeedback('value')}><Input disabled={saving} value={String(form.value)} onChange={(v) => setForm({ ...form, value: v })} /></Item>
        <Space><Item label={t['systemParameters.required']} {...fieldFeedback('required')}><Switch disabled={saving} checked={form.required} onChange={(v) => setForm({ ...form, required: v })} /></Item><Item label={t['systemParameters.public']} {...fieldFeedback('public', 'publicValue')}><Switch disabled={saving} checked={form.public} onChange={(v) => setForm({ ...form, public: v })} /></Item></Space>
        <Space><Item label={t['systemParameters.min']} {...fieldFeedback('min')}><InputNumber disabled={saving} value={form.min} onChange={(v) => setForm({ ...form, min: v })} /></Item><Item label={t['systemParameters.max']} {...fieldFeedback('max')}><InputNumber disabled={saving} value={form.max} onChange={(v) => setForm({ ...form, max: v })} /></Item></Space>
        <Item label={t['systemParameters.description']} {...fieldFeedback('description')}><Input.TextArea disabled={saving} value={form.description} onChange={(v) => setForm({ ...form, description: v })} /></Item>
        {editor && <><Button disabled={saving} onClick={saveDraft} loading={saving}>{t['systemParameters.saveDraft']}</Button>
          <Item label="覆盖范围（_tenant 为租户，或组织 ID）"><Input disabled={saving} value={overrideOrg} onChange={(v) => { setOverrideOrg(v); setOverrideVersion(undefined); }} /></Item>
          <Item label="覆盖版本（新建留空，修改填写已有版本）"><InputNumber disabled={saving} min={0} value={overrideVersion} onChange={setOverrideVersion} /></Item>
          <Button disabled={saving} loading={saving} onClick={() => { try { const value = valueForType(); write(() => saveParameterOverride(editor.key, overrideOrg, { value, publicReadable: form.public, expectedVersion: overrideVersion }), (result) => { setOverrideVersion(result.version); load(); }); } catch (failure) { localError(failure); } }}>发布当前表单值为覆盖</Button>
        </>}
      </Form>
    </Drawer>
  </Card>;
}
