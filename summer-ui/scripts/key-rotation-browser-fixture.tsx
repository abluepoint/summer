// Synthetic HTTP component fixture; real acceptance uses scripts/acceptance/key-rotation.
import React from 'react';
import ReactDOM from 'react-dom';
import { GlobalContext } from '../src/context';
import SecurityKeysPage from '../src/pages/security-keys';
import '@arco-design/web-react/dist/css/arco.css';

ReactDOM.render(
  <GlobalContext.Provider value={{ lang: 'zh-CN' }}>
    <SecurityKeysPage />
  </GlobalContext.Provider>,
  document.getElementById('root')
);
