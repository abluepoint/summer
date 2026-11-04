import React from 'react';
import ReactDOM from 'react-dom';
import { Provider } from 'react-redux';
import { createStore } from 'redux';
import reducer from '../../src/store';
import {
  DictionaryNameDisplay,
  useDictionaryNames,
} from '../../src/pages/dictionary/names';
import { DictionarySelect } from '../../src/pages/dictionary/select';
const store = createStore(reducer);
function Detail({ values }: { values: string[] }) {
  const names = useDictionaryNames('customer-segment', values);
  return <DictionaryNameDisplay names={names} value="a" />;
}
export function mountSelectionProbe(
  host: HTMLElement,
  select: boolean,
  secondGroup = false,
  values = ['a']
) {
  ReactDOM.render(
    <Provider store={store}>
      <Detail values={values} />
      {select && (
        <DictionarySelect
          code="customer-segment"
          value="a"
          onChange={() => undefined}
        />
      )}
      {secondGroup && (
        <DictionarySelect
          code="customer-segment"
          groupCode="reverse"
          value="a"
          onChange={() => undefined}
        />
      )}
    </Provider>,
    host
  );
}
export function unmountSelectionProbe(host: HTMLElement) {
  ReactDOM.unmountComponentAtNode(host);
}

export function unmountApplication() {
  const root = document.getElementById('root');
  if (root) ReactDOM.unmountComponentAtNode(root);
}

export function mountGroupSelectionProbe(host: HTMLElement) {
  ReactDOM.render(
    <Provider store={store}>
      <Detail values={['a']} />
      <DictionarySelect
        code="customer-segment"
        value="a"
        onChange={() => undefined}
      />
      {Array.from({ length: 201 }, (_, index) => (
        <DictionarySelect
          key={index}
          code="customer-segment"
          groupCode={`bulk-${String(index).padStart(3, '0')}`}
          value="a"
          onChange={() => undefined}
        />
      ))}
    </Provider>,
    host
  );
}
