import axios from 'axios';

export type SecurityKey = { kid: string; algorithm: string; source: string; status: string; version: number; health: string };

export async function listSecurityKeys(): Promise<SecurityKey[]> {
  const response = await axios.get('/api/security/keys');
  return response.data?.data?.items || [];
}
