import axios from 'axios';
import { businessData } from '@/api/client';
import { responseRequestId, SummerApiError } from '@/api/errors';

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
export type SecurityKeyRotationSchedule = {
  status: string;
  periodDays: number;
  retentionDays: number;
  nextRotationAt: string;
  lastAttemptAt?: string;
  lastSuccessAt?: string;
  failureCount: number;
  lastError?: string;
};
export type SecurityKeyState = {
  items: SecurityKey[];
  rotation: SecurityKeyRotationSchedule;
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

export async function listSecurityKeyState(): Promise<SecurityKeyState> {
  let requestId = '';
  let status = 0;
  const data = await businessData<SecurityKeyState>(async () => {
    const response = await axios.get('/api/security/keys');
    status = response.status;
    requestId = responseRequestId(response.headers);
    return response;
  });
  if (
    !data ||
    typeof data !== 'object' ||
    !Array.isArray(data.items) ||
    !data.items.every(isSecurityKey)
  ) {
    invalidResponse(status, requestId);
  }
  const rotation = (data as Record<string, unknown>).rotation;
  const schedule =
    rotation && typeof rotation === 'object'
      ? (rotation as Record<string, unknown>)
      : {
          status: 'UNKNOWN',
          periodDays: 90,
          retentionDays: 30,
          nextRotationAt: '',
          failureCount: 0,
        };
  if (
    typeof schedule.status !== 'string' ||
    typeof schedule.periodDays !== 'number' ||
    typeof schedule.retentionDays !== 'number' ||
    typeof schedule.nextRotationAt !== 'string' ||
    typeof schedule.failureCount !== 'number'
  )
    invalidResponse(status, requestId);
  return {
    items: data.items,
    rotation: schedule as unknown as SecurityKeyRotationSchedule,
  };
}

export async function listSecurityKeys(): Promise<SecurityKey[]> {
  return (await listSecurityKeyState()).items;
}

export async function rotateSecurityKey(
  retentionDays: number,
  expectedVersion: number
): Promise<SecurityKeyRotation> {
  let requestId = '';
  let status = 0;
  const data = await businessData<SecurityKeyRotation>(async () => {
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
    status = response.status;
    requestId = responseRequestId(response.headers);
    return response;
  });
  if (
    !data ||
    typeof data !== 'object' ||
    typeof data.activeKid !== 'string' ||
    typeof data.version !== 'number' ||
    typeof data.retentionDays !== 'number'
  ) {
    invalidResponse(status, requestId);
  }
  return data as SecurityKeyRotation;
}
