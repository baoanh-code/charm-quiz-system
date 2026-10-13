import RecommendationCard from './RecommendationCard';

function displayInput(key, input) {
  if (key !== 'birthDate' || !/^\d{4}-\d{2}-\d{2}$/.test(input)) return input;

  return new Intl.DateTimeFormat('en-GB', { timeZone: 'UTC' }).format(
    new Date(`${input}T00:00:00Z`),
  );
}

function RecommendationGroup({ definition, group }) {
  const { key, title, number, symbol } = definition;
  const { recommendations, input, fallback } = group;

  return (
    <section className="result-group" aria-labelledby={`result-${key}`}>
      <div className="result-group__heading">
        <div className="result-group__title">
          <span className="result-group__number" aria-hidden="true">{number}</span>
          <div>
            <h2 id={`result-${key}`}>{title}</h2>
            {input ? <p className="result-group__input">{displayInput(key, input)}</p> : null}
          </div>
        </div>
        <span className="result-group__count">
          {recommendations.length} {recommendations.length === 1 ? 'charm' : 'charms'}
        </span>
      </div>

      {recommendations.length > 0 ? (
        <>
          {fallback ? <p className="result-group__note">A broader pick for this part of your profile.</p> : null}
          <div className="result-grid">
            {recommendations.map((recommendation, index) => (
              <RecommendationCard
                key={recommendation.charm.id ?? `${key}-${index}`}
                recommendation={recommendation}
                symbol={symbol}
                fallback={fallback}
                position={index + 1}
              />
            ))}
          </div>
        </>
      ) : (
        <p className="result-group__empty">No available charms for this part of your profile right now.</p>
      )}
    </section>
  );
}

export default RecommendationGroup;
