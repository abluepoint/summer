import React from 'react';
import { Alert, Select } from '@arco-design/web-react';
import { dictionaryName, useDictionaryNames } from './names';

/** A controlled string value: loading and unavailable options never mutate caller data. */
export function DictionarySelect({
  code,
  groupCode,
  value,
  onChange,
}: {
  code: string;
  groupCode?: string;
  value: string;
  onChange: (value: string) => void;
}) {
  const names = useDictionaryNames(code, value ? [value] : [], groupCode, true);
  const choices = names.status === 'ready' ? names.choices || [] : [];
  const unavailable = names.groupStatus === 'unavailable';
  const options = choices.map((item) => ({
    value: item.value,
    label: item.label,
  }));
  if (value && !choices.some((item) => item.value === value))
    options.push({
      value,
      label: names.status === 'ready' ? dictionaryName(names, value) : value,
    });
  const message =
    names.status === 'loading'
      ? '加载中'
      : names.status === 'forbidden'
      ? '无权读取或字典不可用'
      : names.status === 'error'
      ? names.message || '字典加载失败'
      : unavailable
      ? '字典组不可用'
      : !choices.length
      ? '暂无可选项'
      : '';
  return (
    <div
      data-dictionary-selection={names.status}
      data-group-state={names.groupStatus}
      data-effective-token={names.identity?.effectiveToken}
    >
      <Select
        aria-label="字典选项"
        value={value || undefined}
        loading={names.status === 'loading'}
        disabled={names.status !== 'ready' || unavailable}
        placeholder="请选择"
        onChange={onChange}
        options={options.map((item) => ({
          ...item,
          disabled: !choices.some((choice) => choice.value === item.value),
        }))}
      />
      {message && (
        <Alert
          type={
            names.status === 'error' || names.status === 'forbidden'
              ? 'error'
              : 'info'
          }
          content={message}
        />
      )}
      {names.status === 'ready' &&
        value &&
        !choices.some((item) => item.value === value) && (
          <span>{dictionaryName(names, value)}</span>
        )}
    </div>
  );
}
