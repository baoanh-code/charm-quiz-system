import { useMemo, useRef, useState } from 'react';
import { startQuizSession, submitQuizAnswer } from '../services/quizService';

const initialState = {
  sessionId: '',
  questions: [],
  currentIndex: 0,
  score: 0,
  selectedOptionId: '',
  answerResult: null,
  status: 'idle',
  error: '',
  completed: false,
};

function getOptionClass(option, selectedOptionId, answerResult) {
  if (!answerResult) return '';

  if (option.id === answerResult.correctOptionId) {
    return 'quiz-option--correct';
  }

  if (option.id === selectedOptionId && !answerResult.isCorrect) {
    return 'quiz-option--incorrect';
  }

  return '';
}

function HistoryQuiz() {
  const submittingRef = useRef(false);
  const [quiz, setQuiz] = useState(initialState);

  const question = quiz.questions[quiz.currentIndex];
  const totalQuestions = quiz.questions.length;
  const answered = Boolean(quiz.answerResult);
  const isLoading = quiz.status === 'loading';
  const isSubmitting = quiz.status === 'submitting';
  const progressPercent = useMemo(() => {
    if (!totalQuestions) return 0;
    return ((quiz.currentIndex + (answered ? 1 : 0)) / totalQuestions) * 100;
  }, [answered, quiz.currentIndex, totalQuestions]);

  async function handleStart() {
    setQuiz({ ...initialState, status: 'loading' });

    try {
      const session = await startQuizSession();
      setQuiz({
        ...initialState,
        ...session,
        status: 'ready',
      });
    } catch {
      setQuiz({
        ...initialState,
        status: 'error',
        error: 'We could not start the quiz. Please try again.',
      });
    }
  }

  async function handleAnswer(optionId) {
    if (!question || answered || submittingRef.current) return;

    submittingRef.current = true;
    setQuiz((current) => ({
      ...current,
      selectedOptionId: optionId,
      status: 'submitting',
      error: '',
    }));

    try {
      const result = await submitQuizAnswer({
        sessionId: quiz.sessionId,
        questionId: question.id,
        optionId,
      });

      setQuiz((current) => ({
        ...current,
        answerResult: result,
        score: current.score + (result.isCorrect ? 1 : 0),
        status: 'ready',
      }));
    } catch {
      setQuiz((current) => ({
        ...current,
        selectedOptionId: '',
        status: 'ready',
        error: 'We could not submit that answer. Please try again.',
      }));
    } finally {
      submittingRef.current = false;
    }
  }

  function handleNext() {
    setQuiz((current) => {
      const isLastQuestion = current.currentIndex >= current.questions.length - 1;

      if (isLastQuestion) {
        return {
          ...current,
          selectedOptionId: '',
          answerResult: null,
          completed: true,
        };
      }

      return {
        ...current,
        currentIndex: current.currentIndex + 1,
        selectedOptionId: '',
        answerResult: null,
        error: '',
      };
    });
  }

  if (quiz.completed) {
    return (
      <div className="quiz-page">
        <section className="quiz-finish" aria-labelledby="quiz-complete-title">
          <p className="section-kicker">Vietnam History Quiz</p>
          <h1 id="quiz-complete-title">You finished the journey.</h1>
          <p>
            Score: <strong>{quiz.score} / {totalQuestions}</strong>
          </p>
          <button className="button button--primary" type="button" onClick={handleStart}>
            Play again
          </button>
        </section>
      </div>
    );
  }

  return (
    <div className="quiz-page">
      <section className="quiz-hero" aria-labelledby="quiz-title">
        <div>
          <p className="section-kicker">Vietnam History Quiz</p>
          <h1 id="quiz-title">Answer 10 quick questions from Vietnam's story.</h1>
          <p>
            Pick one answer at a time. The backend checks each choice before feedback appears.
          </p>
        </div>
      </section>

      {quiz.status === 'idle' || quiz.status === 'loading' || quiz.status === 'error' ? (
        <section className="quiz-start" aria-label="Start quiz">
          {quiz.error ? <p className="quiz-alert" role="alert">{quiz.error}</p> : null}
          <button className="button button--primary" type="button" onClick={handleStart} disabled={isLoading}>
            {isLoading ? 'Starting quiz...' : 'Start quiz'}
          </button>
        </section>
      ) : null}

      {question ? (
        <section className="quiz-card" aria-labelledby="current-question" aria-busy={isSubmitting}>
          <div className="quiz-progress">
            <span>Question {quiz.currentIndex + 1} / {totalQuestions}</span>
            <span>{quiz.score} correct</span>
          </div>
          <div className="quiz-progress__bar" aria-hidden="true">
            <span style={{ width: `${progressPercent}%` }} />
          </div>

          <h2 id="current-question">{question.prompt}</h2>

          <div className="quiz-options" role="group" aria-label="Answer options">
            {question.options.map((option) => {
              const optionClass = getOptionClass(option, quiz.selectedOptionId, quiz.answerResult);
              const isSelected = option.id === quiz.selectedOptionId;

              return (
                <button
                  className={`quiz-option ${optionClass}`}
                  key={option.id}
                  type="button"
                  onClick={() => handleAnswer(option.id)}
                  disabled={answered || isSubmitting}
                  aria-pressed={isSelected}
                >
                  <span className="quiz-option__mark" aria-hidden="true" />
                  <span>{option.label}</span>
                </button>
              );
            })}
          </div>

          {isSubmitting ? <p className="quiz-status" role="status">Checking answer...</p> : null}
          {quiz.error ? <p className="quiz-alert" role="alert">{quiz.error}</p> : null}

          {quiz.answerResult ? (
            <div className="quiz-feedback" data-correct={quiz.answerResult.isCorrect}>
              <p>
                {quiz.answerResult.isCorrect
                  ? 'Correct answer.'
                  : 'Incorrect answer. The correct answer is highlighted.'}
              </p>
              {quiz.answerResult.explanation ? <p>{quiz.answerResult.explanation}</p> : null}
              <button className="button button--primary" type="button" onClick={handleNext}>
                {quiz.currentIndex === totalQuestions - 1 ? 'See score' : 'Next question'}
              </button>
            </div>
          ) : null}
        </section>
      ) : null}
    </div>
  );
}

export default HistoryQuiz;
