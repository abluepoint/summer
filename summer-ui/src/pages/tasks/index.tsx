import React, { useEffect, useState } from 'react';
import { Alert, Button, Card, Form, Input, InputNumber, Message, Select, Space, Table, Tag, Typography } from '@arco-design/web-react';
import useLocale from '@/utils/useLocale';
import { createTask, listExecutions, listTasks, listTrustedTaskTypes, setTaskStatus, subscribeTaskProgress, Task, TaskApiError, TaskExecution, triggerTask, TrustedTaskType } from './api';

const enterpriseId = 'ent-acme';
const FormItem = Form.Item;
export default function TasksPage() {
  const t = useLocale();
  const [tasks, setTasks] = useState<Task[]>([]);
  const [types, setTypes] = useState<TrustedTaskType[]>([]);
  const [executions, setExecutions] = useState<TaskExecution[]>([]);
  const [selected, setSelected] = useState<Task | null>(null);
  const [error, setError] = useState<TaskApiError | null>(null);
  const [form] = Form.useForm();
  const load = () => Promise.all([listTasks(enterpriseId), listTrustedTaskTypes(enterpriseId)]).then(([taskPage, trusted]) => { setTasks(taskPage.items); setTypes(trusted); setError(null); }).catch(setError);
  useEffect(() => { load(); }, []);
  const submit = () => form.validate().then((values) => {
    let parameters: Record<string, unknown> = {};
    if (values.parameters) {
      try {
        const parsed = JSON.parse(values.parameters);
        if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') throw new Error('object');
        parameters = parsed;
      } catch {
        Message.error(t['tasks.parametersInvalid']);
        return;
      }
    }
    return createTask(enterpriseId, { ...values, parameters, maxRetries: values.maxRetries || 0, timeoutMs: values.timeoutMs || 30000 }).then((task) => { setTasks((items) => [task, ...items]); form.resetFields(); Message.success(t['tasks.created']); }).catch(setError);
  });
  const trigger = (task: Task) => triggerTask(enterpriseId, task.taskId).then(() => { Message.success(t['tasks.triggered']); return listExecutions(enterpriseId, task.taskId); }).then((page) => { setSelected(task); setExecutions(page.items); }).catch(setError);
  const toggle = (task: Task) => setTaskStatus(enterpriseId, task, task.status === 'ACTIVE' ? 'PAUSED' : 'ACTIVE').then((updated) => setTasks((items) => items.map((item) => item.taskId === updated.taskId ? updated : item))).catch(setError);
  const inspect = (task: Task) => listExecutions(enterpriseId, task.taskId).then((page) => { setSelected(task); setExecutions(page.items); }).catch(setError);
  useEffect(() => {
    if (!selected) return;
    return subscribeTaskProgress(enterpriseId, selected.taskId, (event) => {
      if (event.type === 'resync' || !event.executionId) {
        listExecutions(enterpriseId, selected.taskId).then((page) => setExecutions(page.items)).catch(setError);
        return;
      }
      setExecutions((items) => items.map((item) => item.executionId === event.executionId ? {
        ...item,
        status: (event.status || item.status) as TaskExecution['status'],
        attempt: event.attempt || item.attempt,
        progress: event.progress ?? item.progress,
        failureCode: event.failureCode || item.failureCode,
        finishedAt: event.status === 'SUCCESS' || event.status === 'FAILED' ? new Date().toISOString() : item.finishedAt,
      } : item));
    }, () => setError({ status: 0, code: 'SUMMER_TASK_SSE_DISCONNECTED', message: t['tasks.streamDisconnected'], requestId: '' }));
  }, [selected, t]);
  return <Card title={t['tasks.title']} extra={<Button onClick={load}>{t['tasks.refresh']}</Button>}>
    {error && <Alert type="error" content={`${error.message || t['tasks.error']} ${error.requestId ? `(${error.requestId})` : ''}`} style={{ marginBottom: 16 }} />}
    <Form form={form} layout="inline" initialValues={{ maxRetries: 0, timeoutMs: 30000 }}>
      <FormItem field="name" rules={[{ required: true }]}><Input placeholder={t['tasks.name']} /></FormItem>
      <FormItem field="taskType" rules={[{ required: true }]}><Select placeholder={t['tasks.type']} options={types.map((item) => ({ label: item.taskType, value: item.taskType }))} /></FormItem>
      <FormItem field="cronExpression" rules={[{ required: true }]}><Input placeholder={t['tasks.cron']} /></FormItem>
      <FormItem field="parameters"><Input placeholder={t['tasks.parameters']} /></FormItem>
      <FormItem field="maxRetries"><InputNumber min={0} max={10} /></FormItem>
      <FormItem field="timeoutMs"><InputNumber min={1000} max={300000} /></FormItem>
      <Button type="primary" onClick={submit}>{t['tasks.create']}</Button>
    </Form>
    <Table<Task> rowKey="taskId" style={{ marginTop: 20 }} data={tasks} columns={[
      { title: t['tasks.name'], dataIndex: 'name' }, { title: t['tasks.type'], dataIndex: 'taskType' }, { title: t['tasks.cron'], dataIndex: 'cronExpression' },
      { title: t['tasks.status'], render: (_, task) => <Tag color={task.status === 'ACTIVE' ? 'green' : 'gray'}>{task.status}</Tag> },
      { title: t['tasks.actions'], render: (_, task) => <Space><Button size="small" onClick={() => toggle(task)}>{task.status === 'ACTIVE' ? t['tasks.pause'] : t['tasks.resume']}</Button><Button size="small" onClick={() => trigger(task)}>{t['tasks.trigger']}</Button><Button size="small" onClick={() => inspect(task)}>{t['tasks.history']}</Button></Space> },
    ]} />
    {selected && <><Typography.Title heading={6} style={{ marginTop: 24 }}>{t['tasks.history']} · {selected.name}</Typography.Title><Table<TaskExecution> rowKey="executionId" data={executions} pagination={{ pageSize: 5 }} columns={[{ title: t['tasks.executionStatus'], dataIndex: 'status' }, { title: t['tasks.progress'], render: (_, item) => item.progress == null ? '' : `${item.progress}%` }, { title: t['tasks.attempts'], dataIndex: 'attempt' }, { title: t['tasks.startedAt'], dataIndex: 'startedAt' }, { title: t['tasks.finishedAt'], dataIndex: 'finishedAt' }, { title: t['tasks.failure'], dataIndex: 'failureCode' }, { title: t['tasks.recovered'], render: (_, item) => item.recovered ? t['tasks.recoveredYes'] : '' }]} /></>}
  </Card>;
}
