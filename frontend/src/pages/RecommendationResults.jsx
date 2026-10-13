import { Link, useLocation } from 'react-router-dom';
import RecommendationGroup from '../components/RecommendationGroup';
import { recommendationGroups } from '../services/recommendationService';

function RecommendationResults() {
  const { state } = useLocation();
  const result = state?.result;

  if (!result) {
    return (
      <div className="results-page results-page--empty">
        <p className="section-kicker">Charm Recommendation</p>
        <h1>Your picks start with you.</h1>
        <p>Share your profile to see charm recommendations across five sections.</p>
        <Link className="button button--primary" to="/recommendation">Start recommendation</Link>
      </div>
    );
  }

  const firstName = result.profile?.fullName?.trim().split(/\s+/).at(-1);

  return (
    <div className="results-page">
      <header className="results-intro">
        <div>
          <p className="section-kicker">Charm Recommendation</p>
          <h1>{firstName ? `${firstName}, your charm picks` : 'Your charm picks'}</h1>
          <p>
            {result.isPreview
              ? 'A preview of how your matches could look. Final picks may differ.'
              : 'Explore the charms chosen from each part of your profile.'}
          </p>
        </div>
        <Link className="button button--secondary" to="/recommendation">Try another profile</Link>
      </header>

      {result.isPartial && result.hasResults ? (
        <p className="result-notice" role="status">Some sections have no available charms yet. Your other picks are below.</p>
      ) : null}
      {result.hasFallback ? (
        <p className="result-notice result-notice--fallback">An alternative pick appears where an exact match is unavailable.</p>
      ) : null}
      {!result.hasResults ? (
        <div className="results-none" role="status">
          <h2>No charms available right now</h2>
          <p>Try a different profile to explore other picks.</p>
        </div>
      ) : null}

      <div className="result-groups">
        {recommendationGroups.map((definition) => (
          <RecommendationGroup
            key={definition.key}
            definition={definition}
            group={result.groups.find((group) => group.key === definition.key) ?? {
              input: '', recommendations: [], fallback: false,
            }}
          />
        ))}
      </div>
    </div>
  );
}

export default RecommendationResults;
