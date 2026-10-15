
import { Link } from 'react-router-dom';
import heroImage from '../assets/hero-heritage.jpg';
import recommendationImage from '../assets/feature-recommendation.jpg';
import catalogImage from '../assets/feature-catalog.jpg';
import historyImage from '../assets/feature-history.jpg';
import FeatureCard from '../components/FeatureCard';

const features = [
  {
    eyebrow: '01',
    title: 'Nhận gợi ý charm',
    description:
      'Trả lời vài câu hỏi nhanh để tìm charm phù hợp với bạn.',
    href: '/recommendation',
    cta: 'Bắt đầu nhận gợi ý',
    accent: 'red',
    image: recommendationImage,
  },
  {
    eyebrow: '02',
    title: 'Xem danh mục charm',
    description:
      'Khám phá các mẫu charm trực tiếp từ trang chủ mà không cần qua quy trình gợi ý.',
    href: '/catalog',
    cta: 'Xem danh mục',
    accent: 'coral',
    image: catalogImage,
  },
  {
    eyebrow: '03',
    title: 'Chơi đố vui Lịch sử Việt Nam',
    description:
      'Tham gia bài đố vui ngắn, phù hợp khi truy cập nhanh bằng mã QR trên điện thoại.',
    href: '/history-quiz',
    cta: 'Chơi ngay',
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
            <span aria-hidden="true">✦</span> Khám phá lịch sử Việt Nam
          </p>
          <h1 id="home-title">
            <span>Đeo một chiếc charm.</span>
            <em>Mang theo một câu chuyện.</em>
          </h1>
          <p className="hero-description">
            Tìm chiếc charm kết nối cá tính của bạn với một câu chuyện đầy ý nghĩa.
          </p>

          <div className="hero-actions" aria-label="Các thao tác trên trang chủ">
            <Link className="button button--primary" to="/recommendation">
              Tìm charm dành cho bạn <span aria-hidden="true">↗</span>
            </Link>
            <Link className="button button--secondary" to="/catalog">
              Khám phá bộ sưu tập
            </Link>
          </div>
        </div>

        <div className="hero-media">
          <div className="hero-visual" aria-hidden="true">
            <img className="hero-visual__image" src={heroImage} alt="" />
            <p className="hero-visual__caption">Cảm hứng từ<br />di sản Việt Nam</p>
          </div>
          <p className="hero-image-note">Hình ảnh chỉ mang tính chất minh họa.</p>
        </div>
      </section>

      <section className="features-section" aria-labelledby="features-title">
        <div className="section-heading">
          <h2 id="features-title">Bắt đầu <em>hành trình</em> của bạn</h2>
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
