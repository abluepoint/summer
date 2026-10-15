import axios from 'axios';
import { SummerApiError } from '../customer/api';
import { readEnvelope, readFailure } from '../customer/contract.mjs';

export type OAuthClient = {
  clientId: string;
  clientName: string;
  authenticationMethods: string[];
  grantTypes: string[];
  redirectUris: string[];
  postLogoutRedirectUris: string[];
  scopes: string[];
  status: string;
  version: number;
};

export type OAuthConsent = {
  clientId: string;
  principalName: string;
  authorities: string[];
};

function requestId(headers: unknown): string {
  if (!headers || typeof headers !== 'object') return '';
  const value = headers as {
    [key: string]: unknown;
    get?: (name: string) => unknown;
  };
  return String(value['x-request-id'] || value.get?.('x-request-id') || '');
}

async function request<T>(
  call: () => Promise<{ data: unknown; status: number; headers: unknown }>
): Promise<T> {
  try {
    const response = await call();
    const value = readEnvelope(response.data, response.status);
    return value as T;
  } catch (error) {
    if (error instanceof SummerApiError) throw error;
    if (axios.isAxiosError(error)) {
      const status = error.response?.status || 0;
      const failure = readFailure(
        error.response?.data,
        status,
        requestId(error.response?.headers)
      );
      throw new SummerApiError({
        ...failure,
        message: failure.message || `HTTP ${status || 'network'}`,
      });
    }
    throw error;
  }
}

export async function listOAuthClients(): Promise<OAuthClient[]> {
  const data = await request<{ items: OAuthClient[] }>(() =>
    axios.get('/api/security/oauth-clients')
  );
  return data.items;
}

export async function createOAuthClient(input: {
  clientId: string;
  clientName: string;
  publicClient: true;
  redirectUris: string[];
  scopes: string[];
}): Promise<{ clientId: string; status: string }> {
  return request(() => axios.post('/api/security/oauth-clients', input));
}

export async function updateOAuthClient(
  clientId: string,
  input: {
    clientName: string;
    redirectUris: string[];
    scopes: string[];
    version: number;
  }
) {
  return request<{ clientId: string; status: string; version: number }>(() =>
    axios.patch(
      `/api/security/oauth-clients/${encodeURIComponent(clientId)}`,
      input
    )
  );
}

export async function setOAuthClientStatus(
  clientId: string,
  status: string,
  version: number
) {
  return request<{ clientId: string; status: string }>(() =>
    axios.patch(`/api/security/oauth-clients/${encodeURIComponent(clientId)}`, {
      status,
      version,
    })
  );
}

export async function listOAuthConsents(): Promise<OAuthConsent[]> {
  const data = await request<{ items: OAuthConsent[] }>(() =>
    axios.get('/api/security/oauth-consents')
  );
  return data.items;
}

export async function revokeOAuthConsent(
  clientId: string,
  principalName: string
) {
  return request<{ status: string }>(() =>
    axios.delete(
      `/api/security/oauth-consents/${encodeURIComponent(
        clientId
      )}/${encodeURIComponent(principalName)}`
    )
  );
}
