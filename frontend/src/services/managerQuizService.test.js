import assert from 'node:assert/strict';
import test from 'node:test';
import { inspectQuizImport, loadPreviewManagerQuizzes, savePreviewManagerQuiz } from './managerQuizService.js';

const validJson = JSON.stringify({ title: 'History set', questions: [{ prompt: 'When?', options: ['A', 'B', 'C', 'D'], correctIndex: 2 }] });

test('invalid JSON and question fields report exact paths', () => {
  assert.match(inspectQuizImport('{').issues[0].message, /Invalid JSON/);
  const result = inspectQuizImport(JSON.stringify({ questions: [
    { prompt: '', options: ['A', '', 'C'], correctIndex: 4 },
    { prompt: 'Valid?', options: ['A', 'B', 'C', 'D'], correctIndex: 1 },
  ] }));
  assert.deepEqual(result.issues.map((issue) => issue.path), [
    'questions[0].prompt', 'questions[0].options', 'questions[0].correctIndex',
  ]);
  assert.equal(result.questions.length, 2);
});

test('valid import can be added to preview quiz management', async () => {
  const imported = inspectQuizImport(validJson);
  assert.deepEqual(imported.issues, []);
  const { quiz } = await savePreviewManagerQuiz(imported);
  assert.equal(quiz.questions[0].options[2], 'C');
  assert.equal(quiz.questions[0].correctIndex, 2);
  assert.equal((await loadPreviewManagerQuizzes())[0].id, quiz.id);
});

test('invalid import cannot be saved', async () => {
  await assert.rejects(savePreviewManagerQuiz(inspectQuizImport('{')), /validation issues/);
});
