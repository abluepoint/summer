import axios from 'axios';
import { getAccessTokenFromMemory } from '@/auth/oidc';
import { businessData } from '@/api/client';
import { SummerApiError } from '@/api/errors';

export type RedisDiagnostics = {
  component: 'REDIS';
  status: 'UP' | 'AUTHENTICATION_FAILED' | 'TIMEOUT' | 'UNAVAILABLE';
  endpoint: 'redis';
  reason: string | null;
  message: string;
  latencyMs: number;
  metrics: {
    uptimeSeconds: number | null;
    connectedClients: number | null;
    usedMemoryBytes: number | null;
    totalCommandsProcessed: number | null;
  };
  checkedAt: string;
};

export class RedisDiagnosticsError extends Error {
  status: number;
  code: string;
  requestId: string;

  constructor(status: number, code: string, message: string, requestId: string) {
    super(message || `HTTP ${status || 'network'}`);
    this.name = 'RedisDiagnosticsError';
    this.status = status;
    this.code = code;
    this.requestId = requestId;
  }
}

export async function getRedisDiagnostics(): Promise<RedisDiagnostics> {
  try {
    const token = await getAccessTokenFromMemory();
    return await businessData<RedisDiagnostics>(() => axios.get('/internal/v1/diagnostics/redis', {
      headers: token ? { Authorization: `Bearer ${token}` } : undefined,
    }));
  } catch (error) {
    if (!(error instanceof SummerApiError)) throw error;
    const failure = error;
    throw new RedisDiagnosticsError(
      failure.status,
      failure.code,
      failure.message,
      failure.requestId
    );
  }
}
