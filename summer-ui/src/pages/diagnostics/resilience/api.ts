import axios from 'axios';
import { businessData } from '@/api/client';
import { SummerApiError } from '@/api/errors';

export type ResilienceOperation = {
  name: string;
  circuitBreaker: string;
  retryMaxAttempts: number;
  rateLimitForPeriod: number;
  bulkheadMaxConcurrent: number;
  calls: number;
  retryAttempts: number;
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
    return await businessData<ResilienceDiagnostics>(() => axios.get('/internal/v1/diagnostics/resilience'));
  } catch (error) {
    if (!(error instanceof SummerApiError)) throw error;
    const failure = error;
    throw new ResilienceDiagnosticsError(failure.status, failure.code, failure.message, failure.requestId);
  }
}
