import { Link } from 'react-router-dom';

function FeatureCard({
  eyebrow,
  title,
  description,
  href,
  cta,
  accent = 'violet',
}) {
  return (
    <article className={`feature-card feature-card--${accent}`}>
      <p className="feature-card__eyebrow">{eyebrow}</p>
      <h3>{title}</h3>
      <p>{description}</p>
      <Link className="feature-card__link" to={href}>
        {cta}
        <span aria-hidden="true">→</span>
      </Link>
    </article>
  );
}

export default FeatureCard;
