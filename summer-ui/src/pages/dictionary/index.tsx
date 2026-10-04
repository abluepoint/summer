import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Form, Select, Spin, Tag } from '@arco-design/web-react';
import useLocale from '@/utils/useLocale';
import { DictionaryOption, getPublicDictionary, subscribeDictionaryNotifications, DictionaryStreamState } from './api';

const enterpriseId = 'ent-acme';
const dictionaryCode = 'customer-segment';

export default function DictionaryConsumerPage() {
  const t = useLocale();
  const [options, setOptions] = useState<DictionaryOption[]>([]);
  const [value, setValue] = useState('');
  const [version, setVersion] = useState<number | undefined>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [selectedUnavailable, setSelectedUnavailable] = useState(false);
  const [streamState, setStreamState] = useState<DictionaryStreamState>('CONNECTING');
  const [streamError, setStreamError] = useState(false);
  const valueRef = useRef('');

  const load = useCallback((eventType?: string) => {
    setLoading(true);
    setError('');
    return getPublicDictionary(dictionaryCode)
      .then((dictionary) => {
        setOptions(dictionary.items);
        setVersion(dictionary.version);
        const current = valueRef.current;
        const next = current || dictionary.items[0]?.value || '';
        setSelectedUnavailable(Boolean(current && !dictionary.items.some((item) => item.value === current)));
        valueRef.current = next;
        setValue(next);
      })
      .catch((failure) => {
        setError(failure instanceof Error ? failure.message : t['dictionary.error']);
        if (eventType === 'DICTIONARY_DISABLED' || eventType === 'dictionary.disabled') {
          setSelectedUnavailable(Boolean(valueRef.current));
        }
      })
      .finally(() => setLoading(false));
  }, [t]);

  useEffect(() => {
    load();
    return subscribeDictionaryNotifications(
      enterpriseId,
      dictionaryCode,
      (event) => {
        setStreamError(false);
        load(event.eventType);
      },
      setStreamState,
      () => setStreamError(true),
    );
  }, [load]);

  return (
    <Card
      title={t['dictionary.title']}
      extra={(
        <span>
          <Tag color={streamState === 'OPEN' ? 'green' : 'orange'} style={{ marginRight: 8 }}>
            {streamState}
          </Tag>
          <Button size="small" loading={loading} onClick={() => load()}>{t['dictionary.refresh']}</Button>
        </span>
      )}
    >
      {loading && !options.length ? <Spin /> : (
        <Form layout="vertical" style={{ maxWidth: 520 }}>
          {error && <Alert type="error" content={error} style={{ marginBottom: 16 }} />}
          {streamError && <Alert type="warning" content={t['dictionary.streamDisconnected']} style={{ marginBottom: 16 }} />}
          {selectedUnavailable && <Alert type="warning" content={t['dictionary.reselect']} style={{ marginBottom: 16 }} />}
          <Form.Item label={t['dictionary.segment']} field="segment">
            <Select
              value={value}
              onChange={(next) => { valueRef.current = next; setValue(next); setSelectedUnavailable(false); }}
              placeholder={t['dictionary.choose']}
              options={options.map((item) => ({ label: item.label, value: item.value }))}
            />
          </Form.Item>
          {version !== undefined && <div data-testid="dictionary-version">{t['dictionary.version']}: {version}</div>}
        </Form>
      )}
    </Card>
  );
}
