import { useEffect, useRef, useState } from 'react';
import { X } from 'lucide-react';
import { RULE_FIELDS, validateManagerRule } from '../services/managerRuleService';

function makeDraft(rule) {
  return {
    charmId: rule?.charmId ?? '',
    field: rule?.field ?? 'NAME_INITIAL',
    operator: rule?.operator ?? '',
    value: rule?.value ?? '',
    priority: rule?.priority?.toString() ?? '0',
    status: rule?.status ?? 'ACTIVE',
  };
}

function RuleEditor({ rule, charms, onSave, onClose }) {
  const dialogRef = useRef(null);
  const savingRef = useRef(false);
  const [draft, setDraft] = useState(() => makeDraft(rule));
  const [errors, setErrors] = useState({});
  const [saveError, setSaveError] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    const dialog = dialogRef.current;
    dialog.showModal();
    return () => { if (dialog.open) dialog.close(); };
  }, []);

  function update(field, value) {
    setDraft((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
    setSaveError('');
  }

  async function handleSubmit(event) {
    event.preventDefault();
    if (savingRef.current) return;
    const nextErrors = validateManagerRule(draft, charms);
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length) return;

    savingRef.current = true;
    setSaving(true);
    setSaveError('');
    try {
      await onSave({
        charmId: draft.charmId,
        field: draft.field,
        operator: draft.operator.trim(),
        value: draft.value.trim(),
        priority: Number(draft.priority),
        status: draft.status,
      });
      onClose();
    } catch {
      setSaveError('Could not save the rule preview. Try again.');
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  }

  return (
    <dialog ref={dialogRef} className="manager-editor" onCancel={(event) => {
      event.preventDefault();
      if (!savingRef.current) onClose();
    }} aria-labelledby="rule-editor-title">
      <div className="manager-editor__header">
        <div>
          <p className="manager-eyebrow">Recommendation rules</p>
          <h2 id="rule-editor-title">{rule ? 'Edit rule' : 'Add rule'}</h2>
        </div>
        <button className="manager-icon-button" type="button" title="Close" aria-label="Close" disabled={saving} onClick={onClose}><X size={20} aria-hidden="true" /></button>
      </div>
      <form className="manager-editor__form" onSubmit={handleSubmit} noValidate>
        <div className="manager-field manager-field--wide">
          <label htmlFor="rule-charm">Charm</label>
          <select id="rule-charm" value={draft.charmId} onChange={(event) => update('charmId', event.target.value)} aria-invalid={!!errors.charmId} aria-describedby={errors.charmId ? 'rule-charm-error' : undefined} disabled={saving}>
            <option value="">Select a charm</option>
            {charms.map((charm) => <option key={charm.id} value={charm.id}>{charm.name}{charm.status === 'INACTIVE' ? ' (inactive)' : ''}</option>)}
          </select>
          {errors.charmId ? <span id="rule-charm-error" className="manager-form-error">{errors.charmId}</span> : null}
        </div>
        <div className="manager-field">
          <label htmlFor="rule-field">Field</label>
          <select id="rule-field" value={draft.field} onChange={(event) => update('field', event.target.value)} aria-invalid={!!errors.field} aria-describedby={errors.field ? 'rule-field-error' : undefined} disabled={saving}>
            {RULE_FIELDS.map((field) => <option key={field} value={field}>{field.replaceAll('_', ' ')}</option>)}
          </select>
          {errors.field ? <span id="rule-field-error" className="manager-form-error">{errors.field}</span> : null}
        </div>
        <div className="manager-field">
          <label htmlFor="rule-operator">Operator</label>
          <input id="rule-operator" value={draft.operator} onChange={(event) => update('operator', event.target.value)} aria-invalid={!!errors.operator} aria-describedby={errors.operator ? 'rule-operator-error' : 'rule-operator-hint'} disabled={saving} />
          {errors.operator ? <span id="rule-operator-error" className="manager-form-error">{errors.operator}</span> : <span id="rule-operator-hint" className="manager-field__hint">Operator values await the backend contract.</span>}
        </div>
        <div className="manager-field manager-field--wide">
          <label htmlFor="rule-value">Value</label>
          <input id="rule-value" value={draft.value} onChange={(event) => update('value', event.target.value)} aria-invalid={!!errors.value} aria-describedby={errors.value ? 'rule-value-error' : undefined} disabled={saving} />
          {errors.value ? <span id="rule-value-error" className="manager-form-error">{errors.value}</span> : null}
        </div>
        <div className="manager-field">
          <label htmlFor="rule-priority">Priority</label>
          <input id="rule-priority" type="number" step="1" value={draft.priority} onChange={(event) => update('priority', event.target.value)} aria-invalid={!!errors.priority} aria-describedby={errors.priority ? 'rule-priority-error' : 'rule-priority-hint'} disabled={saving} />
          {errors.priority ? <span id="rule-priority-error" className="manager-form-error">{errors.priority}</span> : <span id="rule-priority-hint" className="manager-field__hint">Higher numbers take precedence.</span>}
        </div>
        <div className="manager-field">
          <label htmlFor="rule-status">Status</label>
          <select id="rule-status" value={draft.status} onChange={(event) => update('status', event.target.value)} aria-invalid={!!errors.status} aria-describedby={errors.status ? 'rule-status-error' : undefined} disabled={saving}>
            <option value="ACTIVE">Active</option>
            <option value="INACTIVE">Inactive</option>
          </select>
          {errors.status ? <span id="rule-status-error" className="manager-form-error">{errors.status}</span> : null}
        </div>
        {saveError ? <p className="manager-form-error manager-field--wide" role="alert">{saveError}</p> : null}
        <div className="manager-editor__footer manager-field--wide">
          <button className="button button--secondary" type="button" onClick={onClose} disabled={saving}>Cancel</button>
          <button className="button button--primary" type="submit" disabled={saving}>{saving ? 'Saving...' : 'Save preview'}</button>
        </div>
      </form>
    </dialog>
  );
}

export default RuleEditor;
