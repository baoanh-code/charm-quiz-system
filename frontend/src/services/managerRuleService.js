import { managerRequest } from './api.js';

const MANAGER_RULES_ENDPOINT = '/manager/recommendation-rules';
export const RULE_FIELDS = [
  'NAME_INITIAL', 'ZODIAC', 'LIFE_PATH', 'INTEREST', 'FAVORITE_ANIMAL', 'FAVORITE_COLOR',
];
export const RULE_OPERATORS = ['EQUALS', 'CONTAINS'];
const RULE_STATUSES = ['ACTIVE', 'INACTIVE'];

export function normalizeManagerRule(rule) {
  if (
    !rule || rule.id == null || rule.charmId == null ||
    !RULE_FIELDS.includes(rule.field) || !RULE_OPERATORS.includes(rule.operator) ||
    typeof rule.value !== 'string' || !rule.value.trim() ||
    !Number.isSafeInteger(rule.priority) || !RULE_STATUSES.includes(rule.status)
  ) {
    throw new Error('Unexpected manager rule response');
  }
  return { ...rule, id: String(rule.id), charmId: String(rule.charmId) };
}

function normalizeRuleList(response) {
  if (!Array.isArray(response)) throw new Error('Unexpected manager rule list response');
  return response.map(normalizeManagerRule);
}

function rulePayload(rule) {
  return {
    charmId: rule.charmId, field: rule.field, operator: rule.operator,
    value: rule.value, priority: rule.priority, status: rule.status,
  };
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
  if (!RULE_OPERATORS.includes(draft.operator)) errors.operator = 'Select a supported operator.';
  if (!draft.value.trim()) errors.value = 'Enter a value.';
  if (draft.priority === '' || !Number.isSafeInteger(Number(draft.priority))) errors.priority = 'Enter a whole-number priority.';
  if (!RULE_STATUSES.includes(draft.status)) errors.status = 'Select a status.';
  return errors;
}

export async function loadManagerRules(options = {}) {
  return sortManagerRules(normalizeRuleList(await managerRequest(MANAGER_RULES_ENDPOINT, options)));
}

export async function createManagerRule(rule, options = {}) {
  return normalizeManagerRule(await managerRequest(MANAGER_RULES_ENDPOINT, {
    ...options, method: 'POST', body: JSON.stringify(rulePayload(rule)),
  }));
}

export async function updateManagerRule(id, rule, options = {}) {
  return normalizeManagerRule(await managerRequest(
    `${MANAGER_RULES_ENDPOINT}/${encodeURIComponent(id)}`,
    { ...options, method: 'PUT', body: JSON.stringify(rulePayload(rule)) },
  ));
}

export async function updateManagerRuleStatus(id, status, options = {}) {
  if (!RULE_STATUSES.includes(status)) throw new Error('Invalid manager rule status');
  return normalizeManagerRule(await managerRequest(
    `${MANAGER_RULES_ENDPOINT}/${encodeURIComponent(id)}/status`,
    { ...options, method: 'PATCH', body: JSON.stringify({ status }) },
  ));
}

export async function deleteManagerRule(id, options = {}) {
  await managerRequest(`${MANAGER_RULES_ENDPOINT}/${encodeURIComponent(id)}`, {
    ...options, method: 'DELETE',
  });
}
