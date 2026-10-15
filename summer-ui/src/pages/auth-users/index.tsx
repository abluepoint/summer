import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Drawer, Form, Input, Modal, Select, Space, Table, Tag } from '@arco-design/web-react';
import { User } from 'oidc-client-ts';
import { useHistory } from 'react-router-dom';
import { getAuthenticatedUser, isAuthRedirecting, oidcManager } from '@/auth/oidc';
import { hasEnterpriseCapability } from '@/auth/businessCapabilities';
import { AuthUser, CreateUser, UserFailure, createUser, listUsers, setUserActive } from './api';
const empty: CreateUser = { username: '', password: '', role: 'USER' };
export default function AuthUsersPage() {
  const router = useHistory();
  const alive = useRef(true); const read = useRef(0); const writing = useRef(false);
  const [rows, setRows] = useState<AuthUser[]>([]); const [query, setQuery] = useState(''); const [status, setStatus] = useState('');
  const [loading, setLoading] = useState(false); const [saving, setSaving] = useState(false); const [canWrite, setCanWrite] = useState(false);
  const [visible, setVisible] = useState(false); const [form, setForm] = useState<CreateUser>(empty);
  const [error, setError] = useState<UserFailure | null>(null); const [validation, setValidation] = useState('');
  const dirty = visible && Boolean(form.username || form.password || form.role !== 'USER');
  useEffect(() => {
    // /api/users is a global Sample resource bound to ent-acme by the backend interceptor.
    const update = (user: User | null) => { if (alive.current) setCanWrite(hasEnterpriseCapability(user, 'ent-acme', 'enterprise.members.write')); };
    getAuthenticatedUser().then(update).catch(() => update(null));
    const unload = () => update(null);
    oidcManager.events.addUserLoaded(update); oidcManager.events.addUserUnloaded(unload);
    return () => { alive.current = false; read.current++; oidcManager.events.removeUserLoaded(update); oidcManager.events.removeUserUnloaded(unload); };
  }, []);
  const load = useCallback(() => {
    const id = ++read.current; setLoading(true); setError(null);
    return listUsers().then((data) => { if (alive.current && id === read.current) setRows(data); })
      .catch((failure) => { if (alive.current && id === read.current) { setRows([]); setError(failure); } })
      .finally(() => { if (alive.current && id === read.current) setLoading(false); });
  }, []);
  useEffect(() => { load(); }, [load]);
  useEffect(() => {
    if (!dirty && !saving) return undefined;
    const unblock = router.block(() => isAuthRedirecting() ? undefined : saving ? false : '用户有未保存修改，确认离开？');
    const guard = (event: BeforeUnloadEvent) => { if (!isAuthRedirecting()) { event.preventDefault(); event.returnValue = ''; } };
    window.addEventListener('beforeunload', guard);
    return () => { unblock(); window.removeEventListener('beforeunload', guard); };
  }, [dirty, saving, router]);
  function discard(action: () => void) { if (writing.current) return; if (!dirty) action(); else Modal.confirm({ title: '放弃未保存的用户？', okText: '放弃修改', cancelText: '继续编辑', onOk: action }); }
  function close() { discard(() => { setForm(empty); setVisible(false); setValidation(''); }); }
  function write(call: () => Promise<AuthUser>, done: () => void | Promise<void>) {
    if (writing.current || !canWrite) return;
    writing.current = true; setSaving(true); setError(null); setValidation('');
    call().then(() => { if (alive.current) return done(); }).catch((failure: UserFailure) => { if (alive.current) { setError(failure); if ([401, 403].includes(failure.status)) setCanWrite(false); } })
      .finally(() => { writing.current = false; if (alive.current) setSaving(false); });
  }
  function create() {
    if (!form.username.trim() || form.username.length > 128) { setValidation('请输入不超过 128 个字符的用户名'); return; }
    if (form.password.length < 12 || form.password.length > 200) { setValidation('密码需要 12–200 个字符'); return; }
    write(() => createUser(form), () => { setForm(empty); setVisible(false); return load(); });
  }
  const failure = error && `${error.status === 403 ? '无权管理认证用户' : error.status === 401 ? '登录状态已失效' : [409, 412].includes(error.status) ? '用户已存在或数据发生冲突，请保留表单并核对后重试' : error.status === 400 ? '用户信息不符合要求，请检查用户名、角色与密码长度' : '用户操作失败，请稍后重试'}${error.requestId ? ` (${error.requestId})` : ''}`;
  return <Card title="认证用户管理" extra={<Space><Button disabled={saving} loading={loading} onClick={() => discard(() => { setForm(empty); setVisible(false); load(); })}>刷新</Button>{canWrite && <Button type="primary" disabled={saving || loading} onClick={() => { setForm(empty); setVisible(true); setError(null); setValidation(''); }}>新建用户</Button>}</Space>}>
    {failure && <Alert type="error" content={failure} style={{ marginBottom: 16 }} />}
    <Space style={{ marginBottom: 16 }}><Input aria-label="搜索用户名" placeholder="搜索用户名" value={query} onChange={setQuery} allowClear /><Select aria-label="登录状态筛选" value={status} onChange={setStatus} options={[{ value: '', label: '全部状态' }, { value: 'active', label: '启用' }, { value: 'inactive', label: '停用' }]} style={{ width: 140 }} /></Space>
    <Table<AuthUser> rowKey="username" loading={loading} data={rows.filter((row) => row.username.toLowerCase().includes(query.toLowerCase()) && (!status || row.active === (status === 'active')))} columns={[
      { title: '用户名', dataIndex: 'username' }, { title: '身份角色', dataIndex: 'role' }, { title: '登录状态', render: (_, row) => <Tag color={row.active ? 'green' : 'gray'}>{row.active ? '启用' : '停用'}</Tag> },
      { title: '操作', render: (_, row) => canWrite ? <Button disabled={saving || visible} onClick={() => write(() => setUserActive(row.username, !row.active), load)}>{row.active ? '停用' : '恢复'}</Button> : '只读' },
    ]} />
    {visible && <Drawer visible title="新建认证用户" okText="创建" cancelText="取消" onOk={create} onCancel={close} okButtonProps={{ loading: saving, disabled: saving || !canWrite }} cancelButtonProps={{ disabled: saving }} width={480}>
      {failure && <Alert type="error" content={failure} style={{ marginBottom: 16 }} />}{validation && <Alert type="error" content={validation} style={{ marginBottom: 16 }} />}
      <Form layout="vertical"><Form.Item label="用户名"><Input aria-label="用户名" value={form.username} disabled={saving} maxLength={128} autoComplete="off" onChange={(username) => setForm({ ...form, username })} /></Form.Item>
        <Form.Item label="初始密码" extra="12–200 个字符；创建后不再显示。"><Input.Password aria-label="初始密码" value={form.password} disabled={saving} maxLength={200} autoComplete="new-password" onChange={(password) => setForm({ ...form, password })} /></Form.Item>
        <Form.Item label="身份角色"><Select aria-label="身份角色" value={form.role} disabled={saving} options={['USER', 'ADMIN']} onChange={(role) => setForm({ ...form, role })} /></Form.Item>
      </Form>
    </Drawer>}
  </Card>;
}
