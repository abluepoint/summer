import axios from 'axios';
import { readEnvelope } from './envelope';
import { readFailure, responseRequestId, SummerApiError } from './errors';
import type { BusinessResponse } from './types';

// Explicit business JSON boundary. Auth interceptors keep ownership of token
// recovery; this adapter neither installs interceptors nor retries requests.
export async function businessData<T>(
  request: () => Promise<BusinessResponse>
): Promise<T> {
  try {
    const response = await request();
    if (response.status === 204) return undefined;
    return readEnvelope<T>(
      response.data,
      response.status,
      responseRequestId(response.headers)
    );
  } catch (error) {
    if (error instanceof SummerApiError) throw error;
    if (axios.isAxiosError(error)) {
      throw new SummerApiError(
        readFailure(
          error.response?.data,
          error.response?.status || 0,
          responseRequestId(error.response?.headers)
        )
      );
    }
    throw error;
  }
}
