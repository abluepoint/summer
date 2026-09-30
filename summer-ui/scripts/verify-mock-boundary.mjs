import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright-core';

const ui = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const requests = [];
const backend = createServer((request, response) => {
  requests.push(request.url);
  response.setHeader('Content-Type', 'application/json');
  response.end(JSON.stringify({ source: 'backend', path: request.url }));
});
backend.listen(0, '127.0.0.1');
await once(backend, 'listening');

async function availablePort() {
  const server = createServer();
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  const port = server.address().port;
  await new Promise((resolve) => server.close(resolve));
  return port;
}

const browser = await chromium.launch({
  headless: true,
  executablePath:
    process.env.CHROME_PATH ||
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
});
try {
  for (const enabled of [true, false]) {
    const port = await availablePort();
    const origin = `http://127.0.0.1:${port}`;
    const vite = spawn(
      process.execPath,
      [join('node_modules', 'vite', 'bin', 'vite.js'), '--host', '127.0.0.1', '--port', String(port), '--strictPort'],
      {
        cwd: ui,
        env: {
          ...process.env,
          SUMMER_BACKEND_ORIGIN: `http://127.0.0.1:${backend.address().port}`,
          VITE_ENABLE_MOCK: String(enabled),
        },
        stdio: 'ignore',
      }
    );
    try {
      let ready = false;
      for (let i = 0; i < 100; i++) {
        if (vite.exitCode !== null) throw new Error('Vite exited before serving');
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
      assert.ok(ready);
      const page = await browser.newPage();
      await page.goto(`${origin}/login`);
      await page.locator('input[autocomplete="username"]').waitFor();
      async function xhr(path) {
        return page.evaluate(
          (target) =>
            new Promise((resolve, reject) => {
              const request = new XMLHttpRequest();
              request.open('GET', target);
              request.onload = () => resolve(JSON.parse(request.responseText));
              request.onerror = reject;
              request.send();
            }),
          path
        );
      }
      const official = await xhr('/api/user/userInfo');
      if (enabled) assert.equal(official.name, 'admin');
      else assert.equal(official.source, 'backend');
      assert.deepEqual(await xhr('/api/customers'), {
        source: 'backend',
        path: '/api/customers',
      });
      assert.equal(requests.filter((path) => path === '/api/customers').length, enabled ? 1 : 2);
      await page.close();
    } finally {
      vite.kill('SIGTERM');
      await new Promise((resolve) => {
        if (vite.exitCode !== null) return resolve();
        vite.once('exit', resolve);
      });
    }
  }
  console.log('Official development Mock toggles; Summer API always reaches backend');
} finally {
  await browser.close();
  await new Promise((resolve) => backend.close(resolve));
}
