import axios from 'axios';
import { readEnvelope, readFailure } from '../customer/contract.mjs';

export type InterfaceResource = {
  httpMethod: string;
  normalizedPath: string;
  handler: string;
  module: string;
  resourceType: 'BUSINESS' | 'PROTOCOL' | 'INFRASTRUCTURE' | 'STATIC';
  sensitiveLevel: 'NORMAL' | 'SENSITIVE' | 'CRITICAL';
  permissionCode: string;
  configurable: boolean;
};

export type InterfaceResourceCatalog = {
  items: InterfaceResource[];
  revision: number;
};

export class InterfaceResourceApiError extends Error {
  status: number;
  code: string;
  requestId: string;

  constructor(status: number, code: string, message: string, requestId: string) {
    super(message || `HTTP ${status || 'network'}`);
    this.name = 'InterfaceResourceApiError';
    this.status = status;
    this.code = code;
    this.requestId = requestId;
  }
}

export async function listInterfaceResources(): Promise<InterfaceResourceCatalog> {
  try {
    const response = await axios.get('/api/v1/platform/interface-resources');
    return readEnvelope(response.data, response.status) as InterfaceResourceCatalog;
  } catch (error) {
    if (!axios.isAxiosError(error)) throw error;
    const status = error.response?.status || 0;
    const failure = readFailure(
      error.response?.data,
      status,
      String(error.response?.headers?.['x-request-id'] || '')
    );
    throw new InterfaceResourceApiError(
      failure.status,
      failure.code,
      failure.message,
      failure.requestId
    );
  }
}
