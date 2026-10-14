export const RULE_FIELDS = [
  'NAME_INITIAL', 'ZODIAC', 'LIFE_PATH', 'INTEREST', 'FAVORITE_ANIMAL', 'FAVORITE_COLOR',
];

export function makePreviewManagerRules() {
  return [
    { id: 'preview-rule-1', charmId: 'sample-1', field: 'ZODIAC', operator: 'EQUALS', value: 'Dragon', priority: 90, status: 'ACTIVE' },
    { id: 'preview-rule-2', charmId: 'sample-27', field: 'FAVORITE_COLOR', operator: 'EQUALS', value: 'Red', priority: 75, status: 'ACTIVE' },
    { id: 'preview-rule-3', charmId: 'sample-39', field: 'LIFE_PATH', operator: 'EQUALS', value: '7', priority: 60, status: 'ACTIVE' },
    { id: 'preview-rule-4', charmId: 'sample-47', field: 'INTEREST', operator: 'EQUALS', value: 'History', priority: 45, status: 'INACTIVE' },
    { id: 'preview-rule-5', charmId: 'sample-55', field: 'FAVORITE_ANIMAL', operator: 'EQUALS', value: 'Cat', priority: 30, status: 'ACTIVE' },
  ];
}

export function sortManagerRules(rules) {
  return [...rules].sort((a, b) => b.priority - a.priority || a.id.localeCompare(b.id));
}

export function filterManagerRules(rules, charms, { search, field, status }) {
  const query = search.trim().toLocaleLowerCase();
  const charmNames = new Map(charms.map((charm) => [charm.id, charm.name.toLocaleLowerCase()]));

  return sortManagerRules(rules.filter((rule) =>
    (!query || [charmNames.get(rule.charmId) ?? '', rule.field, rule.operator, rule.value]
      .some((part) => part.toLocaleLowerCase().includes(query))) &&
    (field === 'all' || rule.field === field) &&
    (status === 'all' || rule.status === status),
  ));
}

export function validateManagerRule(draft, charms) {
  const errors = {};
  if (!charms.some((charm) => charm.id === draft.charmId)) errors.charmId = 'Select a charm.';
  if (!RULE_FIELDS.includes(draft.field)) errors.field = 'Select a supported field.';
  if (!draft.operator.trim()) errors.operator = 'Enter an operator.';
  if (!draft.value.trim()) errors.value = 'Enter a value.';
  if (draft.priority === '' || !Number.isSafeInteger(Number(draft.priority))) {
    errors.priority = 'Enter a whole-number priority.';
  }
  if (!['ACTIVE', 'INACTIVE'].includes(draft.status)) errors.status = 'Select a status.';
  return errors;
}

let previewRules = makePreviewManagerRules();

export function loadPreviewManagerRules() {
  return new Promise((resolve) => {
    setTimeout(() => resolve(sortManagerRules(previewRules)), 450);
  });
}

export function savePreviewManagerRule(rule) {
  return new Promise((resolve) => {
    setTimeout(() => {
      previewRules = previewRules.some((item) => item.id === rule.id)
        ? previewRules.map((item) => item.id === rule.id ? rule : item)
        : [...previewRules, rule];
      resolve(sortManagerRules(previewRules));
    }, 300);
  });
}
