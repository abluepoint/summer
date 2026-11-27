import React, { useEffect, useState } from 'react';
import { Alert, Select } from '@arco-design/web-react';
import { call } from '../permissions/api';

export const base = '/api/v1/platform';
export const departmentsPath = '/platform-departments';
export type Person = { username: string; displayName: string; active: boolean };
export type RosterGroup = {
  id: string;
  name: string;
  description: string;
  administrators: string[];
};

export function saveGroupMembers(
  id: string,
  usernames: string[],
  version: number
) {
  const administrators = usernames.map((username) => ({ username }));
  return call(
    base,
    '/administrators/user-groups/' + encodeURIComponent(id),
    'PUT',
    administrators.length ? { administrators } : {},
    version
  );
}

export type Department = {
  id: string;
  name: string;
  parentId?: string;
  sortOrder: number;
  enabled: boolean;
};
export type Membership = {
  username: string;
  departmentId: string;
  primary: boolean;
  leader: boolean;
};
export type Directory = {
  version: number;
  all: boolean;
  writable: Record<string, { all: boolean; ids: string[] }>;
  departments: Department[];
  memberships: Membership[];
  people: Person[];
};
export function useDirectory<T>(path: string) {
  const [data, setData] = useState<T>();
  const [error, setError] = useState('');
  const [refresh, setRefresh] = useState(0);
  useEffect(() => {
    let active = true;
    setError('');
    call<T>(base, path)
      .then((v) => {
        if (active) setData(v);
      })
      .catch((e) => {
        if (active) {
          setData(undefined);
          setError(e.message);
        }
      });
    return () => {
      active = false;
    };
  }, [path, refresh]);
  return { data, error, reload: () => setRefresh((n) => n + 1) };
}

export function PersonPicker({
  path,
  value,
  onChange,
}: {
  path: string;
  value?: string;
  onChange: (user: string) => void;
}) {
  const [options, setOptions] = useState<Person[]>([]);
  const [error, setError] = useState('');
  const [query, setQuery] = useState('');
  useEffect(() => {
    let active = true;
    setOptions([]);
    setError('');
    if (!query.trim()) return;
    const timer = setTimeout(() => {
      call<Person[]>(
        base,
        path +
          (path.includes('?') ? '&' : '?') +
          'q=' +
          encodeURIComponent(query.trim())
      )
        .then((v) => {
          if (active) setOptions(v);
        })
        .catch((e) => {
          if (active) setError(e.message);
        });
    }, 200);
    return () => {
      clearTimeout(timer);
      active = false;
    };
  }, [path, query]);
  return (
    <>
      <Select
        aria-label="搜索人员"
        showSearch
        filterOption={false}
        placeholder="输入姓名或账号搜索"
        value={value}
        onSearch={setQuery}
        onChange={onChange}
        options={options.map((p) => ({
          label:
            p.displayName === p.username
              ? p.username
              : p.displayName + '（' + p.username + '）',
          value: p.username,
        }))}
      />
      {error && <Alert type="error" content={error} />}
    </>
  );
}
