import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useHistory } from 'react-router-dom';
import { Alert, Button, Card, Drawer, Form, Input, InputNumber, Modal, Select, Space, Table, Tag, Tree } from '@arco-design/web-react';
import { isAuthRedirecting } from '@/auth/oidc';
import useActiveEnterprise from '@/hooks/useActiveEnterprise';
import useOrganizationOperationAccess from './useOperationAccess';
import { AssignmentDraft, OrganizationApiError, OrganizationMember, OrganizationTree, OrganizationUnit, UnitDraft, MemberPage, TenantMember, assignOrganizations, changeMemberStatus, createOrganization, deleteOrganization, getOrganizationContext, getOrganizationDetail, getOrganizationMembers, getOrganizationTree, getTenantMember, listTenantMembers, selectOrganizationContext, updateOrganization } from './api';

const empty: UnitDraft = { parentId: null, unitType: 'DEPARTMENT', name: '', code: '', leaderMemberId: '', description: '', sortOrder: 0, status: 'ACTIVE' };
const emptyAssignment: AssignmentDraft = { memberIds: [], organizationIds: [], primaryOrganizationId: null };
function flatten(items: OrganizationUnit[]): OrganizationUnit[] { return items.flatMap((item) => [item, ...flatten(item.children || [])]); }
function nodes(items: OrganizationUnit[]): { key: string; title: string; children: ReturnType<typeof nodes> }[] { return items.map((item) => ({ key: item.id, title: `${item.name} (${item.memberCount})`, children: nodes(item.children || []) })); }
export default function OrganizationPage() {
  const tenant = useActiveEnterprise();
  return <><Select aria-label="活动租户" placeholder="选择活动租户" value={tenant.enterpriseId || undefined} options={tenant.options} onChange={tenant.change} disabled={tenant.busy} style={{ width: 240, marginBottom: 16 }} />
    {tenant.enterpriseId ? <OrganizationContent key={tenant.enterpriseId} enterprise={tenant.enterpriseId} onDirty={tenant.setDirty} onBusy={tenant.setBusy} /> : tenant.ready && <Alert type="info" content="请选择一个活动成员租户；没有可用租户时不会读取目录。" />}</>;
}
function OrganizationContent({ enterprise, onDirty, onBusy }: { enterprise: string; onDirty: (value: boolean) => void; onBusy: (value: boolean) => void }) {
  const router = useHistory(); const access = useOrganizationOperationAccess(enterprise);
  const alive = useRef(true); const read = useRef(0); const memberRead = useRef(0); const organizationRead = useRef(0); const detailRead = useRef(0); const writing = useRef(false);
  const baseline = useRef(''); const assignmentBaseline = useRef(''); const etag = useRef(''); const assignmentRevision = useRef('');
  const operation = useRef<{ payload: string; key: string } | null>(null);
  const [tree, setTree] = useState<OrganizationTree | null>(null); const [selected, setSelected] = useState(''); const [orgMembers, setOrgMembers] = useState<OrganizationMember[]>([]);
  const [members, setMembers] = useState<MemberPage | null>(null); const [query, setQuery] = useState({ page: 0, size: 20, q: '', status: '' }); const [selectedMembers, setSelectedMembers] = useState<string[]>([]);
  const [context, setContext] = useState<{ id: string | null; name: string } | null>(null);
  const [error, setError] = useState<OrganizationApiError | null>(null); const [loading, setLoading] = useState(false); const [saving, setSaving] = useState(false);
  const [editor, setEditor] = useState<OrganizationUnit | null | false>(false); const [draft, setDraft] = useState<UnitDraft>(empty);
  const [assigning, setAssigning] = useState(false); const [assignment, setAssignment] = useState<AssignmentDraft>(emptyAssignment);
  const dirty = editor !== false && JSON.stringify(draft) !== baseline.current || assigning && JSON.stringify(assignment) !== assignmentBaseline.current;
  useEffect(() => onDirty(dirty), [dirty, onDirty]); useEffect(() => onBusy(saving), [saving, onBusy]);
  useEffect(() => () => { alive.current = false; read.current++; memberRead.current++; organizationRead.current++; detailRead.current++; }, []);
  useEffect(() => {
    if (!dirty && !saving) return undefined;
    const unblock = router.block(() => isAuthRedirecting() ? undefined : saving ? false : '组织或成员关系有未保存修改，确认离开？');
    const guard = (event: BeforeUnloadEvent) => { if (!isAuthRedirecting()) { event.preventDefault(); event.returnValue = ''; } };
    window.addEventListener('beforeunload', guard); return () => { unblock(); window.removeEventListener('beforeunload', guard); };
  }, [dirty, saving, router]);
  const loadTree = useCallback(() => { const id = ++read.current; setLoading(true); setError(null); return Promise.all([getOrganizationTree(enterprise), getOrganizationContext(enterprise)]).then(([data, current]) => { if (alive.current && id === read.current) { setTree(data); setContext({ id: current.organizationId, name: flatten(data.items).find((item) => item.id === current.organizationId)?.name || (current.organizationId ? current.organizationId : '租户范围') }); } }).catch((failure) => { if (alive.current && id === read.current) { organizationRead.current++; detailRead.current++; setTree(null); setSelected(''); setOrgMembers([]); setContext(null); setEditor(false); setAssigning(false); setDraft(empty); setAssignment(emptyAssignment); setError(failure); } }).finally(() => { if (alive.current && id === read.current) setLoading(false); }); }, [enterprise]);
  const loadMembers = useCallback(() => { const id = ++memberRead.current; if (!access.memberRead) { setMembers(null); return Promise.resolve(); } return listTenantMembers(enterprise, { ...query, q: query.q || undefined, status: query.status || undefined }).then((data) => { if (alive.current && id === memberRead.current) setMembers(data); }).catch((failure) => { if (alive.current && id === memberRead.current) { setMembers(null); setSelectedMembers([]); setError(failure); } }); }, [enterprise, query, access.memberRead]);
  const loadOrgMembers = (id: string) => { const request = ++organizationRead.current; setOrgMembers([]); if (!id) return Promise.resolve(); return getOrganizationMembers(enterprise, id).then((data) => { if (alive.current && request === organizationRead.current) setOrgMembers(data.items); }).catch((failure) => { if (alive.current && request === organizationRead.current) setError(failure); }); };
  useEffect(() => { loadTree(); }, [loadTree]); useEffect(() => { loadMembers(); return () => { memberRead.current++; }; }, [loadMembers]);
  const all = flatten(tree?.items || []); const unit = all.find((item) => item.id === selected);
  function local(message: string) { setError({ status: 0, code: 'INVALID_INPUT', message, requestId: '' }); }
  function keyFor(payload: unknown) { const text = JSON.stringify(payload); if (operation.current?.payload !== text) operation.current = { payload: text, key: `org-${Date.now()}-${Math.random().toString(36).slice(2)}` }; return operation.current.key; }
  function discard(action: () => void) { if (writing.current) return; if (!dirty) action(); else Modal.confirm({ title: '放弃未保存的组织或成员关系？', okText: '放弃修改', cancelText: '继续编辑', onOk: () => { if (alive.current && !writing.current) action(); } }); }
  function close() { discard(() => { setEditor(false); setAssigning(false); setDraft(empty); setAssignment(emptyAssignment); }); }
  function write<T>(call: () => Promise<T>, done: (result: T) => void | Promise<unknown>) { if (!alive.current || writing.current) return; writing.current = true; setSaving(true); setError(null); call().then((result) => { if (alive.current) return done(result); }).catch((failure) => { if (alive.current) setError(failure); }).finally(() => { writing.current = false; if (alive.current) setSaving(false); }); }
  function reload() { return Promise.all([loadTree(), loadMembers(), loadOrgMembers(selected)]); }
  function create() { if (!access.orgCreate) return; detailRead.current++; const next = { ...empty, parentId: selected || null }; baseline.current = JSON.stringify(next); setDraft(next); setEditor(null); setError(null); operation.current = null; }
  function edit() {
    if (!access.orgUpdate) return;
    if (!unit) return; const request = ++detailRead.current; const id = unit.id; setError(null);
    getOrganizationDetail(enterprise, id).then((data) => { if (!alive.current || request !== detailRead.current) return; if (!data.etag) return local('组织版本不可用，请刷新后重试'); const { parentId, unitType, name, code, leaderMemberId, description, sortOrder, status } = data.unit; const next = { parentId, unitType, name, code, leaderMemberId: leaderMemberId || '', description: description || '', sortOrder, status }; etag.current = data.etag; baseline.current = JSON.stringify(next); setDraft(next); setEditor(data.unit); operation.current = null; }).catch((failure) => { if (alive.current && request === detailRead.current) setError(failure); });
  }
  function saveUnit() {
    if (editor === false || !(editor ? access.orgUpdate : access.orgCreate)) return;
    if (!draft.name.trim() || !draft.code.trim()) return local('组织名称和编码不能为空');
    if (!Number.isSafeInteger(draft.sortOrder) || draft.sortOrder < 0) return local('排序必须是非负整数');
    if (editor && editor.parentId && !draft.parentId) return local('请选择一个父组织');
    const body = { ...draft, name: draft.name.trim(), code: draft.code.trim() };
    const key = keyFor([enterprise, editor?.id, etag.current, body]);
    write(() => editor ? updateOrganization(enterprise, editor.id, body, etag.current, key) : createOrganization(enterprise, body, key), () => { setEditor(false); return reload(); });
  }
  function remove() {
    if (!unit || !access.orgDelete) return; const id = unit.id; const request = ++detailRead.current;
    getOrganizationDetail(enterprise, id).then((data) => { if (!alive.current || request !== detailRead.current) return; if (!data.etag) return local('组织版本不可用，请刷新后重试'); Modal.confirm({ title: `删除组织 ${data.unit.name}？`, okText: '删除', cancelText: '取消', onOk: () => write(() => deleteOrganization(enterprise, id, data.etag, keyFor(['delete', enterprise, id, data.etag])), () => { setSelected(''); setOrgMembers([]); return Promise.all([loadTree(), loadMembers()]); }) }); }).catch((failure) => { if (alive.current && request === detailRead.current) setError(failure); });
  }
  function openAssignments(ids: string[]) { if (!access.orgAssign) return; const next = { memberIds: ids, organizationIds: [], primaryOrganizationId: null }; assignmentBaseline.current = JSON.stringify(next); assignmentRevision.current = tree?.revision || ''; setAssignment(next); setAssigning(true); setError(null); operation.current = null; }
  function saveAssignments() {
    if (!access.orgAssign || !tree) return;
    if (!assignment.memberIds.length || assignment.organizationIds.length && !assignment.organizationIds.includes(assignment.primaryOrganizationId)) return local('请选择成员，并将一个已选组织设为主组织');
    if (!assignmentRevision.current) return local('组织版本不可用，请刷新后重试');
    write(() => assignOrganizations(enterprise, assignment, assignmentRevision.current, keyFor([enterprise, assignment, assignmentRevision.current])), () => { setAssigning(false); setSelectedMembers([]); return reload(); });
  }
  function memberStatus(member: TenantMember, action: 'enable' | 'disable' | 'leave') { if (!(action === 'enable' ? access.memberEnable : action === 'disable' ? access.memberDisable : access.memberLeave)) return; write(async () => { const detail = await getTenantMember(enterprise, member.memberId); if (!alive.current) return; return changeMemberStatus(enterprise, member.memberId, action, detail.revision, keyFor([enterprise, member.memberId, action, detail.revision])); }, reload); }
  function setPersonalContext(id: string | null) { write(() => selectOrganizationContext(enterprise, id), () => setContext({ id, name: all.find((item) => item.id === id)?.name || '租户范围' })); }
  const failure = error && `${error.status === 403 ? '无权操作此租户的组织或成员' : [409, 412].includes(error.status) ? '组织或成员已变更，请保留表单并核对后重试' : error.message || '目录操作失败'}${error.requestId ? ` (${error.requestId})` : ''}`;
  const descendants = editor ? flatten(all.find((item) => item.id === editor.id)?.children || []).map((item) => item.id) : [];
  const parentOptions = [{ value: '', label: '根级', disabled: Boolean(editor && editor.parentId) }, ...all.map((item) => ({ value: item.id, label: item.name, disabled: item.status !== 'ACTIVE' || editor && (item.id === editor.id || descendants.includes(item.id)) }))];
  return <Card title="组织与成员" extra={<Button disabled={saving} loading={loading} onClick={() => discard(() => { setEditor(false); setAssigning(false); reload(); })}>刷新</Button>}>
    {failure && <Alert type="error" content={failure} style={{ marginBottom: 16 }} />}
    <Space style={{ marginBottom: 16 }}>{access.orgCreate && <Button disabled={saving} onClick={create}>新建组织</Button>}{access.orgUpdate && unit && <Button disabled={saving} onClick={edit}>编辑组织</Button>}{access.orgDelete && unit && <Button disabled={saving} status="danger" onClick={remove}>删除组织</Button>}<Button disabled={saving || loading || !unit || unit.status !== 'ACTIVE'} onClick={() => setPersonalContext(selected)}>设为当前组织</Button><Button disabled={saving || loading} onClick={() => setPersonalContext(null)}>清除当前组织</Button></Space>
    <div>{context ? `本页已确认上下文：${context.name}` : '尚未在本页设置组织上下文'}</div>
    <Tree key={tree?.revision} blockNode defaultExpandedKeys={all.map((item) => item.id)} treeData={nodes(tree?.items || [])} selectedKeys={selected ? [selected] : []} onSelect={(keys) => discard(() => { detailRead.current++; setEditor(false); setAssigning(false); const id = String(keys[0] || ''); setSelected(id); loadOrgMembers(id); })} />
    <Card title={unit ? `${unit.name} · 组织成员` : '选择组织查看成员'} style={{ marginTop: 16 }}><Table<OrganizationMember> rowKey="memberId" pagination={false} data={orgMembers} columns={[{ title: '成员', dataIndex: 'displayName' }, { title: '邮箱', dataIndex: 'email' }, { title: '状态', dataIndex: 'status' }, { title: '主组织', render: (_, member) => member.primary ? '是' : '否' }]} /></Card>
    {access.memberRead && <Card title="租户成员" style={{ marginTop: 16 }}>
      <Space style={{ marginBottom: 16 }}><Input aria-label="搜索成员" placeholder="搜索成员" value={query.q} disabled={saving} onChange={(q) => { setSelectedMembers([]); setQuery({ ...query, q, page: 0 }); }} /><Select aria-label="成员状态筛选" value={query.status} disabled={saving} options={[{ value: '', label: '全部状态' }, { value: 'ACTIVE', label: 'ACTIVE' }, { value: 'DISABLED', label: 'DISABLED' }, { value: 'RESIGNED', label: 'RESIGNED' }]} onChange={(status) => { setSelectedMembers([]); setQuery({ ...query, status, page: 0 }); }} style={{ width: 140 }} />{access.orgAssign && <Button disabled={saving || !selectedMembers.length || !tree} onClick={() => openAssignments(selectedMembers)}>批量分配组织</Button>}</Space>
      <Table<TenantMember> rowKey="memberId" data={members?.items || []} pagination={{ current: (members?.page || 0) + 1, pageSize: members?.size || query.size, total: members?.total || 0, onChange: (page, size) => { if (!saving) { setSelectedMembers([]); setQuery({ ...query, page: page - 1, size }); } } }} rowSelection={access.orgAssign ? { selectedRowKeys: selectedMembers, onChange: (keys) => setSelectedMembers(keys.map(String)), checkboxProps: (member) => ({ disabled: saving || member.status !== 'ACTIVE' }) } : undefined} columns={[
        { title: '成员 ID', dataIndex: 'memberId' }, { title: '姓名', dataIndex: 'displayName' }, { title: '邮箱', dataIndex: 'email' }, { title: '状态', dataIndex: 'status', render: (status) => <Tag>{status}</Tag> },
        { title: '操作', render: (_, member) => <Space>{access.orgAssign && <Button disabled={saving || !tree || member.status !== 'ACTIVE'} onClick={() => openAssignments([member.memberId])}>分配组织</Button>}{(member.status === 'ACTIVE' ? access.memberDisable : access.memberEnable) && <Button disabled={saving || member.status === 'RESIGNED'} onClick={() => memberStatus(member, member.status === 'ACTIVE' ? 'disable' : 'enable')}>{member.status === 'ACTIVE' ? '停用成员' : '恢复成员'}</Button>}{access.memberLeave && <Button disabled={saving || member.status === 'RESIGNED'} onClick={() => memberStatus(member, 'leave')}>成员离职</Button>}</Space> },
      ]} />
    </Card>}
    {editor !== false && <Drawer visible title={editor ? '编辑组织' : '新建组织'} width={520} onCancel={close} onOk={saveUnit} okText="保存组织" cancelText="取消" okButtonProps={{ disabled: saving || !(editor ? access.orgUpdate : access.orgCreate), loading: saving }} cancelButtonProps={{ disabled: saving }}>
      {failure && <Alert type="error" content={failure} />}
      <Form layout="vertical"><Form.Item label="组织名称"><Input aria-label="组织名称" value={draft.name} maxLength={200} disabled={saving} onChange={(name) => setDraft({ ...draft, name })} /></Form.Item><Form.Item label="组织编码"><Input aria-label="组织编码" value={draft.code} maxLength={64} disabled={saving} onChange={(code) => setDraft({ ...draft, code })} /></Form.Item>
        <Form.Item label="父组织"><Select aria-label="父组织" value={draft.parentId || ''} options={parentOptions} disabled={saving} onChange={(parentId) => setDraft({ ...draft, parentId: parentId || null })} /></Form.Item><Form.Item label="组织类型"><Select aria-label="组织类型" value={draft.unitType} options={['COMPANY', 'DEPARTMENT', 'TEAM']} disabled={saving} onChange={(unitType) => setDraft({ ...draft, unitType })} /></Form.Item><Form.Item label="组织状态"><Select aria-label="组织状态" value={draft.status} options={['ACTIVE', 'DISABLED']} disabled={saving} onChange={(status) => setDraft({ ...draft, status })} /></Form.Item>
        <Form.Item label="负责人 ID"><Input aria-label="负责人 ID" value={draft.leaderMemberId} maxLength={64} disabled={saving} onChange={(leaderMemberId) => setDraft({ ...draft, leaderMemberId })} /></Form.Item><Form.Item label="组织排序"><InputNumber aria-label="组织排序" value={draft.sortOrder} precision={0} min={0} disabled={saving} onChange={(sortOrder) => setDraft({ ...draft, sortOrder })} /></Form.Item><Form.Item label="说明"><Input.TextArea aria-label="组织说明" value={draft.description} maxLength={500} disabled={saving} onChange={(description) => setDraft({ ...draft, description })} /></Form.Item>
      </Form>
    </Drawer>}
    {assigning && <Drawer visible title="成员组织关系" width={520} onCancel={close} onOk={saveAssignments} okText="保存关系" cancelText="取消" okButtonProps={{ disabled: saving || !access.orgAssign, loading: saving }} cancelButtonProps={{ disabled: saving }}>
      {failure && <Alert type="error" content={failure} />}<Alert type="info" content="选择要保留的全部组织；保存将替换这些成员原有的组织关系。清空组织将移除所有组织关系。" />
      <Form layout="vertical"><Form.Item label="成员"><Select aria-label="分配成员" mode="multiple" value={assignment.memberIds} disabled={saving} options={Array.from(new Set([...(members?.items || []).filter((member) => member.status === 'ACTIVE').map((member) => member.memberId), ...assignment.memberIds])).map((id) => ({ value: id, label: members?.items.find((member) => member.memberId === id)?.displayName || id }))} onChange={(memberIds) => setAssignment({ ...assignment, memberIds })} /></Form.Item>
        <Form.Item label="组织"><Select aria-label="分配组织" mode="multiple" value={assignment.organizationIds} disabled={saving} options={all.filter((item) => item.status === 'ACTIVE').map((item) => ({ value: item.id, label: item.name }))} onChange={(organizationIds) => setAssignment({ ...assignment, organizationIds, primaryOrganizationId: organizationIds.includes(assignment.primaryOrganizationId) ? assignment.primaryOrganizationId : null })} /></Form.Item>
        <Form.Item label="主组织"><Select aria-label="主组织" value={assignment.primaryOrganizationId || undefined} disabled={saving || !assignment.organizationIds.length} options={all.filter((item) => assignment.organizationIds.includes(item.id)).map((item) => ({ value: item.id, label: item.name }))} onChange={(primaryOrganizationId) => setAssignment({ ...assignment, primaryOrganizationId })} /></Form.Item></Form>
    </Drawer>}
  </Card>;
}
