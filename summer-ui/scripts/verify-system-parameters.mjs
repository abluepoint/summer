import { readFile } from 'node:fs/promises';

const api = await readFile(new URL('../src/pages/system-parameters/api.ts', import.meta.url), 'utf8');
const page = await readFile(new URL('../src/pages/system-parameters/index.tsx', import.meta.url), 'utf8');
const routes = await readFile(new URL('../src/routes.ts', import.meta.url), 'utf8');
for (const [text, pattern, label] of [
  [api, "'/api/system-parameters'", 'real system parameter API'],
  [api, 'axios.patch', 'update request'],
  [api, 'axios.post', 'status request'],
  [api, "'/api/system-parameter-events'", 'outbox delivery query'],
  [api, 'retryParameterEvent', 'outbox retry request'],
  [api, 'listParameterVersions', 'version history request'],
  [api, 'saveParameterDraft', 'draft request'],
  [api, 'publishParameterVersion', 'publish request'],
  [api, 'rollbackParameterVersion', 'rollback request'],
  [page, 'saveDraft', 'draft action'],
  [page, "releaseState", 'release history table'],
  [page, 'ParameterApiError', 'error envelope handling'],
  [page, 'deliveryTitle', 'outbox delivery table'],
  [routes, "key: 'system-parameters'", 'navigation route'],
]) {
  if (!text.includes(pattern)) throw new Error(`Missing ${label}`);
}
if (api.includes('mock') || page.includes('mock')) throw new Error('System parameter UI must not use mock data');
console.log('System parameter UI uses the real API and exposes failure/request-id handling');
