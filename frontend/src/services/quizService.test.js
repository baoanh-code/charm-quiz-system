import assert from 'node:assert/strict';
import test from 'node:test';
import { normalizeAnswerResult, normalizeQuizSession } from './quizService.js';

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
