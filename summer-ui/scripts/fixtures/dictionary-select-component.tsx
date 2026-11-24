import React, { useState } from 'react';
import { Provider } from 'react-redux';
import { createStore } from 'redux';
import { Button, Card, Form, Input, Space } from '@arco-design/web-react';
import reducer from '../../src/store';
import DictionarySelect from '../../src/components/DictionarySelect';

const store = createStore(reducer);
function Example() {
  const [form] = Form.useForm();
  const [code, setCode] = useState('customer-segment');
  const [group, setGroup] = useState('');
  const [disabled, setDisabled] = useState(false);
  const [observed, setObserved] = useState('');
  const [submitted, setSubmitted] = useState('');
  return <Card title="业务表单中的字典选择" style={{ maxWidth: 600, margin: 20 }}>
    <Space direction="vertical" style={{ width: '100%' }}>
      <Input aria-label="组件字典编码" value={code} onChange={setCode} />
      <Input aria-label="组件字典组编码" value={group} onChange={setGroup} />
      <Button onClick={() => setDisabled(!disabled)}>切换禁用</Button>
      <Button onClick={() => { form.setFieldsValue({ segment: 'retired' }); setObserved('retired'); }}>设置旧值</Button>
    </Space>
    <Form form={form} onValuesChange={(_, values) => setObserved(values.segment || '')} onSubmit={(values) => setSubmitted(JSON.stringify(values))}>
      <Form.Item field="segment" label="客户分类" rules={[{ required: true, message: '请选择客户分类' }]}>
        <DictionarySelect code={code} groupCode={group || undefined} aria-label="客户分类选择" placeholder="请选择客户分类" disabled={disabled} showSearch allowClear />
      </Form.Item>
      <Space>
        <Button htmlType="submit">提交</Button>
        <Button onClick={() => { form.resetFields(); setObserved(''); }}>重置</Button>
      </Space>
    </Form>
    <div data-testid="selected-value">{JSON.stringify(observed)}</div>
    <div data-testid="submitted-value">{submitted}</div>
  </Card>;
}
export default function DictionarySelectFixture() {
  return <Provider store={store}><Example /></Provider>;
}
