import { makePreviewQuizBank } from './quizService.js';

const MAX_IMPORT_BYTES = 1024 * 1024;

export function inspectQuizImport(source, fileName = 'Imported quiz') {
  let data;
  try {
    data = JSON.parse(source);
  } catch (error) {
    return { title: fileName, questions: [], issues: [{ path: '$', message: `Invalid JSON: ${error.message}` }] };
  }

  const issues = [];
  if (!data || typeof data !== 'object' || Array.isArray(data)) {
    issues.push({ path: '$', message: 'Expected a quiz object with a questions array.' });
  }
  const questions = Array.isArray(data?.questions) ? data.questions : [];
  if (!Array.isArray(data?.questions)) {
    issues.push({ path: 'questions', message: 'Questions must be an array.' });
  } else if (questions.length === 0) {
    issues.push({ path: 'questions', message: 'Add at least one question.' });
  }

  questions.forEach((question, index) => {
    const path = `questions[${index}]`;
    if (!question || typeof question !== 'object' || Array.isArray(question)) {
      issues.push({ path, message: 'Question must be an object.' });
      return;
    }
    if (typeof question.prompt !== 'string' || !question.prompt.trim()) {
      issues.push({ path: `${path}.prompt`, message: 'Enter question text.' });
    }
    if (!Array.isArray(question.options) || question.options.length !== 4) {
      issues.push({ path: `${path}.options`, message: 'Provide four answer options.' });
    } else {
      question.options.forEach((option, optionIndex) => {
        if (typeof option !== 'string' || !option.trim()) {
          issues.push({ path: `${path}.options[${optionIndex}]`, message: 'Enter answer option text.' });
        }
      });
    }
    if (!Number.isInteger(question.correctIndex) || question.correctIndex < 0 || question.correctIndex > 3) {
      issues.push({ path: `${path}.correctIndex`, message: 'Choose a correct answer index from 0 to 3.' });
    }
  });

  const title = typeof data?.title === 'string' && data.title.trim()
    ? data.title.trim()
    : fileName.replace(/\.json$/i, '');
  return { title, questions, issues };
}

export async function inspectQuizFile(file) {
  if (file.size > MAX_IMPORT_BYTES) {
    return { title: file.name, questions: [], issues: [{ path: '$', message: 'Choose a JSON file under 1 MB.' }] };
  }
  return inspectQuizImport(await file.text(), file.name);
}

let previewQuizzes = [makePreviewQuizBank()];

export function loadPreviewManagerQuizzes() {
  return new Promise((resolve) => {
    setTimeout(() => resolve(previewQuizzes.map((quiz) => ({ ...quiz, questions: [...quiz.questions] }))), 450);
  });
}

export function savePreviewManagerQuiz(imported) {
  if (imported.issues.length) return Promise.reject(new Error('Quiz preview has validation issues.'));
  const quiz = {
    id: `preview-import-${crypto.randomUUID()}`,
    title: imported.title,
    questions: imported.questions.map((question, index) => ({
      id: `import-question-${index + 1}`,
      prompt: question.prompt.trim(),
      options: question.options.map((option) => option.trim()),
      correctIndex: question.correctIndex,
    })),
  };
  return new Promise((resolve) => {
    setTimeout(() => {
      previewQuizzes = [quiz, ...previewQuizzes];
      resolve({ quiz, quizzes: [...previewQuizzes] });
    }, 350);
  });
}
