import { createContext, useContext } from 'react';
import axios from 'axios';
import { readEnvelope } from '@/pages/customer/contract.mjs';

export type AuthorizationDomain =
  | { kind: 'PLATFORM' }
  | { kind: 'ENTERPRISE'; id: string };

/** Current server hints. Actual operations always authorize again on the server. */
export async function qualifyOperations(
  domain: AuthorizationDomain,
  actions: string[]
): Promise<Record<string, boolean>> {
  const base =
    domain.kind === 'PLATFORM'
      ? '/api/v1/platform'
      : `/api/v1/enterprises/${encodeURIComponent(domain.id)}`;
  const response = await axios.get(`${base}/authorization/operations`, {
    params: { actions: actions.join(',') },
    expectedAccessProbe: true,
  });
  const result = readEnvelope(response.data, response.status) as {
    actions: Record<string, boolean>;
  };
  return Object.fromEntries(
    actions.map((action) => [action, result?.actions?.[action] === true])
  );
}

export type OperationHints = {
  engineMode?: 'LEGACY' | 'MANAGED';
  actions: Record<string, boolean>;
};
export const InterfaceOperationHints = createContext<OperationHints>({
  actions: {},
});
export const useOperationHints = () => useContext(InterfaceOperationHints);
