import React from 'react';
import {
  Form,
  Input,
  InputNumber,
  Select,
  Switch,
} from '@arco-design/web-react';
import { MenuManifest, VisibleMenuItem } from '@/menu/api';

export const clientDictionaryHelp =
  '所属客户端由平台字典“菜单所属客户端”维护，发布并启用的字典值可供选择。';

export type MenuFields = Pick<
  VisibleMenuItem,
  | 'menuId'
  | 'titleKey'
  | 'parentMenuId'
  | 'sortOrder'
  | 'clientCode'
  | 'enabled'
>;
export type MenuNode = VisibleMenuItem & { children?: MenuNode[] };

/** Return new nodes so expanding the tree never changes the publication payload. */
export function menuTree(items: VisibleMenuItem[]): MenuNode[] {
  const nodes = new Map(
    items.map((item) => [item.menuId, { ...item } as MenuNode])
  );
  const roots: MenuNode[] = [];
  nodes.forEach((node) => {
    const parent = nodes.get(node.parentMenuId);
    if (parent) (parent.children ||= []).push(node);
    else roots.push(node);
  });
  return roots;
}

export function MenuForm({
  value,
  onChange,
  existing,
  parents,
  clients,
  disabled,
}: {
  value: MenuFields;
  onChange: (next: MenuFields) => void;
  existing: boolean;
  parents: { value: string; label: string }[];
  clients: MenuManifest['clientOptions'];
  disabled: boolean;
}) {
  return (
    <Form layout="vertical" disabled={disabled}>
      <Form.Item label="菜单名称" required>
        <Input
          aria-label="菜单名称"
          maxLength={240}
          value={value.titleKey}
          onChange={(titleKey) => onChange({ ...value, titleKey })}
        />
      </Form.Item>
      <Form.Item label="菜单 ID" required>
        <Input
          aria-label="菜单 ID"
          disabled={existing || disabled}
          maxLength={160}
          value={value.menuId}
          onChange={(menuId) => onChange({ ...value, menuId })}
        />
      </Form.Item>
      <Form.Item label="路径">
        <Input
          aria-label="路径"
          readOnly
          value={value.menuId ? '/' + value.menuId : ''}
        />
      </Form.Item>
      <Form.Item label="父菜单">
        <Select
          aria-label="父菜单"
          showSearch
          value={value.parentMenuId || ''}
          options={[{ value: '', label: '根菜单' }, ...parents]}
          onChange={(parentMenuId) => onChange({ ...value, parentMenuId })}
        />
      </Form.Item>
      <Form.Item
        label="所属客户端"
        required
        extra={<span id="menu-client-help">{clientDictionaryHelp}</span>}
      >
        <Select
          aria-label="所属客户端"
          aria-describedby="menu-client-help"
          value={value.clientCode}
          options={clients.map((option) => ({
            ...option,
            disabled: !option.enabled,
          }))}
          onChange={(clientCode) => onChange({ ...value, clientCode })}
        />
      </Form.Item>
      <Form.Item label="排序">
        <InputNumber
          aria-label="排序"
          min={0}
          precision={0}
          value={value.sortOrder}
          onChange={(sortOrder) => onChange({ ...value, sortOrder })}
        />
      </Form.Item>
      <Form.Item label="状态">
        <Switch
          aria-label="启用菜单"
          checked={value.enabled}
          checkedText="启用"
          uncheckedText="停用"
          onChange={(enabled) => onChange({ ...value, enabled })}
        />
      </Form.Item>
    </Form>
  );
}
