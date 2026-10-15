// Component fixture: synthetic transport, not real backend or login acceptance.
import React from 'react';
import ReactDOM from 'react-dom';
import { GlobalContext } from '../src/context';
import OAuthClientsPage from '../src/pages/oauth-clients';
import '@arco-design/web-react/dist/css/arco.css';
ReactDOM.render(
  <GlobalContext.Provider value={{ lang: 'zh-CN' }}>
    <OAuthClientsPage />
  </GlobalContext.Provider>,
  document.getElementById('root')
);
