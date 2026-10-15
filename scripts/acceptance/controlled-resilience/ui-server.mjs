import http from 'node:http';
import { createRequire } from 'node:module';
import { pathToFileURL } from 'node:url';

const require = createRequire(process.cwd() + '/package.json');
const { createServer } = await import(pathToFileURL(require.resolve('vite')));
const sockets = new Set();
const proxy = http.createServer((request, response) => {
  const upstream = http.request({
    hostname: '127.0.0.1', port: 28081, path: request.url, method: request.method,
    headers: { ...request.headers, host: '127.0.0.1:28573', 'x-forwarded-host': '127.0.0.1:28573',
      'x-forwarded-proto': 'http', 'x-forwarded-port': '28573' },
  }, incoming => {
    incoming.on('aborted', () => response.destroy());
    incoming.on('error', () => response.destroy());
    response.writeHead(incoming.statusCode, incoming.headers); incoming.pipe(response);
  });
  upstream.on('error', () => response.destroy());
  response.on('close', () => upstream.destroy()); request.pipe(upstream);
});
proxy.on('connection', socket => { sockets.add(socket); socket.on('close', () => sockets.delete(socket)); });
await new Promise(resolve => proxy.listen(28580, '127.0.0.1', resolve));
process.env.SUMMER_BACKEND_ORIGIN = 'http://127.0.0.1:28580';
process.env.SUMMER_UI_PORT = '28573'; process.env.VITE_ENABLE_MOCK = 'false';
const vite = await createServer({ logLevel: 'silent', cacheDir: process.env.SUMMER_RESILIENCE_BROWSER_VITE_CACHE,
  server: { strictPort: true, hmr: false, watch: { ignored: ['**/*'] } } });
await vite.listen(); process.stdout.write('UI_READY\n');
let stopping = false;
async function close() {
  if (stopping) return; stopping = true;
  for (const socket of sockets) socket.destroy();
  await vite.close(); proxy.closeAllConnections();
  await new Promise(resolve => proxy.close(resolve));
}
process.on('SIGTERM', () => close().then(() => process.exit(0)));
process.on('SIGINT', () => close().then(() => process.exit(0)));
