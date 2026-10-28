import type { ApiFailure } from './types';

export class SummerApiError extends Error implements ApiFailure {
  status: number;
  code: string;
  violations: unknown[];
  requestId: string;
  timestamp: string;

  constructor(failure: ApiFailure) {
    super(failure.message || `HTTP ${failure.status || 'network'}`);
    this.name = 'SummerApiError';
    Object.assign(this, failure);
    this.message = failure.message || `HTTP ${failure.status || 'network'}`;
  }
}

export function responseRequestId(headers: unknown): string {
  if (!headers || typeof headers !== 'object') return '';
  const values = headers as {
    [key: string]: unknown;
    get?: (name: string) => unknown;
  };
  return String(values['x-request-id'] || values.get?.('x-request-id') || '');
}

export function readFailure(
  body: unknown,
  status: number,
  headerRequestId = ''
): ApiFailure {
  const envelope = body as Record<string, unknown> | null | undefined;
  const error =
    envelope?.error && typeof envelope.error === 'object'
      ? (envelope.error as Record<string, unknown>)
      : {};
  return {
    status,
    code: typeof error.code === 'string' ? error.code : '',
    message: typeof error.message === 'string' ? error.message : '',
    violations: Array.isArray(error.violations) ? error.violations : [],
    requestId:
      typeof envelope?.requestId === 'string'
        ? envelope.requestId
        : headerRequestId,
    timestamp:
      typeof envelope?.timestamp === 'string' ? envelope.timestamp : '',
  };
}
