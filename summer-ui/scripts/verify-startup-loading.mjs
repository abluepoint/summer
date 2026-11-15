import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import test from 'node:test';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server.js';
import ts from 'typescript';

// Render the real callback and loading components; protocol completion remains
// covered separately by the browser PKCE verification.
function load(path, dependencies) {
  const exports = {};
  const source = readFileSync(new URL(path, import.meta.url), 'utf8');
  vm.runInNewContext(ts.transpileModule(source, { compilerOptions: {
    module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020,
    jsx: ts.JsxEmit.React, esModuleInterop: true,
  } }).outputText, { exports, require(name) {
    if (name === 'react') return React;
    if (name in dependencies) return dependencies[name];
    throw new Error(`Unexpected dependency: ${name}`);
  } });
  return exports.default;
}
const locale = load('../src/locale/index.ts', {});
const useLocale = { default: () => locale['zh-CN'], __esModule: true };
const loading = () => load('../src/components/AppLoading/index.tsx', { '@/utils/useLocale': useLocale });

test('refresh callback shows neutral startup feedback instead of claiming a new sign-in', () => {
  const Callback = load('../src/pages/oauth2/callback/index.tsx', {
    '@arco-design/web-react': { Spin: ({ tip }) => React.createElement('span', null, tip), Alert: 'aside', Button: 'button' },
    'react-router-dom': { useHistory: () => ({ replace() {} }) },
    '@/auth/oidc': {}, '@/utils/useLocale': useLocale,
    '@/components/AppLoading': { __esModule: true, get default() { return loading(); } },
  });
  const html = renderToStaticMarkup(React.createElement(Callback));
  assert.doesNotMatch(html, /正在.*登录/, 'Reloading an existing session is normal startup');
  assert.match(html, /正在加载/);
  assert.match(html, /role="status"/);
});

test('initial document and authenticated startup use the same loading structure', () => {
  const html = readFileSync(new URL('../index.html', import.meta.url), 'utf8');
  const component = renderToStaticMarkup(React.createElement(loading()));
  const root = html.match(/<div id="root">([\s\S]*?)<\/div>\s*<script/);
  assert.ok(root, 'The first document must have startup feedback before JavaScript loads');
  assert.equal(root[1].replace(/>\s+</g, '><').trim(), component);
  assert.match(html, /href="\/src\/style\/startup.css"/);
});

test('content loading stays inside its section and preserves a specific progress message', () => {
  const html = renderToStaticMarkup(React.createElement(loading(), {
    fullScreen: false, tip: '正在检查运行环境…',
  }));
  assert.match(html, /class="summer-loading-section"/);
  assert.doesNotMatch(html, /class="summer-startup"/);
  assert.match(html, /summer-startup-spinner/);
  assert.match(html, /正在检查运行环境/);
  assert.match(html, /role="status"/);
});
