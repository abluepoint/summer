import axios from 'axios';
import { SummerApiError } from '../customer/api';
import { readEnvelope, readFailure } from '../customer/contract.mjs';

export type SecurityKey = {
  kid: string;
  algorithm: string;
  source: string;
  status: string;
  version: number;
  health: string;
  graceUntil?: string;
};

export type SecurityKeyRotation = {
  activeKid: string;
  version: number;
  retentionDays: number;
};

function isSecurityKey(value: unknown): value is SecurityKey {
  if (!value || typeof value !== 'object') return false;
  const key = value as Record<string, unknown>;
  return (
    typeof key.kid === 'string' &&
    typeof key.algorithm === 'string' &&
    typeof key.source === 'string' &&
    typeof key.status === 'string' &&
    typeof key.version === 'number' &&
    typeof key.health === 'string'
  );
}

function invalidResponse(status: number, requestId: string): never {
  throw new SummerApiError({
    status,
    code: 'SUMMER_INVALID_RESPONSE',
    message: 'Signing key response was invalid.',
    violations: [],
    requestId,
    timestamp: '',
  });
}

function responseRequestId(headers: unknown): string {
  if (!headers || typeof headers !== 'object') return '';
  const value = headers as {
    [key: string]: unknown;
    get?: (name: string) => unknown;
  };
  return String(value['x-request-id'] || value.get?.('x-request-id') || '');
}

export async function listSecurityKeys(): Promise<SecurityKey[]> {
  let requestId = '';
  try {
    const response = await axios.get('/api/security/keys');
    requestId = responseRequestId(response.headers);
    const data = readEnvelope(response.data, response.status);
    if (
      !data ||
      typeof data !== 'object' ||
      !Array.isArray(data.items) ||
      !data.items.every(isSecurityKey)
    ) {
      invalidResponse(response.status, requestId);
    }
    return data.items;
  } catch (error) {
    if (error instanceof SummerApiError) throw error;
    if (axios.isAxiosError(error)) {
      const status = error.response?.status || 0;
      const failure = readFailure(
        error.response?.data,
        status,
        responseRequestId(error.response?.headers)
      );
      throw new SummerApiError({
        ...failure,
        message:
          failure.message || (status ? `HTTP ${status}` : 'HTTP network'),
      });
    }
    if (error && typeof error === 'object' && 'status' in error) {
      const failure = error as ReturnType<typeof readFailure>;
      throw new SummerApiError({
        ...failure,
        requestId: failure.requestId || requestId,
      });
    }
    throw error;
  }
}

export async function rotateSecurityKey(
  retentionDays: number,
  expectedVersion: number
): Promise<SecurityKeyRotation> {
  let requestId = '';
  try {
    const response = await axios.post(
      '/api/security/keys:rotate',
      { retentionDays },
      {
        headers: {
          'If-Match': `"${expectedVersion}"`,
          'Idempotency-Key': `key-rotation-${Date.now()}`,
        },
      }
    );
    requestId = responseRequestId(response.headers);
    const data = readEnvelope(response.data, response.status);
    if (
      !data ||
      typeof data !== 'object' ||
      typeof data.activeKid !== 'string' ||
      typeof data.version !== 'number' ||
      typeof data.retentionDays !== 'number'
    ) {
      invalidResponse(response.status, requestId);
    }
    return data as SecurityKeyRotation;
  } catch (error) {
    if (error instanceof SummerApiError) throw error;
    if (axios.isAxiosError(error)) {
      const status = error.response?.status || 0;
      const failure = readFailure(
        error.response?.data,
        status,
        responseRequestId(error.response?.headers)
      );
      throw new SummerApiError({
        ...failure,
        message: failure.message || (status ? `HTTP ${status}` : 'HTTP network'),
      });
    }
    throw error;
  }
}
