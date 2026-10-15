import { useState } from 'react';
import { formatCatalogCategory } from '../services/catalogService';

function CatalogCard({ charm }) {
  const [failedUrl, setFailedUrl] = useState(null);
  const imageUrl = charm.thumbnailUrl || charm.imageUrl;
  const hasImage = imageUrl && failedUrl !== imageUrl;
  const stockLabel = charm.availabilityStatus === 'IN_STOCK'
    ? 'Còn hàng'
    : charm.availabilityStatus === 'OUT_OF_STOCK'
      ? 'Hết hàng'
      : 'Kiểm tra tình trạng hàng';
  const symbol = charm.category === 'Letters'
    ? charm.name.replace(/^Letter\s+/, '').charAt(0)
    : charm.name.charAt(0);

  return (
    <article className="catalog-card">
      <div className="catalog-card__media">
        {hasImage ? (
          <img
            src={imageUrl}
            alt={charm.name}
            loading="lazy"
            decoding="async"
            width="320"
            height="320"
            onError={() => setFailedUrl(imageUrl)}
          />
        ) : (
          <span className="catalog-card__symbol" aria-hidden="true">{symbol}</span>
        )}
      </div>
      <div className="catalog-card__body">
        <p className="catalog-card__category">{formatCatalogCategory(charm.category)}</p>
        <h2>{charm.name}</h2>
        <span className={`catalog-card__stock catalog-card__stock--${charm.availabilityStatus.toLowerCase()}`}>
          <span aria-hidden="true" />{stockLabel}
        </span>
      </div>
    </article>
  );
}

export default CatalogCard;
