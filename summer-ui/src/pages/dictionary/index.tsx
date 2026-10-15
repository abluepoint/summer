import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useHistory } from 'react-router-dom';
import { isAuthRedirecting } from '@/auth/oidc';
import { Alert, Button, Card, Form, Select, Spin, Tag, Input, InputNumber } from '@arco-design/web-react';
import useLocale from '@/utils/useLocale';
import { DictionaryOption, getPublicDictionary, subscribeDictionaryNotifications, DictionaryStreamState, saveDictionaryOverride } from './api';

import useActiveEnterprise from '../data-scopes/useActiveEnterprise';
const dictionaryCode = 'customer-segment';

export default function DictionaryConsumerPage() {
  const tenant = useActiveEnterprise();
  return <><Select aria-label="活动租户" placeholder="选择活动租户" value={tenant.enterpriseId || undefined} options={tenant.options} onChange={tenant.change} disabled={tenant.busy} style={{ width: 240, marginBottom: 16 }} />
    {tenant.enterpriseId ? <DictionaryContent key={tenant.enterpriseId} enterpriseId={tenant.enterpriseId} onDirtyChange={tenant.setDirty} onBusyChange={tenant.setBusy} /> : tenant.ready && <Alert type="info" content="请选择一个活动成员租户；没有可用租户时不会读取配置。" />}</>;
}
function DictionaryContent({ enterpriseId, onDirtyChange, onBusyChange }: { enterpriseId: string; onDirtyChange: (value: boolean) => void; onBusyChange: (value: boolean) => void }) {
  const t = useLocale();
  const history = useHistory();
  const [options, setOptions] = useState<DictionaryOption[]>([]);
  const [value, setValue] = useState('');
  const [version, setVersion] = useState<number | undefined>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [selectedUnavailable, setSelectedUnavailable] = useState(false);
  const [streamState, setStreamState] = useState<DictionaryStreamState>('CONNECTING');
  const [streamError, setStreamError] = useState(false);
  const valueRef = useRef('');
  const readRequest = useRef(0);
  const alive = useRef(true);
  const writing = useRef(false);
  const [saving, setSaving] = useState(false);
  useEffect(() => () => { alive.current = false; }, []);
  useEffect(() => onBusyChange(saving), [saving, onBusyChange]);
  const overrideDirty = useRef(false);
  const [overrideOrg, setOverrideOrg] = useState('_tenant');
  const [overrideJson, setOverrideJson] = useState('[]');
  const [overrideVersion, setOverrideVersion] = useState<number | undefined>();
  useEffect(() => onDirtyChange(overrideDirty.current), [overrideJson, overrideOrg, overrideVersion, error, onDirtyChange]);
  useEffect(() => {
    if (!overrideDirty.current && !saving) return undefined;
    const unblock = history.block(() => isAuthRedirecting() ? undefined : saving ? false : '字典覆盖有未保存修改，确认离开？');
    const beforeUnload = (event: BeforeUnloadEvent) => { if (!isAuthRedirecting()) { event.preventDefault(); event.returnValue = ''; } };
    window.addEventListener('beforeunload', beforeUnload);
    return () => { unblock(); window.removeEventListener('beforeunload', beforeUnload); };
  }, [overrideJson, overrideOrg, overrideVersion, saving, history]);

  const load = useCallback((eventType?: string) => {
    const request = ++readRequest.current;
    setLoading(true);
    setError('');
    return getPublicDictionary(dictionaryCode, enterpriseId)
      .then((dictionary) => {
        if (request !== readRequest.current) return;
        setOptions(dictionary.items); if (!overrideDirty.current) setOverrideJson(JSON.stringify(dictionary.items, null, 2));
        setVersion(dictionary.version);
        const current = valueRef.current;
        const next = current || dictionary.items[0]?.value || '';
        setSelectedUnavailable(Boolean(current && !dictionary.items.some((item) => item.value === current)));
        valueRef.current = next;
        setValue(next);
      })
      .catch((failure) => {
        if (request !== readRequest.current) return;
        setOptions([]); setVersion(undefined); setSelectedUnavailable(true);
        if ([401, 403, 404].includes((failure as { status?: number }).status || 0)) { valueRef.current = ''; setValue(''); setOverrideJson('[]'); overrideDirty.current = false; }
        else if (!overrideDirty.current) setOverrideJson('[]');
        const denied = failure as Error & { status?: number; requestId?: string };
        setError(`${denied.status === 403 ? '无权读取此字典' : denied.status === 404 ? '字典不可用或不在授权范围内' : denied.message || t['dictionary.error']}${denied.requestId ? ` (${denied.requestId})` : ''}`);
        if (eventType === 'DICTIONARY_DISABLED' || eventType === 'dictionary.disabled') {
          setSelectedUnavailable(Boolean(valueRef.current));
        }
      })
      .finally(() => { if (request === readRequest.current) setLoading(false); });
  }, [t]);

  useEffect(() => {
    load();
    const unsubscribe = subscribeDictionaryNotifications(
      enterpriseId,
      dictionaryCode,
      (event) => {
        setStreamError(false);
        load(event.eventType);
      },
      setStreamState,
      () => setStreamError(true),
    );
    return () => { readRequest.current++; unsubscribe(); };
  }, [load]);

  return (
    <Card
      title={t['dictionary.title']}
      extra={(
        <span>
          <Tag color={streamState === 'OPEN' ? 'green' : 'orange'} style={{ marginRight: 8 }}>
            {streamState}
          </Tag>
          <Button size="small" disabled={saving} loading={loading} onClick={() => load()}>{t['dictionary.refresh']}</Button>
        </span>
      )}
    >
      {loading && !options.length ? <Spin /> : (
        <Form layout="vertical" style={{ maxWidth: 520 }}>
          <Alert type="info" content="覆盖优先级：组织 > 租户 > 基础值。高优先级覆盖不可访问时不会回退；发布覆盖需要独立授权。" style={{ marginBottom: 16 }} />
          {error && <Alert type="error" content={error} style={{ marginBottom: 16 }} />}
          {streamError && <Alert type="warning" content={t['dictionary.streamDisconnected']} style={{ marginBottom: 16 }} />}
          {selectedUnavailable && <Alert type="warning" content={t['dictionary.reselect']} style={{ marginBottom: 16 }} />}
          <Form.Item label={t['dictionary.segment']}>
            <Select
              value={value}
              onChange={(next) => { valueRef.current = next; setValue(next); setSelectedUnavailable(false); }}
              placeholder={t['dictionary.choose']}
              options={options.map((item) => ({ label: item.label, value: item.value }))}
            />
          </Form.Item>
          <Form.Item label="覆盖范围（_tenant 或组织 ID）"><Input disabled={saving} value={overrideOrg} onChange={(v) => { overrideDirty.current = true; setOverrideOrg(v); setOverrideVersion(undefined); }} /></Form.Item>
          <Form.Item label="覆盖版本（新建留空）"><InputNumber disabled={saving} min={0} value={overrideVersion} onChange={(v) => { overrideDirty.current = true; setOverrideVersion(v); }} /></Form.Item>
          <Form.Item label="覆盖选项 JSON"><Input.TextArea disabled={saving} aria-label="覆盖选项 JSON" value={overrideJson} onChange={(text) => { overrideDirty.current = true; setOverrideJson(text); }} /></Form.Item>
          <Button disabled={saving} loading={saving} onClick={() => { if (writing.current) return; try { const items = JSON.parse(overrideJson); writing.current = true; setSaving(true); saveDictionaryOverride(dictionaryCode, overrideOrg, items, overrideVersion, enterpriseId).then((result) => { if (!alive.current) return; setOverrideVersion(result.version); overrideDirty.current = false; load(); }).catch((e) => { if (alive.current) setError(`${e.status === 403 ? '无权发布字典覆盖' : e.message}${e.requestId ? ` (${e.requestId})` : ''}`); }).finally(() => { writing.current = false; if (alive.current) setSaving(false); }); } catch { setError('请输入有效选项 JSON'); } }}>发布字典覆盖</Button>
          {version !== undefined && <div data-testid="dictionary-version">{t['dictionary.version']}: {version}</div>}
        </Form>
      )}
    </Card>
  );
}
