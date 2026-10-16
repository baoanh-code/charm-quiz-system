import { useEffect, useMemo, useState } from 'react';
import { Pencil, Plus, Search, Trash2 } from 'lucide-react';
import { useOutletContext } from 'react-router-dom';
import { loadManagerCharms } from '../services/managerCharmService';
import {
  createManagerRule,
  deleteManagerRule,
  filterManagerRules,
  loadManagerRules,
  RULE_FIELDS,
  updateManagerRule,
  updateManagerRuleStatus,
} from '../services/managerRuleService';
import RuleEditor from './RuleEditor';

const initialFilters = { search: '', field: 'all', status: 'all' };

function ManagerRules() {
  const { user } = useOutletContext();
  const [rules, setRules] = useState(null);
  const [charms, setCharms] = useState([]);
  const [loadError, setLoadError] = useState('');
  const [retryKey, setRetryKey] = useState(0);
  const [filters, setFilters] = useState(initialFilters);
  const [editor, setEditor] = useState(null);
  const [notice, setNotice] = useState('');
  const [pendingId, setPendingId] = useState(null);
  const [actionError, setActionError] = useState('');

  useEffect(() => {
    let active = true;
    Promise.all([loadManagerRules(), loadManagerCharms()]).then(([loadedRules, loadedCharms]) => {
      if (!active) return;
      setRules(loadedRules);
      setCharms(loadedCharms);
      setLoadError('');
    }).catch(() => {
      if (active) setLoadError('Could not load rules. Please try again.');
    });
    return () => { active = false; };
  }, [retryKey]);

  const charmNames = useMemo(() => new Map(charms.map((charm) => [charm.id, charm.name])), [charms]);
  const filteredRules = useMemo(() => filterManagerRules(rules ?? [], charms, filters), [rules, charms, filters]);
  const activeCount = rules?.filter((rule) => rule.status === 'ACTIVE').length ?? 0;

  function updateFilter(field, value) {
    setFilters((current) => ({ ...current, [field]: value }));
  }

  async function handleSave(draft) {
    const saved = editor.rule
      ? await updateManagerRule(editor.rule.id, draft)
      : await createManagerRule(draft);
    setRules((current) => editor.rule
      ? current.map((rule) => rule.id === saved.id ? saved : rule)
      : [...current, saved]);
    if (!editor.rule) setFilters(initialFilters);
    setActionError('');
    setNotice(`${charmNames.get(draft.charmId)} rule was ${editor.rule ? 'updated' : 'created'}.`);
  }

  async function handleToggle(rule) {
    if (pendingId) return;
    setPendingId(rule.id);
    setActionError('');
    setNotice('');
    try {
      const status = rule.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
      const updated = await updateManagerRuleStatus(rule.id, status);
      setRules((current) => current.map((item) => item.id === updated.id ? updated : item));
      setNotice(`Rule ${status === 'ACTIVE' ? 'enabled' : 'disabled'}.`);
    } catch {
      setActionError('Could not change the rule status. Try again.');
    } finally {
      setPendingId(null);
    }
  }

  async function handleDelete(rule) {
    if (pendingId || !window.confirm('Delete this recommendation rule? This cannot be undone.')) return;
    setPendingId(rule.id);
    setActionError('');
    setNotice('');
    try {
      await deleteManagerRule(rule.id);
      setRules((current) => current.filter((item) => item.id !== rule.id));
      setNotice('Rule was deleted.');
    } catch {
      setActionError('Could not delete the rule. Try again.');
    } finally {
      setPendingId(null);
    }
  }

  return (
    <section className="manager-workspace" aria-labelledby="manager-rules-title">
      <header className="manager-workspace__heading">
        <div>
          <p className="manager-eyebrow">Recommendation setup</p>
          <h1 id="manager-rules-title">Rules</h1>
          <p>Signed in as {user.email}</p>
        </div>
        <button className="button button--primary" type="button" onClick={() => setEditor({ mode: 'create' })} disabled={!rules || charms.length === 0}>
          <Plus size={18} aria-hidden="true" /> Add rule
        </button>
      </header>

      {notice ? <p className="manager-save-notice" role="status">{notice}</p> : null}
      {actionError ? <p className="manager-form-error" role="alert">{actionError}</p> : null}

      <dl className="manager-summary">
        <div><dt>Total rules</dt><dd>{rules?.length ?? '...'}</dd></div>
        <div><dt>Active</dt><dd>{rules ? activeCount : '...'}</dd></div>
        <div><dt>Inactive</dt><dd>{rules ? rules.length - activeCount : '...'}</dd></div>
      </dl>

      <div className="manager-filters manager-filters--rules" role="search" aria-label="Filter rules">
        <label className="manager-filter manager-filter--search">
          <span>Search rules</span>
          <span className="manager-filter__input-wrap"><Search size={17} aria-hidden="true" /><input type="search" value={filters.search} onChange={(event) => updateFilter('search', event.target.value)} placeholder="Charm, field, value" /></span>
        </label>
        <label className="manager-filter">
          <span>Field</span>
          <select value={filters.field} onChange={(event) => updateFilter('field', event.target.value)}>
            <option value="all">All fields</option>
            {RULE_FIELDS.map((field) => <option key={field} value={field}>{field.replaceAll('_', ' ')}</option>)}
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
      </div>

      <div className="manager-list-heading">
        <h2>Rule list</h2>
        <span role="status">{rules ? `${filteredRules.length} of ${rules.length} rules · Higher priority first` : 'Loading rules...'}</span>
      </div>

      {loadError ? <div className="manager-list-message" role="alert"><p>{loadError}</p><button className="button button--secondary" type="button" onClick={() => { setLoadError(''); setRetryKey((key) => key + 1); }}>Try again</button></div> : null}
      {!rules && !loadError ? <div className="manager-list-message" role="status" aria-busy="true">Loading recommendation rules...</div> : null}
      {rules && filteredRules.length === 0 ? (
        <div className="manager-list-message">
          <h3>{rules.length === 0 ? 'No rules yet' : 'No rules match these filters'}</h3>
          {rules.length ? <button className="button button--secondary" type="button" onClick={() => setFilters(initialFilters)}>Clear filters</button> : null}
        </div>
      ) : null}

      {filteredRules.length ? (
        <div className="manager-table-scroll">
          <table className="manager-table manager-rules-table">
            <thead><tr><th scope="col">Priority</th><th scope="col">Charm</th><th scope="col">Field</th><th scope="col">Operator</th><th scope="col">Value</th><th scope="col">Status</th><th scope="col"><span className="visually-hidden">Actions</span></th></tr></thead>
            <tbody>
              {filteredRules.map((rule) => (
                <tr key={rule.id}>
                  <td><strong className="manager-priority">{rule.priority}</strong></td>
                  <td className="manager-rules-table__charm">{charmNames.get(rule.charmId) ?? 'Unknown charm'}</td>
                  <td>{rule.field.replaceAll('_', ' ')}</td>
                  <td><code>{rule.operator}</code></td>
                  <td>{rule.value}</td>
                  <td><span className={`manager-state manager-state--${rule.status.toLowerCase()}`}>{rule.status}</span></td>
                  <td><div className="manager-rule-actions">
                    <label className="manager-rule-toggle" title={`${rule.status === 'ACTIVE' ? 'Disable' : 'Enable'} rule for ${charmNames.get(rule.charmId) ?? 'charm'}`}>
                      <input type="checkbox" checked={rule.status === 'ACTIVE'} disabled={pendingId !== null} onChange={() => handleToggle(rule)} aria-label={`${rule.status === 'ACTIVE' ? 'Disable' : 'Enable'} rule for ${charmNames.get(rule.charmId) ?? 'charm'}`} />
                    </label>
                    <button className="manager-icon-button" type="button" title={`Edit rule for ${charmNames.get(rule.charmId) ?? 'charm'}`} aria-label={`Edit rule for ${charmNames.get(rule.charmId) ?? 'charm'}`} onClick={() => setEditor({ mode: 'edit', rule })} disabled={pendingId !== null}><Pencil size={18} aria-hidden="true" /></button>
                    <button className="manager-icon-button" type="button" title="Delete rule" aria-label={`Delete rule for ${charmNames.get(rule.charmId) ?? 'charm'}`} onClick={() => handleDelete(rule)} disabled={pendingId !== null}><Trash2 size={18} aria-hidden="true" /></button>
                  </div></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}

      {editor ? <RuleEditor key={editor.rule?.id ?? 'new'} rule={editor.rule} charms={charms} onSave={handleSave} onClose={() => setEditor(null)} /> : null}
    </section>
  );
}

export default ManagerRules;
