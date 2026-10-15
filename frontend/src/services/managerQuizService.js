import { managerRequest } from './api.js';

const MAX_IMPORT_BYTES = 1024 * 1024;
const QUESTION_COUNT = 10;
const OPTION_COUNT = 4;
const MANAGER_QUIZZES_ENDPOINT = '/manager/quizzes';

function isNullableString(value) {
  return value == null || typeof value === 'string';
}

function normalizeManagerQuestion(question) {
  if (
    !question ||
    question.id == null ||
    typeof question.prompt !== 'string' ||
    !question.prompt.trim() ||
    !Array.isArray(question.options) ||
    question.options.length !== OPTION_COUNT ||
    question.options.some((option) => typeof option !== 'string' || !option.trim()) ||
    !Number.isInteger(question.correctIndex) ||
    question.correctIndex < 0 ||
    question.correctIndex >= OPTION_COUNT ||
    !isNullableString(question.explanation)
  ) {
    throw new Error('Unexpected manager quiz question response');
  }

  return {
    id: String(question.id),
    prompt: question.prompt,
    options: [...question.options],
    correctIndex: question.correctIndex,
    explanation: question.explanation ?? null,
  };
}

export function normalizeManagerQuiz(quiz) {
  if (
    !quiz ||
    quiz.id == null ||
    typeof quiz.title !== 'string' ||
    !quiz.title.trim() ||
    !isNullableString(quiz.description) ||
    !['ACTIVE', 'INACTIVE'].includes(quiz.status) ||
    !Array.isArray(quiz.questions)
  ) {
    throw new Error('Unexpected manager quiz response');
  }

  return {
    id: String(quiz.id),
    title: quiz.title,
    description: quiz.description ?? null,
    status: quiz.status,
    questions: quiz.questions.map(normalizeManagerQuestion),
  };
}

export function normalizeManagerQuizList(response) {
  if (!Array.isArray(response)) throw new Error('Unexpected manager quiz list response');
  return response.map(normalizeManagerQuiz);
}

export function inspectQuizImport(source, fileName = 'Imported quiz') {
  let data;
  try {
    data = JSON.parse(source);
  } catch (error) {
    return {
      title: fileName,
      description: null,
      questions: [],
      issues: [{ path: '$', message: `Invalid JSON: ${error.message}` }],
    };
  }

  const issues = [];
  const isObject = data && typeof data === 'object' && !Array.isArray(data);
  if (!isObject) {
    issues.push({ path: '$', message: 'Expected a quiz object with a questions array.' });
  }

  const title = typeof data?.title === 'string' ? data.title.trim() : '';
  if (!title) issues.push({ path: 'title', message: 'Enter a quiz title.' });

  const description = data?.description == null
    ? null
    : typeof data.description === 'string'
      ? data.description.trim() || null
      : null;
  if (data?.description != null && typeof data.description !== 'string') {
    issues.push({ path: 'description', message: 'Description must be text.' });
  }

  const rawQuestions = Array.isArray(data?.questions) ? data.questions : [];
  if (!Array.isArray(data?.questions)) {
    issues.push({ path: 'questions', message: 'Questions must be an array.' });
  } else if (rawQuestions.length !== QUESTION_COUNT) {
    issues.push({ path: 'questions', message: 'Provide exactly 10 questions.' });
  }

  const questions = rawQuestions.map((question, index) => {
    const path = `questions[${index}]`;
    if (!question || typeof question !== 'object' || Array.isArray(question)) {
      issues.push({ path, message: 'Question must be an object.' });
      return { prompt: '', options: [], correctIndex: null, explanation: null };
    }

    const prompt = typeof question.prompt === 'string' ? question.prompt.trim() : '';
    if (!prompt) issues.push({ path: `${path}.prompt`, message: 'Enter question text.' });

    const rawOptions = Array.isArray(question.options) ? question.options : [];
    if (!Array.isArray(question.options) || rawOptions.length !== OPTION_COUNT) {
      issues.push({ path: `${path}.options`, message: 'Provide four answer options.' });
    }
    const options = rawOptions.map((option, optionIndex) => {
      const normalized = typeof option === 'string' ? option.trim() : '';
      if (!normalized) {
        issues.push({
          path: `${path}.options[${optionIndex}]`,
          message: 'Enter answer option text.',
        });
      }
      return normalized;
    });

    if (
      !Number.isInteger(question.correctIndex) ||
      question.correctIndex < 0 ||
      question.correctIndex >= OPTION_COUNT
    ) {
      issues.push({
        path: `${path}.correctIndex`,
        message: 'Choose a correct answer index from 0 to 3.',
      });
    }

    const explanation = question.explanation == null
      ? null
      : typeof question.explanation === 'string'
        ? question.explanation.trim() || null
        : null;
    if (question.explanation != null && typeof question.explanation !== 'string') {
      issues.push({ path: `${path}.explanation`, message: 'Explanation must be text.' });
    }

    return {
      prompt,
      options,
      correctIndex: question.correctIndex,
      explanation,
    };
  });

  return { title, description, questions, issues };
}

export async function inspectQuizFile(file) {
  if (file.size > MAX_IMPORT_BYTES) {
    return {
      title: file.name,
      description: null,
      questions: [],
      issues: [{ path: '$', message: 'Choose a JSON file under 1 MB.' }],
    };
  }
  return inspectQuizImport(await file.text(), file.name);
}

function importPayload(imported) {
  if (!imported || imported.issues?.length) {
    throw new Error('Quiz import has validation issues.');
  }
  return {
    title: imported.title,
    description: imported.description ?? null,
    questions: imported.questions.map((question) => ({
      prompt: question.prompt,
      options: [...question.options],
      correctIndex: question.correctIndex,
      explanation: question.explanation ?? null,
    })),
  };
}

export async function loadManagerQuizzes(options = {}) {
  return normalizeManagerQuizList(await managerRequest(MANAGER_QUIZZES_ENDPOINT, options));
}

export async function validateManagerQuiz(imported, options = {}) {
  const response = await managerRequest(`${MANAGER_QUIZZES_ENDPOINT}/validate`, {
    ...options,
    method: 'POST',
    body: JSON.stringify(importPayload(imported)),
  });
  if (!response || response.valid !== true) {
    throw new Error('Unexpected manager quiz validation response');
  }
  return true;
}

export async function importManagerQuiz(imported, options = {}) {
  const response = await managerRequest(`${MANAGER_QUIZZES_ENDPOINT}/import`, {
    ...options,
    method: 'POST',
    body: JSON.stringify(importPayload(imported)),
  });
  return normalizeManagerQuiz(response);
}

export async function updateManagerQuizStatus(id, status, options = {}) {
  if (!id || !['ACTIVE', 'INACTIVE'].includes(status)) {
    throw new Error('Invalid manager quiz status update');
  }
  const response = await managerRequest(
    `${MANAGER_QUIZZES_ENDPOINT}/${encodeURIComponent(id)}/status`,
    {
      ...options,
      method: 'PATCH',
      body: JSON.stringify({ status }),
    },
  );
  return normalizeManagerQuiz(response);
}

export function selectImportedQuiz(quizzes, importedQuiz) {
  return {
    quizzes: [importedQuiz, ...quizzes.filter((quiz) => quiz.id !== importedQuiz.id)],
    selectedId: importedQuiz.id,
  };
}
