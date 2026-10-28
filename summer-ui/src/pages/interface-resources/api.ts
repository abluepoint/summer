import axios from 'axios';
import { businessData } from '@/api/client';
import { SummerApiError } from '@/api/errors';

export type InterfaceResource = {
  httpMethod: string;
  normalizedPath: string;
  handler: string;
  module: string;
  resourceType: 'BUSINESS' | 'PROTOCOL' | 'INFRASTRUCTURE' | 'STATIC';
  sensitiveLevel: 'NORMAL' | 'SENSITIVE' | 'CRITICAL';
  permissionCode: string;
  configurable: boolean;
  contextId: string;
  source: string;
  conditions: Record<string, string[]>;
  configurationPolicy: string;
};
export type DiscoveryIssue = {
  contextId: string;
  source: string;
  code: string;
  detail: string;
};
export type InterfaceResourceCatalog = {
  items: InterfaceResource[];
  revision: number;
  complete: boolean;
  issues: DiscoveryIssue[];
};
export type InterfaceResourcePage = InterfaceResourceCatalog & {
  total: number;
  page: number;
  size: number;
};
export type CatalogRevision = {
  revision: number;
  fingerprint: string;
  resourceCount: number;
  createdAt: string;
};
export type CatalogHistory = {
  items: CatalogRevision[];
  total: number;
  page: number;
  size: number;
};
export type CatalogDifference = {
  from: number;
  to: number;
  added: InterfaceResource[];
  removed: InterfaceResource[];
  changed: {
    before: InterfaceResource;
    after: InterfaceResource;
    kind: 'CHANGED' | 'REPLACED';
  }[];
};
export class InterfaceResourceApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public requestId: string
  ) {
    super(message || `HTTP ${status || 'network'}`);
    this.name = 'InterfaceResourceApiError';
  }
}
const root = '/api/v1/platform/interface-resources';
async function request<T>(
  path: string,
  params?: Record<string, number | boolean>
): Promise<T> {
  try {
    return await businessData<T>(() => axios.get(root + path, { params }));
  } catch (error) {
    if (!(error instanceof SummerApiError)) throw error;
    const failure = error;
    throw new InterfaceResourceApiError(
      failure.status,
      failure.code,
      failure.message,
      failure.requestId
    );
  }
}
export const listInterfaceResources = (
  page = 0,
  size = 10,
  options: { revision?: number; refresh?: boolean } = {}
) => request<InterfaceResourcePage>('', { page, size, ...options });
export const listInterfaceResourceHistory = (page = 0, size = 20) =>
  request<CatalogHistory>('/history', { page, size });
export const getInterfaceResourceRevision = (
  revision: number,
  page = 0,
  size = 10
) => request<InterfaceResourcePage>(`/history/${revision}`, { page, size });
export const compareInterfaceResourceRevisions = (from: number, to: number) =>
  request<CatalogDifference>('/diff', { from, to });
