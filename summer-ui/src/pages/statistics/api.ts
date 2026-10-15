import axios from 'axios';
import { readEnvelope, readFailure } from '../customer/contract.mjs';

export type Statistics = {
  window: { from: string; to: string };
  scope: { enterpriseId: string; organizationId: string | null; subject: string };
  userCount: number | null;
  roleCount: number | null;
  interfaceCount: number | null;
  accessibleInterfaceCount: number | null;
  dictionaryCount: number | null;
  taskCount: number | null;
  taskExecutionCount: number | null;
  successfulTaskExecutionCount: number | null;
  failedTaskExecutionCount: number | null;
  taskSuccessRate: number | null;
  auditEventCount: number | null;
  asOf: string;
};

export class StatisticsError extends Error {
  status: number;
  code: string;
  requestId: string;

  constructor(status: number, code: string, message: string, requestId: string) {
    super(message || `HTTP ${status || 0}`);
    this.name = 'StatisticsError';
    this.status = status;
    this.code = code;
    this.requestId = requestId;
  }
}

export async function getStatistics(
  enterpriseId: string,
  params: { from?: string; to?: string; organizationId?: string } = {}
): Promise<Statistics> {
  try {
    const response = await axios.get(`/api/v1/enterprises/${encodeURIComponent(enterpriseId)}/statistics`, { params });
    return readEnvelope(response.data, response.status) as Statistics;
  } catch (error) {
    if (!axios.isAxiosError(error)) throw error;
    const failure = readFailure(error.response?.data, error.response?.status || 0, String(error.response?.headers?.['x-request-id'] || ''));
    throw new StatisticsError(failure.status, failure.code, failure.message, failure.requestId);
  }
}
