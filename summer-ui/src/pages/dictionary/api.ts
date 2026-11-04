import axios from 'axios';
import { getAccessTokenFromMemory } from '@/auth/oidc';
import { readEnvelope, readFailure } from '../customer/contract.mjs';

export type DictionaryOption = {
  description?: string;
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

export type DictionaryStreamState =
  | 'CONNECTING'
  | 'OPEN'
  | 'RECONNECTING'
  | 'CLOSED';

const publicDictionaryPath = (code: string, enterpriseId?: string) =>
  enterpriseId
    ? `/public/enterprises/${encodeURIComponent(
        enterpriseId
      )}/dictionaries/${encodeURIComponent(code)}`
    : `/public/dictionaries/${encodeURIComponent(code)}`;

async function readPublicDictionary<T>(path: string): Promise<T> {
  try {
    const token = await getAccessTokenFromMemory();
    const response = await axios.get(
      path,
      token ? { headers: { Authorization: `Bearer ${token}` } } : undefined
    );
    return readEnvelope(response.data, response.status) as T;
  } catch (error) {
    if (!axios.isAxiosError(error)) throw error;
    const status = error.response?.status || 0;
    const failure = readFailure(
      error.response?.data,
      status,
      String(error.response?.headers?.['x-request-id'] || '')
    );
    throw Object.assign(
      new Error(failure.message || `HTTP ${status || 'network'}`),
      { status, code: failure.code, requestId: failure.requestId }
    );
  }
}

export const getPublicDictionary = (code: string, enterpriseId?: string) =>
  readPublicDictionary<PublicDictionary>(
    publicDictionaryPath(code, enterpriseId)
  );

export const getPublicDictionaryItem = (
  code: string,
  value: string,
  enterpriseId?: string
) =>
  readPublicDictionary<DictionaryOption>(
    `${publicDictionaryPath(code, enterpriseId)}/items/${encodeURIComponent(
      value
    )}`
  );

export function subscribeDictionaryNotifications(
  enterpriseId: string,
  code: string,
  onEvent: (event: DictionaryNotification) => void,
  onState: (state: DictionaryStreamState) => void,
  onError: () => void
): () => void {
  const source = new EventSource(
    `/api/v1/enterprises/${encodeURIComponent(
      enterpriseId
    )}/dictionaries/${encodeURIComponent(code)}/events`
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
      const data = JSON.parse(message.data) as Omit<
        DictionaryNotification,
        'id'
      >;
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

export async function saveDictionaryOverride(
  code: string,
  organization: string,
  options: DictionaryOption[],
  expectedVersion?: number,
  enterpriseId?: string
): Promise<{ version: number }> {
  try {
    const response = await axios.put(
      enterpriseId
        ? `/api/v1/enterprises/${encodeURIComponent(
            enterpriseId
          )}/dictionaries/${encodeURIComponent(
            code
          )}/overrides/${encodeURIComponent(organization)}`
        : `/api/dictionaries/${encodeURIComponent(
            code
          )}/overrides/${encodeURIComponent(organization)}`,
      { value: options, publicReadable: true, expectedVersion }
    );
    return readEnvelope(response.data, response.status) as { version: number };
  } catch (error) {
    if (!axios.isAxiosError(error)) throw error;
    const failure = readFailure(
      error.response?.data,
      error.response?.status || 0,
      String(error.response?.headers?.['x-request-id'] || '')
    );
    throw Object.assign(new Error(failure.message), failure);
  }
}

export type EffectiveDictionary = {
  code: string;
  name: string | null;
  publishedVersion: number;
  status: string;
  publicReadable: boolean;
  enabled: boolean;
  items: DictionaryOption[];
  groups: unknown[];
  effectiveRevision: number;
};
export const getDictionaryDraftList = (enterpriseId: string) =>
  readPublicDictionary<{ code: string }[]>(
    `/api/v1/enterprises/${encodeURIComponent(enterpriseId)}/dictionaries`
  );
export const getEffectiveDictionary = (enterpriseId: string, code: string) =>
  readPublicDictionary<EffectiveDictionary>(
    `/api/v1/enterprises/${encodeURIComponent(
      enterpriseId
    )}/dictionaries/${encodeURIComponent(code)}/effective`
  );

export type DictionaryDraftItem = DictionaryOption & {
  id: string;
  everPublished: boolean;
};
export type DictionaryDraft = {
  code: string;
  name: string;
  publicReadable: boolean;
  enabled: boolean;
  draftRevision: number;
  publishedVersion: number;
  effectiveRevision: number;
  items: DictionaryDraftItem[];
  itemOrder: string[];
};
const draftPath = (enterpriseId: string, code: string) =>
  `/api/v1/enterprises/${encodeURIComponent(
    enterpriseId
  )}/dictionaries/${encodeURIComponent(code)}/draft`;
export const getDictionaryDraft = (enterpriseId: string, code: string) =>
  readPublicDictionary<DictionaryDraft>(draftPath(enterpriseId, code));
export async function saveDictionaryDraft(
  enterpriseId: string,
  draft: DictionaryDraft
): Promise<DictionaryDraft> {
  try {
    const response = await axios.put(draftPath(enterpriseId, draft.code), {
      expectedDraftRevision: draft.draftRevision,
      name: draft.name,
      publicReadable: draft.publicReadable,
      enabled: draft.enabled,
      items: draft.items.map(({ id, value, label, description, enabled }) => ({
        id,
        value,
        label,
        description,
        enabled,
      })),
      itemOrder: draft.itemOrder,
    });
    return readEnvelope(response.data, response.status) as DictionaryDraft;
  } catch (error) {
    if (!axios.isAxiosError(error)) throw error;
    const failure = readFailure(
      error.response?.data,
      error.response?.status || 0,
      String(error.response?.headers?.['x-request-id'] || '')
    );
    throw Object.assign(new Error(failure.message), failure, {
      status: error.response?.status || 0,
    });
  }
}

export type DictionaryHistory = {
  version: number;
  status: string;
  createdAt: string;
  createdBy: string;
  snapshot?: Omit<
    EffectiveDictionary,
    'code' | 'publishedVersion' | 'status' | 'effectiveRevision'
  >;
};
const managementPath = (enterpriseId: string, code: string) =>
  `/api/v1/enterprises/${encodeURIComponent(
    enterpriseId
  )}/dictionaries/${encodeURIComponent(code)}`;
export const getDictionaryHistory = (enterpriseId: string, code: string) =>
  readPublicDictionary<DictionaryHistory[]>(
    `${managementPath(enterpriseId, code)}/versions`
  );
export const getDictionaryHistoryDetail = (
  enterpriseId: string,
  code: string,
  version: number
) =>
  readPublicDictionary<DictionaryHistory>(
    `${managementPath(enterpriseId, code)}/versions/${version}`
  );
export async function changeDictionaryVersion(
  enterpriseId: string,
  code: string,
  action: 'publish' | 'disable' | 'rollback',
  body: {
    expectedVersion: number;
    expectedEffectiveRevision: number;
    expectedDraftRevision?: number;
    targetVersion?: number;
    idempotencyKey: string;
  }
) {
  try {
    const response = await axios.post(
      `${managementPath(enterpriseId, code)}/${action}`,
      body
    );
    return readEnvelope(response.data, response.status);
  } catch (error) {
    if (!axios.isAxiosError(error)) throw error;
    const failure = readFailure(
      error.response?.data,
      error.response?.status || 0,
      String(error.response?.headers?.['x-request-id'] || '')
    );
    throw Object.assign(new Error(failure.message), failure, {
      status: error.response?.status || 0,
    });
  }
}
