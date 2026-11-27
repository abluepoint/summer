import React, { useMemo, useState } from 'react';
import listStyles from '@/pages/platform/list-scroll.module.less';
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tree,
  TreeSelect,
} from '@arco-design/web-react';
import ResizableTreePanel from '@/components/ResizableTreePanel';
import styles from './style.module.less';
import { useInterfaceOperations } from '@/auth/useInterfaceOperations';
import { call } from '../../permissions/api';
import {
  base,
  departmentsPath as path,
  Department,
  Directory,
  Membership,
  Person,
  PersonPicker,
  useDirectory,
} from '../directory';

type Panel =
  | 'create'
  | 'edit'
  | 'delete'
  | 'join'
  | 'assign'
  | 'leave'
  | 'primary'
  | 'leader'
  | 'clear_leader'
  | 'transfer';
type TreeNode = {
  key: string;
  title: string;
  selectable?: boolean;
  children: TreeNode[];
};

function destinationTree(
  nodes: TreeNode[],
  allowedIds: Set<string>
): TreeNode[] {
  const result: TreeNode[] = [];
  for (const node of nodes) {
    const children = destinationTree(node.children, allowedIds);
    const selectable = allowedIds.has(node.key);
    if (selectable || children.length)
      result.push({ ...node, selectable, children });
  }
  return result;
}

export default function PlatformOrganization() {
  const { data, error, reload } = useDirectory<Directory>(path);
  const ops = useInterfaceOperations({ kind: 'PLATFORM' }, [
    'platformDirectory.create',
    'platformDirectory.edit',
    'platformDirectory.delete',
    'platformDirectory.staff',
  ]);
  const [node, setNode] = useState('');
  const [children, setChildren] = useState(false);
  const [unassigned, setUnassigned] = useState(false);
  const [query, setQuery] = useState('');
  const [panel, setPanel] = useState<Panel>();
  const [subject, setSubject] = useState('');
  const [name, setName] = useState('');
  const [parent, setParent] = useState('');
  const [order, setOrder] = useState(0);
  const [enabled, setEnabled] = useState(true);
  const [departmentSearch, setDepartmentSearch] = useState('');
  const [next, setNext] = useState('');
  const [impact, setImpact] = useState<string[]>();
  const [version, setVersion] = useState(0);
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState('');
  const units = data?.departments || [],
    links = data?.memberships || [];
  const current = units.find((n) => n.id === node);
  function allowed(action: string, department = node) {
    const range = data?.writable?.[action];
    return !!range && (range.all || range.ids.includes(department));
  }
  const destinations = units.filter(
    (unit) => unit.enabled && unit.id !== node && allowed('staff', unit.id)
  );
  function ancestors(id: string) {
    const chain: string[] = [];
    for (
      let n = units.find((d) => d.id === id);
      n && !chain.includes(n.id);
      n = units.find((d) => d.id === n.parentId)
    )
      chain.push(n.id);
    return chain;
  }
  const departmentTree = useMemo(() => {
    const nodes = new Map<string, TreeNode>();
    const roots: TreeNode[] = [];
    for (const unit of units) {
      nodes.set(unit.id, {
        key: unit.id,
        title: unit.name + (unit.enabled ? '' : '（停用）'),
        children: [],
      });
    }
    for (const unit of units) {
      const siblings = nodes.get(unit.parentId)?.children || roots;
      siblings.push(nodes.get(unit.id));
    }
    return roots;
  }, [units]);
  function relevant(m: Membership) {
    return (
      !node ||
      m.departmentId === node ||
      (children && ancestors(m.departmentId).includes(node))
    );
  }
  const rows = (data?.people || []).filter((p) => {
    if (
      !(p.username + ' ' + p.displayName)
        .toLowerCase()
        .includes(query.toLowerCase().trim())
    )
      return false;
    const member = links.filter((m) => m.username === p.username);
    if (!node && unassigned) return !member.length;
    return !node || member.some(relevant);
  });
  const titles: Record<Panel, string> = {
    create: '新建部门',
    edit: '编辑部门',
    delete: '删除部门',
    join: '添加成员',
    assign: '分配部门',
    leave: '调出部门',
    primary: '设为主部门',
    leader: '指定负责人',
    clear_leader: '解除负责人',
    transfer: '调往其他部门',
  };
  async function open(kind: Panel, user = '') {
    if (!data) return;
    setFailure('');
    setSubject(user);
    setNext('');
    setDepartmentSearch('');
    setPanel(kind);
    setVersion(data.version);
    setImpact(undefined);
    setName(kind === 'edit' ? current?.name || '' : '');
    setEnabled(kind === 'edit' ? !!current?.enabled : true);
    setParent(kind === 'create' ? node : current?.parentId || '');
    setOrder(kind === 'edit' ? current?.sortOrder || 0 : 0);
    if (kind === 'delete') {
      try {
        const res = await call<{ permissions: string[]; version: number }>(
          base,
          path + '/' + node + '/deletion-impact'
        );
        setImpact(res.permissions);
        setVersion(res.version);
      } catch (e) {
        setFailure(e.message);
      }
    }
  }
  const remaining = links.filter(
    (m) => m.username === subject && m.departmentId !== node
  );
  const subjectLink = links.find(
    (m) => m.username === subject && m.departmentId === node
  );
  async function save() {
    if (!panel) return;
    setBusy(true);
    setFailure('');
    try {
      if (panel === 'create' || panel === 'edit') {
        await call(
          base,
          path + (panel === 'edit' ? '/' + node : ''),
          panel === 'edit' ? 'PUT' : 'POST',
          { name, parentId: parent || null, enabled, sortOrder: order },
          version
        );
      } else if (panel === 'delete') {
        await call(base, path + '/' + node, 'DELETE', undefined, version);
        setNode(current?.parentId || '');
      } else {
        const department = panel === 'assign' ? next : node;
        await call(
          base,
          path + '/' + department + '/members/' + encodeURIComponent(subject),
          'PUT',
          panel === 'assign'
            ? { action: 'JOIN' }
            : { action: panel.toUpperCase(), newPrimaryId: next || null },
          version
        );
      }
      setPanel(undefined);
      reload();
    } catch (e) {
      setFailure(e.message || '保存失败');
    } finally {
      setBusy(false);
    }
  }
  const departmentPath = (id: string) =>
    ancestors(id)
      .reverse()
      .map((key) => units.find((d) => d.id === key)?.name)
      .join(' / ');
  const options = (list: Department[]) =>
    list.map((n) => ({
      value: n.id,
      label: departmentPath(n.id),
    }));
  return (
    <Card title="平台组织机构管理">
      {error && <Alert type="error" content={error} />}
      <div className={styles.workspace}>
        <ResizableTreePanel id="platform-department-panel" label="平台部门树">
          <div className={`${styles.tree} ${listStyles.listScroll}`}>
            <Tree
              aria-label="平台部门"
              key={units.map((n) => n.id).join(',')}
              defaultExpandedKeys={['root', ...units.map((n) => n.id)]}
              selectedKeys={[node || 'root']}
              treeData={[
                {
                  key: 'root',
                  title: '平台组织机构',
                  children: departmentTree,
                },
              ]}
              onSelect={(keys) => {
                if (keys.length) {
                  setNode(keys[0] === 'root' ? '' : String(keys[0]));
                  setUnassigned(false);
                }
              }}
            />
          </div>
        </ResizableTreePanel>
        <section className={styles.members}>
          <Space wrap>
            {ops['platformDirectory.create'] &&
              allowed('create') &&
              (!current || current.enabled) && (
                <Button onClick={() => open('create')}>新建下级部门</Button>
              )}
            {current && ops['platformDirectory.edit'] && allowed('edit') && (
              <Button onClick={() => open('edit')}>编辑部门</Button>
            )}
            {current && ops['platformDirectory.delete'] && allowed('delete') && (
              <Button status="danger" onClick={() => open('delete')}>
                删除部门
              </Button>
            )}
            {current?.enabled &&
              ops['platformDirectory.staff'] &&
              allowed('staff') && (
                <Button type="primary" onClick={() => open('join')}>
                  添加成员
                </Button>
              )}
            <Button onClick={reload}>刷新</Button>
          </Space>
          <h3>
            {current?.name || '平台组织机构'} · {rows.length} 人
          </h3>
          <Space wrap>
            <Input
              aria-label="搜索部门成员"
              placeholder="搜索姓名或账号"
              value={query}
              onChange={setQuery}
            />
            {node && (
              <Checkbox checked={children} onChange={setChildren}>
                包含下级部门
              </Checkbox>
            )}
            {!node && data?.all && (
              <Checkbox checked={unassigned} onChange={setUnassigned}>
                未分配部门
              </Checkbox>
            )}
          </Space>
          <div
            role="region"
            aria-label="组织成员"
            tabIndex={0}
            className={listStyles.listScroll}
            style={{ overflowX: 'auto' }}
          >
            <Table
              rowKey="username"
              data={rows}
              pagination={{ pageSize: 20, showTotal: true }}
              columns={[
                { title: '姓名', dataIndex: 'displayName' },
                { title: '账号', dataIndex: 'username' },
                {
                  title: '部门归属',
                  render: (_, p: Person) =>
                    links
                      .filter((m) => m.username === p.username && relevant(m))
                      .map(
                        (m) =>
                          (units.find((n) => n.id === m.departmentId)?.name ||
                            '') +
                          '（' +
                          (m.primary ? '主属' : '兼任') +
                          (m.leader ? '、负责人' : '') +
                          '）'
                      )
                      .join('、') || '未分配部门',
                },
                {
                  title: '操作',
                  render: (_, p: Person) => {
                    if (!ops['platformDirectory.staff']) return null;
                    if (
                      !node &&
                      data?.all &&
                      p.active &&
                      data.writable?.staff &&
                      !links.some((link) => link.username === p.username)
                    ) {
                      return (
                        <Button onClick={() => open('assign', p.username)}>
                          分配部门
                        </Button>
                      );
                    }
                    const m = links.find(
                      (m) =>
                        m.username === p.username && m.departmentId === node
                    );
                    if (!m || !allowed('staff')) return null;
                    return (
                      <Space wrap>
                        {!m.primary && (
                          <Button onClick={() => open('primary', p.username)}>
                            设为主部门
                          </Button>
                        )}
                        <Button
                          onClick={() =>
                            open(
                              m.leader ? 'clear_leader' : 'leader',
                              p.username
                            )
                          }
                        >
                          {m.leader ? '解除负责人' : '指定负责人'}
                        </Button>
                        <Button onClick={() => open('transfer', p.username)}>
                          调往其他部门
                        </Button>
                        <Button onClick={() => open('leave', p.username)}>
                          调出部门
                        </Button>
                      </Space>
                    );
                  },
                },
              ]}
            />
          </div>
        </section>
      </div>
      <Modal
        title={panel && titles[panel]}
        visible={!!panel}
        confirmLoading={busy}
        onOk={save}
        okButtonProps={{
          disabled:
            (panel === 'delete' && !impact) ||
            (panel === 'join' && !subject) ||
            ((panel === 'transfer' || panel === 'assign') && !next) ||
            ((panel === 'edit' || panel === 'create') && !name.trim()),
        }}
        onCancel={() => {
          if (!busy) setPanel(undefined);
        }}
      >
        {failure && <Alert type="error" content={failure} />}
        {(panel === 'create' || panel === 'edit') && (
          <Form layout="vertical">
            <Form.Item label="部门名称" required>
              <Input
                aria-label="部门名称"
                value={name}
                onChange={setName}
                maxLength={200}
              />
            </Form.Item>
            <Form.Item label="上级部门">
              <Select
                aria-label="上级部门"
                value={parent}
                onChange={setParent}
                options={[
                  { value: '', label: '平台组织机构' },
                  ...options(
                    units.filter(
                      (n) =>
                        (n.enabled || n.id === current?.parentId) &&
                        (panel !== 'edit' || !ancestors(n.id).includes(node))
                    )
                  ),
                ]}
              />
            </Form.Item>
            <Form.Item label="排序">
              <InputNumber
                min={0}
                precision={0}
                value={order}
                onChange={setOrder}
              />
            </Form.Item>
            <Checkbox checked={enabled} onChange={setEnabled}>
              启用部门
            </Checkbox>
          </Form>
        )}
        {panel === 'delete' && (
          <>
            <p>确认删除“{current?.name}”？部门必须没有子部门和成员。</p>
            {impact && <p>受影响的数据权限：{impact.join('、') || '无'}</p>}
          </>
        )}
        {panel === 'join' && (
          <PersonPicker
            path={path + '/candidates?departmentId=' + encodeURIComponent(node)}
            value={subject || undefined}
            onChange={setSubject}
          />
        )}
        {panel &&
          ['leave', 'primary', 'leader', 'clear_leader', 'transfer'].includes(
            panel
          ) && (
            <p>
              {subject} · {current?.name}
            </p>
          )}
        {panel === 'leave' && subjectLink?.primary && remaining.length > 1 && (
          <Select
            aria-label="新主部门"
            placeholder="选择新主部门"
            value={next || undefined}
            onChange={setNext}
            options={options(
              units.filter((n) =>
                remaining.some((m) => m.departmentId === n.id)
              )
            )}
          />
        )}
        {(panel === 'assign' || panel === 'transfer') && (
          <>
            {panel === 'assign' && <p>为 {subject} 分配主部门</p>}
            <Form layout="vertical">
              <Form.Item label="调入部门" required>
                <TreeSelect
                  aria-label="调入部门"
                  placeholder="选择或搜索调入部门"
                  value={next || undefined}
                  onChange={setNext}
                  showSearch
                  treeData={destinationTree(
                    departmentTree,
                    new Set(destinations.map((d) => d.id))
                  )}
                  onInputValueChange={setDepartmentSearch}
                  treeProps={
                    departmentSearch
                      ? { expandedKeys: units.map((d) => d.id) }
                      : undefined
                  }
                  filterTreeNode={(input, option) =>
                    option.props.title
                      .toLowerCase()
                      .includes(input.trim().toLowerCase())
                  }
                  renderFormat={(_, id) => departmentPath(String(id))}
                  notFoundContent={
                    departmentSearch ? '无匹配部门' : '暂无可分配的启用部门'
                  }
                />
              </Form.Item>
            </Form>
          </>
        )}
        {panel === 'leader' && <p>确认后，该成员将接任本部门负责人。</p>}
      </Modal>
    </Card>
  );
}
