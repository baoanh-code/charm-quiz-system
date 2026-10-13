import assert from 'node:assert/strict';
import test from 'node:test';
import { normalizeGroupedRecommendation } from './recommendationService.js';

test('keeps all groups, sorts picks, and hides out-of-stock charms', () => {
  const result = normalizeGroupedRecommendation({
    groups: [
      {
        key: 'favoriteColor',
        input: 'Red',
        recommendations: [
          { rank: 3, charm: { name: 'Red Star', availabilityStatus: 'OUT_OF_STOCK' } },
          { rank: 4, charm: { name: 'Red Moon', status: 'OUT_OF_STOCK' } },
          { rank: 2, charm: { name: 'Red Flower' } },
          { rank: 1, charm: { name: 'Red Heart' } },
        ],
      },
    ],
  });

  assert.equal(result.groups.length, 5);
  assert.deepEqual(
    result.groups[4].recommendations.map((item) => item.charm.name),
    ['Red Heart', 'Red Flower'],
  );
  assert.equal(result.isPartial, true);
  assert.equal(result.hasResults, true);
});

test('does not present an unavailable fallback as a result', () => {
  const result = normalizeGroupedRecommendation({
    groups: [{
      key: 'favoriteAnimal',
      fallback: true,
      recommendations: [{
        availabilityStatus: 'OUT_OF_STOCK',
        charm: { name: 'Nature Charm' },
      }],
    }],
  });

  assert.equal(result.hasResults, false);
  assert.equal(result.hasFallback, false);
  assert.equal(result.groups[3].recommendations.length, 0);
});

test('keeps an available fallback visible', () => {
  const result = normalizeGroupedRecommendation({
    groups: [{
      key: 'favoriteAnimal',
      fallback: true,
      recommendations: [{ displayOrder: 2, charm: { name: 'Nature Charm' } }],
    }],
  });

  assert.equal(result.hasFallback, true);
  assert.equal(result.groups[3].fallback, true);
  assert.equal(result.groups[3].recommendations[0].charm.name, 'Nature Charm');
});
