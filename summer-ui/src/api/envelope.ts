import { readFailure, SummerApiError } from './errors';
import type { ApiResponse } from './types';

export function readEnvelope<T = unknown>(
  body: unknown,
  status: number,
  headerRequestId = ''
): T {
  const envelope = body as ApiResponse<T> | null | undefined;
  if (status >= 200 && status < 300 && envelope?.success === true)
    return envelope.data;
  throw new SummerApiError(readFailure(body, status, headerRequestId));
}
