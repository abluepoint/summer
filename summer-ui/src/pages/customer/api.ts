import axios from 'axios';
import { readEnvelope, readFailure } from './contract.mjs';

export type Customer = {
  username: string;
  email: string;
  displayName: string;
  active: boolean;
};

export type CustomerPage = {
  items: Customer[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  projection: string;
};

export type CustomerApiFailure = {
  status: number;
  code: string;
  message: string;
  violations: unknown[];
  requestId: string;
  timestamp: string;
};

export class SummerApiError extends Error implements CustomerApiFailure {
  status: number;
  code: string;
  violations: unknown[];
  requestId: string;
  timestamp: string;

  constructor(failure: CustomerApiFailure) {
    super(failure.message || `HTTP ${failure.status || 'network'}`);
    this.name = 'SummerApiError';
    Object.assign(this, failure);
  }
}

async function getBusinessData<T>(
  url: string,
  params?: Record<string, unknown>
): Promise<T> {
  try {
    const response = await axios.get(url, {
      params,
    });
    return readEnvelope(response.data, response.status) as T;
  } catch (error) {
    if (axios.isAxiosError(error)) {
      const status = error.response?.status || 0;
      const requestId = String(error.response?.headers?.['x-request-id'] || '');
      throw new SummerApiError(
        readFailure(error.response?.data, status, requestId)
      );
    }
    if (error && typeof error === 'object' && 'status' in error)
      throw new SummerApiError(error as CustomerApiFailure);
    throw error;
  }
}

export function listCustomers(state: {
  page: number;
  size: number;
  active: string;
  sort: string;
  direction: string;
}) {
  return getBusinessData<CustomerPage>('/api/customers', {
    page: state.page,
    size: state.size,
    ...(state.active ? { active: state.active } : {}),
    sort: state.sort,
    direction: state.direction,
    projection: 'detail',
  });
}

export function getCustomer(username: string) {
  return getBusinessData<Customer>(
    `/api/customers/${encodeURIComponent(username)}`
  );
}
