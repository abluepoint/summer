import React, { useEffect, useRef, useState } from 'react';
import axios from 'axios';
import { useSelector } from 'react-redux';
import { getAccessTokenFromMemory, oidcManager } from '@/auth/oidc';
import { readEnvelope, readFailure } from '../customer/contract.mjs';
import {
  DictionaryOption,
  DictionaryStreamState,
  subscribeDictionaryNotifications,
} from './api';

export type DictionaryName = {
  value: string;
  label: string | null;
  description: string;
  status: 'enabled' | 'disabled' | 'unknown';
};
type ChoiceFacet = {
  choices: DictionaryOption[];
  groupStatus: 'available' | 'unavailable';
};
type Resolution = {
  facets?: Record<string, ChoiceFacet>;
  choices?: DictionaryOption[];
  groupStatus?: 'available' | 'unavailable';
  items: DictionaryName[];
  publishedVersion: number;
  effectiveRevision: number;
  publishedRevision: number | null;
  source: 'base' | 'override';
  effectiveToken: string;
  overrideReviewRequired?: boolean;
};
export type DictionaryNames = {
  status: 'loading' | 'ready' | 'error' | 'forbidden';
  items: ReadonlyMap<string, DictionaryName>;
  identity?: Omit<Resolution, 'items' | 'choices' | 'groupStatus' | 'facets'>;
  choices?: DictionaryOption[];
  groupStatus?: 'available' | 'unavailable';
  facets?: Record<string, ChoiceFacet>;
  message?: string;
};
type Entry = {
  state: DictionaryNames;
  values: Set<string>;
  consumers: Map<symbol, Set<string>>;
  listeners: Set<() => void>;
  generation: number;
  scheduled: boolean;
  pendingValues?: Set<string>;
  code: string;
  selectors: Map<symbol, string>;
  pendingGroups?: Set<string>;
  unsubscribe?: () => void;
  streamConsumers: Set<symbol>;
  streamState: DictionaryStreamState;
  streamError: boolean;
};
const entries = new Map<string, Entry>();
type NotificationSubscription = {
  entries: Set<Entry>;
  state: DictionaryStreamState;
  error: boolean;
  stop?: () => void;
};
const subscriptions = new Map<string, NotificationSubscription>();
function subscribeEntry(entry: Entry, key: string): () => void {
  let subscription = subscriptions.get(key);
  if (!subscription) {
    subscription = { entries: new Set(), state: 'CONNECTING', error: false };
    subscriptions.set(key, subscription);
  }
  const shared = subscription;
  shared.entries.add(entry);
  entry.streamState = shared.state;
  entry.streamError = shared.error;
  if (!shared.stop) {
    shared.stop = subscribeDictionaryNotifications(
      entry.code,
      () => {
        shared.error = false;
        shared.entries.forEach((current) => {
          current.streamError = false;
          schedule(current, true);
        });
      },
      (state) => {
        shared.state = state;
        shared.entries.forEach((current) => {
          current.streamState = state;
          current.listeners.forEach((notify) => notify());
        });
      },
      () => {
        // Revalidate every read context; stream failures do not reveal HTTP status.
        shared.error = true;
        shared.entries.forEach((current) => {
          current.streamError = true;
          schedule(current, true);
        });
      }
    );
  }
  return () => {
    shared.entries.delete(entry);
    if (!shared.entries.size) {
      shared.stop?.();
      subscriptions.delete(key);
    }
  };
}
const empty = (): DictionaryNames => ({ status: 'loading', items: new Map() });
function publish(entry: Entry, state: DictionaryNames) {
  entry.state = state;
  entry.listeners.forEach((listener) => listener());
}
function collectValues(entry: Entry) {
  entry.values = new Set(
    Array.from(entry.consumers.values()).flatMap((values) => Array.from(values))
  );
  if (entry.state.status === 'ready') {
    const items = new Map(
      Array.from(entry.state.items).filter(
        ([value]) =>
          entry.values.has(value) ||
          Object.values(entry.state.facets || {}).some((facet) =>
            facet.choices.some((item) => item.value === value)
          )
      )
    );
    if (items.size !== entry.state.items.size)
      publish(entry, { ...entry.state, items });
  }
}
function invalidate(entry: Entry) {
  entry.generation++;
  entry.pendingValues = undefined;
  entry.pendingGroups = undefined;
  publish(entry, empty());
}
function clearNames() {
  const previous = Array.from(entries.values());
  entries.clear();
  previous.forEach(invalidate);
}
oidcManager.events.addUserLoaded(clearNames);
oidcManager.events.addUserUnloaded(clearNames);

function schedule(entry: Entry, refresh = false) {
  if (!entry.values.size && !entry.selectors.size) return;
  if (
    entry.scheduled ||
    (!refresh &&
      entry.pendingValues &&
      Array.from(entry.values).every((value) =>
        entry.pendingValues.has(value)
      ) &&
      Array.from(entry.selectors.values()).every((group) =>
        entry.pendingGroups?.has(group)
      ))
  )
    return;
  entry.scheduled = true;
  const generation = ++entry.generation;
  publish(entry, empty());
  // Collect all values requested in this render turn, independently of cell count.
  Promise.resolve().then(async () => {
    entry.scheduled = false;
    if (entry.generation !== generation) return;
    const values = Array.from(entry.values);
    entry.pendingValues = new Set(values);
    const groupCodes = Array.from(new Set(entry.selectors.values()));
    entry.pendingGroups = new Set(groupCodes);
    try {
      const token = await getAccessTokenFromMemory();
      if (entry.generation !== generation) return;
      const batches: string[][] = values.length ? [] : [[]];
      for (let start = 0; start < values.length; start += 500)
        batches.push(values.slice(start, start + 500));
      const groupBatches: string[][] = groupCodes.length ? [] : [[]];
      for (let start = 0; start < groupCodes.length; start += 200)
        groupBatches.push(groupCodes.slice(start, start + 200));
      // Values are requested once; extra group facets need no duplicate names payload.
      const requests = [
        ...batches.map((batch) => ({
          values: batch,
          groupCodes: groupBatches[0],
        })),
        ...groupBatches
          .slice(1)
          .map((groups) => ({ values: [], groupCodes: groups })),
      ];
      const resolutions = await Promise.all(
        requests.map(async (request) => {
          const response = await axios.post(
            `/api/v1/platform/dictionaries/${encodeURIComponent(entry.code)}/${
              groupCodes.length ? 'resolve-selection' : 'resolve-names'
            }`,
            request,
            token
              ? { headers: { Authorization: `Bearer ${token}` } }
              : undefined
          );
          return readEnvelope(response.data, response.status) as Resolution;
        })
      );
      if (entry.generation !== generation) return;
      const {
        items,
        choices,
        groupStatus,
        facets: firstFacets,
        ...identity
      } = resolutions[0];
      // Null prototype also keeps valid codes such as "constructor" from inheriting a facet.
      const facets: Record<string, ChoiceFacet> = Object.assign(
        Object.create(null),
        firstFacets || {},
        ...resolutions.slice(1).map((result) => result.facets || {})
      );
      const choiceNames = Object.values(facets).flatMap(
        (facet) => facet.choices
      );
      if (
        resolutions.some(
          (result) =>
            result.publishedVersion !== identity.publishedVersion ||
            result.effectiveRevision !== identity.effectiveRevision ||
            result.publishedRevision !== identity.publishedRevision ||
            result.source !== identity.source ||
            result.effectiveToken !== identity.effectiveToken
        )
      )
        throw new Error('dictionary_name_identity_changed');
      entry.pendingValues = undefined;
      entry.pendingGroups = undefined;
      publish(entry, {
        status: 'ready',
        items: new Map(
          [
            ...choiceNames.map((item) => ({
              ...item,
              status: 'enabled' as const,
              description: item.description || '',
            })),
            ...items,
            ...resolutions.slice(1).flatMap((result) => result.items),
          ]
            .filter(
              (item) =>
                entry.values.has(item.value) ||
                choiceNames.some((choice) => choice.value === item.value)
            )
            .map((item) => [item.value, item])
        ),
        identity,
        choices,
        groupStatus,
        facets,
      });
    } catch (error) {
      if (entry.generation !== generation) return;
      entry.pendingValues = undefined;
      entry.pendingGroups = undefined;
      const status = axios.isAxiosError(error)
        ? error.response?.status || 0
        : 0;
      const failure = axios.isAxiosError(error)
        ? readFailure(
            error.response?.data,
            status,
            String(error.response?.headers?.['x-request-id'] || '')
          )
        : { message: '字典加载失败', requestId: '' };
      publish(entry, {
        status: [401, 403, 404].includes(status) ? 'forbidden' : 'error',
        items: new Map(),
        message: `${failure.message || '字典加载失败'}${
          failure.requestId ? ` (${failure.requestId})` : ''
        }`,
      });
    }
  });
}

/** Shared in-memory read for a page's business values. Cells only consume this result. */
export function useDictionaryNames(
  code: string,
  values: readonly string[],
  groupCode?: string,
  selection = false
) {
  const consumer = useRef(Symbol('dictionary-name-consumer')).current;
  const permissions = useSelector(
    (state: { userInfo?: unknown }) => state.userInfo
  );
  const key = JSON.stringify([code, permissions]);
  // Notifications carry metadata only and use the current session cookie.
  // Distinct permission snapshots retain separate authorized reads, not duplicate streams.
  const streamKey = key;
  const interested = values.length > 0 || selection;
  const signature = JSON.stringify(Array.from(new Set(values)).sort());
  const [, update] = useState(0);
  let entry = entries.get(key);
  if (!entry) {
    entry = {
      state: empty(),
      values: new Set(),
      consumers: new Map(),
      listeners: new Set(),
      generation: 0,
      scheduled: false,
      code,
      selectors: new Map(),
      streamConsumers: new Set(),
      streamState: 'CONNECTING',
      streamError: false,
    };
    entries.set(key, entry);
  }
  const current = entry;
  useEffect(() => {
    const listener = () => update((value) => value + 1);
    current.listeners.add(listener);
    if (interested) current.streamConsumers.add(consumer);
    if (!current.unsubscribe && current.streamConsumers.size) {
      current.unsubscribe = subscribeEntry(current, streamKey);
    }
    const refresh = () => {
      schedule(current, true);
    };
    window.addEventListener('focus', refresh);
    return () => {
      current.listeners.delete(listener);
      current.streamConsumers.delete(consumer);
      if (!current.streamConsumers.size) {
        current.unsubscribe?.();
        current.unsubscribe = undefined;
      }
      current.consumers.delete(consumer);
      current.selectors.delete(consumer);
      collectValues(current);
      window.removeEventListener('focus', refresh);
      if (!current.listeners.size) {
        current.unsubscribe?.();
        current.unsubscribe = undefined;
        invalidate(current);
        if (entries.get(key) === current) entries.delete(key);
      }
    };
  }, [current, key, consumer, interested, streamKey]);
  useEffect(() => {
    const requested: string[] = JSON.parse(signature);
    current.consumers.set(consumer, new Set(requested));
    if (selection) current.selectors.set(consumer, groupCode || '');
    else current.selectors.delete(consumer);
    collectValues(current);
    const missing = Array.from(current.values).some(
      (value) => !current.state.items.has(value)
    );
    if (
      (current.values.size > 0 || current.selectors.size > 0) &&
      (missing ||
        current.state.status === 'loading' ||
        (selection && !current.state.facets?.[groupCode || '']))
    )
      schedule(current);
  }, [current, key, signature, consumer, selection, groupCode]);
  return {
    ...current.state,
    streamState: current.streamState,
    streamError: current.streamError,
    status:
      selection &&
      current.state.status === 'ready' &&
      !current.state.facets?.[groupCode || '']
        ? ('loading' as const)
        : current.state.status,
    choices: current.state.facets?.[groupCode || '']?.choices,
    groupStatus: current.state.facets?.[groupCode || '']?.groupStatus,
    refresh: () => schedule(current, true),
    forbid: () => {
      current.generation++;
      current.pendingValues = undefined;
      current.pendingGroups = undefined;
      publish(current, { status: 'forbidden', items: new Map() });
    },
  };
}

/** Pure conversion: unknown is only possible after a successful authorized read. */
export function dictionaryName(names: DictionaryNames, value: string): string {
  if (!value) return '—';
  if (names.status === 'loading') return '加载中';
  if (names.status === 'forbidden') return '无权读取或字典不可用';
  if (names.status === 'error') return '字典加载失败';
  const item = names.items.get(value);
  if (!item) return '加载中';
  if (item.status === 'unknown') return `${value}（未识别）`;
  return `${item.label}${item.status === 'disabled' ? '（已停用）' : ''}`;
}

export function DictionaryNameDisplay({
  names,
  value,
}: {
  names: DictionaryNames;
  value: string;
}) {
  return (
    <span
      title={
        names.status === 'ready'
          ? names.items.get(value)?.description
          : names.message
      }
      data-dictionary-state={names.status}
      data-effective-token={names.identity?.effectiveToken}
    >
      {dictionaryName(names, value)}
    </span>
  );
}
