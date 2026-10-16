import { useEffect, useMemo, useRef, useState } from 'react';
import CharmCard from '../components/CharmCard';
import { formatCatalogCategory, isRealtimeStale, watchCatalog } from '../services/catalogService';

function Catalog() {
  const [charms, setCharms] = useState(null);
  const [error, setError] = useState('');
  const [refreshError, setRefreshError] = useState('');
  const [realtimeStatus, setRealtimeStatus] = useState('CONNECTING');
  const [announcement, setAnnouncement] = useState('');
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('all');
  const [stock, setStock] = useState('all');
  const [retryKey, setRetryKey] = useState(0);
  const previousCharms = useRef(null);

  useEffect(() => watchCatalog((nextCharms) => {
    const previous = previousCharms.current;
    if (previous) {
      const oldById = new Map(previous.map((charm) => [charm.id, charm.availabilityStatus]));
      const changed = nextCharms.find((charm) =>
        oldById.has(charm.id) && oldById.get(charm.id) !== charm.availabilityStatus,
      );
      if (changed) {
        setAnnouncement(`${changed.name} hiện ${changed.availabilityStatus === 'IN_STOCK' ? 'còn hàng' : 'hết hàng'}.`);
      }
    }
    previousCharms.current = nextCharms;
    setCharms(nextCharms);
    setError('');
    setRefreshError('');
  }, (_error, { hasData }) => {
    if (hasData) {
      setRefreshError('Không thể tải thông tin tồn kho mới nhất. Đang hiển thị danh mục gần nhất.');
    } else {
      setError('Không thể tải danh mục charm. Vui lòng thử lại.');
    }
  }, setRealtimeStatus), [retryKey]);

  const realtimeStale = isRealtimeStale(realtimeStatus);

  const categories = useMemo(() =>
    [...new Set((charms ?? []).map((charm) => charm.category))].sort(),
  [charms]);

  const visibleCharms = useMemo(() => {
    const query = search.trim().toLocaleLowerCase();
    return (charms ?? []).filter((charm) =>
      (category === 'all' || charm.category === category) &&
      (stock === 'all' || charm.availabilityStatus === stock) &&
      (!query || `${charm.name} ${charm.category} ${formatCatalogCategory(charm.category)}`.toLocaleLowerCase().includes(query)),
    );
  }, [charms, search, category, stock]);

  return (
    <div className="catalog-page">
      <header className="catalog-heading">
        <div>
          <p className="section-kicker">Bộ sưu tập</p>
          <h1>Danh mục charm</h1>
        </div>
        <p className="catalog-heading__meta">
          <span className={`catalog-heading__dot${realtimeStale ? ' catalog-heading__dot--stale' : ''}`} aria-hidden="true" />
          {realtimeStatus === 'SUBSCRIBED'
            ? 'Tồn kho trực tiếp'
            : realtimeStale
              ? 'Đang kết nối lại cập nhật trực tiếp'
              : 'Đang kết nối tồn kho trực tiếp'}
        </p>
      </header>

      <div className="catalog-toolbar">
        <label className="catalog-control catalog-control--search">
          <span>Tìm charm</span>
          <input
            type="search"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Tên hoặc danh mục"
          />
        </label>
        <label className="catalog-control">
          <span>Danh mục</span>
          <select value={category} onChange={(event) => setCategory(event.target.value)}>
            <option value="all">Tất cả danh mục</option>
            {categories.map((item) => <option key={item} value={item}>{formatCatalogCategory(item)}</option>)}
          </select>
        </label>
        <label className="catalog-control">
          <span>Tình trạng hàng</span>
          <select value={stock} onChange={(event) => setStock(event.target.value)}>
            <option value="all">Tất cả charm</option>
            <option value="IN_STOCK">Còn hàng</option>
            <option value="OUT_OF_STOCK">Hết hàng</option>
          </select>
        </label>
      </div>

      <p className="catalog-count" role="status">
        {charms ? `Đang hiển thị ${visibleCharms.length} / ${charms.length} charm` : 'Đang tải charm...'}
      </p>
      <p className="visually-hidden" role="status">{announcement}</p>

      {realtimeStale ? (
        <p className="catalog-live-status" role="status">
          Hệ thống đang kết nối lại cập nhật tồn kho. Danh mục có thể tạm thời chưa phải bản mới nhất.
        </p>
      ) : null}

      {error ? (
        <div className="catalog-message catalog-message--error" role="alert">
          <p>{error}</p>
          <button className="button button--secondary" type="button" onClick={() => setRetryKey((key) => key + 1)}>
            Thử lại
          </button>
        </div>
      ) : null}

      {refreshError ? (
        <div className="catalog-message catalog-message--warning" role="alert">
          <p>{refreshError}</p>
          <button className="button button--secondary" type="button" onClick={() => setRetryKey((key) => key + 1)}>
            Làm mới danh mục
          </button>
        </div>
      ) : null}

      {charms === null && !error ? (
        <div className="catalog-grid" aria-label="Đang tải danh mục" aria-busy="true">
          {Array.from({ length: 12 }, (_, index) => <div className="catalog-skeleton" key={index} />)}
        </div>
      ) : null}

      {charms && visibleCharms.length > 0 ? (
        <div className="catalog-grid">
          {visibleCharms.map((charm) => <CharmCard key={charm.id} charm={charm} />)}
        </div>
      ) : null}

      {charms && visibleCharms.length === 0 ? (
        <div className="catalog-message">
          <h2>{charms.length === 0 ? 'Danh mục chưa có charm' : 'Không có charm phù hợp với bộ lọc'}</h2>
          {charms.length > 0 ? (
            <button className="button button--secondary" type="button" onClick={() => {
              setSearch('');
              setCategory('all');
              setStock('all');
            }}>Xóa bộ lọc</button>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}

export default Catalog;
