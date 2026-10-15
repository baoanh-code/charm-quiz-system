import { request } from './api.js';

const QUIZ_LENGTH = 10;
const MOCK_DELAY = 450;
const START_ENDPOINT = '/quiz/start';
const ANSWER_ENDPOINT = '/quiz/answer';
const RESULT_ENDPOINT = '/quiz/result';

const sampleQuestions = [
  {
    id: 'trung-sisters',
    prompt: 'Ai đã lãnh đạo cuộc khởi nghĩa chống ách đô hộ nhà Hán vào năm 40?',
    options: ['Hai Bà Trưng', 'Trần Hưng Đạo', 'Lê Lợi', 'Quang Trung'],
    correctIndex: 0,
  },
  {
    id: 'bach-dang',
    prompt: 'Con sông nào gắn liền với những chiến thắng lớn nhờ trận địa cọc gỗ?',
    options: ['Sông Hồng', 'Sông Hương', 'Sông Bạch Đằng', 'Sông Mê Kông'],
    correctIndex: 2,
  },
  {
    id: 'tran-hung-dao',
    prompt: 'Trần Hưng Đạo nổi tiếng với cuộc kháng chiến chống đế quốc nào?',
    options: ['Đế quốc La Mã', 'Đế quốc Mông Cổ', 'Đế quốc Ottoman', 'Đế quốc Anh'],
    correctIndex: 1,
  },
  {
    id: 'le-loi',
    prompt: 'Vị anh hùng nào gắn với truyền thuyết trả gươm tại hồ Hoàn Kiếm?',
    options: ['Lê Lợi', 'Đinh Bộ Lĩnh', 'Gia Long', 'Phan Bội Châu'],
    correctIndex: 0,
  },
  {
    id: 'hue',
    prompt: 'Thành phố nào là kinh đô dưới triều Nguyễn?',
    options: ['Đà Nẵng', 'Cần Thơ', 'Huế', 'Hải Phòng'],
    correctIndex: 2,
  },
  {
    id: 'dong-son',
    prompt: 'Văn hóa Đông Sơn nổi tiếng với hiện vật nào?',
    options: ['Trống đồng', 'Thành đá', 'Quạt giấy', 'Tranh ghép kính'],
    correctIndex: 0,
  },
  {
    id: 'dien-bien-phu',
    prompt: 'Chiến thắng Điện Biên Phủ diễn ra vào năm nào?',
    options: ['1945', '1954', '1968', '1975'],
    correctIndex: 1,
  },
  {
    id: 'declaration',
    prompt: 'Việt Nam tuyên bố độc lập tại Quảng trường Ba Đình thuộc thành phố nào?',
    options: ['Hà Nội', 'Huế', 'Sài Gòn', 'Nha Trang'],
    correctIndex: 0,
  },
  {
    id: 'quang-trung',
    prompt: 'Quang Trung là niên hiệu của vị lãnh tụ Tây Sơn nào?',
    options: ['Nguyễn Huệ', 'Nguyễn Trãi', 'Mạc Đăng Dung', 'Lý Thái Tổ'],
    correctIndex: 0,
  },
  {
    id: 'van-mieu',
    prompt: 'Văn Miếu tại Hà Nội gắn liền với truyền thống nào?',
    options: ['Giao thương đường biển', 'Giáo dục Nho học', 'Ẩm thực cung đình', 'Dệt lụa'],
    correctIndex: 1,
  },
];

const mockAnswers = new Map(
  sampleQuestions.map((question) => [
    question.id,
    {
      correctOptionId: `${question.id}-${question.correctIndex}`,
      explanation: 'Bạn có trực giác lịch sử rất tốt. Hãy tiếp tục nhé!',
    },
  ]),
);

export function makePreviewQuizBank() {
  return {
    id: 'preview-vietnam-history',
    title: 'Lịch sử Việt Nam',
    questions: sampleQuestions.map((question) => ({
      id: question.id,
      prompt: question.prompt,
      options: [...question.options],
      correctIndex: question.correctIndex,
    })),
  };
}

export const isQuizPreview = import.meta.env?.VITE_QUIZ_SOURCE === 'preview';

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
    label: String(label ?? `Lựa chọn ${index + 1}`),
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
    finalResult: response?.result ?? response?.quizResult ?? null,
  };
}

export function normalizeQuizResult(response, answers, { allowRetry = false } = {}) {
  if (!Array.isArray(answers) || answers.length !== QUIZ_LENGTH) {
    throw new Error('Unexpected quiz result response');
  }

  const correctFromAnswers = answers.filter((answer) => answer.isCorrect).length;
  const correctCount = response?.correctCount ?? response?.correctAnswers ??
    response?.correct_count ?? response?.score ?? correctFromAnswers;
  const incorrectCount = response?.incorrectCount ?? response?.incorrectAnswers ??
    response?.incorrect_count ?? QUIZ_LENGTH - correctCount;
  const score = response?.score ?? correctCount;

  if (![score, correctCount, incorrectCount].every((value) =>
    Number.isInteger(value) && value >= 0 && value <= QUIZ_LENGTH) ||
    correctCount + incorrectCount !== QUIZ_LENGTH || score !== correctCount) {
    throw new Error('Unexpected quiz result response');
  }

  const details = response?.details ?? response?.results ?? answers;
  if (!Array.isArray(details)) {
    throw new Error('Unexpected quiz result details');
  }

  const retryAllowed = response?.canRetry ?? response?.retryAllowed ?? allowRetry;
  const backendRewardEligible = response?.rewardEligible ?? response?.reward_eligible;
  if (backendRewardEligible != null && typeof backendRewardEligible !== 'boolean') {
    throw new Error('Unexpected quiz result response');
  }

  return {
    score,
    correctCount,
    incorrectCount,
    totalQuestions: QUIZ_LENGTH,
    rewardEligible: backendRewardEligible ?? score >= 6,
    canRetry: retryAllowed === true,
    details: details.map((detail, index) => ({
      id: String(detail?.questionId ?? detail?.id ?? index + 1),
      question: String(detail?.question ?? detail?.questionText ?? detail?.prompt ?? ''),
      selectedAnswer: String(detail?.selectedAnswer ?? detail?.selectedOption ?? ''),
      correctAnswer: String(detail?.correctAnswer ?? detail?.correctOption ?? ''),
      isCorrect: detail?.isCorrect ?? detail?.is_correct ?? detail?.correct ?? false,
      explanation: String(detail?.explanation ?? ''),
    })),
  };
}

export async function loadQuizResult({ sessionId, answers, finalResult }) {
  if (finalResult || isQuizPreview) {
    return normalizeQuizResult(finalResult, answers, { allowRetry: isQuizPreview });
  }

  const response = await request(`${RESULT_ENDPOINT}?sessionId=${encodeURIComponent(sessionId)}`);
  return normalizeQuizResult(response, answers);
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
