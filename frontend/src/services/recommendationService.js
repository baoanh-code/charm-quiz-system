const MOCK_SUBMIT_DELAY = 700;

export const recommendationGroups = [
  { key: 'name', title: 'Your name', number: '01', symbol: 'Aa' },
  { key: 'birthDate', title: 'Birth date', number: '02', symbol: '*' },
  { key: 'interests', title: 'Interests', number: '03', symbol: 'I' },
  { key: 'favoriteAnimal', title: 'Favorite animal', number: '04', symbol: 'A' },
  { key: 'favoriteColor', title: 'Favorite color', number: '05', symbol: 'C' },
];

export function buildRecommendationPayload(profile) {
  return {
    fullName: profile.fullName.trim(),
    birthDate: profile.birthDate,
    interests: profile.interests
      .split(',')
      .map((interest) => interest.trim())
      .filter(Boolean),
    favoriteAnimal: profile.favoriteAnimal.trim(),
    favoriteColor: profile.favoriteColor.trim(),
  };
}

export function normalizeGroupedRecommendation(response) {
  const groups = recommendationGroups.map(({ key }) => {
    const group = response.groups?.find((item) => item.key === key);
    const recommendations = (group?.recommendations ?? [])
      .filter((item) =>
        item?.charm?.name &&
        item.availabilityStatus !== 'OUT_OF_STOCK' &&
        item.status !== 'OUT_OF_STOCK' &&
        item.charm.availabilityStatus !== 'OUT_OF_STOCK' &&
        item.charm.status !== 'OUT_OF_STOCK',
      )
      .sort((left, right) =>
        (left.rank ?? left.displayOrder ?? Number.MAX_SAFE_INTEGER) -
        (right.rank ?? right.displayOrder ?? Number.MAX_SAFE_INTEGER),
      );

    return {
      key,
      input: group?.input ?? '',
      recommendations,
      fallback: Boolean(group?.fallback) && recommendations.length > 0,
    };
  });

  return {
    ...response,
    groups,
    hasResults: groups.some((group) => group.recommendations.length > 0),
    isPartial: groups.some((group) => group.recommendations.length === 0),
    hasFallback: groups.some((group) => group.fallback),
  };
}

function mockCharm(name, rank, reason, source, availabilityStatus = 'IN_STOCK') {
  return {
    rank,
    reason,
    source,
    availabilityStatus,
    charm: {
      id: `${source}-${name.toLowerCase().replace(/[^a-z0-9]+/g, '-')}-${rank}`,
      name,
      imageUrl: null,
    },
  };
}

function createMockGroupedResponse(payload) {
  const firstName = payload.fullName.trim().split(/\s+/).at(-1);
  const initial = firstName.charAt(0).toLocaleUpperCase();
  const animal = payload.favoriteAnimal.toLowerCase();
  const color = payload.favoriteColor.toLowerCase();
  const knownAnimals = ['cat', 'dog', 'rabbit', 'dragon', 'butterfly'];
  const knownColors = ['red', 'blue', 'pink', 'green', 'gold', 'purple'];
  const colorRecommendations = knownColors.includes(color)
    ? [
        mockCharm(`${payload.favoriteColor} Heart Charm`, 1, 'Inspired by your favorite color.', 'Favorite color'),
        mockCharm(`${payload.favoriteColor} Flower Charm`, 2, 'Another way to carry your chosen color.', 'Favorite color'),
        mockCharm(`${payload.favoriteColor} Star Charm`, 3, 'A bright accent in your chosen color.', 'Favorite color', color === 'pink' ? 'OUT_OF_STOCK' : 'IN_STOCK'),
      ]
    : [];

  return {
    profile: payload,
    isPreview: true,
    groups: [
      {
        key: 'name',
        input: firstName,
        recommendations: [mockCharm(`Letter ${initial} Charm`, 1, `A nod to the first letter of ${firstName}.`, 'Name')],
      },
      {
        key: 'birthDate',
        input: payload.birthDate,
        recommendations: [
          mockCharm('Celestial Charm', 1, 'A sample birth date inspired pick.', 'Birth date'),
          mockCharm('Number Charm', 2, 'A second sample pick for this section.', 'Birth date'),
        ],
      },
      {
        key: 'interests',
        input: payload.interests.join(', '),
        recommendations: payload.interests.map((interest, index) =>
          mockCharm(`${interest} Charm`, index + 1, `Inspired by your interest in ${interest}.`, 'Interests'),
        ),
      },
      {
        key: 'favoriteAnimal',
        input: payload.favoriteAnimal,
        fallback: !knownAnimals.includes(animal),
        recommendations: knownAnimals.includes(animal)
          ? [mockCharm(`${payload.favoriteAnimal} Charm`, 1, 'Inspired by your favorite animal.', 'Favorite animal')]
          : [mockCharm('Nature Charm', 1, 'A broader pick while your animal is unavailable.', 'Favorite animal')],
      },
      {
        key: 'favoriteColor',
        input: payload.favoriteColor,
        recommendations: colorRecommendations,
      },
    ],
  };
}

export async function submitRecommendationProfile(profile) {
  const payload = buildRecommendationPayload(profile);

  // Replace this mock response with the grouped API response when its contract is ready.
  return new Promise((resolve) => {
    window.setTimeout(() => {
      resolve(normalizeGroupedRecommendation(createMockGroupedResponse(payload)));
    }, MOCK_SUBMIT_DELAY);
  });
}
