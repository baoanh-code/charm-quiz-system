import { request } from './api.js';

const QUIZ_LENGTH = 10;
const MOCK_DELAY = 450;
const START_ENDPOINT = '/quiz/start';
const ANSWER_ENDPOINT = '/quiz/answer';

const sampleQuestions = [
  {
    id: 'trung-sisters',
    prompt: 'Who led an uprising against Han rule in 40 CE?',
    options: ['The Trung Sisters', 'Tran Hung Dao', 'Le Loi', 'Quang Trung'],
    correctIndex: 0,
  },
  {
    id: 'bach-dang',
    prompt: 'Which river is linked to major Vietnamese victories using wooden stakes?',
    options: ['Red River', 'Perfume River', 'Bach Dang River', 'Mekong River'],
    correctIndex: 2,
  },
  {
    id: 'tran-hung-dao',
    prompt: 'Tran Hung Dao is best known for resisting which empire?',
    options: ['Roman Empire', 'Mongol Empire', 'Ottoman Empire', 'British Empire'],
    correctIndex: 1,
  },
  {
    id: 'le-loi',
    prompt: 'Which hero is connected with the legend of the returned sword at Hoan Kiem Lake?',
    options: ['Le Loi', 'Dinh Bo Linh', 'Gia Long', 'Phan Boi Chau'],
    correctIndex: 0,
  },
  {
    id: 'hue',
    prompt: 'Which city served as the imperial capital under the Nguyen dynasty?',
    options: ['Da Nang', 'Can Tho', 'Hue', 'Hai Phong'],
    correctIndex: 2,
  },
  {
    id: 'dong-son',
    prompt: 'Dong Son culture is famous for which artifact?',
    options: ['Bronze drums', 'Stone castles', 'Paper fans', 'Glass mosaics'],
    correctIndex: 0,
  },
  {
    id: 'dien-bien-phu',
    prompt: 'The Battle of Dien Bien Phu took place in which year?',
    options: ['1945', '1954', '1968', '1975'],
    correctIndex: 1,
  },
  {
    id: 'declaration',
    prompt: 'Vietnam declared independence in Ba Dinh Square in which city?',
    options: ['Hanoi', 'Hue', 'Saigon', 'Nha Trang'],
    correctIndex: 0,
  },
  {
    id: 'quang-trung',
    prompt: 'Quang Trung was the imperial title of which Tay Son leader?',
    options: ['Nguyen Hue', 'Nguyen Trai', 'Mac Dang Dung', 'Ly Thai To'],
    correctIndex: 0,
  },
  {
    id: 'van-mieu',
    prompt: 'The Temple of Literature in Hanoi is associated with which tradition?',
    options: ['Maritime trade', 'Classical education', 'Royal cuisine', 'Silk weaving'],
    correctIndex: 1,
  },
];

const mockAnswers = new Map(
  sampleQuestions.map((question) => [
    question.id,
    {
      correctOptionId: `${question.id}-${question.correctIndex}`,
      explanation: 'Nice history instinct. Keep going.',
    },
  ]),
);

export const isQuizPreview = import.meta.env?.VITE_QUIZ_SOURCE !== 'api';

function getArray(value, keys) {
  for (const key of keys) {
    if (Array.isArray(value?.[key])) return value[key];
  }

  return null;
}

function normalizeOption(option, index, questionId) {
  const id = option?.id ?? option?.optionId ?? option?.answerId ?? option?.key ??
    `${questionId}-option-${index + 1}`;
  const label = option?.label ?? option?.text ?? option?.content ?? option?.answer ??
    option?.title ?? option;

  return {
    id: String(id),
    label: String(label ?? `Option ${index + 1}`),
  };
}

function normalizeQuestion(question, index) {
  const id = question?.id ?? question?.questionId ?? question?.uuid ?? `question-${index + 1}`;
  const rawOptions = getArray(question, ['options', 'answers', 'choices']);

  if (!rawOptions || rawOptions.length !== 4) {
    throw new Error('Unexpected quiz question response');
  }

  return {
    id: String(id),
    prompt: String(question?.prompt ?? question?.question ?? question?.text ?? ''),
    options: rawOptions.map((option, optionIndex) =>
      normalizeOption(option, optionIndex, String(id)),
    ),
  };
}

export function normalizeQuizSession(response) {
  const questions = getArray(response, ['questions', 'items']) ?? (
    Array.isArray(response) ? response : null
  );

  if (!questions || questions.length !== QUIZ_LENGTH) {
    throw new Error('Unexpected quiz session response');
  }

  return {
    sessionId: String(response?.sessionId ?? response?.id ?? response?.quizSessionId ?? ''),
    questions: questions.map(normalizeQuestion),
  };
}

export function normalizeAnswerResult(response) {
  const isCorrect = response?.correct ?? response?.isCorrect ?? response?.is_correct;
  const correctOptionId = response?.correctOptionId ?? response?.correctAnswerId ??
    response?.correct_option_id ?? response?.correct_answer_id;

  if (typeof isCorrect !== 'boolean') {
    throw new Error('Unexpected quiz answer response');
  }

  return {
    isCorrect,
    correctOptionId: correctOptionId == null ? null : String(correctOptionId),
    explanation: response?.explanation ?? response?.message ?? '',
  };
}

function buildMockSession() {
  return normalizeQuizSession({
    sessionId: 'preview-history-quiz',
    questions: sampleQuestions.map((question) => ({
      id: question.id,
      prompt: question.prompt,
      options: question.options.map((label, index) => ({
        id: `${question.id}-${index}`,
        label,
      })),
    })),
  });
}

export async function startQuizSession() {
  if (!isQuizPreview) {
    return normalizeQuizSession(await request(START_ENDPOINT, { method: 'POST' }));
  }

  return new Promise((resolve) => {
    window.setTimeout(() => resolve(buildMockSession()), MOCK_DELAY);
  });
}

export async function submitQuizAnswer({ sessionId, questionId, optionId }) {
  if (!isQuizPreview) {
    return normalizeAnswerResult(await request(ANSWER_ENDPOINT, {
      method: 'POST',
      body: JSON.stringify({ sessionId, questionId, optionId }),
    }));
  }

  return new Promise((resolve) => {
    window.setTimeout(() => {
      const result = mockAnswers.get(questionId);
      resolve(normalizeAnswerResult({
        correct: result?.correctOptionId === optionId,
        correctOptionId: result?.correctOptionId,
        explanation: result?.explanation,
      }));
    }, MOCK_DELAY);
  });
}
