import http from 'node:http';
import { createRequire } from 'node:module';
import { pathToFileURL } from 'node:url';
import { createInterface } from 'node:readline';
const require = createRequire(process.cwd() + '/package.json');
const { createServer } = await import(pathToFileURL(require.resolve('vite')));
const headers = request => ({ ...request.headers, host: '127.0.0.1:23573', 'x-forwarded-host': '127.0.0.1:23573', 'x-forwarded-proto': 'http', 'x-forwarded-port': '23573' });
const sockets = new Set();
const eventPath = '/api/v1/enterprises/ent-acme/dictionaries/customer-segment/events';
const eventSockets = new Set();
const fault = { blocked: false, upstreamAborts: 0, relayAborts: 0, relayErrors: 0, interruptedSockets: 0 };
const proxy = http.createServer((request, response) => {
  const isDictionaryStream = request.url?.split('?')[0] === eventPath;
  const upstream = http.request({ hostname: '127.0.0.1', port: 23081, path: request.url, method: request.method, headers: headers(request) }, incoming => {
    incoming.on('aborted', () => { if (isDictionaryStream) fault.upstreamAborts++; response.destroy(); });
    incoming.on('error', () => response.destroy());
    if (isDictionaryStream && incoming.statusCode === 200) {
      const socket = incoming.socket;
      eventSockets.add(socket); socket.on('close', () => eventSockets.delete(socket));
      if (fault.blocked) { fault.interruptedSockets++; socket.destroy(); return; }
    }
    response.writeHead(incoming.statusCode, incoming.headers); incoming.pipe(response);
  });
  response.on('close', () => upstream.destroy());
  upstream.on('error', () => {
    if (isDictionaryStream || response.headersSent) response.destroy();
    else { response.writeHead(502); response.end(); }
  }); request.pipe(upstream);
});
proxy.on('upgrade', (request, socket, head) => {
  sockets.add(socket); socket.on('close', () => sockets.delete(socket));
  const upstream = http.request({ hostname: '127.0.0.1', port: 23081, path: request.url, method: request.method, headers: headers(request) });
  upstream.on('upgrade', (response, peer, peerHead) => {
    sockets.add(peer); peer.on('close', () => sockets.delete(peer));
    socket.write(`HTTP/1.1 ${response.statusCode} ${response.statusMessage}\r\n${Object.entries(response.headers).map(([key, value]) => `${key}: ${value}\r\n`).join('')}\r\n`);
    if (peerHead.length) socket.write(peerHead);
    if (head.length) peer.write(head);
    peer.pipe(socket); socket.pipe(peer);
    peer.on('error', () => socket.destroy()); socket.on('error', () => peer.destroy());
  });
  upstream.on('response', response => { socket.end(`HTTP/1.1 ${response.statusCode} ${response.statusMessage}\r\nConnection: close\r\n\r\n`); response.resume(); });
  upstream.on('error', () => socket.destroy()); upstream.end();
});
await new Promise(resolve => proxy.listen(23580, '127.0.0.1', resolve));
process.env.SUMMER_BACKEND_ORIGIN = 'http://127.0.0.1:23580';
process.env.SUMMER_UI_PORT = '23573'; process.env.VITE_ENABLE_MOCK = 'false';
const vite = await createServer({ logLevel: 'silent', cacheDir: process.env.DICTIONARY_NOTIFICATIONS_VITE_CACHE, server: {
  strictPort: true, hmr: false, watch: { ignored: ['**/*'] },
  proxy: { '/api': { target: process.env.SUMMER_BACKEND_ORIGIN, configure(proxyServer) {
    proxyServer.on('error', (_error, request, response) => {
      if (request.url?.split('?')[0] !== eventPath) return;
      fault.relayErrors++;
      response.destroy();
    });
    proxyServer.on('proxyRes', (upstream, request, response) => {
      if (request.url?.split('?')[0] !== eventPath) return;
      upstream.on('aborted', () => { fault.relayAborts++; response.destroy(); });
      upstream.on('error', () => response.destroy());
    });
  } } },
} });
await vite.listen(); console.log('UI_READY');
const input = createInterface({ input: process.stdin });
input.on('line', async command => {
  if (!['SSE_DOWN', 'SSE_UP'].includes(command)) return;
  fault.blocked = command === 'SSE_DOWN';
  if (fault.blocked) for (const socket of eventSockets) { fault.interruptedSockets++; socket.destroy(); }
  await new Promise(resolve => setTimeout(resolve, 100));
  console.log('CONTROL ' + JSON.stringify({ command, ...fault }));
});
let stopping = false;
async function close() {
  if (stopping) return; stopping = true;
  input.close(); process.stdin.destroy();
  for (const socket of eventSockets) socket.destroy();
  for (const socket of sockets) socket.destroy();
  await vite.close(); proxy.closeAllConnections(); await new Promise(resolve => proxy.close(resolve)); process.exit(0);
}
process.on('SIGTERM', close); process.on('SIGINT', close);
