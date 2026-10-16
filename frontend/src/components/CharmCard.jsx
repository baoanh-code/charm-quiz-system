import { useCallback, useEffect, useId, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import { formatCatalogCategory } from '../services/catalogService';
import {
  closeCharmLightbox,
  closeCharmLightboxFromBackdrop,
  closeCharmLightboxFromKey,
  openCharmLightbox,
} from '../services/charmLightbox';

const stockLabels = {
  IN_STOCK: 'Còn hàng',
  OUT_OF_STOCK: 'Hết hàng',
};

function CharmCard({ charm, categoryLabel, fallbackSymbol, titleAs = 'h2' }) {
  const [failedUrl, setFailedUrl] = useState(null);
  const [previewOpen, setPreviewOpen] = useState(false);
  const previewTitleId = useId();
  const imageButtonRef = useRef(null);
  const closeButtonRef = useRef(null);
  const imageUrl = charm.thumbnailUrl || charm.imageUrl;
  const hasImage = imageUrl && failedUrl !== imageUrl;
  const availabilityStatus = charm.availabilityStatus ?? charm.stockStatus ?? 'UNKNOWN';
  const stockLabel = stockLabels[availabilityStatus] ?? 'Kiểm tra tình trạng hàng';
  const symbol = fallbackSymbol ?? (charm.category === 'Letters'
    ? charm.name.replace(/^Letter\s+/, '').charAt(0)
    : charm.name.charAt(0));
  const Title = titleAs;

  const closePreview = useCallback(() => {
    setPreviewOpen(closeCharmLightbox());
    window.requestAnimationFrame(() => imageButtonRef.current?.focus());
  }, []);

  useEffect(() => {
    if (!previewOpen) return undefined;

    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    closeButtonRef.current?.focus();

    function handleKeyDown(event) {
      if (!closeCharmLightboxFromKey(true, event.key)) {
        event.preventDefault();
        closePreview();
      }
    }

    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = previousOverflow;
    };
  }, [closePreview, previewOpen]);

  return (
    <article className="catalog-card">
      <div className="catalog-card__media">
        {hasImage ? (
          <button
            ref={imageButtonRef}
            className="catalog-card__image-button"
            type="button"
            aria-label={`Xem ảnh lớn của ${charm.name}`}
            aria-haspopup="dialog"
            onClick={() => setPreviewOpen(openCharmLightbox())}
          >
            <img
              src={imageUrl}
              alt={charm.name}
              loading="lazy"
              decoding="async"
              width="320"
              height="320"
              onError={() => setFailedUrl(imageUrl)}
            />
          </button>
        ) : (
          <span className="catalog-card__symbol" aria-hidden="true">{symbol}</span>
        )}
      </div>
      <div className="catalog-card__body">
        <p className="catalog-card__category">
          {categoryLabel ?? formatCatalogCategory(charm.category)}
        </p>
        <Title className="catalog-card__title">{charm.name}</Title>
        <span className={`catalog-card__stock catalog-card__stock--${availabilityStatus.toLowerCase()}`}>
          <span aria-hidden="true" />{stockLabel}
        </span>
      </div>
      {previewOpen ? createPortal(
        <div
          className="charm-lightbox"
          role="presentation"
          onMouseDown={(event) => {
            if (closeCharmLightboxFromBackdrop(true, event.target === event.currentTarget) === false) {
              closePreview();
            }
          }}
        >
          <section
            className="charm-lightbox__dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby={previewTitleId}
          >
            <header className="charm-lightbox__header">
              <h2 id={previewTitleId}>{charm.name}</h2>
              <button
                ref={closeButtonRef}
                className="charm-lightbox__close"
                type="button"
                aria-label="Đóng xem ảnh"
                onClick={closePreview}
              >
                <X aria-hidden="true" />
              </button>
            </header>
            <div className="charm-lightbox__media">
              <img src={imageUrl} alt={charm.name} />
            </div>
          </section>
        </div>,
        document.body,
      ) : null}
    </article>
  );
}

export default CharmCard;
