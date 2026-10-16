import RecommendationCard from './RecommendationCard';

function displayInput(key, input) {
  if (key !== 'birthDate' || !/^\d{4}-\d{2}-\d{2}$/.test(input)) return input;

  return new Intl.DateTimeFormat('vi-VN', { timeZone: 'UTC' }).format(
    new Date(`${input}T00:00:00Z`),
  );
}

function RecommendationGroup({ definition, group }) {
  const { key, title, subtitle, number, symbol } = definition;
  const { recommendations, input, fallback } = group;

  return (
    <section className="result-group" aria-labelledby={`result-${key}`}>
      <div className="result-group__heading">
        <div className="result-group__title">
          <span className="result-group__number" aria-hidden="true">{number}</span>
          <div>
            <h2 id={`result-${key}`}>{title}</h2>
            {input || subtitle ? (
              <p className="result-group__input">{input ? displayInput(key, input) : subtitle}</p>
            ) : null}
          </div>
        </div>
        <span className="result-group__count">
          {recommendations.length} charm
        </span>
      </div>

      {recommendations.length > 0 ? (
        <>
          {fallback ? <p className="result-group__note">Gợi ý mở rộng cho phần thông tin này của bạn.</p> : null}
          <div className="result-grid">
            {recommendations.map((recommendation, index) => (
              <RecommendationCard
                key={recommendation.charm.id ?? `${key}-${index}`}
                recommendation={recommendation}
                symbol={symbol}
                fallback={fallback}
                position={index + 1}
                categoryLabel={title}
              />
            ))}
          </div>
        </>
      ) : (
        <p className="result-group__empty">Hiện chưa có charm phù hợp cho phần thông tin này.</p>
      )}
    </section>
  );
}

export default RecommendationGroup;
