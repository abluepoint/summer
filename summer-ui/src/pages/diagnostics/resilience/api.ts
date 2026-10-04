import axios from 'axios';
import { readEnvelope, readFailure } from '../../customer/contract.mjs';

export type ResilienceOperation = {
  name: string;
  circuitBreaker: string;
  retryMaxAttempts: number;
  rateLimitForPeriod: number;
  bulkheadMaxConcurrent: number;
  calls: number;
  successes: number;
  failures: number;
  fallbacks: number;
  rejected: number;
  timedOut: number;
  lastFailureAt?: string;
};

export type ResilienceDiagnostics = { requestId: string; checkedAt: string; operations: ResilienceOperation[] };

export class ResilienceDiagnosticsError extends Error {
  status: number; code: string; requestId: string;
  constructor(status: number, code: string, message: string, requestId: string) {
    super(message || `HTTP ${status || 'network'}`); this.name = 'ResilienceDiagnosticsError';
    this.status = status; this.code = code; this.requestId = requestId;
  }
}

export async function getResilienceDiagnostics(): Promise<ResilienceDiagnostics> {
  try {
    const response = await axios.get('/internal/v1/diagnostics/resilience');
    return readEnvelope(response.data, response.status) as ResilienceDiagnostics;
  } catch (error) {
    if (!axios.isAxiosError(error)) throw error;
    const failure = readFailure(error.response?.data, error.response?.status || 0,
      String(error.response?.headers?.['x-request-id'] || ''));
    throw new ResilienceDiagnosticsError(failure.status, failure.code, failure.message, failure.requestId);
  }
}
