import { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  buildRecommendationPayload,
  submitRecommendationProfile,
} from '../services/recommendationService';

const initialProfile = {
  fullName: '',
  birthDate: '',
  interests: '',
  favoriteAnimal: '',
  favoriteColor: '',
};

const animalOptions = ['Mèo', 'Chó', 'Thỏ', 'Rồng', 'Bướm'];
const colorOptions = ['Đỏ', 'Xanh dương', 'Hồng', 'Xanh lá', 'Vàng', 'Tím'];

function validateProfile(profile) {
  const errors = {};
  const payload = buildRecommendationPayload(profile);

  if (!payload.fullName) {
    errors.fullName = 'Vui lòng nhập họ và tên.';
  }

  if (!payload.birthDate) {
    errors.birthDate = 'Vui lòng chọn đầy đủ ngày sinh.';
  }

  if (payload.interests.length === 0) {
    errors.interests = 'Vui lòng nhập ít nhất một sở thích.';
  }

  if (!payload.favoriteAnimal) {
    errors.favoriteAnimal = 'Vui lòng nhập hoặc chọn động vật yêu thích.';
  }

  if (!payload.favoriteColor) {
    errors.favoriteColor = 'Vui lòng nhập hoặc chọn màu sắc yêu thích.';
  }

  return errors;
}

function Recommendation() {
  const navigate = useNavigate();
  const submittingRef = useRef(false);
  const [profile, setProfile] = useState(initialProfile);
  const [errors, setErrors] = useState({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [requestError, setRequestError] = useState('');

  function updateField(event) {
    const { name, value } = event.target;

    setProfile((currentProfile) => ({
      ...currentProfile,
      [name]: value,
    }));

    setErrors((currentErrors) => ({
      ...currentErrors,
      [name]: '',
    }));
    setRequestError('');
  }

  async function handleSubmit(event) {
    event.preventDefault();
    if (submittingRef.current) return;

    const nextErrors = validateProfile(profile);
    setErrors(nextErrors);

    if (Object.keys(nextErrors).length > 0) {
      return;
    }

    submittingRef.current = true;
    setIsSubmitting(true);
    setRequestError('');

    try {
      const result = await submitRecommendationProfile(profile);
      navigate('/recommendation/results', { state: { result } });
    } catch {
      setRequestError('Không thể tải gợi ý charm. Vui lòng thử lại.');
    } finally {
      submittingRef.current = false;
      setIsSubmitting(false);
    }
  }

  return (
    <div className="recommendation-page">
      <section className="recommendation-hero" aria-labelledby="recommendation-title">
        <div>
          <p className="section-kicker">Gợi ý charm</p>
          <h1 id="recommendation-title">Hãy chia sẻ điều tạo nên chiếc charm dành cho bạn.</h1>
          <p>
            Chia sẻ một vài thông tin để khám phá những mẫu charm lấy cảm hứng từ chính bạn.
          </p>
        </div>
      </section>

      <section className="form-shell" aria-label="Biểu mẫu thông tin nhận gợi ý charm">
        <form className="profile-form" noValidate onSubmit={handleSubmit} aria-busy={isSubmitting}>
          <div className="form-field">
            <label htmlFor="fullName">Họ và tên</label>
            <input
              id="fullName"
              name="fullName"
              type="text"
              value={profile.fullName}
              onChange={updateField}
              placeholder="Lâm Phú Bảo Anh"
              autoComplete="name"
              aria-describedby={errors.fullName ? 'fullName-error' : undefined}
              aria-invalid={Boolean(errors.fullName)}
              disabled={isSubmitting}
              required
            />
            {errors.fullName ? (
              <p className="field-error" id="fullName-error">
                {errors.fullName}
              </p>
            ) : null}
          </div>

          <div className="form-field">
            <label htmlFor="birthDate">Ngày sinh</label>
            <input
              id="birthDate"
              name="birthDate"
              type="date"
              value={profile.birthDate}
              onChange={updateField}
              aria-describedby={errors.birthDate ? 'birthDate-error' : undefined}
              aria-invalid={Boolean(errors.birthDate)}
              disabled={isSubmitting}
              required
            />
            {errors.birthDate ? (
              <p className="field-error" id="birthDate-error">
                {errors.birthDate}
              </p>
            ) : null}
          </div>

          <div className="form-field form-field--wide">
            <label htmlFor="interests">Sở thích</label>
            <textarea
              id="interests"
              name="interests"
              value={profile.interests}
              onChange={updateField}
              placeholder="Âm nhạc, bóng đá, ô tô"
              rows="4"
              aria-describedby={
                errors.interests ? 'interests-help interests-error' : 'interests-help'
              }
              aria-invalid={Boolean(errors.interests)}
              disabled={isSubmitting}
              required
            />
            <p className="field-help" id="interests-help">
              Phân tách nhiều sở thích bằng dấu phẩy.
            </p>
            {errors.interests ? (
              <p className="field-error" id="interests-error">
                {errors.interests}
              </p>
            ) : null}
          </div>

          <div className="form-field">
            <label htmlFor="favoriteAnimal">Động vật yêu thích</label>
            <input
              id="favoriteAnimal"
              name="favoriteAnimal"
              type="text"
              value={profile.favoriteAnimal}
              onChange={updateField}
              placeholder="Mèo"
              list="animal-options"
              autoComplete="off"
              aria-describedby={
                errors.favoriteAnimal ? 'favoriteAnimal-error' : undefined
              }
              aria-invalid={Boolean(errors.favoriteAnimal)}
              disabled={isSubmitting}
              required
            />
            <datalist id="animal-options">
              {animalOptions.map((animal) => (
                <option key={animal} value={animal} />
              ))}
            </datalist>
            {errors.favoriteAnimal ? (
              <p className="field-error" id="favoriteAnimal-error">
                {errors.favoriteAnimal}
              </p>
            ) : null}
          </div>

          <div className="form-field">
            <label htmlFor="favoriteColor">Màu sắc yêu thích</label>
            <input
              id="favoriteColor"
              name="favoriteColor"
              type="text"
              value={profile.favoriteColor}
              onChange={updateField}
              placeholder="Hồng"
              list="color-options"
              autoComplete="off"
              aria-describedby={
                errors.favoriteColor ? 'favoriteColor-error' : undefined
              }
              aria-invalid={Boolean(errors.favoriteColor)}
              disabled={isSubmitting}
              required
            />
            <datalist id="color-options">
              {colorOptions.map((color) => (
                <option key={color} value={color} />
              ))}
            </datalist>
            {errors.favoriteColor ? (
              <p className="field-error" id="favoriteColor-error">
                {errors.favoriteColor}
              </p>
            ) : null}
          </div>

          <div className="form-actions">
            {requestError ? <p className="field-error" role="alert">{requestError}</p> : null}
            {isSubmitting ? <p className="field-help" role="status">Đang tìm charm phù hợp với bạn...</p> : null}
            <button className="button button--primary" type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Đang tìm gợi ý...' : 'Xem gợi ý charm'}
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}

export default Recommendation;
