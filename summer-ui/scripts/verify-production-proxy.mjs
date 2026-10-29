import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { createServer } from 'node:http';
import { once } from 'node:events';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { attachWebSocketEcho, verifyWebSocketProxy } from './websocket-proxy-probe.mjs';

const ui = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const backendRequests = [];
const backend = createServer((request, response) => {
  backendRequests.push(`${request.method} ${request.url}`);
  response.setHeader('Content-Type', 'application/json');
  response.end(JSON.stringify({ method: request.method, url: request.url }));
});
const echo = attachWebSocketEcho(backend);
backend.listen(0, '0.0.0.0');
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

const publicPort = await availablePort();
const origin = `http://127.0.0.1:${publicPort}`;
let containerId;
try {
  containerId = execFileSync('docker', [
    'run', '--rm', '-d',
    '-p', `127.0.0.1:${publicPort}:8193`,
    '-v', `${resolve(ui, 'deploy', 'Caddyfile')}:/etc/caddy/Caddyfile:ro`,
    '-v', `${resolve(ui, 'dist')}:/srv/summer-ui:ro`,
    '-e', 'SUMMER_PUBLIC_HOST=:8193',
    '-e', 'SUMMER_UI_DIST=/srv/summer-ui',
    '-e', `SUMMER_BACKEND_ORIGIN=http://host.docker.internal:${backendPort}`,
    'caddy:2',
  ], { encoding: 'utf8' }).trim();

  let ready = false;
  for (let i = 0; i < 100; i++) {
    try {
      const response = await fetch(`${origin}/login`);
      if (response.ok) {
        ready = true;
        break;
      }
    } catch {
      await new Promise((resolve) => setTimeout(resolve, 100));
    }
  }
  assert.ok(ready, 'production proxy must start');

  for (const path of [
    '/login', '/oauth2/callback?code=probe', '/customer/page/create',
    '/customer/detail/probe', '/dashboard/workplace',
  ]) {
    const response = await fetch(`${origin}${path}`);
    assert.equal(response.status, 200, path);
    const html = await response.text();
    assert.match(html, /<div id="root">/, path);
    assert.match(html, /<script\b[^>]*src="\/assets\/[^"]+\.js"/, path);
  }
  assert.deepEqual(backendRequests, []);
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
    const response = await fetch(`${origin}${path}`, { method });
    assert.equal(response.status, 200, `${method} ${path}`);
    assert.deepEqual(await response.json(), { method, url: path });
  }
  assert.equal(backendRequests.length, 9);
  await verifyWebSocketProxy(origin, echo.upgrades);
  console.log('Production Caddy proxy passed (14 HTTP requests, 1 WebSocket upgrade, 2 echoes)');
} finally {
  if (containerId) execFileSync('docker', ['stop', containerId], { stdio: 'ignore' });
  await echo.close();
  backend.closeAllConnections();
  await new Promise((resolve) => backend.close(resolve));
  if (containerId) assert.equal(execFileSync('docker', ['ps', '-aq', '--filter', `id=${containerId}`], { encoding: 'utf8' }).trim(), '', 'temporary Caddy container must be removed');
  console.log(`Production proxy cleanup: container removed=${Boolean(containerId)}, backend listening=${backend.listening}`);
}
