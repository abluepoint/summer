import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ui = resolve(dirname(fileURLToPath(import.meta.url)), '..');

const backendRequests = [];
const backend = createServer((request, response) => {
  backendRequests.push(`${request.method} ${request.url}`);
  response.setHeader('Content-Type', 'application/json');
  response.end(JSON.stringify({ method: request.method, url: request.url }));
});
backend.listen(0, '127.0.0.1');
await once(backend, 'listening');
const backendPort = backend.address().port;

async function availablePort() {
  const server = createServer();
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  const port = server.address().port;
  await new Promise((resolve) => server.close(resolve));
  return port;
}

const vitePort = await availablePort();
const origin = `http://127.0.0.1:${vitePort}`;
const vite = spawn(
  process.execPath,
  [join('node_modules', 'vite', 'bin', 'vite.js'), '--host', '127.0.0.1', '--port', String(vitePort), '--strictPort'],
  {
    cwd: ui,
    env: {
      ...process.env,
      SUMMER_BACKEND_ORIGIN: `http://127.0.0.1:${backendPort}`,
      VITE_ENABLE_MOCK: 'false',
    },
    stdio: 'ignore',
  }
);

async function request(path, options) {
  const response = await fetch(`${origin}${path}`, { redirect: 'manual', ...options });
  return { response, body: await response.text() };
}

try {
  let ready = false;
  for (let i = 0; i < 100; i++) {
    if (vite.exitCode !== null) throw new Error('Vite exited before serving');
    try {
      const { response } = await request('/login');
      if (response.ok) {
        ready = true;
        break;
      }
    } catch {
      await new Promise((resolve) => setTimeout(resolve, 100));
    }
  }
  assert.ok(ready, 'Vite must start');

  for (const path of [
    '/login',
    '/oauth2/callback?code=probe',
    '/customer/page/create',
    '/customer/detail/probe',
    '/dashboard/workplace',
  ]) {
    const { response, body } = await request(path);
    assert.equal(response.status, 200, path);
    assert.match(body, /<div id="root"><\/div>/, path);
  }
  assert.deepEqual(backendRequests, [], 'SPA GET requests must stay in Vite');

  for (const [method, path] of [
    ['POST', '/login'],
    ['GET', '/api/customers'],
    ['GET', '/oauth2/authorize?client_id=probe'],
    ['POST', '/oauth2/token'],
    ['GET', '/.well-known/openid-configuration'],
    ['GET', '/connect/logout'],
    ['GET', '/csrf'],
    ['GET', '/internal/v1/diagnostics/redis'],
    ['GET', '/public/dictionaries/customer-segment'],
  ]) {
    const { response, body } = await request(path, { method });
    assert.equal(response.status, 200, `${method} ${path}`);
    assert.deepEqual(JSON.parse(body), { method, url: path });
  }
  assert.equal(backendRequests.length, 9);
  console.log('Vite SPA and backend route split passed (14 requests, Mock disabled)');
} finally {
  vite.kill('SIGTERM');
  await new Promise((resolve) => {
    if (vite.exitCode !== null) return resolve();
    vite.once('exit', resolve);
  });
  await new Promise((resolve) => backend.close(resolve));
}
