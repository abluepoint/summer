import React, { useEffect, useRef, useState } from 'react';
import axios from 'axios';
import { useSelector } from 'react-redux';
import { getAccessTokenFromMemory, oidcManager } from '@/auth/oidc';
import { readEnvelope, readFailure } from '../customer/contract.mjs';
import useActiveEnterprise from '../data-scopes/useActiveEnterprise';

export type DictionaryName = {
  value: string;
  label: string | null;
  description: string;
  status: 'enabled' | 'disabled' | 'unknown';
};
type Resolution = {
  items: DictionaryName[];
  publishedVersion: number;
  effectiveRevision: number;
  publishedRevision: number | null;
  source: 'base' | 'override';
  effectiveToken: string;
};
export type DictionaryNames = {
  status: 'loading' | 'ready' | 'error' | 'forbidden';
  items: ReadonlyMap<string, DictionaryName>;
  identity?: Omit<Resolution, 'items'>;
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
  enterprise: string;
  code: string;
};
const entries = new Map<string, Entry>();
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
      Array.from(entry.state.items).filter(([value]) => entry.values.has(value))
    );
    if (items.size !== entry.state.items.size)
      publish(entry, { ...entry.state, items });
  }
}
function invalidate(entry: Entry) {
  entry.generation++;
  entry.pendingValues = undefined;
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
  if (!entry.values.size) return;
  if (
    entry.scheduled ||
    (!refresh &&
      entry.pendingValues &&
      Array.from(entry.values).every((value) => entry.pendingValues.has(value)))
  )
    return;
  entry.scheduled = true;
  const generation = ++entry.generation;
  publish(entry, empty());
  // Collect all values requested in this render turn, independently of cell count.
  Promise.resolve().then(async () => {
    entry.scheduled = false;
    const values = Array.from(entry.values);
    entry.pendingValues = new Set(values);
    try {
      const token = await getAccessTokenFromMemory();
      if (entry.generation !== generation) return;
      const batches: string[][] = [];
      for (let start = 0; start < values.length; start += 500)
        batches.push(values.slice(start, start + 500));
      const resolutions = await Promise.all(
        batches.map(async (batch) => {
          const response = await axios.post(
            `/api/v1/enterprises/${encodeURIComponent(
              entry.enterprise
            )}/dictionaries/${encodeURIComponent(entry.code)}/resolve-names`,
            { values: batch },
            token ? { headers: { Authorization: `Bearer ${token}` } } : undefined
          );
          return readEnvelope(response.data, response.status) as Resolution;
        })
      );
      if (entry.generation !== generation) return;
      const { items, ...identity } = resolutions[0];
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
      publish(entry, {
        status: 'ready',
        items: new Map(
          [...items, ...resolutions.slice(1).flatMap((result) => result.items)]
            .filter((item) => entry.values.has(item.value))
            .map((item) => [item.value, item])
        ),
        identity,
      });
    } catch (error) {
      if (entry.generation !== generation) return;
      entry.pendingValues = undefined;
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
export function useDictionaryNames(code: string, values: readonly string[]) {
  const tenant = useActiveEnterprise();
  const consumer = useRef(Symbol('dictionary-name-consumer')).current;
  const permissions = useSelector(
    (state: { userInfo?: unknown }) => state.userInfo
  );
  const key = JSON.stringify([
    tenant.enterpriseId,
    tenant.contextVersion,
    code,
    permissions,
  ]);
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
      enterprise: tenant.enterpriseId,
      code,
    };
    entries.set(key, entry);
  }
  const current = entry;
  useEffect(() => {
    const listener = () => update((value) => value + 1);
    current.listeners.add(listener);
    const refresh = () => {
      if (current.enterprise) schedule(current, true);
    };
    window.addEventListener('focus', refresh);
    return () => {
      current.listeners.delete(listener);
      current.consumers.delete(consumer);
      collectValues(current);
      window.removeEventListener('focus', refresh);
      if (!current.listeners.size) {
        invalidate(current);
        if (entries.get(key) === current) entries.delete(key);
      }
    };
  }, [current, key, consumer]);
  useEffect(() => {
    const requested: string[] = JSON.parse(signature);
    current.consumers.set(consumer, new Set(requested));
    collectValues(current);
    const missing = Array.from(current.values).some(
      (value) => !current.state.items.has(value)
    );
    if (
      current.enterprise &&
      current.values.size > 0 &&
      (missing || current.state.status === 'loading')
    )
      schedule(current);
  }, [current, key, signature, consumer]);
  return {
    ...current.state,
    refresh: () => schedule(current, true),
    forbid: () => {
      current.generation++;
      current.pendingValues = undefined;
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
    >
      {dictionaryName(names, value)}
    </span>
  );
}
