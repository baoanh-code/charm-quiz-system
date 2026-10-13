
import { Link } from 'react-router-dom';
import heroImage from '../assets/hero-heritage.jpg';
import recommendationImage from '../assets/feature-recommendation.jpg';
import catalogImage from '../assets/feature-catalog.jpg';
import historyImage from '../assets/feature-history.jpg';
import FeatureCard from '../components/FeatureCard';

const features = [
  {
    eyebrow: '01',
    title: 'Get Charm Recommendation',
    description:
      'Answer a few quick prompts and move toward the charm that fits your purpose.',
    href: '/recommendation',
    cta: 'Start recommendation',
    accent: 'red',
    image: recommendationImage,
  },
  {
    eyebrow: '02',
    title: 'Browse Charm Catalog',
    description:
      'Explore charm options from the home page without waiting for a guided flow.',
    href: '/catalog',
    cta: 'View catalog',
    accent: 'coral',
    image: catalogImage,
  },
  {
    eyebrow: '03',
    title: 'Play Vietnam History Quiz',
    description:
      'Take a short quiz experience designed for quick mobile visits from a QR scan.',
    href: '/history-quiz',
    cta: 'Play quiz',
    accent: 'gold',
    image: historyImage,
  },
];

function Home() {
  return (
    <div className="home-page">
      <section className="hero-section" aria-labelledby="home-title">
        <div className="hero-copy">
          <p className="section-kicker hero-kicker">
            <span aria-hidden="true">✦</span> Discover Vietnamese History
          </p>
          <h1 id="home-title">
            <span>Wear a Charm.</span>
            <em>Carry a Story.</em>
          </h1>
          <p className="hero-description">
            Find the piece that connects your personality to a meaningful story.
          </p>

          <div className="hero-actions" aria-label="Home page actions">
            <Link className="button button--primary" to="/recommendation">
              Find Your Charm <span aria-hidden="true">↗</span>
            </Link>
            <Link className="button button--secondary" to="/catalog">
              Explore Collection
            </Link>
          </div>
        </div>

        <div className="hero-media">
          <div className="hero-visual" aria-hidden="true">
            <img className="hero-visual__image" src={heroImage} alt="" />
            <p className="hero-visual__caption">Inspired by<br />Vietnamese Heritage</p>
          </div>
          <p className="hero-image-note">Images are for illustrative purposes only.</p>
        </div>
      </section>

      <section className="features-section" aria-labelledby="features-title">
        <div className="section-heading">
          <h2 id="features-title">Start your <em>journey</em></h2>
        </div>

        <div className="feature-grid">
          {features.map((feature) => (
            <FeatureCard key={feature.href} {...feature} />
          ))}
        </div>
      </section>
    </div>
  );
}

export default Home;
