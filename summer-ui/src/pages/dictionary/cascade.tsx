import React, { useEffect, useRef, useState } from 'react';
import { Alert, Button } from '@arco-design/web-react';
import { DictionarySelect } from './select';
import { DictionaryNameDisplay, useDictionaryNames } from './names';
import useActiveEnterprise from '../data-scopes/useActiveEnterprise';

export type DictionaryCascadeLevel = {
  code: string;
  label: string;
  /** Exact upstream business string -> group code in this level's dictionary. */
  groups?: Readonly<Record<string, string>>;
};
type Props = {
  levels: readonly DictionaryCascadeLevel[];
  values: readonly string[];
  onChange: (values: string[]) => void;
};

/** Controlled business values. Only an explicit user selection starts reconciliation. */
export function DictionaryCascade(props: Props) {
  const tenant = useActiveEnterprise();
  return (
    <CascadeContent
      key={JSON.stringify([
        tenant.enterpriseId,
        tenant.contextVersion,
        props.levels,
      ])}
      {...props}
    />
  );
}
function CascadeContent({ levels, values, onChange }: Props) {
  const [pending, setPending] = useState<number | null>(null);
  const pendingRef = useRef(pending);
  pendingRef.current = pending;
  function change(index: number, value: string) {
    const next = levels.map((_, i) => values[i] || '');
    next[index] = value;
    if (!value) next.fill('', index + 1);
    // A descendant edit cannot cancel validation started by an ancestor.
    const ancestor = pendingRef.current;
    pendingRef.current =
      ancestor !== null && ancestor <= index
        ? ancestor
        : value && index + 1 < levels.length
        ? index + 1
        : null;
    setPending(pendingRef.current);
    onChange(next);
  }
  function reconcile(index: number, applicable: boolean) {
    if (pendingRef.current !== index) return;
    pendingRef.current = null;
    if (!applicable) {
      const next = levels.map((_, i) => values[i] || '');
      next.fill('', index);
      onChange(next);
      setPending(null);
    } else setPending(index + 1 < levels.length ? index + 1 : null);
  }
  return (
    <div>
      {levels.map((level, index) => {
        const upstream = values[index - 1] || '';
        const mapped =
          index === 0 ||
          (upstream !== '' &&
            Object.prototype.hasOwnProperty.call(
              level.groups || {},
              upstream
            ) &&
            typeof level.groups[upstream] === 'string' &&
            level.groups[upstream] !== '');
        return (
          <div key={index} data-cascade-level={index}>
            <p>{level.label}</p>
            <CascadeLevel
              code={level.code}
              label={level.label}
              groupCode={
                index === 0
                  ? undefined
                  : mapped
                  ? level.groups[upstream]
                  : undefined
              }
              mapped={mapped}
              value={values[index] || ''}
              pending={pending === index}
              onResolved={(applicable) => reconcile(index, applicable)}
              onChange={(value) => change(index, value)}
            />
          </div>
        );
      })}
    </div>
  );
}
function CascadeLevel({
  code,
  label,
  groupCode,
  mapped,
  value,
  pending,
  onResolved,
  onChange,
}: {
  code: string;
  label: string;
  groupCode?: string;
  mapped: boolean;
  value: string;
  pending: boolean;
  onResolved: (applicable: boolean) => void;
  onChange: (value: string) => void;
}) {
  // No mapping requests names only; it must never request the default selection facet.
  const names = useDictionaryNames(
    code,
    value ? [value] : [],
    groupCode,
    mapped
  );
  useEffect(() => {
    if (!pending) return;
    if (!mapped) {
      onResolved(false);
      return;
    }
    if (names.status !== 'ready') return;
    onResolved(
      names.groupStatus === 'available' &&
        (!value || (names.choices || []).some((item) => item.value === value))
    );
  }, [pending, mapped, value, names, onResolved]);
  return (
    <>
      {mapped ? (
        <DictionarySelect
          code={code}
          groupCode={groupCode}
          value={value}
          onChange={onChange}
        />
      ) : (
        <div data-cascade-state="unmapped">
          {value && <DictionaryNameDisplay names={names} value={value} />}
          <Alert type="info" content="上游未选择或未配置对应字典组" />
        </div>
      )}
      <Button
        aria-label={`清空${label}`}
        size="mini"
        disabled={!value}
        onClick={() => onChange('')}
      >
        清空
      </Button>
    </>
  );
}
