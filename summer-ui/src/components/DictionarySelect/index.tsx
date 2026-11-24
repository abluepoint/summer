import React from 'react';
import { Alert, Select, SelectProps } from '@arco-design/web-react';
import { dictionaryName, useDictionaryNames } from '@/pages/dictionary/names';

export type DictionarySelectProps = Pick<
  SelectProps,
  | 'id'
  | 'disabled'
  | 'allowClear'
  | 'showSearch'
  | 'placeholder'
  | 'size'
  | 'style'
  | 'className'
  | 'onBlur'
  | 'onFocus'
  | 'status'
  | 'error'
> & {
  /** Stable platform-wide dictionary code. */
  code: string;
  groupCode?: string;
  value?: string;
  /** Business string only. Clearing emits an empty string. */
  onChange?: (value: string) => void;
  'aria-label'?: string;
  'aria-describedby'?: string;
  'aria-invalid'?: React.AriaAttributes['aria-invalid'];
};

/** A controlled single-select, also usable directly inside Arco Form.Item. */
export function DictionarySelect({
  code,
  groupCode,
  value,
  onChange,
  ...selectProps
}: DictionarySelectProps) {
  if (!code.trim()) {
    return (
      <Select
        {...selectProps}
        aria-label={selectProps['aria-label'] || '字典选项'}
        value={value || undefined}
        disabled
        placeholder={selectProps.placeholder || '请先指定字典'}
      />
    );
  }
  return (
    <DictionaryOptions
      {...selectProps}
      code={code}
      groupCode={groupCode}
      value={value}
      onChange={onChange}
    />
  );
}

function DictionaryOptions({
  code,
  groupCode,
  value = '',
  onChange,
  disabled,
  placeholder = '请选择',
  'aria-label': ariaLabel = '字典选项',
  ...selectProps
}: DictionarySelectProps) {
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
        {...selectProps}
        aria-label={ariaLabel}
        value={value || undefined}
        loading={names.status === 'loading'}
        disabled={disabled || names.status !== 'ready' || unavailable}
        placeholder={placeholder}
        onChange={(next) => onChange?.(next == null ? '' : String(next))}
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

export default DictionarySelect;
