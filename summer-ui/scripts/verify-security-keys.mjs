import test, { afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { build } from 'esbuild';

// Compile the actual API module; only the HTTP transport is replaced.
const require = createRequire(import.meta.url);
const axios = require('axios');
const originalAdapter = axios.defaults.adapter;
const compiled = await build({
  entryPoints: [
    fileURLToPath(
      new URL('../src/pages/security-keys/api.ts', import.meta.url)
    ),
  ],
  bundle: true,
  platform: 'node',
  format: 'cjs',
  target: 'es2019',
  external: ['axios'],
  write: false,
});
const apiModule = { exports: {} };
new Function('require', 'module', 'exports', compiled.outputFiles[0].text)(
  require,
  apiModule,
  apiModule.exports
);
const { listSecurityKeys, rotateSecurityKey } = apiModule.exports;
afterEach(() => {
  axios.defaults.adapter = originalAdapter;
});

function respond(data, status = 200, headers = {}) {
  axios.defaults.adapter = async (config) => {
    assert.equal(config.url, '/api/security/keys');
    assert.equal(config.method, 'get');
    const response = { data, status, statusText: '', headers, config };
    if (status >= 400) throw { isAxiosError: true, response };
    return response;
  };
}

test('returns signing-key metadata and accepts an explicitly empty collection', async () => {
  const items = [
    {
      kid: 'key-1',
      algorithm: 'RS256',
      source: 'mysql',
      status: 'ACTIVE',
      version: 1,
      health: 'READY',
    },
  ];
  respond({ success: true, data: { items } });
  assert.deepEqual(await listSecurityKeys(), items);
  respond({ success: true, data: { items: [] } });
  assert.deepEqual(await listSecurityKeys(), []);
});

test('a business failure cannot become an empty successful list', async () => {
  respond(
    {
      success: false,
      error: {
        code: 'KEY_UNAVAILABLE',
        message: 'Key store unavailable',
        violations: [],
      },
      timestamp: '2026-10-05T00:00:00Z',
    },
    200,
    { 'x-request-id': 'key-business-failure' }
  );
  await assert.rejects(listSecurityKeys(), {
    name: 'SummerApiError',
    status: 200,
    code: 'KEY_UNAVAILABLE',
    requestId: 'key-business-failure',
    timestamp: '2026-10-05T00:00:00Z',
    message: 'Key store unavailable',
    violations: [],
  });
});

test('authorization failures retain the structured diagnostic metadata', async () => {
  respond(
    {
      success: false,
      error: {
        code: 'SUMMER_FORBIDDEN',
        message: 'Access denied',
        violations: [],
      },
      requestId: 'key-forbidden',
      timestamp: '2026-10-05T00:00:00Z',
    },
    403
  );
  await assert.rejects(listSecurityKeys(), {
    name: 'SummerApiError',
    status: 403,
    code: 'SUMMER_FORBIDDEN',
    message: 'Access denied',
    violations: [],
    requestId: 'key-forbidden',
    timestamp: '2026-10-05T00:00:00Z',
  });
});

test('non-envelope server failures preserve the response-header request ID', async () => {
  respond('<html>Unavailable</html>', 503, {
    'x-request-id': 'key-server-failure',
  });
  await assert.rejects(listSecurityKeys(), {
    name: 'SummerApiError',
    status: 503,
    requestId: 'key-server-failure',
  });
});

test('network failures are normalized without exposing raw transport details', async () => {
  axios.defaults.adapter = async () => {
    throw { isAxiosError: true, message: 'transport details' };
  };
  await assert.rejects(listSecurityKeys(), {
    name: 'SummerApiError',
    status: 0,
    code: '',
    requestId: '',
    message: 'HTTP network',
  });
});

test('malformed success bodies are rejected instead of showing an empty key store', async () => {
  for (const data of [
    { success: true, data: {} },
    { success: true, data: { items: 'invalid' } },
    {},
  ]) {
    respond(data, 200, { 'x-request-id': 'key-invalid-response' });
    await assert.rejects(listSecurityKeys(), {
      name: 'SummerApiError',
      status: 200,
      requestId: 'key-invalid-response',
    });
  }
});

test('rotation sends the version precondition and returns the new active key', async () => {
  axios.defaults.adapter = async (config) => {
    assert.equal(config.url, '/api/security/keys:rotate');
    assert.equal(config.method, 'post');
    assert.deepEqual(config.data, JSON.stringify({ retentionDays: 30 }));
    assert.equal(config.headers['If-Match'], '"7"');
    assert.ok(config.headers['Idempotency-Key']);
    return {
      data: {
        success: true,
        data: { activeKid: 'key-8', version: 8, retentionDays: 30 },
      },
      status: 200,
      statusText: '',
      headers: { etag: '"8"' },
      config,
    };
  };
  assert.deepEqual(await rotateSecurityKey(30, 7), {
    activeKid: 'key-8',
    version: 8,
    retentionDays: 30,
  });
});
