import assert from 'node:assert/strict';
import axios from 'axios';
import { buildSync } from 'esbuild';
import { fileURLToPath } from 'node:url';
function load(file) {
  const code = buildSync({ entryPoints: [fileURLToPath(new URL(file, import.meta.url))], bundle: true, target: 'es2019', platform: 'node', format: 'cjs', external: ['axios', '@/auth/oidc'], write: false }).outputFiles[0].text;
  const module = { exports: {} };
  new Function('require', 'module', 'exports', code)((name) => {
    if (name === 'axios') return axios;
    assert.equal(name, '@/auth/oidc');
    return { getAccessTokenFromMemory: async () => accessToken };
  }, module, module.exports);
  return module.exports;
}
const exported = load('../src/pages/diagnostics/redis/api.ts');
let accessToken='fixture-first', call;
const originalAdapter = axios.defaults.adapter;
axios.defaults.adapter = async (config) => {
  call = { url: config.url, options: config };
  return { config, status: 200, headers: {}, data: { success: true, data: { status: 'UP' } } };
};
try {
assert.equal((await exported.getRedisDiagnostics()).status,'UP');
assert.equal(call.options?.headers?.Authorization,'Bearer fixture-first','diagnostic requests must use current bearer credentials so Redis session failures do not hide diagnostics');
accessToken='fixture-renewed';await exported.getRedisDiagnostics();assert.equal(call.options?.headers?.Authorization,'Bearer fixture-renewed','read credentials for each request');
accessToken=null;await exported.getRedisDiagnostics();assert.equal(call.options?.headers?.Authorization,undefined,'never synthesize a bearer credential');
const dictionary = load('../src/pages/dictionary/api.ts');
assert.equal((await dictionary.getPublicDictionary('countries')).status, 'UP');
for (const status of [200, 403]) {
  axios.defaults.adapter = async (config) => {
    const response = { config, status, headers: { 'x-request-id': 'dictionary-request' }, data: { success: false, error: { code: 'DICTIONARY_UNAVAILABLE', message: 'Unavailable' } } };
    if (status === 403) throw { isAxiosError: true, response };
    return response;
  };
  await assert.rejects(dictionary.getPublicDictionary('countries'), error => error.status === status && error.requestId === 'dictionary-request');
  await assert.rejects(exported.getRedisDiagnostics(), error => error.status === status && error.requestId === 'dictionary-request');
}
} finally {
  axios.defaults.adapter = originalAdapter;
}
console.log('Redis diagnostics and public dictionary: bearer renewal, absent token and shared response/error contracts passed');
