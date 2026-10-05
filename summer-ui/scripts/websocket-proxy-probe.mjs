import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';

// Reuse the WebSocket implementation bundled with the pinned Playwright dependency.
const require = createRequire(import.meta.url);
const { ws: WebSocket, wsServer: WebSocketServer } = require(join(
  dirname(require.resolve('playwright-core/package.json')), 'lib', 'utilsBundle.js'
));

export function attachWebSocketEcho(server) {
  const upgrades = [];
  const echo = new WebSocketServer({ server });
  echo.on('connection', (socket, request) => {
    upgrades.push({ url: request.url, origin: request.headers.origin });
    socket.on('message', (data, binary) => socket.send(data, { binary }));
  });
  return {
    upgrades,
    close: () => new Promise((resolve) => {
      for (const socket of echo.clients) socket.terminate();
      echo.close(resolve);
    }),
  };
}

export async function verifyWebSocketProxy(origin, upgrades) {
  const path = '/ws/v1/messages?enterpriseId=proxy-probe&after=7';
  const socket = new WebSocket(`${origin.replace(/^http/, 'ws')}${path}`, {
    origin,
    handshakeTimeout: 3000,
  });
  try {
    await new Promise((resolve, reject) => {
      socket.once('open', resolve);
      socket.once('error', reject);
    });
    for (const payload of ['proxy echo', 'x'.repeat(65536)]) {
      const echoed = await new Promise((resolve, reject) => {
        const timer = setTimeout(() => reject(new Error('WebSocket echo timed out')), 3000);
        socket.once('message', (data) => { clearTimeout(timer); resolve(data.toString()); });
        socket.once('error', (error) => { clearTimeout(timer); reject(error); });
        socket.send(payload);
      });
      assert.equal(echoed, payload, 'proxy must preserve WebSocket payloads');
    }
    assert.deepEqual(upgrades, [{ url: path, origin }], 'upgrade path, query and Origin must reach the backend');
  } catch (error) {
    throw new Error(`WebSocket proxy probe failed: ${error.message}`);
  } finally {
    socket.terminate();
  }
}
