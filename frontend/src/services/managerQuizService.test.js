import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiError } from './api.js';
import {
  importManagerQuiz,
  inspectQuizImport,
  loadManagerQuizzes,
  selectImportedQuiz,
  updateManagerQuizStatus,
  validateManagerQuiz,
} from './managerQuizService.js';

const ACCESS_TOKEN = 'test-manager-access-token';

function questions(count = 10) {
  return Array.from({ length: count }, (_, index) => ({
    prompt: `Question ${index + 1}`,
    options: ['A', 'B', 'C', 'D'],
    correctIndex: index % 4,
    explanation: index === 0 ? 'Because.' : null,
  }));
}

function importData(count = 10) {
  return inspectQuizImport(JSON.stringify({
    title: ' History set ',
    description: ' Vietnam history ',
    questions: questions(count),
  }));
}

function apiQuiz(overrides = {}) {
  return {
    id: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    title: 'History set',
    description: 'Vietnam history',
    status: 'INACTIVE',
    questions: questions().map((question, index) => ({
      ...question,
      id: `10000000-0000-0000-0000-${String(index + 1).padStart(12, '0')}`,
    })),
    ...overrides,
  };
}

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    statusText: status === 200 || status === 201 ? 'OK' : 'Error',
    headers: { 'Content-Type': 'application/json' },
  });
}

test('local inspection requires title and exactly 10 valid questions', () => {
  const valid = importData();
  assert.deepEqual(valid.issues, []);
  assert.equal(valid.title, 'History set');
  assert.equal(valid.description, 'Vietnam history');
  assert.equal(valid.questions[0].explanation, 'Because.');

  const tooFew = importData(9);
  assert.deepEqual(tooFew.issues.map((issue) => issue.path), ['questions']);
  assert.match(tooFew.issues[0].message, /exactly 10/);

  const missingTitle = inspectQuizImport(JSON.stringify({ questions: questions() }));
  assert.deepEqual(missingTitle.issues.map((issue) => issue.path), ['title']);
});

test('invalid JSON and question fields report exact paths', () => {
  assert.match(inspectQuizImport('{').issues[0].message, /Invalid JSON/);
  const invalidQuestions = questions();
  invalidQuestions[0] = {
    prompt: '',
    options: ['A', '', 'C'],
    correctIndex: 4,
    explanation: 42,
  };
  const result = inspectQuizImport(JSON.stringify({
    title: 'History set',
    questions: invalidQuestions,
  }));
  assert.deepEqual(result.issues.map((issue) => issue.path), [
    'questions[0].prompt',
    'questions[0].options',
    'questions[0].options[1]',
    'questions[0].correctIndex',
    'questions[0].explanation',
  ]);
});

test('manager list uses bearer token and normalizes the API contract', async () => {
  const originalFetch = globalThis.fetch;
  let captured;
  globalThis.fetch = async (url, options) => {
    captured = { url, options };
    return jsonResponse([apiQuiz()]);
  };

  try {
    const result = await loadManagerQuizzes({ accessToken: ACCESS_TOKEN });
    assert.equal(result[0].questions.length, 10);
    assert.equal(result[0].questions[0].correctIndex, 0);
    assert.equal(captured.url, 'http://localhost:8080/api/manager/quizzes');
    assert.equal(captured.options.headers.Authorization, `Bearer ${ACCESS_TOKEN}`);
    assert.equal(captured.options.headers['Content-Type'], undefined);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('backend validation posts the normalized import payload', async () => {
  const originalFetch = globalThis.fetch;
  let captured;
  globalThis.fetch = async (url, options) => {
    captured = { url, options };
    return jsonResponse({ valid: true });
  };

  try {
    assert.equal(await validateManagerQuiz(importData(), { accessToken: ACCESS_TOKEN }), true);
    assert.equal(captured.url, 'http://localhost:8080/api/manager/quizzes/validate');
    assert.equal(captured.options.method, 'POST');
    const payload = JSON.parse(captured.options.body);
    assert.equal(payload.title, 'History set');
    assert.equal(payload.questions.length, 10);
    assert.equal(payload.questions[0].explanation, 'Because.');
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('import returns normalized quiz and helper selects it', async () => {
  const originalFetch = globalThis.fetch;
  let captured;
  globalThis.fetch = async (url, options) => {
    captured = { url, options };
    return jsonResponse(apiQuiz(), 201);
  };

  try {
    const imported = await importManagerQuiz(importData(), { accessToken: ACCESS_TOKEN });
    const state = selectImportedQuiz([{ ...apiQuiz(), id: 'old-quiz' }], imported);
    assert.equal(captured.url, 'http://localhost:8080/api/manager/quizzes/import');
    assert.equal(captured.options.method, 'POST');
    assert.equal(imported.status, 'INACTIVE');
    assert.equal(state.selectedId, imported.id);
    assert.equal(state.quizzes[0].id, imported.id);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('activation uses the status endpoint and exact body', async () => {
  const originalFetch = globalThis.fetch;
  let captured;
  globalThis.fetch = async (url, options) => {
    captured = { url, options };
    return jsonResponse(apiQuiz({ status: 'ACTIVE' }));
  };

  try {
    const result = await updateManagerQuizStatus(
      apiQuiz().id,
      'ACTIVE',
      { accessToken: ACCESS_TOKEN },
    );
    assert.equal(
      captured.url,
      `http://localhost:8080/api/manager/quizzes/${apiQuiz().id}/status`,
    );
    assert.equal(captured.options.method, 'PATCH');
    assert.deepEqual(JSON.parse(captured.options.body), { status: 'ACTIVE' });
    assert.equal(result.status, 'ACTIVE');
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('manager API failures remain typed and invalid contracts are rejected', async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response('', {
    status: 403,
    statusText: 'Forbidden',
  });

  try {
    await assert.rejects(
      loadManagerQuizzes({ accessToken: ACCESS_TOKEN }),
      (error) => error instanceof ApiError && error.status === 403,
    );
  } finally {
    globalThis.fetch = originalFetch;
  }

  globalThis.fetch = async () => jsonResponse([{ ...apiQuiz(), status: 'DRAFT' }]);
  try {
    await assert.rejects(
      loadManagerQuizzes({ accessToken: ACCESS_TOKEN }),
      /Unexpected manager quiz response/,
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});
