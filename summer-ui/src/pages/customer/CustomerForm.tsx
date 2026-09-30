import React, { useRef, useState } from 'react';
import {
  Alert,
  Button,
  Form,
  Input,
  Message,
  Space,
  Typography,
} from '@arco-design/web-react';
import clipboard from '@/utils/clipboard';
import useLocale from '@/utils/useLocale';
import { createCustomer, SummerApiError } from './api';
import {
  customerPayload,
  mapCustomerFailure,
  validateCustomerDraft,
} from './form-contract.mjs';

export type CustomerDraft = {
  username: string;
  email: string;
  displayName: string;
};
type Field = keyof CustomerDraft;

export default function CustomerForm({
  value,
  onChange,
  onSuccess,
  onCancel,
  onBusyChange,
  canWrite,
}: {
  value: CustomerDraft;
  onChange: (value: CustomerDraft) => void;
  onSuccess: () => void;
  onCancel: () => void;
  onBusyChange: (busy: boolean) => void;
  canWrite: boolean;
}) {
  const t = useLocale();
  const submitting = useRef(false);
  const [busy, setBusy] = useState(false);
  const [errors, setErrors] = useState<Partial<Record<Field, string>>>({});
  const [formError, setFormError] = useState('');
  const [requestId, setRequestId] = useState('');

  function change(field: Field, next: string) {
    onChange({ ...value, [field]: next });
    setErrors((current) => ({ ...current, [field]: undefined }));
    setFormError('');
    setRequestId('');
  }

  async function submit() {
    if (!canWrite) return;
    if (submitting.current) return;
    const payload = customerPayload(value);
    const validation = validateCustomerDraft(payload);
    if (Object.keys(validation).length) {
      setErrors(
        Object.fromEntries(
          Object.keys(validation).map((field) => [
            field,
            t[`customer.validation.${field}`],
          ])
        )
      );
      return;
    }
    submitting.current = true;
    setBusy(true);
    onBusyChange(true);
    setErrors({});
    setFormError('');
    setRequestId('');
    try {
      await createCustomer(payload);
      onSuccess();
    } catch (failure) {
      if (failure instanceof SummerApiError) {
        const mapped = mapCustomerFailure(failure);
        setErrors(mapped.fields);
        setRequestId(failure.requestId);
        if (failure.status === 400 || failure.status === 409) {
          setFormError(
            mapped.other.join('；') ||
              (!Object.keys(mapped.fields).length
                ? failure.message ||
                  t[
                    failure.status === 400
                      ? 'customer.error.validation'
                      : 'customer.error.conflict'
                  ]
                : '')
          );
        } else if (failure.status !== 401) {
          setFormError(
            failure.status === 0
              ? t['customer.error.network']
              : failure.message || t['customer.error.save']
          );
          if (failure.status !== 0) Message.error(t['customer.error.save']);
        }
      } else {
        setFormError(t['customer.error.network']);
        Message.error(t['customer.error.network']);
      }
    } finally {
      submitting.current = false;
      setBusy(false);
      onBusyChange(false);
    }
  }

  return (
    <Form layout="vertical" onSubmit={submit}>
      {!canWrite && (
        <Alert
          type="warning"
          content={t['auth.forbidden']}
          style={{ marginBottom: 16 }}
        />
      )}
      {formError && (
        <Alert type="error" content={formError} style={{ marginBottom: 16 }} />
      )}
      {requestId && (
        <Space style={{ marginBottom: 16 }}>
          <Typography.Text>requestId: {requestId}</Typography.Text>
          <Button
            size="mini"
            onClick={() =>
              clipboard(requestId).catch(() =>
                Message.error(t['customer.error.copyFailed'])
              )
            }
          >
            {t['customer.error.copyId']}
          </Button>
        </Space>
      )}
      {(['username', 'email', 'displayName'] as Field[]).map((field) => (
        <Form.Item
          key={field}
          label={
            t[field === 'displayName' ? 'customer.name' : `customer.${field}`]
          }
          required
          validateStatus={errors[field] ? 'error' : undefined}
          help={errors[field]}
        >
          <Input
            value={value[field]}
            onChange={(next) => change(field, next)}
            disabled={busy}
            maxLength={field === 'username' ? 64 : 200}
            aria-label={
              t[field === 'displayName' ? 'customer.name' : `customer.${field}`]
            }
            autoFocus={field === 'username'}
          />
        </Form.Item>
      ))}
      <Space>
        <Button
          type="primary"
          htmlType="submit"
          loading={busy}
          disabled={busy || !canWrite}
        >
          {t['customer.create.submit']}
        </Button>
        <Button onClick={onCancel} disabled={busy}>
          {t['customer.create.cancel']}
        </Button>
      </Space>
    </Form>
  );
}
