import { useState } from 'react';
import { recommendationSourceLabels } from '../services/recommendationService';

function RecommendationCard({ recommendation, symbol, fallback, position }) {
  const [imageFailed, setImageFailed] = useState(false);
  const { charm, rank, displayOrder, reason, source } = recommendation;
  const imageUrl = charm.imageUrl;

  return (
    <article className="result-card">
      <div className="result-card__media">
        {imageUrl && !imageFailed ? (
          <img src={imageUrl} alt="" loading="lazy" onError={() => setImageFailed(true)} />
        ) : (
          <span className="result-card__symbol" aria-hidden="true">{symbol}</span>
        )}
      </div>
      <div className="result-card__body">
        <p className="result-card__rank">Pick {rank ?? displayOrder ?? position}{fallback ? ' / Alternative' : ''}</p>
        <h3>{charm.name}</h3>
        {reason ? <p className="result-card__reason">{reason}</p> : null}
        {source ? <p className="result-card__source">Source: {recommendationSourceLabels[source]}</p> : null}
      </div>
    </article>
  );
}

export default RecommendationCard;
