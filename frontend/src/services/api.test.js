import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiError, getCharms, getRecommendation } from './api.js';

test('GET catalog uses the configured API boundary without a JSON content type', async () => {
  const originalFetch = globalThis.fetch;
  let captured;
  globalThis.fetch = async (url, options) => {
    captured = { url, options };
    return new Response('[]', {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });
  };

  try {
    assert.deepEqual(await getCharms(), []);
    assert.equal(captured.url, 'http://localhost:8080/api/charms');
    assert.deepEqual(captured.options.headers, {});
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('POST recommendation sends JSON and surfaces HTTP status failures', async () => {
  const originalFetch = globalThis.fetch;
  let captured;
  globalThis.fetch = async (url, options) => {
    captured = { url, options };
    return new Response('', { status: 503, statusText: 'Service Unavailable' });
  };

  try {
    await assert.rejects(
      getRecommendation({ fullName: 'Test User' }),
      (error) => error instanceof ApiError && error.status === 503,
    );
    assert.equal(captured.url, 'http://localhost:8080/api/recommendations');
    assert.equal(captured.options.method, 'POST');
    assert.equal(captured.options.headers['Content-Type'], 'application/json');
  } finally {
    globalThis.fetch = originalFetch;
  }
});
