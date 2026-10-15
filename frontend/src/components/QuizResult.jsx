import { Link } from 'react-router-dom';

function QuizResult({ result, status, onReload, onRetry }) {
  const isLoading = status === 'loading';

  return (
    <section className="quiz-result" aria-labelledby="quiz-result-title" aria-busy={isLoading}>
      <p className="section-kicker">Đố vui Lịch sử Việt Nam</p>
      <h1 id="quiz-result-title">Kết quả</h1>

      {isLoading ? (
        <p className="quiz-result__status" role="status">Đang tải kết quả...</p>
      ) : null}

      {status === 'error' ? (
        <div className="quiz-result__notice" role="alert">
          <p>Không thể tải kết quả cuối cùng. Điểm bên dưới được tính từ những câu trả lời đã kiểm tra.</p>
          <button className="button button--secondary" type="button" onClick={onReload}>
            Tải lại kết quả
          </button>
        </div>
      ) : null}

      {result ? (
        <>
          <div className="quiz-result__summary">
            <div className="quiz-result__score">
              <span className="quiz-result__label">Điểm</span>
              <strong>{result.score}<span> / {result.totalQuestions}</span></strong>
            </div>
            <dl className="quiz-result__counts">
              <div>
                <dt>Số câu đúng</dt>
                <dd>{result.correctCount}</dd>
              </div>
              <div>
                <dt>Số câu sai</dt>
                <dd>{result.incorrectCount}</dd>
              </div>
            </dl>
          </div>

          <div className={`quiz-result__reward ${result.rewardEligible ? 'quiz-result__reward--eligible' : ''}`}>
            <p className="quiz-result__label">Điều kiện nhận quà</p>
            <h2>{result.rewardEligible ? 'Bạn đủ điều kiện nhận quà' : 'Bạn chưa đủ điều kiện nhận quà lần này'}</h2>
            <p>{result.rewardEligible ? 'Bạn đã đạt yêu cầu 6 / 10.' : 'Bạn cần đạt 6 / 10 để nhận quà.'}</p>
          </div>

          {result.details.length > 0 ? (
            <section className="quiz-result__details" aria-labelledby="quiz-details-title">
              <h2 id="quiz-details-title">Chi tiết câu trả lời</h2>
              <ol>
                {result.details.map((detail, index) => (
                  <li key={`${detail.id}-${index}`}>
                    <div className="quiz-result__detail-heading">
                      <h3>{detail.question || `Câu ${index + 1}`}</h3>
                      <span className={detail.isCorrect ? 'quiz-result__correct' : 'quiz-result__incorrect'}>
                        {detail.isCorrect ? 'Đúng' : 'Sai'}
                      </span>
                    </div>
                    {detail.selectedAnswer ? <p>Câu trả lời của bạn: {detail.selectedAnswer}</p> : null}
                    {!detail.isCorrect && detail.correctAnswer ? <p>Đáp án đúng: {detail.correctAnswer}</p> : null}
                    {detail.explanation ? <p>{detail.explanation}</p> : null}
                  </li>
                ))}
              </ol>
            </section>
          ) : null}
        </>
      ) : null}

      <div className="quiz-result__actions">
        <Link className="button button--secondary" to="/">Về trang chủ</Link>
        {result?.canRetry ? (
          <button className="button button--primary" type="button" onClick={onRetry}>
            Làm lại
          </button>
        ) : null}
      </div>
    </section>
  );
}

export default QuizResult;
