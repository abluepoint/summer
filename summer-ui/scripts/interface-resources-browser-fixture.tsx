// Actual React page with synthetic HTTP transport; not real-login acceptance.
import React from 'react';
import ReactDOM from 'react-dom';
import { GlobalContext } from '../src/context';
import InterfaceResourcesPage from '../src/pages/interface-resources';
import '@arco-design/web-react/dist/css/arco.css';
ReactDOM.render(
  <GlobalContext.Provider value={{ lang: 'zh-CN' }}>
    <InterfaceResourcesPage />
  </GlobalContext.Provider>,
  document.getElementById('root')
);
