import axios from 'axios';
import { readEnvelope, readFailure } from '../../customer/contract.mjs';

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
    const response = await axios.get('/internal/v1/release/readiness');
    const body = response.data;
    const data = readEnvelope(body, response.status);
    const checks = data?.checks;
    const requestId = typeof body?.requestId === 'string' ? body.requestId : '';
    if (!Array.isArray(checks) || checks.length !== checkNames.length ||
      !checkNames.every((name) => checks.filter((check) => check?.name === name).length === 1) ||
      !checks.every((check) => typeof check.passed === 'boolean' && typeof check.detail === 'string') ||
      !['READY', 'NOT_READY'].includes(data.status) ||
      (data.status === 'READY') !== checks.every((check) => check.passed)) {
      throw new ReleaseReadinessError(response.status, '', requestId);
    }
    return { status: data.status, checks, requestId, timestamp: typeof body.timestamp === 'string' ? body.timestamp : '' };
  } catch (error) {
    if (error instanceof ReleaseReadinessError) throw error;
    if (!axios.isAxiosError(error)) throw new ReleaseReadinessError(0, '', '');
    const failure = readFailure(error.response?.data, error.response?.status || 0,
      String(error.response?.headers?.['x-request-id'] || ''));
    throw new ReleaseReadinessError(failure.status, failure.code, failure.requestId);
  }
}
