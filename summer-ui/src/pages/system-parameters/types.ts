export type Parameter = {
  id: string; key: string; type: 'STRING' | 'INTEGER' | 'DECIMAL' | 'BOOLEAN'; value: unknown;
  required: boolean; min?: number; max?: number; pattern?: string; public: boolean;
  description?: string; status: 'ACTIVE' | 'INACTIVE'; version?: number;
  revision?: number; state?: 'DRAFT' | 'PUBLISHED' | 'ROLLED_BACK' | 'INACTIVE';
};
export type ParameterSummary = Pick<Parameter, 'id' | 'key' | 'type' | 'status' | 'public'>;
export type ParameterPage = { items: Array<Parameter | ParameterSummary>; page: number; size: number; totalElements: number; totalPages: number; projection: 'summary' | 'detail' };
export type ParameterEvent = {
  eventId: string; parameterKey: string; eventType: string; version: number; requestId?: string;
  status: 'PENDING' | 'PUBLISHED' | 'DEAD_LETTER'; attempts: number; nextAttemptAt: string;
  lastError?: string; createdAt: string; updatedAt: string;
  event: { eventId: string; parameterKey: string; eventType: string; version: number; requestId?: string };
};
export type ParameterEventPage = { items: ParameterEvent[]; totalElements: number };
export type ParameterVersion = { revision: number; state: 'DRAFT' | 'PUBLISHED' | 'ROLLED_BACK' | 'INACTIVE'; parameterKey: string; baseVersion: number; principal: string; createdAt: string; value: unknown; status: 'ACTIVE' | 'INACTIVE'; currentVersion: number };
export type ParameterVersionPage = { items: ParameterVersion[]; currentVersion: number; currentState: ParameterVersion['state'] };
export type ParameterListQuery = {
  page: number; size: number; key?: string; type?: Parameter['type']; status?: Parameter['status'];
  public?: boolean; sort: 'key' | 'type' | 'status' | 'createdAt' | 'updatedAt' | 'version';
  direction: 'asc' | 'desc'; projection: 'summary' | 'detail';
};

export type ParameterViolation = { field: string; message: string };
