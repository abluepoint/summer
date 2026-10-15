import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { createInterface } from 'node:readline';

const require = createRequire(process.cwd() + '/summer-ui/package.json');
const { ws: WebSocket } = require(join(dirname(require.resolve('playwright-core/package.json')), 'lib/utilsBundle.js'));
const socket = new WebSocket('ws://127.0.0.1:20081/ws/v1/messages?enterpriseId=ent-acme&after=0', {
  headers: { Origin: 'http://127.0.0.1:20573', Authorization: 'Bearer ' + process.env.SUMMER_T33_WS_TOKEN },
});
let ready = false;
const input = createInterface({ input: process.stdin });
const timer = setTimeout(() => { console.log('TIMEOUT'); socket.terminate(); input.close(); process.exitCode = 1; }, 12000);
socket.on('message', data => {
  try {
    if (!ready && JSON.parse(data.toString()).type === 'connected') {
      ready = true; clearTimeout(timer); console.log('OPEN');
    }
  } catch { /* Only the handshake type is needed; message contents stay in memory. */ }
});
socket.on('unexpected-response', (_, response) => {
  response.resume(); clearTimeout(timer); console.log('REJECTED'); input.close(); process.exitCode = 1;
});
socket.on('error', () => { clearTimeout(timer); console.log('FAILED'); input.close(); process.exitCode = 1; });
socket.on('close', () => { clearTimeout(timer); input.close(); });
input.on('line', () => socket.close());
process.on('SIGTERM', () => { socket.terminate(); input.close(); });
