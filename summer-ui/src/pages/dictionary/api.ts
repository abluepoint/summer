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

const publicDictionaryPath = (code: string) =>
  `/public/dictionaries/${encodeURIComponent(code)}`;

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

export const getPublicDictionary = (code: string) =>
  readPublicDictionary<PublicDictionary>(publicDictionaryPath(code));

export const getPublicDictionaryItem = (code: string, value: string) =>
  readPublicDictionary<DictionaryOption>(
    `${publicDictionaryPath(code)}/items/${encodeURIComponent(value)}`
  );

export function subscribeDictionaryNotifications(
  code: string,
  onEvent: (event: DictionaryNotification) => void,
  onState: (state: DictionaryStreamState) => void,
  onError: () => void
): () => void {
  const source = new EventSource(
    `/api/v1/platform/dictionaries/${encodeURIComponent(code)}/events`
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

export type EffectiveDictionary = {
  code: string;
  name: string | null;
  publishedVersion: number;
  status: string;
  publicReadable: boolean;
  enabled: boolean;
  items: DictionaryOption[];
  groups: DictionarySnapshotGroup[];
  effectiveRevision: number;
};
export type DictionaryType = {
  code: string;
  name: string;
  published: boolean;
  publicReadable: boolean;
  enabled: boolean;
  publishedVersion: number | null;
  draftRevision: number;
  effectiveRevision: number;
  items: DictionaryOption[];
};
export type CreateDictionary = {
  code: string;
  name: string;
  publicReadable: boolean;
  enabled: boolean;
};
export const getDictionaryDraftList = () =>
  readPublicDictionary<DictionaryType[]>(`/api/v1/platform/dictionaries`);
export async function createDictionary(
  input: CreateDictionary
): Promise<DictionaryType> {
  try {
    const response = await axios.post(`/api/v1/platform/dictionaries`, input);
    return readEnvelope(response.data, response.status) as DictionaryType;
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
export const getEffectiveDictionary = (code: string) =>
  readPublicDictionary<EffectiveDictionary>(
    `/api/v1/platform/dictionaries/${encodeURIComponent(code)}/effective`
  );

export type DictionaryDraftItem = DictionaryOption & {
  id: string;
  everPublished: boolean;
};
export type DictionaryDraftGroup = {
  id: string;
  code: string;
  name: string;
  enabled: boolean;
  everPublished: boolean;
  memberIds: string[];
  memberSortOrders?: Record<string, number>;
};
export type DictionarySnapshotGroup = {
  id: string;
  code: string;
  name: string;
  enabled: boolean;
  memberValues: string[];
  memberSortOrders?: Record<string, number>;
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
  groups: DictionaryDraftGroup[];
};
const draftPath = (code: string) =>
  `/api/v1/platform/dictionaries/${encodeURIComponent(code)}/draft`;
export const getDictionaryDraft = (code: string) =>
  readPublicDictionary<DictionaryDraft>(draftPath(code));
export async function saveDictionaryDraft(
  draft: DictionaryDraft
): Promise<DictionaryDraft> {
  try {
    const itemIds = new Set(draft.items.map((item) => item.id));
    const response = await axios.put(draftPath(draft.code), {
      expectedDraftRevision: draft.draftRevision,
      name: draft.name,
      publicReadable: draft.publicReadable,
      enabled: draft.enabled,
      items: draft.items.map(
        ({ id, value, label, description, enabled, sortOrder }) => ({
          id,
          value,
          label,
          description,
          enabled,
          sortOrder,
        })
      ),
      itemOrder: draft.itemOrder,
      // Preserve legacy groups in aggregate saves, pruning only deleted items.
      groups: draft.groups.map(
        ({ id, code, name, enabled, memberIds, memberSortOrders }) => {
          const members = memberIds.filter((id) => itemIds.has(id));
          let nextRank = Math.max(0, ...Object.values(memberSortOrders || {}));
          return {
            id,
            code,
            name,
            enabled,
            memberIds: members,
            memberOrder: members,
            memberSortOrders: Object.fromEntries(
              members.map((memberId) => [
                memberId,
                memberSortOrders?.[memberId] ??
                  (memberSortOrders
                    ? Math.min(++nextRank, 2147483647)
                    : memberIds.indexOf(memberId) + 1),
              ])
            ),
          };
        }
      ),
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
const managementPath = (code: string) =>
  `/api/v1/platform/dictionaries/${encodeURIComponent(code)}`;
export const getDictionaryHistory = (code: string) =>
  readPublicDictionary<DictionaryHistory[]>(`${managementPath(code)}/versions`);
export const getDictionaryHistoryDetail = (code: string, version: number) =>
  readPublicDictionary<DictionaryHistory>(
    `${managementPath(code)}/versions/${version}`
  );
export async function changeDictionaryVersion(
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
      `${managementPath(code)}/${action}`,
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
