import { businessData as request } from '@/api/client';
import axios from 'axios';

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
