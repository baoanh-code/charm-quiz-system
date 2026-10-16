import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiError } from './api.js';
import {
  clearManagerCharmImage,
  createManagerCharm,
  deleteManagerCharm,
  deleteManagerCharmAndReload,
  filterManagerCharms,
  loadManagerCharms,
  managerCharmErrorMessage,
  saveManagerCharm,
  selectManagerCharmImage,
  updateManagerCharm,
  updateManagerCharmStatus,
  updateManagerCharmStockStatus,
  uploadManagerCharmImage,
} from './managerCharmService.js';

const ACCESS_TOKEN = 'manager-token';
const ID = '11111111-1111-1111-1111-111111111111';
const charm = {
  id: ID, name: 'Music Charm', description: null,
  imageUrl: 'https://example.com/music.png', category: 'Interests', tags: ['music'],
  status: 'ACTIVE', stockStatus: 'IN_STOCK',
};

function jsonResponse(body, status = 200) {
  return new Response(status === 204 ? null : JSON.stringify(body), {
    status, headers: status === 204 ? {} : { 'Content-Type': 'application/json' },
  });
}

async function captureRequest(action, response = charm, status = 200) {
  const originalFetch = globalThis.fetch;
  let captured;
  globalThis.fetch = async (url, options) => {
    captured = { url, options };
    return jsonResponse(response, status);
  };
  try {
    const result = await action();
    return { captured, result };
  } finally {
    globalThis.fetch = originalFetch;
  }
}

test('loads real manager charm list with bearer authentication', async () => {
  const { captured, result } = await captureRequest(
    () => loadManagerCharms({ accessToken: ACCESS_TOKEN }), [charm],
  );
  assert.equal(captured.url, 'http://localhost:8080/api/manager/charms');
  assert.equal(captured.options.headers.Authorization, `Bearer ${ACCESS_TOKEN}`);
  assert.deepEqual(result, [charm]);
});

test('creates and updates charms through live endpoints', async () => {
  const payload = { ...charm };
  delete payload.id;
  const created = await captureRequest(
    () => createManagerCharm(payload, { accessToken: ACCESS_TOKEN }), charm, 201,
  );
  assert.equal(created.captured.options.method, 'POST');
  assert.deepEqual(JSON.parse(created.captured.options.body), payload);

  const updated = await captureRequest(
    () => updateManagerCharm(ID, payload, { accessToken: ACCESS_TOKEN }), charm,
  );
  assert.equal(updated.captured.url, `http://localhost:8080/api/manager/charms/${ID}`);
  assert.equal(updated.captured.options.method, 'PUT');
});

test('uses dedicated status and stock-status patches', async () => {
  const statusResult = await captureRequest(
    () => updateManagerCharmStatus(ID, 'INACTIVE', { accessToken: ACCESS_TOKEN }),
    { ...charm, status: 'INACTIVE' },
  );
  assert.equal(statusResult.captured.options.method, 'PATCH');
  assert.equal(statusResult.captured.url, `http://localhost:8080/api/manager/charms/${ID}/status`);
  assert.deepEqual(JSON.parse(statusResult.captured.options.body), { status: 'INACTIVE' });

  const stockResult = await captureRequest(
    () => updateManagerCharmStockStatus(ID, 'OUT_OF_STOCK', { accessToken: ACCESS_TOKEN }),
    { ...charm, stockStatus: 'OUT_OF_STOCK' },
  );
  assert.equal(stockResult.captured.url, `http://localhost:8080/api/manager/charms/${ID}/stock-status`);
  assert.deepEqual(JSON.parse(stockResult.captured.options.body), { stockStatus: 'OUT_OF_STOCK' });
});

test('uploads image as multipart without forcing JSON content type', async () => {
  const file = new Blob(['image'], { type: 'image/png' });
  const { captured, result } = await captureRequest(
    () => uploadManagerCharmImage(file, { accessToken: ACCESS_TOKEN }),
    { imageUrl: 'https://project.supabase.co/storage/charm.png' }, 201,
  );
  assert.equal(result, 'https://project.supabase.co/storage/charm.png');
  assert.equal(captured.url, 'http://localhost:8080/api/manager/charm-images');
  assert.ok(captured.options.body instanceof FormData);
  assert.equal(captured.options.headers['Content-Type'], undefined);
});

test('selects, removes, and reselects an image without stale file state', () => {
  const original = { imageUrl: '', imageFile: null, name: 'Test charm' };
  const firstFile = new Blob(['first'], { type: 'image/png' });
  const secondFile = new Blob(['second'], { type: 'image/webp' });

  const selected = selectManagerCharmImage(original, firstFile, 'data:image/png;base64,first');
  assert.equal(selected.imageFile, firstFile);
  assert.equal(selected.imageUrl, 'data:image/png;base64,first');

  const cleared = clearManagerCharmImage(selected);
  assert.equal(cleared.imageFile, null);
  assert.equal(cleared.imageUrl, '');

  const reselected = selectManagerCharmImage(
    cleared,
    secondFile,
    'data:image/webp;base64,second',
  );
  assert.equal(reselected.imageFile, secondFile);
  assert.equal(reselected.imageUrl, 'data:image/webp;base64,second');
});

test('uploads a selected image before creating the charm with its public URL', async () => {
  const originalFetch = globalThis.fetch;
  const calls = [];
  const file = new Blob(['image'], { type: 'image/png' });
  const imageUrl = 'https://project.supabase.co/storage/v1/object/public/charm-images/test.png';
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options });
    return calls.length === 1
      ? jsonResponse({ imageUrl }, 201)
      : jsonResponse(charm, 201);
  };
  try {
    const result = await saveManagerCharm({
      draft: { ...charm, id: undefined, imageUrl: 'data:image/png;base64,preview', imageFile: file },
    }, { accessToken: ACCESS_TOKEN });

    assert.deepEqual(result, charm);
    assert.equal(calls.length, 2);
    assert.equal(calls[0].url, 'http://localhost:8080/api/manager/charm-images');
    assert.ok(calls[0].options.body instanceof FormData);
    assert.equal(calls[1].url, 'http://localhost:8080/api/manager/charms');
    assert.equal(JSON.parse(calls[1].options.body).imageUrl, imageUrl);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('creates with an existing HTTPS image URL without uploading it again', async () => {
  const originalFetch = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options });
    return jsonResponse(charm, 201);
  };
  try {
    await saveManagerCharm({
      draft: { ...charm, id: undefined, imageFile: null },
    }, { accessToken: ACCESS_TOKEN });

    assert.equal(calls.length, 1);
    assert.equal(calls[0].url, 'http://localhost:8080/api/manager/charms');
    assert.equal(JSON.parse(calls[0].options.body).imageUrl, charm.imageUrl);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('keeps upload failures distinguishable and provides a useful manager message', async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => jsonResponse({
    code: 'UPSTREAM_ERROR', message: 'Upstream service request failed.',
  }, 502);
  try {
    await assert.rejects(
      saveManagerCharm({
        draft: {
          ...charm,
          id: undefined,
          imageUrl: 'data:image/png;base64,preview',
          imageFile: new Blob(['image'], { type: 'image/png' }),
        },
      }, { accessToken: ACCESS_TOKEN }),
      (error) => {
        assert.equal(error.action, 'upload');
        assert.equal(error.status, 502);
        assert.equal(error.code, 'UPSTREAM_ERROR');
        assert.match(managerCharmErrorMessage(error), /Supabase Storage rejected/);
        return true;
      },
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('deletes through manager API and propagates typed failures', async () => {
  const deleted = await captureRequest(
    () => deleteManagerCharm(ID, { accessToken: ACCESS_TOKEN }), null, 204,
  );
  assert.equal(deleted.captured.options.method, 'DELETE');
  assert.equal(deleted.captured.url, `http://localhost:8080/api/manager/charms/${ID}`);

  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response('', { status: 409, statusText: 'Conflict' });
  try {
    await assert.rejects(
      deleteManagerCharm(ID, { accessToken: ACCESS_TOKEN }),
      (error) => error instanceof ApiError && error.status === 409,
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('reloads live inventory after a successful delete', async () => {
  const originalFetch = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options });
    return calls.length === 1 ? jsonResponse(null, 204) : jsonResponse([charm]);
  };
  try {
    const result = await deleteManagerCharmAndReload(ID, { accessToken: ACCESS_TOKEN });
    assert.deepEqual(result, [charm]);
    assert.equal(calls.length, 2);
    assert.equal(calls[0].options.method, 'DELETE');
    assert.equal(calls[1].options.method, undefined);
    assert.equal(calls[1].url, 'http://localhost:8080/api/manager/charms');
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('invalid live response is rejected and filters keep inactive charms', async () => {
  await assert.rejects(
    () => captureRequest(
      () => loadManagerCharms({ accessToken: ACCESS_TOKEN }),
      [{ ...charm, imageUrl: '' }],
    ),
    /Unexpected manager charm response/,
  );
  const inactive = { ...charm, status: 'INACTIVE', stockStatus: 'OUT_OF_STOCK' };
  assert.deepEqual(filterManagerCharms([inactive], {
    search: ' music ', category: 'Interests', status: 'INACTIVE', stockStatus: 'OUT_OF_STOCK',
  }), [inactive]);
});
