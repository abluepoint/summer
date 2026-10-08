import axios from 'axios';
import { getAccessTokenFromMemory } from '@/auth/oidc';
import { readEnvelope, readFailure } from '../customer/contract.mjs';

export type DictionaryOption = {
  value: string;
  label: string;
  sortOrder: number;
  enabled: boolean;
};

export type PublicDictionary = {
  code: string;
  items: DictionaryOption[];
  version?: number;
};

export type DictionaryNotification = {
  id: number;
  dictionaryCode: string;
  eventType: string;
  version: number;
  requestId?: string;
};

export type DictionaryStreamState = 'CONNECTING' | 'OPEN' | 'RECONNECTING' | 'CLOSED';

export async function getPublicDictionary(code: string): Promise<PublicDictionary> {
  try {
    const token = await getAccessTokenFromMemory();
    const response = await axios.get(`/public/dictionaries/${encodeURIComponent(code)}`, token ? { headers: { Authorization: `Bearer ${token}` } } : undefined);
    return readEnvelope(response.data, response.status) as PublicDictionary;
  } catch (error) {
    if (!axios.isAxiosError(error)) throw error;
    const status = error.response?.status || 0;
    const failure = readFailure(
      error.response?.data,
      status,
      String(error.response?.headers?.['x-request-id'] || '')
    );
    throw Object.assign(new Error(failure.message || `HTTP ${status || 'network'}`), { status, code: failure.code, requestId: failure.requestId });
  }
}

export function subscribeDictionaryNotifications(
  enterpriseId: string,
  code: string,
  onEvent: (event: DictionaryNotification) => void,
  onState: (state: DictionaryStreamState) => void,
  onError: () => void,
): () => void {
  const source = new EventSource(
    `/api/v1/enterprises/${encodeURIComponent(enterpriseId)}/dictionaries/${encodeURIComponent(code)}/events`,
  );
  // Browser EventSource automatically sends Last-Event-ID on reconnect.
  const eventTypes = [
    'DICTIONARY_PUBLISHED',
    'DICTIONARY_DISABLED',
    'DICTIONARY_ROLLED_BACK',
    'dictionary.published',
    'dictionary.disabled',
    'dictionary.rolled_back',
  ];
  const handle = (event: Event) => {
    const message = event as MessageEvent<string>;
    try {
      const data = JSON.parse(message.data) as Omit<DictionaryNotification, 'id'>;
      onEvent({ ...data, id: Number(message.lastEventId || 0) });
    } catch {
      onError();
    }
  };
  eventTypes.forEach((type) => source.addEventListener(type, handle));
  source.addEventListener('connected', () => onState('OPEN'));
  source.addEventListener('resync', () => onError());
  source.onopen = () => onState('OPEN');
  source.onerror = () => {
    onState('RECONNECTING');
    onError();
  };
  onState('CONNECTING');
  return () => {
    source.close();
    onState('CLOSED');
  };
}

export async function saveDictionaryOverride(code: string, organization: string, options: DictionaryOption[], expectedVersion?: number): Promise<{ version: number }> {
  try {
    const response = await axios.put(`/api/dictionaries/${encodeURIComponent(code)}/overrides/${encodeURIComponent(organization)}`, { value: options, publicReadable: true, expectedVersion });
    return readEnvelope(response.data, response.status) as { version: number };
  } catch (error) {
    if (!axios.isAxiosError(error)) throw error;
    const failure = readFailure(error.response?.data, error.response?.status || 0, String(error.response?.headers?.['x-request-id'] || ''));
    throw Object.assign(new Error(failure.message), failure);
  }
}
