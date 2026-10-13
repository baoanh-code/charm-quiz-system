import { Link } from 'react-router-dom';

function FeatureCard({
  eyebrow,
  title,
  description,
  href,
  cta,
  image,
  accent = 'red',
}) {
  return (
    <article className={`feature-card feature-card--${accent}`}>
      <div className="feature-card__media">
        <img src={image} alt="" width="1672" height="941" loading="lazy" decoding="async" />
        <p className="feature-card__eyebrow">{eyebrow}</p>
      </div>
      <div className="feature-card__content">
        <h3>{title}</h3>
        <p className="feature-card__description">{description}</p>
        <Link className="feature-card__link" to={href}>
          {cta}
          <span className="feature-card__arrow" aria-hidden="true">→</span>
        </Link>
      </div>
    </article>
  );
}

export default FeatureCard;
