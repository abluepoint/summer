import axios from 'axios';
import { businessData } from '@/api/client';

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

function getBusinessData<T>(url: string, params?: Record<string, unknown>) {
  return businessData<T>(() => axios.get(url, { params }));
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

export async function deleteCustomer(username: string): Promise<void> {
  await businessData<void>(async () => {
    // DELETE is a raw 204 response, outside the business success envelope.
    const response = await axios.delete(
      `/api/customers/${encodeURIComponent(username)}`
    );
    if (response.status !== 204) throw new Error('Unexpected delete response');
    return response;
  });
}

export function createCustomer(payload: {
  username: string;
  email: string;
  displayName: string;
}) {
  return businessData<Customer>(() => axios.post('/api/customers', payload));
}

export function updateCustomer(
  username: string,
  payload: Partial<Pick<Customer, 'email' | 'displayName' | 'active'>>
) {
  return businessData<Customer>(() =>
    axios.patch(`/api/customers/${encodeURIComponent(username)}`, payload)
  );
}
