import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiError } from './api.js';
import {
  createManagerRule,
  deleteManagerRule,
  filterManagerRules,
  loadManagerRules,
  RULE_FIELDS,
  sortManagerRules,
  updateManagerRule,
  updateManagerRuleStatus,
  validateManagerRule,
} from './managerRuleService.js';

const ACCESS_TOKEN = 'manager-token';
const ID = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
const CHARM_ID = '11111111-1111-1111-1111-111111111111';
const charms = [{ id: CHARM_ID, name: 'Music Charm' }];
const rule = {
  id: ID, charmId: CHARM_ID, field: 'INTEREST', operator: 'CONTAINS',
  value: 'music', priority: 10, status: 'ACTIVE',
};

function jsonResponse(body, status = 200) {
  return new Response(status === 204 ? null : JSON.stringify(body), {
    status, headers: status === 204 ? {} : { 'Content-Type': 'application/json' },
  });
}

async function captureRequest(action, response = rule, status = 200) {
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

test('loads and sorts real manager rule list with bearer authentication', async () => {
  const lower = { ...rule, id: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', priority: 1 };
  const { captured, result } = await captureRequest(
    () => loadManagerRules({ accessToken: ACCESS_TOKEN }), [lower, rule],
  );
  assert.equal(captured.url, 'http://localhost:8080/api/manager/recommendation-rules');
  assert.equal(captured.options.headers.Authorization, `Bearer ${ACCESS_TOKEN}`);
  assert.deepEqual(result.map((item) => item.id), [ID, lower.id]);
});

test('creates and updates rules through live endpoints', async () => {
  const payload = { ...rule };
  delete payload.id;
  const created = await captureRequest(
    () => createManagerRule(payload, { accessToken: ACCESS_TOKEN }), rule, 201,
  );
  assert.equal(created.captured.options.method, 'POST');
  assert.deepEqual(JSON.parse(created.captured.options.body), payload);

  const updated = await captureRequest(
    () => updateManagerRule(ID, payload, { accessToken: ACCESS_TOKEN }), rule,
  );
  assert.equal(updated.captured.options.method, 'PUT');
  assert.equal(updated.captured.url, `http://localhost:8080/api/manager/recommendation-rules/${ID}`);
});

test('patches status and deletes rules through dedicated endpoints', async () => {
  const patched = await captureRequest(
    () => updateManagerRuleStatus(ID, 'INACTIVE', { accessToken: ACCESS_TOKEN }),
    { ...rule, status: 'INACTIVE' },
  );
  assert.equal(patched.captured.options.method, 'PATCH');
  assert.deepEqual(JSON.parse(patched.captured.options.body), { status: 'INACTIVE' });

  const deleted = await captureRequest(
    () => deleteManagerRule(ID, { accessToken: ACCESS_TOKEN }), null, 204,
  );
  assert.equal(deleted.captured.options.method, 'DELETE');
  assert.equal(deleted.captured.url, `http://localhost:8080/api/manager/recommendation-rules/${ID}`);
});

test('validates approved enums and retains filtering helpers', () => {
  assert.deepEqual(RULE_FIELDS, ['NAME_INITIAL', 'ZODIAC', 'LIFE_PATH', 'INTEREST', 'FAVORITE_ANIMAL', 'FAVORITE_COLOR']);
  assert.deepEqual(validateManagerRule({ ...rule, priority: '10' }, charms), {});
  assert.equal(sortManagerRules([rule])[0], rule);
  assert.deepEqual(filterManagerRules([rule], charms, {
    search: 'music', field: 'INTEREST', status: 'ACTIVE',
  }), [rule]);
});

test('API errors remain typed and invalid contracts have no preview fallback', async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response('', { status: 502, statusText: 'Bad Gateway' });
  try {
    await assert.rejects(
      loadManagerRules({ accessToken: ACCESS_TOKEN }),
      (error) => error instanceof ApiError && error.status === 502,
    );
  } finally {
    globalThis.fetch = originalFetch;
  }

  await assert.rejects(
    () => captureRequest(
      () => loadManagerRules({ accessToken: ACCESS_TOKEN }),
      [{ ...rule, operator: 'MATCHES' }],
    ),
    /Unexpected manager rule response/,
  );
});
