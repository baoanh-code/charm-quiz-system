import assert from 'node:assert/strict';
import test from 'node:test';
import {
  normalizeGroupedRecommendation,
  submitRecommendationProfile,
} from './recommendationService.js';

const sectionTypes = [
  'NAME',
  'BIRTH_DATE',
  'INTERESTS',
  'FAVORITE_ANIMAL',
  'FAVORITE_COLOR',
];

function section(type, recommendations = []) {
  return {
    type,
    input: type === 'INTERESTS' ? ['music', 'football'] : [type.toLowerCase()],
    derivedValues: {},
    recommendations,
  };
}

function item(source = 'RULE', rank = 1) {
  return {
    charmId: `${source}-${rank}`,
    name: `${source} Charm`,
    imageUrl: null,
    source,
    rank,
    reason: 'Matched customer profile',
  };
}

function completeResponse() {
  return {
    sections: sectionTypes.map((type) => section(type)),
  };
}

test('normalizes sections by type into fixed display order regardless of backend order', () => {
  const response = completeResponse();
  response.sections.reverse();
  response.sections.find((value) => value.type === 'INTERESTS').recommendations = [
    item('AI', 2),
    item('RULE', 1),
  ];
  response.sections.find((value) => value.type === 'FAVORITE_COLOR').recommendations = [
    item('LOCAL_FALLBACK', 1),
  ];

  const profile = { fullName: 'Test User' };
  const result = normalizeGroupedRecommendation(response, profile);

  assert.deepEqual(result.groups.map((group) => group.type), sectionTypes);
  assert.deepEqual(
    result.groups[2].recommendations.map((recommendation) => recommendation.rank),
    [1, 2],
  );
  assert.equal(result.groups[2].recommendations[1].charm.name, 'AI Charm');
  assert.equal(result.groups[4].fallback, true);
  assert.equal(result.hasFallback, true);
  assert.equal(result.profile, profile);
});

test('accepts five valid empty recommendation sections', () => {
  const result = normalizeGroupedRecommendation(completeResponse(), {});

  assert.equal(result.groups.length, 5);
  assert.equal(result.hasResults, false);
  assert.equal(result.isPartial, true);
  assert.ok(result.groups.every((group) => group.recommendations.length === 0));
});

test('submits the approved profile payload and maps the backend response', async () => {
  const originalFetch = globalThis.fetch;
  let captured;
  globalThis.fetch = async (url, options) => {
    captured = { url, options };
    return new Response(JSON.stringify(completeResponse()), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });
  };

  try {
    const result = await submitRecommendationProfile({
      fullName: '  Lam Phu Bao Anh  ',
      birthDate: '2006-07-24',
      interests: ' music, football, ',
      favoriteAnimal: ' cat ',
      favoriteColor: ' blue ',
    });

    assert.equal(captured.url, 'http://localhost:8080/api/recommendations');
    assert.deepEqual(JSON.parse(captured.options.body), {
      fullName: 'Lam Phu Bao Anh',
      birthDate: '2006-07-24',
      interests: ['music', 'football'],
      favoriteAnimal: 'cat',
      favoriteColor: 'blue',
    });
    assert.deepEqual(result.groups.map((group) => group.type), sectionTypes);
    assert.equal(result.isPreview, false);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('rejects missing, duplicate, unknown, oversized, and invalid-source sections', async (context) => {
  await context.test('missing section', () => {
    const response = completeResponse();
    response.sections.pop();
    assert.throws(() => normalizeGroupedRecommendation(response, {}), /Missing required/);
  });

  await context.test('duplicate section', () => {
    const response = completeResponse();
    response.sections[4] = section('NAME');
    assert.throws(() => normalizeGroupedRecommendation(response, {}), /Duplicate/);
  });

  await context.test('unknown section', () => {
    const response = completeResponse();
    response.sections[4] = section('UNKNOWN');
    assert.throws(() => normalizeGroupedRecommendation(response, {}), /Unknown/);
  });

  await context.test('more than three recommendations', () => {
    const response = completeResponse();
    response.sections[0].recommendations = [item(), item('RULE', 2), item('RULE', 3), item('RULE', 4)];
    assert.throws(() => normalizeGroupedRecommendation(response, {}), /Too many/);
  });

  await context.test('invalid source', () => {
    const response = completeResponse();
    response.sections[0].recommendations = [item('UNKNOWN')];
    assert.throws(() => normalizeGroupedRecommendation(response, {}), /Unexpected recommendation item/);
  });
});
