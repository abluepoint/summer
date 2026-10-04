import axios from 'axios';
import { readEnvelope, readFailure } from '../customer/contract.mjs';

export type Task = {
  taskId: string; enterpriseId: string; name: string; taskType: string;
  cronExpression: string; parameters: Record<string, unknown>; maxRetries: number;
  timeoutMs: number;
  status: 'ACTIVE' | 'PAUSED'; version: number; createdAt: string; updatedAt: string;
};
export type TaskExecution = {
  executionId: string; taskId: string; enterpriseId: string; fireInstanceId: string;
  status: 'RUNNING' | 'SUCCESS' | 'FAILED'; attempt: number; startedAt: string;
  finishedAt?: string; failureCode?: string;
  idempotencyKey: string; recovered: boolean;
  attempts: { attempt: number; status: string; startedAt: string; finishedAt?: string; failureCode?: string; nodeId?: string }[];
};
export type TrustedTaskType = { taskType: string; description: string };
export type TaskApiError = { status: number; code: string; message: string; requestId: string };

async function request<T>(call: () => Promise<{ data: unknown; status: number }>): Promise<T> {
  try { return readEnvelope((await call()).data, 200) as T; }
  catch (error) {
    if (!axios.isAxiosError(error)) throw error;
    throw readFailure(error.response?.data, error.response?.status || 0, String(error.response?.headers?.['x-request-id'] || '')) as TaskApiError;
  }
}
const base = (enterpriseId: string) => `/api/v1/enterprises/${encodeURIComponent(enterpriseId)}/tasks`;
export const listTasks = (enterpriseId: string) => request<{ items: Task[] }>(() => axios.get(base(enterpriseId)));
export const listTrustedTaskTypes = (enterpriseId: string) => request<TrustedTaskType[]>(() => axios.get(`${base(enterpriseId)}/types`));
export const createTask = (enterpriseId: string, payload: Pick<Task, 'name' | 'taskType' | 'cronExpression' | 'parameters' | 'maxRetries' | 'timeoutMs'>) => request<Task>(() => axios.post(base(enterpriseId), payload));
export const updateTask = (enterpriseId: string, task: Task, payload: Partial<Pick<Task, 'name' | 'cronExpression' | 'parameters' | 'maxRetries' | 'timeoutMs'>>) => request<Task>(() => axios.patch(`${base(enterpriseId)}/${encodeURIComponent(task.taskId)}`, payload, { headers: { 'If-Match': `"${task.version}"` } }));
export const setTaskStatus = (enterpriseId: string, task: Task, status: Task['status']) => request<Task>(() => axios.post(`${base(enterpriseId)}/${encodeURIComponent(task.taskId)}:${status === 'ACTIVE' ? 'resume' : 'pause'}`, null, { headers: { 'If-Match': `"${task.version}"` } }));
export const triggerTask = (enterpriseId: string, taskId: string) => request<{ taskId: string; status: string }>(() => axios.post(`${base(enterpriseId)}/${encodeURIComponent(taskId)}:trigger`, null, { headers: { 'Idempotency-Key': `ui-task-trigger-${taskId}-${Date.now()}` } }));
export const listExecutions = (enterpriseId: string, taskId: string) => request<{ items: TaskExecution[] }>(() => axios.get(`${base(enterpriseId)}/${encodeURIComponent(taskId)}/executions`));
