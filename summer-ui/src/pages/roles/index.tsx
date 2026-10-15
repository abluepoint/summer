import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Button, Card, Checkbox, Input, Message, Select, Space, Table, Typography } from '@arco-design/web-react';
import useLocale from '@/utils/useLocale';
import useActiveEnterprise from '../data-scopes/useActiveEnterprise';
import { assignUserRoles, AuthorizationApiError, AuthorizationResource, AuthorizationRole, createRole, getUserRoleAssignment, listAuthorizationResources, listRoles } from './api';

export default function RolesPage() {
  const tenant = useActiveEnterprise();
  return <><Select aria-label="活动租户" placeholder="选择活动租户" value={tenant.enterpriseId || undefined} options={tenant.options} onChange={tenant.change} disabled={tenant.busy} style={{ width: 240, marginBottom: 16 }} />
    {tenant.enterpriseId ? <RolesContent key={tenant.enterpriseId} enterpriseId={tenant.enterpriseId} onDirtyChange={tenant.setDirty} onBusyChange={tenant.setBusy} /> : tenant.ready && <Alert type="info" content="请选择一个活动成员租户；没有可用租户时不会读取角色。" />}</>;
}

function RolesContent({ enterpriseId, onDirtyChange, onBusyChange }: { enterpriseId: string; onDirtyChange: (value: boolean) => void; onBusyChange: (value: boolean) => void }) {
  const t = useLocale();
  const [roles, setRoles] = useState<AuthorizationRole[]>([]);
  const [resources, setResources] = useState<AuthorizationResource[]>([]);
  const [username, setUsername] = useState('');
  const [selectedRole, setSelectedRole] = useState<string>();
  const [denied, setDenied] = useState<string[]>([]);
  const [roleName, setRoleName] = useState('');
  const [roleCode, setRoleCode] = useState('');
  const [selectedPermissions, setSelectedPermissions] = useState<string[]>([]);
  const [bindingDirty, setBindingDirty] = useState(false);
  const [error, setError] = useState<AuthorizationApiError | null>(null);
  const [ready, setReady] = useState(false);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [assignmentVersion, setAssignmentVersion] = useState<number | null>(null);
  const [assignmentLoading, setAssignmentLoading] = useState(false);
  const alive = useRef(true);
  const writing = useRef(false);
  const epoch = useRef(0);
  const listRequest = useRef(0);
  const assignmentRequest = useRef(0);
  useEffect(() => () => { alive.current = false; onDirtyChange(false); onBusyChange(false); }, [onDirtyChange, onBusyChange]);
  const dirty = !!(roleName || roleCode || selectedPermissions.length || bindingDirty);
  useEffect(() => onDirtyChange(dirty), [dirty, onDirtyChange]);

  // A denial invalidates every outstanding read, including the other read channel.
  const fail = useCallback((failure: AuthorizationApiError) => {
    if (!alive.current) return;
    if (failure.status === 401 || failure.status === 403) {
      epoch.current++;
      setReady(false); setRoles([]); setResources([]); setUsername('');
      setSelectedRole(undefined); setDenied([]); setAssignmentVersion(null);
      setRoleName(''); setRoleCode(''); setSelectedPermissions([]); setBindingDirty(false);
      setAssignmentLoading(false); setLoading(false);
    }
    setError(failure);
  }, []);
  const load = useCallback(async () => {
    if (writing.current) return;
    const request = ++listRequest.current;
    const generation = epoch.current;
    setLoading(true);
    try {
      const [roleItems, catalog] = await Promise.all([listRoles(enterpriseId), listAuthorizationResources()]);
      if (!alive.current || request !== listRequest.current || generation !== epoch.current) return;
      setRoles(roleItems); setResources(catalog.items); setReady(true); setError(null);
    } catch (failure) {
      if (alive.current && request === listRequest.current && generation === epoch.current) fail(failure as AuthorizationApiError);
    } finally {
      if (alive.current && request === listRequest.current && generation === epoch.current) setLoading(false);
    }
  }, [enterpriseId, fail]);
  useEffect(() => { load(); }, [load]);
  useEffect(() => {
    const request = ++assignmentRequest.current;
    const generation = epoch.current;
    setAssignmentVersion(null);
    if (!ready || !username.trim()) { setAssignmentLoading(false); return; }
    setAssignmentLoading(true);
    const current = () => alive.current && request === assignmentRequest.current && generation === epoch.current;
    getUserRoleAssignment(enterpriseId, username.trim()).then((binding) => {
      if (!current()) return;
      setAssignmentVersion(binding?.version ?? 1);
      setSelectedRole(binding?.roleIds[0]); setDenied(binding?.deniedCapabilityCodes ?? []);
      setBindingDirty(false); setError(null);
    }).catch((failure) => { if (current()) fail(failure); })
      .finally(() => { if (current()) setAssignmentLoading(false); });
    return () => { assignmentRequest.current++; };
  }, [enterpriseId, username, ready, fail]);
  const configurable = useMemo(() => resources.filter((resource) => resource.configurable && resource.resourceType === 'BUSINESS'), [resources]);
  const beginWrite = () => {
    writing.current = true; setSaving(true); onBusyChange(true);
    // Reads begun before a write must not replace its result or a subsequent denial.
    epoch.current++; setLoading(false); setAssignmentLoading(false);
    return epoch.current;
  };
  const finishWrite = () => {
    writing.current = false;
    if (alive.current) { setSaving(false); onBusyChange(false); }
  };
  const create = async () => {
    if (!ready || writing.current || assignmentLoading || !roleName || !roleCode || !selectedPermissions.length) return;
    const generation = beginWrite();
    try {
      const role = await createRole(enterpriseId, roleName, roleCode, selectedPermissions);
      if (!alive.current || generation !== epoch.current) return;
      setRoles((items) => [...items, role]); setSelectedRole(role.roleId); setBindingDirty(!!username.trim());
      setRoleName(''); setRoleCode(''); setSelectedPermissions([]); setError(null);
      Message.success(t['roles.created']);
    } catch (failure) { if (alive.current && generation === epoch.current) fail(failure as AuthorizationApiError); }
    finally { finishWrite(); }
  };
  const saveAssignment = async () => {
    if (!ready || writing.current || !username.trim() || !selectedRole || assignmentVersion === null || assignmentLoading) return;
    const generation = beginWrite();
    const current = () => alive.current && generation === epoch.current;
    try {
      const binding = await assignUserRoles(enterpriseId, username.trim(), [selectedRole], denied, assignmentVersion);
      if (!current()) return;
      setAssignmentVersion(binding.version); setBindingDirty(false); setError(null);
      Message.success(t['roles.assigned']);
    } catch (failure) {
      if (!current()) return;
      const apiError = failure as AuthorizationApiError;
      if (apiError.status === 412) {
        setAssignmentVersion(null);
        try {
          const binding = await getUserRoleAssignment(enterpriseId, username.trim());
          if (!current()) return;
          setAssignmentVersion(binding?.version ?? 1);
          setError({ ...apiError, message: '授权已被其他操作修改。已更新版本并保留当前输入，请核对后再次保存。' });
        } catch (refreshError) { if (current()) fail(refreshError as AuthorizationApiError); }
      } else fail(apiError);
    } finally { finishWrite(); }
  };
  return <Card title={t['roles.title']} extra={<Button loading={loading} disabled={saving} onClick={load}>{t['roles.refresh']}</Button>}>
    <Typography.Paragraph>当前租户：{enterpriseId}</Typography.Paragraph>
    {error && <Alert type="error" content={`${error.message || t['roles.error']}${error.requestId ? ` (${error.requestId})` : ''}`} style={{ marginBottom: 16 }} />}
    <Typography.Title heading={6}>{t['roles.createTitle']}</Typography.Title>
    <Space wrap><Input disabled={saving} value={roleName} onChange={setRoleName} placeholder={t['roles.name']} /><Input disabled={saving} value={roleCode} onChange={setRoleCode} placeholder={t['roles.code']} /><Button type="primary" loading={saving} disabled={!ready || assignmentLoading || !roleName || !roleCode || !selectedPermissions.length} onClick={create}>{t['roles.create']}</Button></Space>
    <Table<AuthorizationResource> rowKey={(resource) => JSON.stringify([resource.httpMethod, resource.normalizedPath])} style={{ marginTop: 16 }} data={configurable} pagination={{ pageSize: 8 }} columns={[{ title: t['roles.permission'], dataIndex: 'permissionCode' }, { title: t['roles.path'], dataIndex: 'normalizedPath' }, { title: t['roles.method'], dataIndex: 'httpMethod' }, { title: t['roles.bind'], render: (_, item) => <Checkbox disabled={saving} checked={selectedPermissions.includes(item.permissionCode)} onChange={(checked) => setSelectedPermissions((values) => checked ? [...values, item.permissionCode] : values.filter((value) => value !== item.permissionCode))} /> }]} />
    <Typography.Title heading={6} style={{ marginTop: 24 }}>{t['roles.assignTitle']}</Typography.Title>
    <Space direction="vertical" style={{ width: '100%' }}>
      <Input value={username} disabled={saving} onChange={(value) => { setUsername(value); setAssignmentVersion(null); setSelectedRole(undefined); setDenied([]); setBindingDirty(false); }} placeholder={t['roles.username']} />
      <Select placeholder={t['roles.chooseRole']} value={selectedRole} disabled={!ready || assignmentLoading || saving} onChange={(value) => { setSelectedRole(value); setBindingDirty(true); }} options={roles.map((role) => ({ label: `${role.name} (${role.code})`, value: role.roleId }))} />
      <Typography.Text>{t['roles.denyLabel']}</Typography.Text>
      <Checkbox.Group value={denied} disabled={!ready || assignmentLoading || saving} onChange={(value) => { setDenied(value); setBindingDirty(true); }}>{configurable.map((resource) => <Checkbox key={JSON.stringify([resource.httpMethod, resource.normalizedPath])} value={resource.permissionCode}>{resource.permissionCode}</Checkbox>)}</Checkbox.Group>
      <Button type="primary" loading={saving} disabled={!ready || !username.trim() || !selectedRole || assignmentVersion === null || assignmentLoading} onClick={saveAssignment}>{t['roles.assign']}</Button>
    </Space>
  </Card>;
}
