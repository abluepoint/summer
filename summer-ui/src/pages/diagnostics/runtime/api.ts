import axios from 'axios';
import { readEnvelope, readFailure } from '../../customer/contract.mjs';

export type RuntimeDiagnostics = {
  requestId: string;
  traceId: string | null;
  checkedAt: string;
  components: Array<{ name: string; status: string }>;
  metrics: {
    registeredMeters: number;
    meterNames: string[];
    samples: Record<string, number>;
    http: {
      status: string;
      window: string;
      processStartedAt: string;
      requests: number;
      clientErrors: number;
      serverErrors: number;
      meanLatencyMs: number | null;
      errorRate: number | null;
    };
    audit: RuntimeModuleMetrics;
    authentication: RuntimeModuleMetrics;
    cache: RuntimeModuleMetrics;
    realtime: RuntimeModuleMetrics;
  };
};

export type RuntimeModuleMetrics = {
  status: string;
  window: string;
  checkedAt: string;
  values: Record<string, number>;
};

export class RuntimeDiagnosticsError extends Error {
  status: number;
  code: string;
  requestId: string;
  traceId: string;

  constructor(status: number, code: string, message: string, requestId: string, traceId = '') {
    super(message || `HTTP ${status || 'network'}`);
    this.name = 'RuntimeDiagnosticsError';
    this.status = status;
    this.code = code;
    this.requestId = requestId;
    this.traceId = /^[a-f0-9]{32}$/.test(traceId) ? traceId : '';
  }
}

export async function getRuntimeDiagnostics(): Promise<RuntimeDiagnostics> {
  try {
    const response = await axios.get('/internal/v1/diagnostics/runtime');
    return readEnvelope(response.data, response.status) as RuntimeDiagnostics;
  } catch (error) {
    if (!axios.isAxiosError(error)) throw error;
    const status = error.response?.status || 0;
    const failure = readFailure(
      error.response?.data,
      status,
      String(error.response?.headers?.['x-request-id'] || '')
    );
    throw new RuntimeDiagnosticsError(
      failure.status,
      failure.code,
      failure.message,
      failure.requestId,
      String(error.response?.headers?.['x-trace-id'] || '')
    );
  }
}
