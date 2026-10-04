import React, { useEffect, useState } from 'react';
import { Alert, Button, Card, Form, Select, Spin } from '@arco-design/web-react';
import useLocale from '@/utils/useLocale';
import { DictionaryOption, getPublicDictionary } from './api';

export default function DictionaryConsumerPage() {
  const t = useLocale();
  const [options, setOptions] = useState<DictionaryOption[]>([]);
  const [value, setValue] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  function load() {
    setLoading(true);
    setError('');
    getPublicDictionary('customer-segment')
      .then((dictionary) => {
        setOptions(dictionary.items);
        setValue((current) =>
          dictionary.items.some((item) => item.value === current)
            ? current
            : dictionary.items[0]?.value || ''
        );
      })
      .catch((failure) => setError(failure instanceof Error ? failure.message : t['dictionary.error']))
      .finally(() => setLoading(false));
  }

  useEffect(() => load(), []);

  return (
    <Card title={t['dictionary.title']} extra={<Button size="small" loading={loading} onClick={load}>{t['dictionary.refresh']}</Button>}>
      {loading && !options.length ? <Spin /> : (
        <Form layout="vertical" style={{ maxWidth: 520 }}>
          {error && <Alert type="error" content={error} style={{ marginBottom: 16 }} />}
          <Form.Item label={t['dictionary.segment']} field="segment">
            <Select value={value} onChange={setValue} placeholder={t['dictionary.choose']} options={options.map((item) => ({ label: item.label, value: item.value }))} />
          </Form.Item>
        </Form>
      )}
    </Card>
  );
}
