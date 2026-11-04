import React, { useState } from 'react';
import ReactDOM from 'react-dom';
import { Provider } from 'react-redux';
import reducer from '../../src/store';
import { createStore } from 'redux';
const store = createStore(reducer);
import { DictionaryCascade } from '../../src/pages/dictionary/cascade';
export function mountCascade(
  host: HTMLElement,
  initial = ['1', 'shared', 'old']
) {
  host.style.cssText =
    'position:relative;background:white;max-width:520px;padding:120px 24px 24px;margin-left:250px';
  function Probe() {
    const [values, setValues] = useState(initial);
    return (
      <Provider store={store}>
        <DictionaryCascade
          levels={[
            { code: 'customer-segment', label: '一级' },
            {
              code: 'cascade-second',
              label: '二级',
              groups: {
                '1': 'one',
                '01': 'zero-one',
                missing: 'absent',
                disabled: 'disabled',
              },
            },
            {
              code: 'cascade-third',
              label: '三级',
              groups: { shared: 'shared', other: 'other' },
            },
          ]}
          values={values}
          onChange={setValues}
        />
        <output>{JSON.stringify(values)}</output>
      </Provider>
    );
  }
  ReactDOM.render(<Probe />, host);
}
export function unmountCascade(
  host: HTMLElement,
  initial = ['1', 'shared', 'old']
) {
  ReactDOM.unmountComponentAtNode(host);
}
