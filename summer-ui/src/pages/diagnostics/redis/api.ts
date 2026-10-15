import axios from 'axios';
import { getAccessTokenFromMemory } from '@/auth/oidc';
import { readEnvelope, readFailure } from '../../customer/contract.mjs';

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
    const response = await axios.get('/internal/v1/diagnostics/redis', {
      headers: token ? { Authorization: `Bearer ${token}` } : undefined,
    });
    return readEnvelope(response.data, response.status) as RedisDiagnostics;
  } catch (error) {
    if (!axios.isAxiosError(error)) throw error;
    const status = error.response?.status || 0;
    const failure = readFailure(
      error.response?.data,
      status,
      String(error.response?.headers?.['x-request-id'] || '')
    );
    throw new RedisDiagnosticsError(
      failure.status,
      failure.code,
      failure.message,
      failure.requestId
    );
  }
}
