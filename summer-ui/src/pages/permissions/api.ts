import axios from 'axios';
import { readEnvelope, readFailure } from '../customer/contract.mjs';
export type Group = {
  groupId: string;
  parentGroupId: string | null;
  name: string;
  sortOrder: number;
  version: number;
};
export type Permission = {
  permissionId: string;
  code: string;
  name: string;
  description: string | null;
  type: string;
  groupId: string;
  accessMode: string;
  status: string;
  version: number;
  dataScope?: DataScopeDetail;
};
export type Page<T> = { items: T[]; total: number; page: number; size: number };
export type Failure = { status: number; message: string; requestId?: string };
export async function call<T>(
  base: string,
  path: string,
  method = 'GET',
  data?: unknown,
  version?: number,
  key?: string
): Promise<T> {
  try {
    const response = await axios.request({
      url: base + path,
      method: method as 'GET',
      data,
      headers: {
        ...(key ? { 'Idempotency-Key': key } : {}),
        ...(version === undefined ? {} : { 'If-Match': `"${version}"` }),
      },
    });
    return readEnvelope(response.data, response.status) as T;
  } catch (e) {
    if (!axios.isAxiosError(e)) throw e;
    throw readFailure(
      e.response?.data,
      e.response?.status || 0,
      String(e.response?.headers?.['x-request-id'] || '')
    );
  }
}

export type PermissionDomain = { kind: 'PLATFORM' | 'ENTERPRISE'; id: string };
export type PermissionAssociationProps = {
  domain: PermissionDomain;
  permissionId: string;
  version: number;
  onSaved: () => void;
  onDirtyChange?: (value: boolean) => void;
  onBusyChange?: (value: boolean) => void;
  disabled?: boolean;
};
export type DataScopeDetail = {
  dataResourceKey: string;
  action: string;
  scopePreset:
    | 'SELF'
    | 'TENANT_ALL'
    | 'DEPARTMENT'
    | 'DEPARTMENT_AND_DESCENDANTS'
    | 'SPECIFIED_DEPARTMENTS';
  departmentIds?: string[];
};
export type DataResource = {
  key: string;
  name: string;
  selfMeaning: string;
  actions: string[];
};
