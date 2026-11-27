import DataInterfacePicker from './components/DataInterfacePicker';
import { useInterfaceOperations } from '@/auth/useInterfaceOperations';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  TableColumnProps,
  Tabs,
} from '@arco-design/web-react';
import { Prompt } from 'react-router-dom';
import treeStyles from './components/permission-group-tree.module.less';
import PermissionGroupTree from './components/PermissionGroupTree';
import MenuPermissionBindings from './components/MenuPermissionBindings';
import InterfaceAssociationsEditor, {
  InterfaceDraft,
} from './components/InterfaceAssociationsEditor';
import { getOrganizationTree, OrganizationUnit } from '../organization/api';
import useActiveEnterprise from '@/hooks/useActiveEnterprise';
import {
  call,
  Failure,
  Group,
  Page,
  Permission,
  DataScopeDetail,
  PermissionDomain,
} from './api';

const enumLabels: Record<string, string> = {
  INTERFACE: '接口权限',
  MENU: '菜单权限',
  DATA: '数据权限',
  AUTHORIZED: '授权访问',
  AUTHENTICATED: '登录访问',
  PUBLIC: '公开访问',
  ACTIVE: '启用',
  DISABLED: '停用',
};
const filterLabels: Record<string, string> = {
  status: '状态',
  accessMode: '访问方式',
};
const enumOptions = (values: string[]) =>
  values.map((value) => ({ label: enumLabels[value], value }));

const permissionTypes = ['INTERFACE', 'MENU', 'DATA'] as const;
type PermissionType = typeof permissionTypes[number];

function PermissionTable({
  base,
  type,
  query,
  columns,
  onAccessError,
}: {
  base: string;
  type: PermissionType;
  query: string;
  columns: TableColumnProps<Permission>[];
  onAccessError: (issue: Failure) => void;
}) {
  const [page, setPage] = useState(1);
  const [result, setResult] = useState<Page<Permission>>();
  const [issue, setIssue] = useState<Failure>();
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    let current = true;
    setLoading(true);
    setIssue(undefined);
    const params = new URLSearchParams(query);
    params.set('type', type);
    params.set('page', String(page));
    params.set('size', '20');
    call<Page<Permission>>(base, `/permissions?${params}`)
      .then((value) => {
        if (current) setResult(value);
      })
      .catch((error: Failure) => {
        if (!current) return;
        setResult(undefined);
        if ([401, 403].includes(error.status)) onAccessError(error);
        else setIssue(error);
      })
      .finally(() => {
        if (current) setLoading(false);
      });
    return () => {
      current = false;
    };
  }, [base, type, query, page, onAccessError]);
  return (
    <>
      {issue && (
        <Alert
          type="error"
          content={`${issue.status || '网络错误'}：${issue.message} ${
            issue.requestId || ''
          }`}
        />
      )}
      <Table
        rowKey="permissionId"
        scroll={{ x: 680 }}
        loading={loading}
        columns={columns}
        data={result?.items || []}
        pagination={{
          current: page,
          pageSize: 20,
          total: result?.total || 0,
          hideOnSinglePage: true,
          onChange: setPage,
        }}
      />
    </>
  );
}

export default function PermissionsPage() {
  const { setDirty, setBusy } = useActiveEnterprise();
  return (
    <Content
      domain={{ kind: 'PLATFORM', id: 'platform' }}
      base="/api/v1/platform"
      dirty={setDirty}
      busy={setBusy}
    />
  );
}
function Content({
  domain,
  base,
  dirty,
  busy,
}: {
  domain: PermissionDomain;
  base: string;
  dirty: (v: boolean) => void;
  busy: (v: boolean) => void;
}) {
  const operations = useInterfaceOperations(
    domain.kind === 'PLATFORM'
      ? { kind: 'PLATFORM' }
      : { kind: 'ENTERPRISE', id: domain.id },
    [
      'groups.create',
      'groups.update',
      'groups.delete',
      'permissions.create',
      'permissions.update',
      'permissions.delete',
      'permissions.interfaces.write',
      'permissions.menus.write',
    ]
  );
  const allowed = (action: string) => operations[action] === true;
  const metadataAction = (group: boolean, exists: boolean, remove = false) =>
    `${group ? 'groups' : 'permissions'}.${
      remove ? 'delete' : exists ? 'update' : 'create'
    }`;
  const canOpen = (group: boolean, row?: Group | Permission) =>
    allowed(metadataAction(group, !!row)) ||
    (!group &&
      !!row &&
      allowed(
        (row as Permission).type === 'MENU'
          ? 'permissions.menus.write'
          : 'permissions.interfaces.write'
      ));
  const [groups, setGroups] = useState<Group[]>([]);
  const [revision, setRevision] = useState(0);
  const [accessDenied, setAccessDenied] = useState(false);
  const [filter, setFilter] = useState<Record<string, string>>({});
  const [selected, setSelected] = useState<string>();
  const [error, setError] = useState<Failure>();
  const [groupError, setGroupError] = useState<Failure>();
  const [saving, setSaving] = useState(false);
  const [editor, setEditor] =
    useState<{ group: boolean; row?: Group | Permission }>();
  const [changed, setChanged] = useState(false);
  const [bindingDirty, setBindingDirty] = useState(false);
  const [bindingBusy, setBindingBusy] = useState(false);
  const [interfaceDraft, setInterfaceDraft] = useState<InterfaceDraft>();
  const hasUnsaved = changed || bindingDirty || !!interfaceDraft;
  const requestBusy = saving || bindingBusy;
  const associationContext = useRef<string>();
  const bindingKey =
    editor?.row && !editor.group
      ? (editor.row as Permission).permissionId
      : undefined;
  const onBindingDirty = useCallback(
    (value: boolean) => {
      if (bindingKey && associationContext.current === bindingKey)
        setBindingDirty(value);
    },
    [bindingKey]
  );
  const onBindingBusy = useCallback(
    (value: boolean) => {
      if (bindingKey && associationContext.current === bindingKey)
        setBindingBusy(value);
    },
    [bindingKey]
  );
  const clearBinding = () => {
    associationContext.current = undefined;
    setInterfaceDraft(undefined);
    setBindingDirty(false);
    setBindingBusy(false);
  };
  const [form] = Form.useForm();
  const [activeType, setActiveType] = useState('INTERFACE');
  const [dataType, setDataType] = useState('INTERFACE');
  const [departments, setDepartments] = useState<
    { label: string; value: string }[]
  >([]);
  const [departmentsError, setDepartmentsError] = useState('');
  useEffect(() => {
    let active = true;
    setDepartments([]);
    setDepartmentsError('');
    if (domain.kind === 'ENTERPRISE' && editor && !editor.group)
      getOrganizationTree(domain.id)
        .then((tree) => {
          const items: { label: string; value: string }[] = [];
          const visit = (nodes: OrganizationUnit[], parent: string) =>
            nodes.forEach((node) => {
              const label = parent ? `${parent} / ${node.name}` : node.name;
              if (node.status === 'ACTIVE')
                items.push({ label, value: node.id });
              visit(node.children || [], label);
            });
          visit(tree.items, '');
          if (active) setDepartments(items);
        })
        .catch((e) => {
          if (active) setDepartmentsError(e.message || '部门目录读取失败');
        });
    if (
      domain.kind === 'PLATFORM' &&
      editor &&
      !editor.group &&
      dataType === 'DATA'
    )
      call<{ departments: { id: string; name: string }[] }>(
        '/api/v1/platform',
        '/platform-departments'
      )
        .then((result) => {
          if (active)
            setDepartments(
              result.departments.map((n) => ({ label: n.name, value: n.id }))
            );
        })
        .catch((e) => {
          if (active) setDepartmentsError(e.message || '平台部门读取失败');
        });
    return () => {
      active = false;
    };
  }, [domain.kind, domain.id, editor, dataType]);
  const groupGeneration = useRef(0);
  const [loadingGroups, setLoadingGroups] = useState(true);
  const alive = useRef(true);
  const pending = useRef<{ fingerprint: string; key: string }>();
  useEffect(
    () => () => {
      alive.current = false;
      groupGeneration.current++;
      dirty(false);
      busy(false);
    },
    [dirty, busy]
  );
  useEffect(() => {
    dirty(hasUnsaved);
    const before = (e: BeforeUnloadEvent) => {
      if (hasUnsaved || requestBusy) {
        e.preventDefault();
        e.returnValue = '';
      }
    };
    window.addEventListener('beforeunload', before);
    return () => window.removeEventListener('beforeunload', before);
  }, [hasUnsaved, requestBusy, dirty]);
  useEffect(() => {
    busy(requestBusy);
  }, [requestBusy, busy]);
  const failure = useCallback((e: Failure) => {
    if (!alive.current) return;
    setError(e);
    if ([401, 403].includes(e.status)) {
      groupGeneration.current++;
      setLoadingGroups(false);
      setGroups([]);
      setSelected(undefined);
      setAccessDenied(true);
      setEditor(undefined);
      clearBinding();
      setChanged(false);
    }
  }, []);
  const loadGroups = useCallback(async () => {
    const ticket = ++groupGeneration.current;
    setLoadingGroups(true);
    setGroupError(undefined);
    try {
      const all: Group[] = [];
      for (let page = 1; ; page++) {
        const result = await call<Page<Group>>(
          base,
          `/permission-groups?page=${page}&size=200`
        );
        if (!alive.current || ticket !== groupGeneration.current) return;
        all.push(...result.items);
        if (all.length >= result.total) break;
        if (!result.items.length)
          throw new Error('权限组目录已变化，请刷新重试');
      }
      setGroups(all);
      setSelected((current) =>
        all.some((group) => group.groupId === current) ? current : undefined
      );
    } catch (e) {
      if (ticket === groupGeneration.current) {
        const issue = e as Failure;
        setGroupError(issue);
        if ([401, 403].includes(issue.status)) failure(issue);
      }
    } finally {
      if (alive.current && ticket === groupGeneration.current)
        setLoadingGroups(false);
    }
  }, [base, failure]);
  useEffect(() => {
    loadGroups();
  }, [loadGroups]);
  const query = new URLSearchParams({
    ...filter,
    ...(selected ? { groupId: selected, includeDescendants: 'true' } : {}),
  }).toString();
  const load = useCallback(() => {
    setError(undefined);
    setAccessDenied(false);
    setRevision((value) => value + 1);
  }, []);
  const open = (group: boolean, row?: Group | Permission) => {
    if (
      requestBusy ||
      !canOpen(group, row) ||
      (editor && hasUnsaved && !window.confirm('放弃未保存修改？'))
    )
      return;
    setInterfaceDraft(undefined);
    setBindingDirty(false);
    setBindingBusy(false);
    associationContext.current =
      !group && row ? (row as Permission).permissionId : undefined;
    pending.current = undefined;
    setEditor({ group, row });
    form.resetFields();
    setDataType(row && !group ? (row as Permission).type : activeType);
    form.setFieldsValue(
      row ||
        (group
          ? { parentGroupId: selected, sortOrder: 0 }
          : {
              groupId: selected,
              type: activeType,
              accessMode: 'AUTHORIZED',
              status: 'ACTIVE',
            })
    );
    // Arco emits onValuesChange during reset/set; initialization is not a user edit.
    setChanged(false);
  };
  const save = async (remove = false, target = editor) => {
    if (!target || requestBusy || bindingDirty) return;
    const editInterface =
      !remove &&
      !target.group &&
      (target.row as Permission)?.type === 'INTERFACE';
    const draft = editInterface ? interfaceDraft : undefined;
    const saveMetadata = !editInterface || changed;
    if (
      (saveMetadata &&
        !allowed(metadataAction(target.group, !!target.row, remove))) ||
      (draft && !allowed('permissions.interfaces.write'))
    )
      return;
    let values: Record<string, unknown> = {};
    if (!remove && saveMetadata) {
      try {
        await form.validate();
        values = form.getFieldsValue();
      } catch {
        return;
      }
    }
    const id =
      target.row &&
      (target.group
        ? (target.row as Group).groupId
        : (target.row as Permission).permissionId);
    if (!target.group && target.row) {
      delete values.code;
      delete values.type;
    }
    if (!target.group && dataType !== 'DATA') delete values.dataScope;
    if (dataType === 'DATA' && values.dataScope) {
      const detail = values.dataScope as DataScopeDetail;
      if (
        !['SPECIFIED_DEPARTMENTS', 'PLATFORM_SPECIFIED_SUBTREES'].includes(
          detail.scopePreset
        )
      )
        delete detail.departmentIds;
    }
    if (target.group) values.parentGroupId = values.parentGroupId || null;
    else if (
      typeof values.description === 'string' &&
      !values.description.trim()
    )
      values.description = null;
    const path = `/${target.group ? 'permission-groups' : 'permissions'}${
      id ? `/${id}` : ''
    }`;
    const method = remove ? 'DELETE' : id ? 'PATCH' : 'POST';
    const fingerprint = JSON.stringify([
      method,
      path,
      values,
      target.row?.version,
    ]);
    if (pending.current?.fingerprint !== fingerprint)
      pending.current = {
        fingerprint,
        key: `ui-permission-${Date.now()}-${Math.random()
          .toString(36)
          .slice(2)}`,
      };
    setSaving(true);
    setError(undefined);
    let metadataSaved = false;
    try {
      if (saveMetadata) {
        const result = await call<Permission>(
          base,
          path,
          method,
          remove ? undefined : values,
          target.row?.version,
          pending.current.key
        );
        if (!alive.current) return;
        if (draft) {
          metadataSaved = true;
          setEditor({ group: false, row: result });
          setChanged(false);
        }
      }
      if (draft) {
        await call(
          base,
          `${path}/interfaces`,
          'PUT',
          { resourceIds: draft.resourceIds },
          draft.version,
          draft.mutationKey
        );
        if (!alive.current) return;
      }
      setEditor(undefined);
      clearBinding();
      setChanged(false);
      pending.current = undefined;
      if (target.group) await loadGroups();
      if (remove && target.group && id === selected) setSelected(undefined);
      else load();
    } catch (e) {
      if (metadataSaved) load();
      failure(
        metadataSaved
          ? { ...e, message: `权限资料已保存，接口关联保存失败：${e.message}` }
          : e
      );
    } finally {
      if (alive.current) {
        setSaving(false);
      }
    }
  };
  const options = groups.map((g) => ({ value: g.groupId, label: g.name }));
  const listError = error || groupError;
  const selectedGroup = groups.find((group) => group.groupId === selected);
  const columns: TableColumnProps<Permission>[] = [
    { title: '名称', dataIndex: 'name' },
    { title: '标识', dataIndex: 'code' },
    {
      title: '访问方式',
      dataIndex: 'accessMode',
      render: (v) => enumLabels[v],
    },
    {
      title: '状态',
      dataIndex: 'status',
      render: (v) => enumLabels[v],
    },
    { title: '版本', dataIndex: 'version' },
    {
      title: '操作',
      render: (_, row: Permission) => (
        <Space>
          <Button
            disabled={!canOpen(false, row)}
            onClick={() => open(false, row)}
          >
            编辑
          </Button>
          <Button
            disabled={!allowed('permissions.delete')}
            status="danger"
            onClick={() =>
              Modal.confirm({
                title: '删除权限？',
                content: '仍被引用的权限不可删除。',
                onOk: () => save(true, { group: false, row }),
              })
            }
          >
            删除
          </Button>
        </Space>
      ),
    },
  ];
  return (
    <Card title="权限管理">
      <Prompt
        when={hasUnsaved || requestBusy}
        message={() => (requestBusy ? false : '放弃未保存修改？')}
      />
      {listError && (
        <Alert
          type="error"
          content={`${listError.status || '网络错误'}：${listError.message} ${
            listError.requestId || ''
          }`}
          style={{ marginBottom: 16 }}
        />
      )}
      <Space wrap>
        <Button
          type="primary"
          onClick={() => open(false)}
          disabled={
            requestBusy || !groups.length || !allowed('permissions.create')
          }
        >
          新建权限
        </Button>
        <Button
          onClick={() => {
            setError(undefined);
            loadGroups();
            load();
          }}
          disabled={requestBusy}
        >
          刷新
        </Button>
        <Input
          aria-label="权限名称筛选"
          placeholder="权限名称"
          onChange={(v) => setFilter((q) => ({ ...q, name: v }))}
        />
        {['status', 'accessMode'].map((k) => (
          <Select
            key={k}
            aria-label={filterLabels[k]}
            placeholder={filterLabels[k]}
            allowClear
            style={{ width: 160 }}
            options={(k === 'status'
              ? ['ACTIVE', 'DISABLED']
              : ['AUTHORIZED', 'PUBLIC', 'AUTHENTICATED']
            ).map((v) => ({ label: enumLabels[v], value: v }))}
            onChange={(v) =>
              setFilter((q) => {
                const n = { ...q };
                if (v) n[k] = v;
                else delete n[k];
                return n;
              })
            }
          />
        ))}
      </Space>
      <div className={treeStyles.workspace}>
        <PermissionGroupTree
          groups={groups}
          selected={selectedGroup}
          onSelect={setSelected}
          management={{
            onCreate: () => open(true),
            onEdit: (group) => open(true, group),
            onDelete: (group) =>
              Modal.confirm({
                title: '删除空权限组？',
                content: group.name,
                onOk: () => save(true, { group: true, row: group }),
              }),
            canCreate: !requestBusy && allowed('groups.create'),
            canEdit: !requestBusy && allowed('groups.update'),
            canDelete: !requestBusy && allowed('groups.delete'),
          }}
          loading={loadingGroups}
        />
        <div className={treeStyles.permissionTables}>
          {!accessDenied && (
            <Tabs activeTab={activeType} onChange={setActiveType}>
              {permissionTypes.map((type) => (
                <Tabs.TabPane key={type} title={enumLabels[type]}>
                  <PermissionTable
                    key={JSON.stringify([base, query, revision])}
                    base={base}
                    type={type}
                    query={query}
                    columns={columns}
                    onAccessError={failure}
                  />
                </Tabs.TabPane>
              ))}
            </Tabs>
          )}
        </div>
      </div>
      <Modal
        style={{
          width:
            dataType === 'DATA' ||
            (!editor?.group &&
              (editor?.row as Permission)?.type === 'INTERFACE')
              ? 1040
              : 720,
          maxWidth: 'calc(100vw - 32px)',
        }}
        title={editor?.group ? '权限组' : '权限'}
        visible={!!editor}
        confirmLoading={saving}
        okButtonProps={{
          disabled:
            bindingBusy ||
            bindingDirty ||
            !editor ||
            (!allowed(metadataAction(editor.group, !!editor.row)) &&
              !(interfaceDraft && allowed('permissions.interfaces.write'))),
        }}
        cancelButtonProps={{ disabled: requestBusy }}
        maskClosable={!requestBusy}
        closable={!requestBusy}
        onOk={() => save()}
        onCancel={() => {
          if (
            !requestBusy &&
            (!hasUnsaved || window.confirm('放弃未保存修改？'))
          ) {
            setEditor(undefined);
            clearBinding();
            setChanged(false);
          }
        }}
        unmountOnExit
      >
        <div style={{ maxHeight: '70vh', overflowY: 'auto' }}>
          {error && (
            <Alert
              type="error"
              content={`${error.status}：${error.message}`}
              style={{ marginBottom: 12 }}
            />
          )}
          {bindingDirty && (
            <Alert
              content="请先保存或还原关联修改，再保存权限资料。"
              style={{ marginBottom: 12 }}
            />
          )}
          <Form
            form={form}
            disabled={
              requestBusy ||
              !editor ||
              !allowed(metadataAction(editor.group, !!editor.row))
            }
            layout="vertical"
            onValuesChange={() => setChanged(true)}
          >
            <Form.Item
              label="名称"
              field="name"
              rules={[{ required: true, maxLength: 200 }]}
            >
              <Input />
            </Form.Item>
            {editor?.group ? (
              <>
                <Form.Item label="父组" field="parentGroupId">
                  <Select
                    allowClear
                    options={options.filter(
                      (x) => x.value !== (editor.row as Group)?.groupId
                    )}
                  />
                </Form.Item>
                <Form.Item
                  label="排序"
                  field="sortOrder"
                  rules={[{ required: true }]}
                >
                  <InputNumber precision={0} />
                </Form.Item>
              </>
            ) : (
              <>
                <Form.Item
                  label="稳定标识"
                  field="code"
                  rules={[
                    {
                      required: true,
                      match: /^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/,
                    },
                  ]}
                >
                  <Input disabled={!!editor?.row} />
                </Form.Item>
                <Form.Item label="说明" field="description">
                  <Input.TextArea maxLength={2000} />
                </Form.Item>
                <Form.Item
                  label="权限类型"
                  field="type"
                  rules={[{ required: true }]}
                >
                  <Select
                    disabled={!!editor?.row}
                    onChange={setDataType}
                    options={enumOptions(['INTERFACE', 'MENU', 'DATA'])}
                  />
                </Form.Item>
                <Alert content="创建权限不会自动授予任何角色。" />
                {dataType === 'DATA' && (
                  <>
                    <Form.Item
                      label="关联接口权限"
                      field="dataScope.interfacePermissionIds"
                      rules={[
                        {
                          required: true,
                          type: 'array',
                          minLength: 1,
                          message: '请选择至少一个接口权限',
                        },
                      ]}
                    >
                      <DataInterfacePicker
                        base={base}
                        groups={groups}
                        disabled={
                          requestBusy ||
                          !allowed(metadataAction(false, !!editor?.row))
                        }
                      />
                    </Form.Item>
                    <Form.Item
                      label="记录范围"
                      field="dataScope.scopePreset"
                      rules={[{ required: true }]}
                    >
                      <Select
                        options={[
                          { label: '本人', value: 'SELF' },
                          { label: '当前租户全部', value: 'TENANT_ALL' },
                          {
                            label: '本部门（当前租户全部有效任职）',
                            value: 'DEPARTMENT',
                          },
                          {
                            label: '本部门及下级部门（当前租户）',
                            value: 'DEPARTMENT_AND_DESCENDANTS',
                          },
                          ...(domain.kind === 'PLATFORM'
                            ? [
                                {
                                  label: '全部平台部门',
                                  value: 'PLATFORM_ALL_DEPARTMENTS',
                                },
                                {
                                  label: '本人平台主部门及下级',
                                  value: 'PLATFORM_PRIMARY_SUBTREE',
                                },
                                {
                                  label: '指定平台部门及下级',
                                  value: 'PLATFORM_SPECIFIED_SUBTREES',
                                },
                              ]
                            : []),
                        ]}
                      />
                    </Form.Item>
                    <Form.Item noStyle shouldUpdate>
                      {() =>
                        [
                          'SPECIFIED_DEPARTMENTS',
                          'PLATFORM_SPECIFIED_SUBTREES',
                        ].includes(
                          form.getFieldValue('dataScope.scopePreset')
                        ) && (
                          <>
                            {departmentsError && (
                              <Alert type="error" content={departmentsError} />
                            )}
                            <Form.Item
                              label="指定部门"
                              field="dataScope.departmentIds"
                              rules={[
                                {
                                  required: true,
                                  type: 'array',
                                  minLength: 1,
                                  message: '至少选择一个有效部门',
                                },
                                {
                                  validator: (ids, callback) => {
                                    if (
                                      !Array.isArray(ids) ||
                                      ids.some(
                                        (id) =>
                                          !departments.some(
                                            (d) => d.value === id
                                          )
                                      )
                                    )
                                      callback('请移除不可用的部门并重新选择');
                                    else callback();
                                  },
                                },
                              ]}
                            >
                              <Select
                                mode="multiple"
                                options={departments}
                                disabled={requestBusy || !!departmentsError}
                                allowClear
                                placeholder="仅授权选中节点，不自动包含下级"
                              />
                            </Form.Item>
                          </>
                        )
                      }
                    </Form.Item>
                  </>
                )}
                <Form.Item
                  label="所属组"
                  field="groupId"
                  rules={[{ required: true }]}
                >
                  <Select options={options} />
                </Form.Item>
                <Form.Item
                  label="访问方式"
                  field="accessMode"
                  rules={[{ required: true }]}
                >
                  <Select
                    options={enumOptions([
                      'AUTHORIZED',
                      'AUTHENTICATED',
                      'PUBLIC',
                    ])}
                  />
                </Form.Item>
                <Form.Item
                  label="状态"
                  field="status"
                  rules={[{ required: true }]}
                >
                  <Select options={enumOptions(['ACTIVE', 'DISABLED'])} />
                </Form.Item>
              </>
            )}
          </Form>
          {!editor?.group &&
            editor?.row &&
            (editor.row as Permission).type === 'MENU' && (
              <MenuPermissionBindings
                key={`${domain.kind}:${domain.id}:${
                  (editor.row as Permission).permissionId
                }`}
                domain={domain}
                permissionId={(editor.row as Permission).permissionId}
                version={editor.row.version}
                onSaved={load}
                onDirtyChange={onBindingDirty}
                onBusyChange={onBindingBusy}
                disabled={saving || !allowed('permissions.menus.write')}
              />
            )}
          {!editor?.group &&
            editor?.row &&
            (editor.row as Permission).type === 'INTERFACE' && (
              <InterfaceAssociationsEditor
                key={`${domain.kind}:${domain.id}:${
                  (editor.row as Permission).permissionId
                }`}
                domain={domain}
                permissionId={(editor.row as Permission).permissionId}
                draft={interfaceDraft}
                onChange={setInterfaceDraft}
                onBusyChange={onBindingBusy}
                disabled={saving || !allowed('permissions.interfaces.write')}
              />
            )}
        </div>
      </Modal>
    </Card>
  );
}
