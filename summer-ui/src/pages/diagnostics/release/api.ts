import axios from 'axios';
import { businessData } from '@/api/client';
import { SummerApiError, responseRequestId } from '@/api/errors';

export const checkNames = [
  'database', 'migrations', 'configuration', 'keys', 'authorization',
  'tasks', 'cache', 'events', 'realtime', 'plugins',
] as const;
export type CheckName = typeof checkNames[number];
export type ReleaseReadiness = {
  status: 'READY' | 'NOT_READY';
  checks: Array<{ name: CheckName; passed: boolean; detail: string }>;
  requestId: string;
  timestamp: string;
};

export class ReleaseReadinessError extends Error {
  constructor(public status: number, public code: string, public requestId: string) {
    super('Upgrade preflight could not be loaded');
    this.name = 'ReleaseReadinessError';
  }
}

export async function getReleaseReadiness(): Promise<ReleaseReadiness> {
  try {
    let body;
    let status = 0;
    let headerRequestId = '';
    const data = await businessData<ReleaseReadiness>(async () => {
      const response = await axios.get('/internal/v1/release/readiness');
      body = response.data;
      status = response.status;
      headerRequestId = responseRequestId(response.headers);
      return response;
    });
    const checks = data?.checks;
    const requestId = typeof body?.requestId === 'string' ? body.requestId : headerRequestId;
    if (!Array.isArray(checks) || checks.length !== checkNames.length ||
      !checkNames.every((name) => checks.filter((check) => check?.name === name).length === 1) ||
      !checks.every((check) => typeof check.passed === 'boolean' && typeof check.detail === 'string') ||
      !['READY', 'NOT_READY'].includes(data.status) ||
      (data.status === 'READY') !== checks.every((check) => check.passed)) {
      throw new ReleaseReadinessError(status, '', requestId);
    }
    return { status: data.status, checks, requestId, timestamp: typeof body.timestamp === 'string' ? body.timestamp : '' };
  } catch (error) {
    if (error instanceof ReleaseReadinessError) throw error;
    if (!(error instanceof SummerApiError)) throw new ReleaseReadinessError(0, '', '');
    const failure = error;
    throw new ReleaseReadinessError(failure.status, failure.code, failure.requestId);
  }
}
