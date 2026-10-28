import React from 'react';
import { Alert, Drawer, Form, Input, InputNumber, Switch } from '@arco-design/web-react';
import { MenuOverride } from '../types';
import { VisibleMenuItem } from '@/menu/api';
type Props = { item: VisibleMenuItem; value: MenuOverride; onChange: (value: MenuOverride) => void; onSave: () => void; onClose: () => void; saving: boolean };
export default function MenuEditor({ item, value, onChange, onSave, onClose, saving }: Props) {
  return <Drawer visible title={`编辑菜单 · ${item.menuId}`} width={520} okText="保存" cancelText="取消" onOk={onSave} onCancel={onClose} okButtonProps={{ loading: saving }} cancelButtonProps={{ disabled: saving }}>
    <Alert type="info" content="路径和组件映射由前端路由声明维护。清空父菜单表示移到根级，清空能力码表示不绑定能力。API 仍独立鉴权。" />
    <Form layout="vertical" style={{ marginTop: 16 }}>
      <Form.Item label="路径"><Input value={item.path} disabled /></Form.Item>
      <Form.Item label="启用"><Switch aria-label="菜单启用" checked={value.enabled} onChange={(enabled) => onChange({ ...value, enabled })} disabled={saving} /></Form.Item>
      <Form.Item label="父菜单 ID"><Input aria-label="父菜单 ID" value={value.parentMenuId || ''} onChange={(parentMenuId) => onChange({ ...value, parentMenuId })} disabled={saving} /></Form.Item>
      <Form.Item label="排序"><InputNumber aria-label="排序" min={0} precision={0} value={value.sortOrder} onChange={(sortOrder) => onChange({ ...value, sortOrder })} disabled={saving} /></Form.Item>
      <Form.Item label="能力码"><Input aria-label="能力码" value={value.capabilityCode || ''} onChange={(capabilityCode) => onChange({ ...value, capabilityCode })} disabled={saving} /></Form.Item>
      <div>覆盖版本：{item.version}</div>
    </Form>
  </Drawer>;
}
