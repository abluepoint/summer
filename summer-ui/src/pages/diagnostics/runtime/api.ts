import axios from 'axios';
import { businessData } from '@/api/client';
import { SummerApiError } from '@/api/errors';

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
  let traceId = '';
  try {
    return await businessData<RuntimeDiagnostics>(async () => {
      try {
        return await axios.get('/internal/v1/diagnostics/runtime');
      } catch (error) {
        if (axios.isAxiosError(error)) traceId = String(error.response?.headers?.['x-trace-id'] || '');
        throw error;
      }
    });
  } catch (error) {
    if (!(error instanceof SummerApiError)) throw error;
    const failure = error;
    throw new RuntimeDiagnosticsError(
      failure.status,
      failure.code,
      failure.message,
      failure.requestId,
      traceId
    );
  }
}
