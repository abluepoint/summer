import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Input, InputNumber, Message, Modal, Select, Space, Table, Tag } from '@arco-design/web-react';
import { useHistory } from 'react-router-dom';
import { isAuthRedirecting } from '@/auth/oidc';
import { getVisibleMenuManifest, MenuManifest, VisibleMenuItem } from '@/menu/api';
import { getManagedManifest, getPlatformManifest, saveOverride, savePlatformManifest } from './api';
import MenuEditor from './components/MenuEditor';
import type { MenuError, MenuOverride } from './types';

import useActiveEnterprise from '../data-scopes/useActiveEnterprise';
export default function MenuManifestPage() {
  const router = useHistory();
  const tenant = useActiveEnterprise();
  const enterpriseId = tenant.enterpriseId;
  const [canWrite, setCanWrite] = useState(false);
  const [canPlatform, setCanPlatform] = useState(false);
  const [mode, setMode] = useState<'tenant' | 'platform'>('tenant');
  const [manifest, setManifest] = useState<MenuManifest | null>(null);
  const [error, setError] = useState<MenuError | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editing, setEditing] = useState<VisibleMenuItem | null>(null);
  const [draft, setDraft] = useState<MenuOverride | null>(null);
  const [platformText, setPlatformText] = useState('[]');
  const [platformVersion, setPlatformVersion] = useState(1);
  const baseline = useRef('');
  const platformBaseline = useRef('');
  const request = useRef(0);
  const mutation = useRef<{ payload: string; key: string } | null>(null);
  const dirty = editing ? JSON.stringify(draft) !== baseline.current : mode === 'platform' && JSON.stringify([platformText, platformVersion]) !== platformBaseline.current && manifest !== null;
  useEffect(() => tenant.setDirty(dirty), [dirty, tenant.setDirty]);
  useEffect(() => tenant.setBusy(saving), [saving, tenant.setBusy]);
  useEffect(() => {
    if (!dirty && !saving) return undefined;
    const unblock = router.block(() => isAuthRedirecting() ? undefined : saving ? false : '菜单有未保存修改，确认离开？');
    const guard = (event: BeforeUnloadEvent) => { if (!isAuthRedirecting()) { event.preventDefault(); event.returnValue = ''; } };
    window.addEventListener('beforeunload', guard);
    return () => { unblock(); window.removeEventListener('beforeunload', guard); };
  }, [dirty, saving, router]);
  const load = useCallback(() => {
    const id = ++request.current; setError(null);
    if (mode === 'tenant' && !enterpriseId) { setManifest(null); setLoading(false); return Promise.resolve(); }
    setLoading(true); setCanWrite(false);
    // Route read actions cannot establish management permissions. These reads
    // are protected by the same server permissions as the corresponding writes.
    if (mode === 'tenant') getPlatformManifest().then(() => { if (id === request.current) setCanPlatform(true); }).catch(() => { if (id === request.current) setCanPlatform(false); });
    const call = mode === 'platform' ? getPlatformManifest() : getManagedManifest(enterpriseId).then((data) => { if (id === request.current) setCanWrite(true); return data; }).catch((failure) => {
      if (failure.status !== 403) throw failure;
      return getVisibleMenuManifest(enterpriseId);
    });
    return call.then((data) => {
      if (id !== request.current) return;
      setManifest(data);
      const text = JSON.stringify(data.items.map(({ menuId, parentMenuId, path, titleKey, iconKey, sortOrder, capabilityCode }) => ({ menuId, parentMenuId, path, titleKey, iconKey, sortOrder, capabilityCode })), null, 2);
      setPlatformText(text); setPlatformVersion(data.manifestVersion + 1); platformBaseline.current = JSON.stringify([text, data.manifestVersion + 1]);
    }).catch((failure) => { if (id === request.current) { setManifest(null); setEditing(null); setDraft(null); setPlatformText('[]'); setError(failure); } }).finally(() => { if (id === request.current) setLoading(false); });
  }, [mode, enterpriseId]);
  useEffect(() => { setManifest(null); setEditing(null); setDraft(null); load(); return () => { request.current++; }; }, [load]);
  const discard = (action: () => void) => { if (saving) return; if (!dirty) action(); else Modal.confirm({ title: '放弃未保存的菜单修改？', okText: '放弃修改', cancelText: '继续编辑', onOk: action }); };
  function open(item: VisibleMenuItem) { const value = { enabled: item.enabled, parentMenuId: item.parentMenuId || '', sortOrder: item.sortOrder, capabilityCode: item.capabilityCode || '' }; baseline.current = JSON.stringify(value); setDraft(value); setEditing(item); setError(null); mutation.current = null; }
  function keyFor(payload: unknown) { const text = JSON.stringify(payload); if (mutation.current?.payload !== text) mutation.current = { payload: text, key: `menu-${Date.now()}-${Math.random().toString(36).slice(2)}` }; return mutation.current.key; }
  function localError(message: string) { setError({ status: 0, code: 'INVALID_INPUT', message, requestId: '' }); }
  function save() {
    if (!editing || !draft || saving) return;
    if (!Number.isSafeInteger(draft.sortOrder) || draft.sortOrder < 0) return localError('排序必须是非负整数');
    if (draft.parentMenuId === editing.menuId || draft.parentMenuId && !manifest.items.some((item) => item.menuId === draft.parentMenuId)) return localError('请选择有效的父菜单，不能选择自身');
    setSaving(true); setError(null);
    saveOverride(enterpriseId, editing.menuId, draft, editing.version, keyFor([enterpriseId, editing.menuId, editing.version, draft]))
      .then(() => { setEditing(null); setDraft(null); Message.success('菜单已更新。'); return load(); }).catch(setError).finally(() => setSaving(false));
  }
  function sync() {
    try {
      const items = JSON.parse(platformText);
      if (!Array.isArray(items) || !Number.isSafeInteger(platformVersion) || platformVersion < 1) return localError('请填写有效版本和菜单数组 JSON');
      const body = { manifestVersion: platformVersion, items };
      setSaving(true); setError(null);
      savePlatformManifest(body, keyFor(body)).then(() => { Message.success('清单已发布。'); return load(); }).catch(setError).finally(() => setSaving(false));
    } catch { localError('请输入有效菜单数组 JSON'); }
  }
  const failure = error && `${error.status === 403 ? '无权管理菜单' : error.status === 409 || error.status === 412 ? '菜单已被修改，请保留草稿并刷新后重试' : error.message || '菜单清单加载失败'}${error.requestId ? ` (${error.requestId})` : ''}`;
  return <Card title="菜单清单" extra={<Button loading={loading} disabled={saving} onClick={() => discard(() => { setEditing(null); setDraft(null); load(); })}>刷新</Button>}>
    <Space style={{ marginBottom: 16 }}><Select aria-label="活动租户" placeholder="选择活动租户" value={enterpriseId || undefined} options={tenant.options} onChange={tenant.change} disabled={saving || mode === 'platform'} style={{ width: 220 }} /><Button onClick={() => discard(() => setMode('tenant'))} disabled={saving}>租户菜单</Button>{canPlatform && <Button onClick={() => discard(() => setMode('platform'))} disabled={saving}>平台清单</Button>}<span>{mode === 'tenant' ? enterpriseId : '平台'} · 清单版本 {manifest?.manifestVersion ?? '-'}</span></Space>
    {failure && <Alert type="error" content={failure} style={{ marginBottom: 16 }} />}
    <Alert type="info" content={mode === 'tenant' && canWrite ? '管理视图包含停用菜单；当前主体可见菜单、路由和动作仍由服务端权限决定，直接 API 调用也独立鉴权。' : '这里展示当前服务端清单；组件解析仍由本地路由定义。'} style={{ marginBottom: 16 }} />
    {mode === 'platform' && manifest && canPlatform && <Space direction="vertical" style={{ width: '100%', marginBottom: 16 }}><InputNumber aria-label="清单版本" min={1} precision={0} value={platformVersion} onChange={setPlatformVersion} disabled={saving} /><Input.TextArea aria-label="平台菜单 JSON" value={platformText} onChange={setPlatformText} disabled={saving} autoSize={{ minRows: 6, maxRows: 16 }} /><Button type="primary" loading={saving} onClick={sync}>发布清单</Button></Space>}
    <Table<VisibleMenuItem> rowKey="menuId" loading={loading} data={manifest?.items || []} pagination={false} columns={[
      { title: '菜单 ID', dataIndex: 'menuId' }, { title: '父菜单', dataIndex: 'parentMenuId' }, { title: '路径', dataIndex: 'path' }, { title: '排序', dataIndex: 'sortOrder' },
      { title: '能力码', dataIndex: 'capabilityCode' }, { title: '状态', render: (_, item) => item.enabled ? '启用' : '停用' },
      { title: '动作权限', dataIndex: 'actions', render: (actions: string[]) => (actions || []).map((action) => <Tag key={action}>{action}</Tag>) },
      { title: '操作', render: (_, item) => mode === 'tenant' && canWrite ? <Button disabled={saving} size="small" onClick={() => discard(() => open(item))}>编辑</Button> : '只读' },
    ]} />
    {editing && draft && <MenuEditor item={editing} value={draft} onChange={setDraft} onSave={save} onClose={() => discard(() => { setEditing(null); setDraft(null); })} saving={saving} />}
  </Card>;
}
