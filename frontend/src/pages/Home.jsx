
import { Link } from 'react-router-dom';
import heroImage from '../assets/hero.png';
import FeatureCard from '../components/FeatureCard';

const features = [
  {
    eyebrow: '01',
    title: 'Get Charm Recommendation',
    description:
      'Answer a few quick prompts and move toward the charm that fits your purpose.',
    href: '/recommendation',
    cta: 'Start recommendation',
    accent: 'violet',
  },
  {
    eyebrow: '02',
    title: 'Browse Charm Catalog',
    description:
      'Explore charm options from the home page without waiting for a guided flow.',
    href: '/catalog',
    cta: 'View catalog',
    accent: 'teal',
  },
  {
    eyebrow: '03',
    title: 'Play Vietnam History Quiz',
    description:
      'Take a short quiz experience designed for quick mobile visits from a QR scan.',
    href: '/history-quiz',
    cta: 'Play quiz',
    accent: 'amber',
  },
];

function Home() {
  return (
    <div className="home-page">
      <section className="hero-section" aria-labelledby="home-title">
        <div className="hero-copy">
          <p className="section-kicker">Charm Quiz System</p>
          <h1 id="home-title">Find your charm, explore the story, start in seconds.</h1>
          <p>
            A mobile-first entry point for customers to get a charm recommendation,
            browse the catalog, or play a quick Vietnam history quiz.
          </p>

          <div className="hero-actions" aria-label="Home page actions">
            <Link className="button button--primary" to="/recommendation">
              Get recommendation
            </Link>
            <Link className="button button--secondary" to="/catalog">
              Browse catalog
            </Link>
          </div>
        </div>

        <div className="hero-visual" aria-hidden="true">
          <img className="hero-visual__image" src={heroImage} alt="" />
          <div className="charm-orbit">
            <span className="charm-orbit__ring" />
            <span className="charm-orbit__piece charm-orbit__piece--top" />
            <span className="charm-orbit__piece charm-orbit__piece--left" />
            <span className="charm-orbit__piece charm-orbit__piece--right" />
          </div>
        </div>
      </section>

      <section className="features-section" aria-labelledby="features-title">
        <div className="section-heading">
          <p className="section-kicker">Main customer paths</p>
          <h2 id="features-title">Choose what you want to do next</h2>
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
