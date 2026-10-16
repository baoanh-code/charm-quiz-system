import { useEffect, useMemo, useState } from 'react';
import { ChevronLeft, ChevronRight, Pencil, Plus, Search, Trash2 } from 'lucide-react';
import { useOutletContext } from 'react-router-dom';
import {
  deleteManagerCharmAndReload,
  filterManagerCharms,
  loadManagerCharms,
  saveManagerCharm,
  updateManagerCharmStatus,
  updateManagerCharmStockStatus,
} from '../services/managerCharmService';
import CharmEditor from './CharmEditor';

const PAGE_SIZE = 12;
const initialFilters = { search: '', category: 'all', status: 'all', stockStatus: 'all' };

function CharmThumbnail({ charm }) {
  const [failedUrl, setFailedUrl] = useState('');
  const imageUrl = charm.imageUrl;

  return (
    <span className="manager-charm-thumb">
      {imageUrl && imageUrl !== failedUrl ? (
        <img src={imageUrl} alt="" onError={() => setFailedUrl(imageUrl)} />
      ) : (
        <span aria-hidden="true">{charm.name.charAt(0).toUpperCase()}</span>
      )}
    </span>
  );
}

function ManagerHome() {
  const { user } = useOutletContext();
  const [charms, setCharms] = useState(null);
  const [loadError, setLoadError] = useState('');
  const [retryKey, setRetryKey] = useState(0);
  const [filters, setFilters] = useState(initialFilters);
  const [page, setPage] = useState(1);
  const [editor, setEditor] = useState(null);
  const [notice, setNotice] = useState('');
  const [pendingId, setPendingId] = useState(null);
  const [actionError, setActionError] = useState('');

  useEffect(() => {
    let active = true;

    loadManagerCharms().then((rows) => {
      if (!active) return;
      setCharms(rows);
      setLoadError('');
    }).catch(() => {
      if (active) setLoadError('Could not load charms. Please try again.');
    });

    return () => { active = false; };
  }, [retryKey]);

  const categories = useMemo(() =>
    [...new Set((charms ?? []).map((charm) => charm.category).filter(Boolean))].sort(),
  [charms]);
  const filteredCharms = useMemo(() =>
    filterManagerCharms(charms ?? [], filters),
  [charms, filters]);
  const pageCount = Math.max(1, Math.ceil(filteredCharms.length / PAGE_SIZE));
  const currentPage = Math.min(page, pageCount);
  const pageRows = filteredCharms.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);
  const activeCount = charms?.filter((charm) => charm.status === 'ACTIVE').length ?? 0;
  const outOfStockCount = charms?.filter((charm) => charm.stockStatus === 'OUT_OF_STOCK').length ?? 0;

  function updateFilter(field, value) {
    setFilters((current) => ({ ...current, [field]: value }));
    setPage(1);
  }

  function replaceCharm(updated) {
    setCharms((current) => current.map((charm) => charm.id === updated.id ? updated : charm));
  }

  async function handleSave(draft) {
    const saved = await saveManagerCharm({
      id: editor.mode === 'edit' ? editor.charm.id : null,
      draft,
    });
    if (editor.mode === 'create') {
      setCharms((current) => [saved, ...current.filter((charm) => charm.id !== saved.id)]);
      setFilters(initialFilters);
      setPage(1);
      setNotice(`${saved.name} was created.`);
    } else {
      replaceCharm(saved);
      setNotice(`${saved.name} was updated.`);
    }
    setActionError('');
  }

  async function handleStatusToggle(charm) {
    if (pendingId) return;
    setPendingId(charm.id);
    setActionError('');
    try {
      const status = charm.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
      replaceCharm(await updateManagerCharmStatus(charm.id, status));
      setNotice(`${charm.name} is now ${status.toLowerCase()}.`);
    } catch {
      setActionError('Could not change charm status. Try again.');
    } finally {
      setPendingId(null);
    }
  }

  async function handleStockToggle(charm) {
    if (pendingId) return;
    setPendingId(charm.id);
    setActionError('');
    try {
      const stockStatus = charm.stockStatus === 'IN_STOCK' ? 'OUT_OF_STOCK' : 'IN_STOCK';
      replaceCharm(await updateManagerCharmStockStatus(charm.id, stockStatus));
      setNotice(`${charm.name} stock was updated.`);
    } catch {
      setActionError('Could not change stock status. Try again.');
    } finally {
      setPendingId(null);
    }
  }

  async function handleDelete(charm) {
    if (pendingId || !window.confirm(`Delete ${charm.name}? This cannot be undone.`)) return;
    setPendingId(charm.id);
    setActionError('');
    try {
      setCharms(await deleteManagerCharmAndReload(charm.id));
      setNotice(`${charm.name} was deleted.`);
    } catch (error) {
      setActionError(error?.status === 409
        ? 'This charm is used by a recommendation rule and cannot be deleted.'
        : 'Could not delete the charm. Try again.');
    } finally {
      setPendingId(null);
    }
  }

  return (
    <section className="manager-workspace" aria-labelledby="manager-title">
      <header className="manager-workspace__heading">
        <div>
          <p className="manager-eyebrow">Charm inventory</p>
          <h1 id="manager-title">Charms</h1>
          <p>Signed in as {user.email}</p>
        </div>
        <button className="button button--primary" type="button" onClick={() => setEditor({ mode: 'create' })} disabled={!charms}>
          <Plus size={18} aria-hidden="true" /> Add charm
        </button>
      </header>

      {notice ? <p className="manager-save-notice" role="status">{notice}</p> : null}
      {actionError ? <p className="manager-form-error" role="alert">{actionError}</p> : null}

      <dl className="manager-summary">
        <div><dt>Total charms</dt><dd>{charms?.length ?? '...'}</dd></div>
        <div><dt>Active</dt><dd>{charms ? activeCount : '...'}</dd></div>
        <div><dt>Out of stock</dt><dd>{charms ? outOfStockCount : '...'}</dd></div>
      </dl>

      <div className="manager-filters" role="search" aria-label="Filter charms">
        <label className="manager-filter manager-filter--search">
          <span>Search by name</span>
          <span className="manager-filter__input-wrap">
            <Search size={17} aria-hidden="true" />
            <input type="search" value={filters.search} onChange={(event) => updateFilter('search', event.target.value)} placeholder="Search charms" />
          </span>
        </label>
        <label className="manager-filter">
          <span>Category</span>
          <select value={filters.category} onChange={(event) => updateFilter('category', event.target.value)}>
            <option value="all">All categories</option>
            {categories.map((category) => <option key={category} value={category}>{category}</option>)}
          </select>
        </label>
        <label className="manager-filter">
          <span>Status</span>
          <select value={filters.status} onChange={(event) => updateFilter('status', event.target.value)}>
            <option value="all">All statuses</option>
            <option value="ACTIVE">Active</option>
            <option value="INACTIVE">Inactive</option>
          </select>
        </label>
        <label className="manager-filter">
          <span>Stock</span>
          <select value={filters.stockStatus} onChange={(event) => updateFilter('stockStatus', event.target.value)}>
            <option value="all">All stock</option>
            <option value="IN_STOCK">In stock</option>
            <option value="OUT_OF_STOCK">Out of stock</option>
          </select>
        </label>
      </div>

      <div className="manager-list-heading">
        <h2>Inventory</h2>
        <span role="status">{charms ? `${filteredCharms.length} of ${charms.length} charms` : 'Loading charms...'}</span>
      </div>

      {loadError ? (
        <div className="manager-list-message" role="alert">
          <p>{loadError}</p>
          <button className="button button--secondary" type="button" onClick={() => {
            setLoadError('');
            setRetryKey((key) => key + 1);
          }}>Try again</button>
        </div>
      ) : null}

      {!charms && !loadError ? (
        <div className="manager-list-message" role="status" aria-busy="true">Loading charm inventory...</div>
      ) : null}

      {charms && filteredCharms.length === 0 ? (
        <div className="manager-list-message">
          <h3>{charms.length === 0 ? 'No charms yet' : 'No charms match these filters'}</h3>
          {charms.length > 0 ? (
            <button className="button button--secondary" type="button" onClick={() => {
              setFilters(initialFilters);
              setPage(1);
            }}>Clear filters</button>
          ) : null}
        </div>
      ) : null}

      {pageRows.length > 0 ? (
        <div className="manager-table-scroll">
          <table className="manager-table">
            <thead><tr><th scope="col">Charm</th><th scope="col">Category</th><th scope="col">Status</th><th scope="col">Stock</th><th scope="col"><span className="visually-hidden">Actions</span></th></tr></thead>
            <tbody>
              {pageRows.map((charm) => (
                <tr key={charm.id}>
                  <td>
                    <div className="manager-table__charm">
                      <CharmThumbnail charm={charm} />
                      <span>{charm.name}</span>
                    </div>
                  </td>
                  <td>{charm.category}</td>
                  <td><button className="manager-state-button" type="button" disabled={pendingId !== null} onClick={() => handleStatusToggle(charm)}><span className={`manager-state manager-state--${charm.status.toLowerCase()}`}>{charm.status}</span></button></td>
                  <td><button className="manager-state-button" type="button" disabled={pendingId !== null} onClick={() => handleStockToggle(charm)}><span className={`manager-state manager-state--${charm.stockStatus.toLowerCase()}`}>{charm.stockStatus.replaceAll('_', ' ')}</span></button></td>
                  <td>
                    <button className="manager-icon-button" type="button" title={`Edit ${charm.name}`} aria-label={`Edit ${charm.name}`} disabled={pendingId !== null} onClick={() => setEditor({ mode: 'edit', charm })}>
                      <Pencil size={18} aria-hidden="true" />
                    </button>
                    <button className="manager-icon-button" type="button" title={`Delete ${charm.name}`} aria-label={`Delete ${charm.name}`} disabled={pendingId !== null} onClick={() => handleDelete(charm)}>
                      <Trash2 size={18} aria-hidden="true" />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}

      {filteredCharms.length > PAGE_SIZE ? (
        <div className="manager-pagination">
          <span>Page {currentPage} of {pageCount}</span>
          <div>
            <button className="manager-icon-button" type="button" title="Previous page" aria-label="Previous page" onClick={() => setPage(currentPage - 1)} disabled={currentPage === 1}>
              <ChevronLeft size={19} aria-hidden="true" />
            </button>
            <button className="manager-icon-button" type="button" title="Next page" aria-label="Next page" onClick={() => setPage(currentPage + 1)} disabled={currentPage === pageCount}>
              <ChevronRight size={19} aria-hidden="true" />
            </button>
          </div>
        </div>
      ) : null}

      {editor ? (
        <CharmEditor key={editor.charm?.id ?? 'new'} charm={editor.charm} categories={categories} onSave={handleSave} onClose={() => setEditor(null)} />
      ) : null}
    </section>
  );
}

export default ManagerHome;
