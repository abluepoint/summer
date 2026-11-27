import { readFile } from 'node:fs/promises';

const api = await readFile(new URL('../src/pages/dictionary/api.ts', import.meta.url), 'utf8');
const page = await readFile(new URL('./fixtures/dictionary-consumer.tsx', import.meta.url), 'utf8');
const names = await readFile(new URL('../src/pages/dictionary/names.tsx', import.meta.url), 'utf8');
for (const [text, pattern, label] of [
  [api, 'EventSource', 'dictionary notification stream'],
  [api, '/api/v1/platform/dictionaries/', 'platform-wide stream URL'],
  [api, 'Last-Event-ID', 'resumable event cursor'],
  [names, 'subscribeDictionaryNotifications', 'dictionary stream subscription'],
  [page, 'dictionary.reselect', 'disabled selected value feedback'],
  [page, 'dictionary.streamDisconnected', 'stream failure feedback'],
  [page, 'value={value}', 'controlled selected value'],
]) {
  if (!text.includes(pattern)) throw new Error(`Missing ${label}`);
}
if (api.includes('mock') || page.includes('mock')) throw new Error('Dictionary UI must not use mock data');
console.log('Dictionary UI subscribes to platform-wide notifications and preserves dirty selections');
