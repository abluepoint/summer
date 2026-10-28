export type ApiFailure = {
  status: number;
  code: string;
  message: string;
  violations: unknown[];
  requestId: string;
  timestamp: string;
};

export type ApiResponse<T> = {
  success: boolean;
  data?: T;
  error?: { code?: string; message?: string; violations?: unknown[] };
  requestId?: string;
  timestamp?: string;
};

export type BusinessResponse = {
  data: unknown;
  status: number;
  headers?: unknown;
};
