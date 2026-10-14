import { Link } from 'react-router-dom';

function QuizResult({ result, status, onReload, onRetry }) {
  const isLoading = status === 'loading';

  return (
    <section className="quiz-result" aria-labelledby="quiz-result-title" aria-busy={isLoading}>
      <p className="section-kicker">Vietnam History Quiz</p>
      <h1 id="quiz-result-title">Your quiz result</h1>

      {isLoading ? (
        <p className="quiz-result__status" role="status">Loading your result...</p>
      ) : null}

      {status === 'error' ? (
        <div className="quiz-result__notice" role="alert">
          <p>We could not load the final result. Your score below uses the answers already checked.</p>
          <button className="button button--secondary" type="button" onClick={onReload}>
            Try loading result again
          </button>
        </div>
      ) : null}

      {result ? (
        <>
          <div className="quiz-result__summary">
            <div className="quiz-result__score">
              <span className="quiz-result__label">Final score</span>
              <strong>{result.score}<span> / {result.totalQuestions}</span></strong>
            </div>
            <dl className="quiz-result__counts">
              <div>
                <dt>Correct answers</dt>
                <dd>{result.correctCount}</dd>
              </div>
              <div>
                <dt>Incorrect answers</dt>
                <dd>{result.incorrectCount}</dd>
              </div>
            </dl>
          </div>

          <div className={`quiz-result__reward ${result.rewardEligible ? 'quiz-result__reward--eligible' : ''}`}>
            <p className="quiz-result__label">Reward eligibility</p>
            <h2>{result.rewardEligible ? 'You are eligible for a reward' : 'Not eligible for a reward this time'}</h2>
            <p>{result.rewardEligible ? 'You reached the 6 / 10 requirement.' : 'A score of 6 / 10 is needed.'}</p>
          </div>

          {result.details.length > 0 ? (
            <section className="quiz-result__details" aria-labelledby="quiz-details-title">
              <h2 id="quiz-details-title">Answer details</h2>
              <ol>
                {result.details.map((detail, index) => (
                  <li key={`${detail.id}-${index}`}>
                    <div className="quiz-result__detail-heading">
                      <h3>{detail.question || `Question ${index + 1}`}</h3>
                      <span className={detail.isCorrect ? 'quiz-result__correct' : 'quiz-result__incorrect'}>
                        {detail.isCorrect ? 'Correct' : 'Incorrect'}
                      </span>
                    </div>
                    {detail.selectedAnswer ? <p>Your answer: {detail.selectedAnswer}</p> : null}
                    {!detail.isCorrect && detail.correctAnswer ? <p>Correct answer: {detail.correctAnswer}</p> : null}
                    {detail.explanation ? <p>{detail.explanation}</p> : null}
                  </li>
                ))}
              </ol>
            </section>
          ) : null}
        </>
      ) : null}

      <div className="quiz-result__actions">
        <Link className="button button--secondary" to="/">Return to Home</Link>
        {result?.canRetry ? (
          <button className="button button--primary" type="button" onClick={onRetry}>
            Retry quiz
          </button>
        ) : null}
      </div>
    </section>
  );
}

export default QuizResult;
