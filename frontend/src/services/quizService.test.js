import assert from 'node:assert/strict';
import test from 'node:test';
import {
  loadQuizResult,
  normalizeAnswerResult,
  normalizeQuizResult,
  normalizeQuizSession,
  startQuizSession,
  submitQuizAnswer,
} from './quizService.js';

test('normalizes a 10-question quiz session without exposing answer correctness', () => {
  const result = normalizeQuizSession({
    id: 'session-1',
    questions: Array.from({ length: 10 }, (_, index) => ({
      id: index + 1,
      text: `Question ${index + 1}`,
      options: [
        { id: 'a', text: 'A', is_correct: true },
        { id: 'b', text: 'B', is_correct: false },
        { id: 'c', text: 'C', is_correct: false },
        { id: 'd', text: 'D', is_correct: false },
      ],
    })),
  });

  assert.equal(result.sessionId, 'session-1');
  assert.equal(result.questions.length, 10);
  assert.equal(result.questions[0].options.length, 4);
  assert.equal('is_correct' in result.questions[0].options[0], false);
});

test('rejects quiz sessions that are not exactly 10 questions', () => {
  assert.throws(() => normalizeQuizSession({ questions: [] }), /Unexpected quiz session/);
});

test('normalizes backend answer feedback', () => {
  const result = normalizeAnswerResult({
    isCorrect: false,
    correctAnswerId: 42,
    message: 'Try the highlighted answer.',
  });

  assert.equal(result.isCorrect, false);
  assert.equal(result.correctOptionId, '42');
  assert.equal(result.explanation, 'Try the highlighted answer.');
});

test('reward eligibility changes at six correct answers', () => {
  for (const correctCount of [5, 6]) {
    const answers = Array.from({ length: 10 }, (_, index) => ({
      question: `Question ${index + 1}`,
      isCorrect: index < correctCount,
    }));
    const result = normalizeQuizResult(null, answers, { allowRetry: true });

    assert.equal(result.score, correctCount);
    assert.equal(result.correctCount, correctCount);
    assert.equal(result.incorrectCount, 10 - correctCount);
    assert.equal(result.rewardEligible, correctCount >= 6);
    assert.equal(result.canRetry, true);
  }
});

test('uses backend result details and retry permission when returned', async () => {
  const answers = Array.from({ length: 10 }, (_, index) => ({
    question: `Question ${index + 1}`,
    isCorrect: index < 5,
  }));
  const result = await loadQuizResult({
    sessionId: 'session-1',
    answers,
    finalResult: {
      score: 6,
      correctCount: 6,
      incorrectCount: 4,
      canRetry: false,
      details: [{ questionId: 1, questionText: 'Backend detail', isCorrect: true }],
    },
  });

  assert.equal(result.score, 6);
  assert.equal(result.rewardEligible, true);
  assert.equal(result.canRetry, false);
  assert.equal(result.details[0].question, 'Backend detail');
});

test('prefers backend reward eligibility when it is present', () => {
  const answers = Array.from({ length: 10 }, (_, index) => ({ isCorrect: index < 6 }));
  const result = normalizeQuizResult({
    score: 6,
    correctCount: 6,
    incorrectCount: 4,
    rewardEligible: false,
    details: [],
  }, answers);

  assert.equal(result.rewardEligible, false);
});

test('customer quiz API flow stays public and uses exact start answer and result contracts', async () => {
  const originalFetch = globalThis.fetch;
  const requests = [];
  const backendQuestions = Array.from({ length: 10 }, (_, index) => ({
    id: `question-${index + 1}`,
    prompt: `Question ${index + 1}`,
    options: ['A', 'B', 'C', 'D'].map((label, optionIndex) => ({
      id: `question-${index + 1}-option-${optionIndex + 1}`,
      label,
    })),
  }));
  const responses = [
    { sessionId: 'session-1', questions: backendQuestions },
    { isCorrect: true, correctOptionId: 'question-1-option-1', explanation: 'Correct.' },
    {
      score: 6,
      correctCount: 6,
      incorrectCount: 4,
      rewardEligible: true,
      details: [],
    },
  ];
  globalThis.fetch = async (url, options) => {
    requests.push({ url, options });
    return new Response(JSON.stringify(responses.shift()), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });
  };

  try {
    const session = await startQuizSession();
    const answer = await submitQuizAnswer({
      sessionId: session.sessionId,
      questionId: session.questions[0].id,
      optionId: session.questions[0].options[0].id,
    });
    const answers = Array.from({ length: 10 }, (_, index) => ({ isCorrect: index < 6 }));
    const result = await loadQuizResult({ sessionId: session.sessionId, answers });

    assert.equal(session.questions.length, 10);
    assert.equal('correctIndex' in session.questions[0], false);
    assert.equal(answer.isCorrect, true);
    assert.equal(result.rewardEligible, true);
    assert.equal(requests[0].url, 'http://localhost:8080/api/quiz/start');
    assert.equal(requests[0].options.headers.Authorization, undefined);
    assert.equal(requests[1].url, 'http://localhost:8080/api/quiz/answer');
    assert.deepEqual(JSON.parse(requests[1].options.body), {
      sessionId: 'session-1',
      questionId: 'question-1',
      optionId: 'question-1-option-1',
    });
    assert.equal(
      requests[2].url,
      'http://localhost:8080/api/quiz/result?sessionId=session-1',
    );
    assert.equal(requests[2].options.headers.Authorization, undefined);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('rejects inconsistent backend result totals', () => {
  const answers = Array.from({ length: 10 }, () => ({ isCorrect: false }));
  assert.throws(() => normalizeQuizResult({ score: 6, incorrectCount: 5 }, answers),
    /Unexpected quiz result/);
});
