import { Link, useLocation } from 'react-router-dom';
import RecommendationGroup from '../components/RecommendationGroup';
import { recommendationGroups } from '../services/recommendationService';

function RecommendationResults() {
  const { state } = useLocation();
  const result = state?.result;

  if (!result) {
    return (
      <div className="results-page results-page--empty">
        <p className="section-kicker">Gợi ý charm</p>
        <h1>Gợi ý phù hợp bắt đầu từ chính bạn.</h1>
        <p>Chia sẻ thông tin để xem gợi ý charm theo năm tiêu chí.</p>
        <Link className="button button--primary" to="/recommendation">Bắt đầu nhận gợi ý</Link>
      </div>
    );
  }

  const firstName = result.profile?.fullName?.trim().split(/\s+/).at(-1);

  return (
    <div className="results-page">
      <header className="results-intro">
        <div>
          <p className="section-kicker">Gợi ý charm</p>
          <h1>{firstName ? `${firstName}, charm dành cho bạn` : 'Charm dành cho bạn'}</h1>
          <p>
            {result.isPreview
              ? 'Đây là bản xem trước kết quả phù hợp. Gợi ý cuối cùng có thể thay đổi.'
              : 'Khám phá những mẫu charm được chọn theo từng phần trong thông tin của bạn.'}
          </p>
        </div>
        <Link className="button button--secondary" to="/recommendation">Thử thông tin khác</Link>
      </header>

      {result.isPartial && result.hasResults ? (
        <p className="result-notice" role="status">Một số mục chưa có charm phù hợp. Các gợi ý khác của bạn ở bên dưới.</p>
      ) : null}
      {result.hasFallback ? (
        <p className="result-notice result-notice--fallback">Gợi ý thay thế được hiển thị khi chưa có kết quả khớp chính xác.</p>
      ) : null}
      {!result.hasResults ? (
        <div className="results-none" role="status">
          <h2>Hiện chưa có charm phù hợp</h2>
          <p>Hãy thử thông tin khác để khám phá thêm gợi ý.</p>
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
