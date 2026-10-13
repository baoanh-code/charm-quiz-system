import { useEffect, useMemo, useRef, useState } from 'react';
import CatalogCard from '../components/CatalogCard';
import { isCatalogPreview, watchCatalog } from '../services/catalogService';

function Catalog() {
  const [charms, setCharms] = useState(null);
  const [error, setError] = useState('');
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
        setAnnouncement(`${changed.name} is now ${changed.availabilityStatus === 'IN_STOCK' ? 'in stock' : 'out of stock'}.`);
      }
    }
    previousCharms.current = nextCharms;
    setCharms(nextCharms);
    setError('');
  }, () => setError('The catalog could not be updated. Please try again.')), [retryKey]);

  const categories = useMemo(() =>
    [...new Set((charms ?? []).map((charm) => charm.category))].sort(),
  [charms]);

  const visibleCharms = useMemo(() => {
    const query = search.trim().toLocaleLowerCase();
    return (charms ?? []).filter((charm) =>
      (category === 'all' || charm.category === category) &&
      (stock === 'all' || charm.availabilityStatus === stock) &&
      (!query || `${charm.name} ${charm.category}`.toLocaleLowerCase().includes(query)),
    );
  }, [charms, search, category, stock]);

  return (
    <div className="catalog-page">
      <header className="catalog-heading">
        <div>
          <p className="section-kicker">The collection</p>
          <h1>Charm Catalog</h1>
        </div>
        <p className="catalog-heading__meta">
          <span className="catalog-heading__dot" aria-hidden="true" />
          {isCatalogPreview ? 'Sample inventory' : 'Current inventory'}
        </p>
      </header>

      <div className="catalog-toolbar">
        <label className="catalog-control catalog-control--search">
          <span>Search charms</span>
          <input
            type="search"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Name or category"
          />
        </label>
        <label className="catalog-control">
          <span>Category</span>
          <select value={category} onChange={(event) => setCategory(event.target.value)}>
            <option value="all">All categories</option>
            {categories.map((item) => <option key={item} value={item}>{item}</option>)}
          </select>
        </label>
        <label className="catalog-control">
          <span>Availability</span>
          <select value={stock} onChange={(event) => setStock(event.target.value)}>
            <option value="all">All charms</option>
            <option value="IN_STOCK">In Stock</option>
            <option value="OUT_OF_STOCK">Out of Stock</option>
          </select>
        </label>
      </div>

      <p className="catalog-count" role="status">
        {charms ? `Showing ${visibleCharms.length} of ${charms.length} charms` : 'Loading charms...'}
      </p>
      <p className="visually-hidden" role="status">{announcement}</p>

      {error ? (
        <div className="catalog-message catalog-message--error" role="alert">
          <p>{error}</p>
          <button className="button button--secondary" type="button" onClick={() => setRetryKey((key) => key + 1)}>
            Try again
          </button>
        </div>
      ) : null}

      {charms === null && !error ? (
        <div className="catalog-grid" aria-label="Loading catalog" aria-busy="true">
          {Array.from({ length: 12 }, (_, index) => <div className="catalog-skeleton" key={index} />)}
        </div>
      ) : null}

      {charms && visibleCharms.length > 0 ? (
        <div className="catalog-grid">
          {visibleCharms.map((charm) => <CatalogCard key={charm.id} charm={charm} />)}
        </div>
      ) : null}

      {charms && visibleCharms.length === 0 ? (
        <div className="catalog-message">
          <h2>{charms.length === 0 ? 'No charms in the catalog yet' : 'No charms match your filters'}</h2>
          {charms.length > 0 ? (
            <button className="button button--secondary" type="button" onClick={() => {
              setSearch('');
              setCategory('all');
              setStock('all');
            }}>Clear filters</button>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}

export default Catalog;
