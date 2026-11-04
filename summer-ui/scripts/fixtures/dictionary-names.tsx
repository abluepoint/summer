// Browser acceptance fixture consuming only exported component/read APIs.
import React from 'react';
import ReactDOM from 'react-dom';
import { Provider } from 'react-redux';
import { createStore } from 'redux';
import reducer from '../../src/store';
import {
  DictionaryNameDisplay,
  useDictionaryNames,
} from '../../src/pages/dictionary/names';

const store = createStore(reducer);
function Consumer({
  id,
  values,
  code,
}: {
  id: string;
  values: string[];
  code: string;
}) {
  const names = useDictionaryNames(code, values);
  return (
    <div data-probe={id}>
      {values.map((value) => (
        <DictionaryNameDisplay key={value} names={names} value={value} />
      ))}
    </div>
  );
}
export function mountNamesProbe(
  host: HTMLElement,
  second: boolean,
  extra = false,
  code = 'customer-segment',
  firstValues = ['alpha']
) {
  ReactDOM.render(
    <Provider store={store}>
      <Consumer code={code} id="first" values={firstValues} />
      {second && (
        <Consumer
          code={code}
          id="second"
          values={extra ? ['alpha', '01'] : ['alpha']}
        />
      )}
    </Provider>,
    host
  );
}
export function unmountNamesProbe(host: HTMLElement) {
  ReactDOM.unmountComponentAtNode(host);
}
