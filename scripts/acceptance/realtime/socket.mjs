import { createRequire } from 'node:module';
import readline from 'node:readline';
const require = createRequire(process.cwd() + '/summer-ui/package.json');
const path = require('node:path');
const { ws: WebSocket } = require(path.join(path.dirname(require.resolve('playwright-core/package.json')), 'lib/utilsBundle.js'));
const input = readline.createInterface({ input: process.stdin });
const sockets = new Map();
const output = (id, data) => console.log(JSON.stringify({ id, ...data }));
input.on('line', async line => {
  const command = JSON.parse(line);
  const { id, name } = command;
  try {
    if (command.op === 'connect') {
      const state = { events: [], closeCode: null, socket: new WebSocket(command.url, { headers: { Origin: 'http://127.0.0.1:19573', ...(command.cookie ? { Cookie: command.cookie } : {}), ...(command.bearer ? { Authorization: 'Bearer ' + command.bearer } : {}) } }) };
      sockets.set(name, state);
      let replied = false;
      const reply = data => { if (!replied) { replied = true; clearTimeout(timer); output(id, data); } };
      const timer = setTimeout(() => reply({ connected: false, failure: 'timeout' }), 10000);
      state.socket.on('unexpected-response', (_, response) => { response.resume(); reply({ connected: false, status: response.statusCode }); });
      state.socket.on('error', () => reply({ connected: false, failure: 'socket' }));
      state.socket.on('close', code => { state.closeCode = code; reply({ connected: false, closeCode: code }); });
      state.socket.on('message', raw => {
        const event = JSON.parse(raw.toString());
        state.events.push(event);
        if (event.type === 'connected') reply({ connected: true });
      });
      return;
    }
    const state = sockets.get(name);
    if (command.op === 'send') state.socket.send(typeof command.raw === 'string' ? command.raw : JSON.stringify(command.payload));
    if (command.op === 'close') state.socket.close();
    if (command.op === 'observe') await new Promise(resolve => setTimeout(resolve, command.waitMs || 100));
    if (command.op === 'shutdown') {
      for (const state of sockets.values()) { state.socket.on('error', () => {}); state.socket.terminate(); }
      input.close(); output(id, { stopped: true }); process.exit(0);
    }
    output(id, { events: state?.events || [], closeCode: state?.closeCode });
  } catch { output(id, { failure: 'command' }); }
});
