import axios from 'axios';
import { getAccessTokenFromMemory } from '@/auth/oidc';
import { businessData } from '@/api/client';
import { SummerApiError } from '@/api/errors';

import type { DictionaryOption, PublicDictionary, DictionaryNotification, DictionaryStreamState } from './types';
export type { DictionaryOption, PublicDictionary, DictionaryNotification, DictionaryStreamState } from './types';

const publicDictionaryPath = (code: string, enterpriseId?: string) => enterpriseId ? `/public/enterprises/${encodeURIComponent(enterpriseId)}/dictionaries/${encodeURIComponent(code)}` : `/public/dictionaries/${encodeURIComponent(code)}`;

async function readPublicDictionary<T>(path: string): Promise<T> {
  try {
    const token = await getAccessTokenFromMemory();
    return await businessData<T>(() => axios.get(path, token ? { headers: { Authorization: `Bearer ${token}` } } : undefined));
  } catch (error) {
    if (!(error instanceof SummerApiError)) throw error;
    const failure = error;
    throw Object.assign(new Error(failure.message || `HTTP ${failure.status || 'network'}`), { status: failure.status, code: failure.code, requestId: failure.requestId });
  }
}

export const getPublicDictionary = (code: string, enterpriseId?: string) =>
  readPublicDictionary<PublicDictionary>(publicDictionaryPath(code, enterpriseId));

export const getPublicDictionaryItem = (code: string, value: string, enterpriseId?: string) =>
  readPublicDictionary<DictionaryOption>(`${publicDictionaryPath(code, enterpriseId)}/items/${encodeURIComponent(value)}`);

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

export async function saveDictionaryOverride(code: string, organization: string, options: DictionaryOption[], expectedVersion?: number, enterpriseId?: string): Promise<{ version: number }> {
  try {
    return await businessData<{ version: number }>(() => axios.put(enterpriseId ? `/api/v1/enterprises/${encodeURIComponent(enterpriseId)}/dictionaries/${encodeURIComponent(code)}/overrides/${encodeURIComponent(organization)}` : `/api/dictionaries/${encodeURIComponent(code)}/overrides/${encodeURIComponent(organization)}`, { value: options, publicReadable: true, expectedVersion }));
  } catch (error) {
    if (!(error instanceof SummerApiError)) throw error;
    const failure = error;
    throw Object.assign(new Error(failure.message), failure);
  }
}
