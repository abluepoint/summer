import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Button, Card, Checkbox, Input, Message, Select, Space, Table, Typography } from '@arco-design/web-react';
import useLocale from '@/utils/useLocale';
import { assignUserRoles, AuthorizationApiError, AuthorizationResource, AuthorizationRole, createRole, getUserRoleAssignment, listAuthorizationResources, listRoles } from './api';

const enterpriseId = 'ent-acme';

export default function RolesPage() {
  const t = useLocale();
  const [roles, setRoles] = useState<AuthorizationRole[]>([]);
  const [resources, setResources] = useState<AuthorizationResource[]>([]);
  const [username, setUsername] = useState('');
  const [selectedRole, setSelectedRole] = useState<string>();
  const [denied, setDenied] = useState<string[]>([]);
  const [roleName, setRoleName] = useState('');
  const [roleCode, setRoleCode] = useState('');
  const [selectedPermissions, setSelectedPermissions] = useState<string[]>([]);
  const [error, setError] = useState<AuthorizationApiError | null>(null);
  const [assignmentVersion, setAssignmentVersion] = useState<number | null>(null);
  const [assignmentLoading, setAssignmentLoading] = useState(false);
  const [assignmentSaving, setAssignmentSaving] = useState(false);
  const assignmentRequest = useRef(0);
  const load = () => Promise.all([listRoles(enterpriseId), listAuthorizationResources()]).then(([roleItems, catalog]) => { setRoles(roleItems); setResources(catalog.items); setError(null); }).catch(setError);
  useEffect(() => { load(); }, []);
  useEffect(() => {
    const request = ++assignmentRequest.current;
    setAssignmentVersion(null);
    if (!username.trim()) { setAssignmentLoading(false); return; }
    setAssignmentLoading(true);
    getUserRoleAssignment(enterpriseId, username.trim()).then((binding) => {
      if (assignmentRequest.current !== request) return;
      setAssignmentVersion(binding?.version ?? 1);
      setSelectedRole(binding?.roleIds[0]);
      setDenied(binding?.deniedCapabilityCodes ?? []);
      setError(null);
    }).catch((failure) => { if (assignmentRequest.current === request) setError(failure); })
      .finally(() => { if (assignmentRequest.current === request) setAssignmentLoading(false); });
    return () => { assignmentRequest.current++; };
  }, [username]);
  const configurable = useMemo(() => resources.filter((resource) => resource.configurable && resource.resourceType === 'BUSINESS'), [resources]);
  const create = () => createRole(enterpriseId, roleName, roleCode, selectedPermissions).then((role) => { setRoles((items) => [...items, role]); setSelectedRole(role.roleId); setRoleName(''); setRoleCode(''); Message.success(t['roles.created']); }).catch(setError);
  const saveAssignment = async () => {
    if (!username.trim() || !selectedRole || assignmentVersion === null || assignmentLoading || assignmentSaving) return;
    setAssignmentSaving(true);
    try {
      const binding = await assignUserRoles(enterpriseId, username.trim(), [selectedRole], denied, assignmentVersion);
      setAssignmentVersion(binding.version);
      setError(null);
      Message.success(t['roles.assigned']);
    } catch (failure) {
      const apiError = failure as AuthorizationApiError;
      if (apiError.status === 412) {
        setAssignmentVersion(null);
        try {
          const current = await getUserRoleAssignment(enterpriseId, username.trim());
          setAssignmentVersion(current?.version ?? 1);
          setError({ ...apiError, message: '授权已被其他操作修改。已更新版本并保留当前输入，请核对后再次保存。' });
        } catch (refreshError) { setError(refreshError as AuthorizationApiError); }
      } else setError(apiError);
    } finally { setAssignmentSaving(false); }
  };
  return <Card title={t['roles.title']} extra={<Button onClick={load}>{t['roles.refresh']}</Button>}>
    {error && <Alert type="error" content={error.message || t['roles.error']} style={{ marginBottom: 16 }} />}
    <Typography.Title heading={6}>{t['roles.createTitle']}</Typography.Title>
    <Space wrap><Input value={roleName} onChange={setRoleName} placeholder={t['roles.name']} /><Input value={roleCode} onChange={setRoleCode} placeholder={t['roles.code']} /><Button type="primary" disabled={!roleName || !roleCode || !selectedPermissions.length} onClick={create}>{t['roles.create']}</Button></Space>
    <Table<AuthorizationResource> rowKey="permissionCode" style={{ marginTop: 16 }} data={configurable} pagination={{ pageSize: 8 }} columns={[{ title: t['roles.permission'], dataIndex: 'permissionCode' }, { title: t['roles.path'], dataIndex: 'normalizedPath' }, { title: t['roles.method'], dataIndex: 'httpMethod' }, { title: t['roles.bind'], render: (_, item) => <Checkbox checked={selectedPermissions.includes(item.permissionCode)} onChange={(checked) => setSelectedPermissions((values) => checked ? [...values, item.permissionCode] : values.filter((value) => value !== item.permissionCode))} /> }]} />
    <Typography.Title heading={6} style={{ marginTop: 24 }}>{t['roles.assignTitle']}</Typography.Title>
    <Space direction="vertical" style={{ width: '100%' }}>
      <Input value={username} disabled={assignmentSaving} onChange={(value) => { setUsername(value); setAssignmentVersion(null); setSelectedRole(undefined); setDenied([]); }} placeholder={t['roles.username']} />
      <Select placeholder={t['roles.chooseRole']} value={selectedRole} disabled={assignmentLoading || assignmentSaving} onChange={setSelectedRole} options={roles.map((role) => ({ label: `${role.name} (${role.code})`, value: role.roleId }))} />
      <Typography.Text>{t['roles.denyLabel']}</Typography.Text>
      <Checkbox.Group value={denied} disabled={assignmentLoading || assignmentSaving} onChange={setDenied}>{configurable.map((resource) => <Checkbox key={resource.permissionCode} value={resource.permissionCode}>{resource.permissionCode}</Checkbox>)}</Checkbox.Group>
      <Button type="primary" loading={assignmentSaving} disabled={!username.trim() || !selectedRole || assignmentVersion === null || assignmentLoading} onClick={saveAssignment}>{t['roles.assign']}</Button>
    </Space>
  </Card>;
}
