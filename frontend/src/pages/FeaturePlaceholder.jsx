import { Link } from 'react-router-dom';

function FeaturePlaceholder({ title, description }) {
  return (
    <section className="placeholder-page" aria-labelledby="feature-title">
      <p className="section-kicker">Customer feature</p>
      <h1 id="feature-title">{title}</h1>
      <p>{description}</p>
      <Link className="button button--primary" to="/">
        Back to Home
      </Link>
    </section>
  );
}

export default FeaturePlaceholder;
