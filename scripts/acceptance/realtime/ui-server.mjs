import http from 'node:http';
import { createRequire } from 'node:module';
import { pathToFileURL } from 'node:url';
const require = createRequire(process.cwd() + '/package.json');
const { createServer } = await import(pathToFileURL(require.resolve('vite')));
const headers = request => ({ ...request.headers, host: '127.0.0.1:19573', 'x-forwarded-host': '127.0.0.1:19573', 'x-forwarded-proto': 'http', 'x-forwarded-port': '19573' });
const sockets = new Set();
const proxy = http.createServer((request, response) => {
  const upstream = http.request({ hostname: '127.0.0.1', port: 19081, path: request.url, method: request.method, headers: headers(request) }, incoming => {
    response.writeHead(incoming.statusCode, incoming.headers); incoming.pipe(response);
  });
  upstream.on('error', () => { response.writeHead(502); response.end(); }); request.pipe(upstream);
});
proxy.on('upgrade', (request, socket, head) => {
  sockets.add(socket); socket.on('close', () => sockets.delete(socket));
  const upstream = http.request({ hostname: '127.0.0.1', port: 19081, path: request.url, method: request.method, headers: headers(request) });
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
await new Promise(resolve => proxy.listen(19580, '127.0.0.1', resolve));
process.env.SUMMER_BACKEND_ORIGIN = 'http://127.0.0.1:19580';
process.env.SUMMER_UI_PORT = '19573'; process.env.VITE_ENABLE_MOCK = 'false';
const vite = await createServer({ logLevel: 'silent', cacheDir: process.env.REALTIME_VITE_CACHE, server: { strictPort: true, hmr: false, watch: { ignored: ['**/*'] } } });
await vite.listen(); console.log('UI_READY');
let stopping = false;
async function close() {
  if (stopping) return; stopping = true;
  for (const socket of sockets) socket.destroy();
  await vite.close(); proxy.closeAllConnections(); await new Promise(resolve => proxy.close(resolve)); process.exit(0);
}
process.on('SIGTERM', close); process.on('SIGINT', close);
