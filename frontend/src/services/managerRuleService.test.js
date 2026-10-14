import assert from 'node:assert/strict';
import test from 'node:test';
import { filterManagerRules, loadPreviewManagerRules, makePreviewManagerRules, RULE_FIELDS, savePreviewManagerRule, sortManagerRules, validateManagerRule } from './managerRuleService.js';

const charms = [{ id: 'charm-1', name: 'Lotus Charm' }, { id: 'charm-2', name: 'Dragon Charm' }];
const valid = { charmId: 'charm-1', field: 'ZODIAC', operator: 'BACKEND_OPERATOR', value: 'Dragon', priority: '10', status: 'ACTIVE' };

test('preview rules include all required fields and both statuses', () => {
  const rules = makePreviewManagerRules();
  assert.ok(rules.length > 0);
  assert.deepEqual(RULE_FIELDS, ['NAME_INITIAL', 'ZODIAC', 'LIFE_PATH', 'INTEREST', 'FAVORITE_ANIMAL', 'FAVORITE_COLOR']);
  assert.ok(rules.some((rule) => rule.status === 'ACTIVE'));
  assert.ok(rules.some((rule) => rule.status === 'INACTIVE'));
});

test('higher priority appears first and filters combine', () => {
  const rules = [
    { id: 'a', charmId: 'charm-1', field: 'ZODIAC', operator: 'EQUALS', value: 'Dragon', priority: 3, status: 'ACTIVE' },
    { id: 'b', charmId: 'charm-2', field: 'INTEREST', operator: 'EQUALS', value: 'History', priority: 8, status: 'INACTIVE' },
    { id: 'c', charmId: 'charm-1', field: 'ZODIAC', operator: 'EQUALS', value: 'Tiger', priority: 9, status: 'INACTIVE' },
  ];
  assert.deepEqual(sortManagerRules(rules).map((rule) => rule.id), ['c', 'b', 'a']);
  assert.deepEqual(filterManagerRules(rules, charms, { search: 'lotus', field: 'ZODIAC', status: 'INACTIVE' }), [rules[2]]);
});

test('validation requires fields but leaves operator vocabulary to backend contract', () => {
  assert.deepEqual(validateManagerRule(valid, charms), {});
  assert.deepEqual(validateManagerRule({ ...valid, charmId: '', field: 'OTHER', operator: ' ', value: '', priority: '1.5', status: 'OTHER' }, charms), {
    charmId: 'Select a charm.', field: 'Select a supported field.', operator: 'Enter an operator.',
    value: 'Enter a value.', priority: 'Enter a whole-number priority.', status: 'Select a status.',
  });
});

test('preview saves an edited priority and status for subsequent loads', async () => {
  const [first] = await loadPreviewManagerRules();
  const changed = { ...first, priority: -2, status: 'INACTIVE' };
  const saved = await savePreviewManagerRule(changed);

  assert.deepEqual(saved.find((rule) => rule.id === first.id), changed);
  assert.equal(saved.at(-1).id, first.id);
  assert.deepEqual((await loadPreviewManagerRules()).find((rule) => rule.id === first.id), changed);
});
