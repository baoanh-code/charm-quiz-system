import { getRecommendation } from './api.js';

export const recommendationGroups = [
  { key: 'name', type: 'NAME', title: 'Theo tên', number: '01', symbol: 'Aa' },
  { key: 'birthDate', type: 'BIRTH_DATE', title: 'Theo ngày sinh', number: '02', symbol: '*' },
  { key: 'interests', type: 'INTERESTS', title: 'Theo sở thích', number: '03', symbol: 'I' },
  { key: 'favoriteAnimal', type: 'FAVORITE_ANIMAL', title: 'Theo động vật yêu thích', number: '04', symbol: 'A' },
  { key: 'favoriteColor', type: 'FAVORITE_COLOR', title: 'Theo màu sắc yêu thích', number: '05', symbol: 'C' },
];

export const recommendationSourceLabels = {
  RULE: {
    label: 'Quy tắc phù hợp',
    reason: 'Phù hợp với thông tin bạn đã cung cấp.',
  },
  AI: {
    label: 'Gợi ý từ AI',
    reason: 'Được chọn dựa trên thông tin bạn đã cung cấp.',
  },
  LOCAL_FALLBACK: {
    label: 'Gợi ý thay thế',
    reason: 'Gợi ý gần nhất dựa trên thông tin bạn đã cung cấp.',
  },
};

const sectionByType = new Map(recommendationGroups.map((group) => [group.type, group]));
const validSources = new Set(Object.keys(recommendationSourceLabels));

const customerInputAliases = {
  interests: new Map([
    ['âm nhạc', 'music'],
    ['bóng đá', 'football'],
    ['ô tô', 'cars'],
    ['xe hơi', 'cars'],
  ]),
  favoriteAnimal: new Map([
    ['mèo', 'cat'],
    ['chó', 'dog'],
    ['thỏ', 'rabbit'],
    ['rồng', 'dragon'],
    ['bướm', 'butterfly'],
  ]),
  favoriteColor: new Map([
    ['đỏ', 'red'],
    ['xanh dương', 'blue'],
    ['hồng', 'pink'],
    ['xanh lá', 'green'],
    ['vàng', 'gold'],
    ['tím', 'purple'],
  ]),
};

function toBackendValue(value, aliases) {
  const trimmed = value.trim();
  return aliases.get(trimmed.toLocaleLowerCase('vi')) ?? trimmed;
}

export function buildRecommendationPayload(profile) {
  return {
    fullName: profile.fullName.trim(),
    birthDate: profile.birthDate,
    interests: profile.interests
      .split(',')
      .map((interest) => toBackendValue(interest, customerInputAliases.interests))
      .filter(Boolean),
    favoriteAnimal: toBackendValue(profile.favoriteAnimal, customerInputAliases.favoriteAnimal),
    favoriteColor: toBackendValue(profile.favoriteColor, customerInputAliases.favoriteColor),
  };
}

function normalizeRecommendation(item) {
  if (
    !item ||
    item.charmId == null ||
    typeof item.name !== 'string' ||
    !item.name.trim() ||
    !(item.imageUrl == null || typeof item.imageUrl === 'string') ||
    !validSources.has(item.source) ||
    !Number.isInteger(item.rank) ||
    item.rank < 1 ||
    typeof item.reason !== 'string'
  ) {
    throw new Error('Unexpected recommendation item or rank');
  }

  return {
    charm: {
      id: String(item.charmId),
      name: item.name,
      imageUrl: item.imageUrl,
    },
    source: item.source,
    rank: item.rank,
    reason: item.reason,
  };
}

export function normalizeGroupedRecommendation(response, profile) {
  if (!response || !Array.isArray(response.sections)) {
    throw new Error('Unexpected recommendation response');
  }

  const sections = new Map();
  for (const section of response.sections) {
    if (!section || !sectionByType.has(section.type)) {
      throw new Error('Unknown recommendation section');
    }
    if (sections.has(section.type)) {
      throw new Error('Duplicate recommendation section');
    }
    if (!Array.isArray(section.input) || !Array.isArray(section.recommendations)) {
      throw new Error('Unexpected recommendation section');
    }
    if (section.recommendations.length > 3) {
      throw new Error('Too many recommendations in section');
    }

    sections.set(section.type, section);
  }

  if (sections.size !== recommendationGroups.length) {
    throw new Error('Missing required recommendation section');
  }

  const groups = recommendationGroups.map(({ key, type }) => {
    const section = sections.get(type);
    const recommendations = section.recommendations
      .map(normalizeRecommendation)
      .sort((left, right) => left.rank - right.rank);
    const charmIds = new Set();

    recommendations.forEach((recommendation, index) => {
      if (charmIds.has(recommendation.charm.id)) {
        throw new Error('Duplicate recommendation charm ID');
      }
      charmIds.add(recommendation.charm.id);

      if (recommendation.rank !== index + 1) {
        throw new Error('Unexpected recommendation rank sequence');
      }
    });

    return {
      key,
      type,
      input: section.input.join(', '),
      derivedValues: section.derivedValues ?? {},
      recommendations,
      fallback: recommendations.some((item) => item.source === 'LOCAL_FALLBACK'),
    };
  });

  return {
    profile,
    isPreview: false,
    groups,
    hasResults: groups.some((group) => group.recommendations.length > 0),
    isPartial: groups.some((group) => group.recommendations.length === 0),
    hasFallback: groups.some((group) => group.fallback),
  };
}

export async function submitRecommendationProfile(profile) {
  const payload = buildRecommendationPayload(profile);
  const response = await getRecommendation(payload);
  return normalizeGroupedRecommendation(response, payload);
}
