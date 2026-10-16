import { recommendationSourceLabels } from '../services/recommendationService';
import CharmCard from './CharmCard';

function RecommendationCard({ recommendation, symbol, fallback, position, categoryLabel }) {
  const { charm, rank, displayOrder, source } = recommendation;

  return (
    <div className="recommendation-card">
      <p className="result-card__rank">
        Gợi ý {rank ?? displayOrder ?? position}{fallback ? ' / Thay thế' : ''}
      </p>
      <CharmCard
        charm={{ ...charm, availabilityStatus: charm.availabilityStatus ?? 'IN_STOCK' }}
        categoryLabel={categoryLabel}
        fallbackSymbol={symbol}
        titleAs="h3"
      />
      <div className="recommendation-card__context">
        {source ? <p className="result-card__reason">{recommendationSourceLabels[source].reason}</p> : null}
        {source ? <p className="result-card__source">Nguồn: {recommendationSourceLabels[source].label}</p> : null}
      </div>
    </div>
  );
}

export default RecommendationCard;
