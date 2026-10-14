const previewGroups = [
  ['Letters', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split('')],
  ['Zodiac', ['Aries', 'Taurus', 'Gemini', 'Cancer', 'Leo', 'Virgo', 'Libra', 'Scorpio', 'Sagittarius', 'Capricorn', 'Aquarius', 'Pisces']],
  ['Animals', ['Cat', 'Dog', 'Rabbit', 'Butterfly', 'Dragon', 'Fox', 'Bird', 'Dolphin']],
  ['Colors', ['Red Heart', 'Blue Star', 'Pink Flower', 'Green Leaf', 'Gold Sun', 'Purple Moon', 'White Pearl', 'Black Onyx']],
  ['Interests', ['Music', 'Football', 'Car', 'Book', 'Travel', 'Art', 'Coffee', 'Camera']],
];

export function makePreviewManagerCharms() {
  let index = 0;
  return previewGroups.flatMap(([category, names]) => names.map((name) => {
    index += 1;
    const charmName = category === 'Letters' ? `Letter ${name} Charm` : `${name} Charm`;
    return {
      id: `sample-${index}`,
      name: charmName,
      description: `${charmName} from the ${category.toLowerCase()} collection.`,
      category,
      tags: [category.toLowerCase()],
      imageUrl: '',
      status: (index - 1) % 9 === 0 ? 'INACTIVE' : 'ACTIVE',
      stockStatus: index % 7 === 0 ? 'OUT_OF_STOCK' : 'IN_STOCK',
    };
  }));
}

export function filterManagerCharms(charms, { search, category, status, stockStatus }) {
  const query = search.trim().toLocaleLowerCase();

  return charms.filter((charm) =>
    (!query || charm.name.toLocaleLowerCase().includes(query)) &&
    (category === 'all' || charm.category === category) &&
    (status === 'all' || charm.status === status) &&
    (stockStatus === 'all' || charm.stockStatus === stockStatus),
  );
}

let previewCharms = makePreviewManagerCharms();

export function loadPreviewManagerCharms() {
  return new Promise((resolve) => {
    window.setTimeout(() => resolve(previewCharms.map((charm) => ({ ...charm }))), 450);
  });
}

export function savePreviewManagerCharm(charm) {
  previewCharms = previewCharms.some((item) => item.id === charm.id)
    ? previewCharms.map((item) => item.id === charm.id ? charm : item)
    : [charm, ...previewCharms];
  return previewCharms.map((item) => ({ ...item }));
}
