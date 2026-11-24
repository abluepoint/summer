# 字典选择组件标准用法

业务页面统一从 `@/components/DictionarySelect` 引入 `DictionarySelect`。它是单选下拉框：配置字典编码 `code`，可选配置组编码 `groupCode`，选择结果为原始字符串字典值。无需跳转“字典表单”菜单，也无需页面自行加载 options。

## 受控使用

```tsx
import React, { useState } from 'react';
import DictionarySelect from '@/components/DictionarySelect';

export default function CustomerSegmentField() {
  const [segment, setSegment] = useState('');
  return (
    <DictionarySelect
      code="customer-segment"
      value={segment}
      onChange={setSegment}
      placeholder="请选择客户分类"
      aria-label="客户分类"
      showSearch
      allowClear
      style={{ width: 280 }}
    />
  );
}
```

`code` 使用“字典管理”中的字典编码。限定可选范围时增加 `groupCode="vip"`（替换为该字典下真实已发布的组编码），选项按组内顺序显示；不传 groupCode 时使用字典默认顺序。code 为空时禁用控件，不请求字典。

## Arco Form 接入

```tsx
import { Form, Button } from '@arco-design/web-react';
import DictionarySelect from '@/components/DictionarySelect';

export default function CustomerForm() {
  return (
    <Form onSubmit={(values) => console.log(values.segment)}>
      <Form.Item
        field="segment"
        label="客户分类"
        rules={[{ required: true, message: '请选择客户分类' }]}
      >
        <DictionarySelect
          code="customer-segment"
          placeholder="请选择客户分类"
          showSearch
          allowClear
        />
      </Form.Item>
      <Button htmlType="submit">提交</Button>
    </Form>
  );
}
```

Form.Item 自动注入 value/onChange，页面无需重复维护 state。必填和其他业务校验由 Form.Item 配置；组件不在初始化时主动标记必填错误。表单提交仅得到选项原值，例如 `"01"`，不会转成数字或显示名称。清空统一回传空字符串 `""`，表单的 required 规则会将它视为空值。

| 属性 | 类型/含义 |
| --- | --- |
| `code` | 必填 string，字典编码 |
| `groupCode` | 可选 string，同字典的组编码 |
| `value` | 可选 string，受控值；支持 Form.Item 注入 |
| `onChange` | 可选 `(value: string) => void`，用户选择或清空时触发 |
| `disabled`、`allowClear`、`showSearch` | 沿用 Arco 单选控件语义 |
| `placeholder`、`size`、`style`、`className`、`id` | 控件文字、外观及标识 |
| `onBlur`、`onFocus`、`status`、`error` | 表单交互及校验状态透传 |
| `aria-label`、`aria-describedby`、`aria-invalid` | 无障碍名称、提示及校验状态 |

组件使用应用现有登录和 Redux 权限状态。字典由平台统一发布，读取、名称解析及更新通知均使用平台域；业务页面无需选择企业。服务名称字典编码为 `interface-service-name`，刷新接口目录时保存稳定的项值，资源列表显示已发布名称。无需字典管理菜单权限，但调用者仍需具有对应读取接口和数据权限。

加载失败、字典/组不可用或原值已停用时，会展示状态并禁止选择不可用项，不擅自清空调用方业务值。修改字典或组编码也不会自动修改 value；若业务联动要求清空，请调用方明确设置为空字符串。空组不回退到完整字典。多级联动沿用既有 DictionaryCascade，不能用本单选组件传数组代替。

原 `@/pages/dictionary/select` 路径保留兼容转导出，新增业务代码统一使用组件目录。原硬编码演示表单仅保留在 `summer-ui/scripts/fixtures/dictionary-consumer.tsx` 供回归测试，不进入应用导航或生产页面路由。
