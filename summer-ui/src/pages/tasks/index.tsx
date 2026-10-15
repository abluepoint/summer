import React, { useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Form, Input, InputNumber, Message, Modal, Select, Space, Table, Tag, Typography } from '@arco-design/web-react';
import useLocale from '@/utils/useLocale';
import useActiveEnterprise from '@/pages/data-scopes/useActiveEnterprise';
import { Prompt } from 'react-router-dom';
import { createTask, listExecutions, listTasks, listTrustedTaskTypes, newTaskTriggerKey, setTaskStatus, subscribeTaskProgress, Task, TaskApiError, TaskExecution, triggerTask, updateTask, TrustedTaskType } from './api';

const FormItem = Form.Item;
export default function TasksPage() {
  const tenant = useActiveEnterprise();
  const t = useLocale();
  return <>
    <Select aria-label={t['tasks.enterprise']} placeholder={t['tasks.enterprise']} value={tenant.enterpriseId || undefined}
      options={tenant.options} disabled={tenant.busy} onChange={tenant.change} style={{ width: 280, marginBottom: 16 }} />
    {tenant.enterpriseId && <TasksBody key={tenant.enterpriseId} enterpriseId={tenant.enterpriseId}
      onDirtyChange={tenant.setDirty} onBusyChange={tenant.setBusy} />}
  </>;
}
function TasksBody({ enterpriseId, onDirtyChange, onBusyChange }: {
  enterpriseId: string; onDirtyChange: (dirty: boolean) => void; onBusyChange: (busy: boolean) => void;
}) {
  const t = useLocale();
  const [tasks, setTasks] = useState<Task[]>([]);
  const [types, setTypes] = useState<TrustedTaskType[]>([]);
  const [executions, setExecutions] = useState<TaskExecution[]>([]);
  const [selected, setSelected] = useState<Task | null>(null);
  const [error, setError] = useState<TaskApiError | null>(null);
  const [editing, setEditing] = useState<Task | null>(null);
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  const [busyTask, setBusyTask] = useState<string | null>(null);
  const busyTaskRef = useRef<string | null>(null);
  const triggerKeys = useRef(new Map<string, string>());
  const dirtyRef = useRef(false);
  const [form] = Form.useForm();
  const load = () => Promise.all([listTasks(enterpriseId), listTrustedTaskTypes(enterpriseId)]).then(([taskPage, trusted]) => { setTasks(taskPage.items); setTypes(trusted); setError(null); }).catch(setError);
  useEffect(() => { load(); }, []);
  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => {
      if (dirtyRef.current) { event.preventDefault(); event.returnValue = ''; }
    };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, []);
  const changeEditor = (task: Task | null) => {
    if (savingRef.current || busyTaskRef.current) return;
    const change = () => {
      form.resetFields();
      if (task) form.setFieldsValue({ ...task, parameters: JSON.stringify(task.parameters, null, 2) });
      setEditing(task);
      dirtyRef.current = false;
      onDirtyChange(false);
      setError(null);
    };
    if (dirtyRef.current) Modal.confirm({ title: t['tasks.discard'], content: t['tasks.discardContent'], onOk: change });
    else change();
  };
  const submit = async () => {
    if (savingRef.current || busyTaskRef.current) return;
    savingRef.current = true;
    setSaving(true);
    onBusyChange(true);
    try {
      const values = await form.validate();
      let parameters: Record<string, unknown> = {};
      if (values.parameters) {
        try {
          const parsed = JSON.parse(values.parameters);
          if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') throw new Error('object');
          parameters = parsed;
        } catch { Message.error(t['tasks.parametersInvalid']); return; }
      }
      const payload = { ...values, parameters, maxRetries: values.maxRetries ?? 0, timeoutMs: values.timeoutMs ?? 30000 };
      const task = editing ? await updateTask(enterpriseId, editing, payload) : await createTask(enterpriseId, payload);
      setTasks((items) => editing ? items.map((item) => item.taskId === task.taskId ? task : item) : [task, ...items]);
      form.resetFields();
      dirtyRef.current = false;
      onDirtyChange(false);
      setEditing(null);
      setError(null);
      Message.success(t[editing ? 'tasks.saved' : 'tasks.created']);
    } catch (failure) {
      // Arco validation failures already appear next to their fields. Server conflicts preserve the draft.
      if (failure && typeof failure === 'object' && 'code' in failure) setError(failure as TaskApiError);
    } finally { savingRef.current = false; setSaving(false); onBusyChange(false); }
  };
  const mutate = async (task: Task, action: () => Promise<void>) => {
    if (busyTaskRef.current || savingRef.current) return;
    busyTaskRef.current = task.taskId;
    setBusyTask(task.taskId);
    onBusyChange(true);
    try { await action(); setError(null); } catch (failure) { setError(failure as TaskApiError); }
    finally { busyTaskRef.current = null; setBusyTask(null); onBusyChange(false); }
  };
  const trigger = (task: Task) => mutate(task, async () => {
    setSelected(task);
    const key = triggerKeys.current.get(task.taskId) || newTaskTriggerKey();
    triggerKeys.current.set(task.taskId, key);
    await triggerTask(enterpriseId, task.taskId, key);
    triggerKeys.current.delete(task.taskId);
    Message.success(t['tasks.triggered']);
  });
  const toggle = (task: Task) => mutate(task, async () => {
    const updated = await setTaskStatus(enterpriseId, task, task.status === 'ACTIVE' ? 'PAUSED' : 'ACTIVE');
    setTasks((items) => items.map((item) => item.taskId === updated.taskId ? updated : item));
  });
  const inspect = (task: Task) => setSelected(task);
  useEffect(() => {
    if (!selected) return;
    let active = true;
    let loading = false;
    let reload = false;
    const progress = new Map<string, Partial<TaskExecution>>();
    setExecutions([]);
    const refresh = async () => {
      if (loading) { reload = true; return; }
      loading = true;
      try {
        const page = await listExecutions(enterpriseId, selected.taskId);
        if (active) setExecutions(page.items.map((item) => ({ ...item, ...progress.get(item.executionId) })));
      } catch (failure) { if (active) setError(failure as TaskApiError); }
      finally {
        loading = false;
        if (active && reload) { reload = false; refresh(); }
      }
    };
    refresh();
    const close = subscribeTaskProgress(enterpriseId, selected.taskId, (event) => {
      if (!active) return;
      if (event.executionId) {
        const patch: Partial<TaskExecution> = { ...progress.get(event.executionId) };
        if (event.status) patch.status = event.status as TaskExecution['status'];
        if (event.attempt != null) patch.attempt = event.attempt;
        if (event.progress != null) patch.progress = event.progress;
        if (event.failureCode) patch.failureCode = event.failureCode;
        progress.set(event.executionId, patch);
        setExecutions((items) => items.map((item) => item.executionId === event.executionId ? { ...item, ...patch } : item));
      }
      if (event.type === 'resync' || event.type === 'execution.started'
        || event.status === 'SUCCESS' || event.status === 'FAILED') refresh();
    }, () => { if (active) setError({ status: 0, code: 'SUMMER_TASK_SSE_DISCONNECTED', message: t['tasks.streamDisconnected'], requestId: '' }); });
    return () => { active = false; close(); };
  }, [selected, t]);
  return <Card title={t['tasks.title']} extra={<Button onClick={load}>{t['tasks.refresh']}</Button>}>
    <Prompt when={dirtyRef.current} message={t['tasks.discardContent']} />
    {error && <Alert type="error" content={`${error.message || t['tasks.error']} ${error.requestId ? `(${error.requestId})` : ''}`} style={{ marginBottom: 16 }} />}
    <Form form={form} layout="inline" onValuesChange={() => { dirtyRef.current = true; onDirtyChange(true); }} initialValues={{ maxRetries: 0, timeoutMs: 30000 }}>
      <FormItem field="name" rules={[{ required: true }]}><Input placeholder={t['tasks.name']} /></FormItem>
      <FormItem field="taskType" rules={[{ required: true }]}><Select disabled={!!editing || saving} placeholder={t['tasks.type']} options={types.map((item) => ({ label: item.taskType, value: item.taskType }))} /></FormItem>
      <FormItem field="cronExpression" rules={[{ required: true }]}><Input placeholder={t['tasks.cron']} /></FormItem>
      <FormItem field="parameters"><Input placeholder={t['tasks.parameters']} /></FormItem>
      <FormItem field="maxRetries" label={t['tasks.maxRetries']}><InputNumber min={0} max={10} /></FormItem>
      <FormItem field="timeoutMs" label={t['tasks.timeoutMs']}><InputNumber min={1000} max={300000} /></FormItem>
      <Button type="primary" disabled={!!busyTask} loading={saving} onClick={submit}>{t[editing ? 'tasks.save' : 'tasks.create']}</Button>
      {editing && <Button disabled={saving} onClick={() => changeEditor(null)}>{t['tasks.cancel']}</Button>}
    </Form>
    <Table<Task> rowKey="taskId" style={{ marginTop: 20 }} data={tasks} columns={[
      { title: t['tasks.name'], dataIndex: 'name' }, { title: t['tasks.type'], dataIndex: 'taskType' }, { title: t['tasks.cron'], dataIndex: 'cronExpression' },
      { title: t['tasks.status'], render: (_, task) => <Tag color={task.status === 'ACTIVE' ? 'green' : 'gray'}>{task.status}</Tag> },
      { title: t['tasks.actions'], render: (_, task) => <Space><Button size="small" disabled={saving} onClick={() => changeEditor(task)}>{t['tasks.edit']}</Button><Button size="small" loading={busyTask === task.taskId} disabled={!!busyTask} onClick={() => toggle(task)}>{task.status === 'ACTIVE' ? t['tasks.pause'] : t['tasks.resume']}</Button><Button size="small" disabled={!!busyTask || task.status === 'PAUSED'} onClick={() => trigger(task)}>{t['tasks.trigger']}</Button><Button size="small" onClick={() => inspect(task)}>{t['tasks.history']}</Button></Space> },
    ]} />
    {selected && <><Typography.Title heading={6} style={{ marginTop: 24 }}>{t['tasks.history']} · {selected.name}</Typography.Title><Table<TaskExecution> rowKey="executionId" data={executions} pagination={{ pageSize: 5 }} columns={[{ title: t['tasks.executionStatus'], dataIndex: 'status' }, { title: t['tasks.progress'], render: (_, item) => item.progress == null ? '' : `${item.progress}%` }, { title: t['tasks.attempts'], dataIndex: 'attempt' }, { title: t['tasks.startedAt'], dataIndex: 'startedAt' }, { title: t['tasks.finishedAt'], dataIndex: 'finishedAt' }, { title: t['tasks.failure'], dataIndex: 'failureCode' }, { title: t['tasks.recovered'], render: (_, item) => item.recovered ? t['tasks.recoveredYes'] : '' }]} /></>}
  </Card>;
}
