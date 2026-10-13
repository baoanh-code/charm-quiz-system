import { getCharms } from './api.js';

const REFRESH_INTERVAL = 8000;
const MOCK_DELAY = 450;
const catalogGroups = [
  ['Letters', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split('')],
  ['Zodiac', ['Aries', 'Taurus', 'Gemini', 'Cancer', 'Leo', 'Virgo', 'Libra', 'Scorpio', 'Sagittarius', 'Capricorn', 'Aquarius', 'Pisces']],
  ['Animals', ['Cat', 'Dog', 'Rabbit', 'Butterfly', 'Dragon', 'Fox', 'Bird', 'Dolphin']],
  ['Colors', ['Red Heart', 'Blue Star', 'Pink Flower', 'Green Leaf', 'Gold Sun', 'Purple Moon', 'White Pearl', 'Black Onyx']],
  ['Interests', ['Music', 'Football', 'Car', 'Book', 'Travel', 'Art', 'Coffee', 'Camera']],
];

export const isCatalogPreview = import.meta.env?.VITE_CATALOG_SOURCE !== 'api';

export function makeMockCatalog() {
  let index = 0;

  return catalogGroups.flatMap(([category, names]) =>
    names.map((name) => {
      index += 1;
      return {
        id: `sample-${index}`,
        name: category === 'Letters' ? `Letter ${name} Charm` : `${name} Charm`,
        category,
        availabilityStatus: index % 7 === 0 ? 'OUT_OF_STOCK' : 'IN_STOCK',
        imageUrl: null,
        thumbnailUrl: null,
      };
    }),
  );
}

let mockCatalog = makeMockCatalog();
let mockUpdateIndex = 0;

export function normalizeCatalog(response) {
  const rows = Array.isArray(response) ? response : response?.items ?? response?.charms;
  if (!Array.isArray(rows)) throw new Error('Unexpected catalog response');

  return rows
    .filter((charm) =>
      charm?.id != null && charm.name &&
      charm.isPublic !== false && charm.is_public !== false &&
      charm.status !== 'INACTIVE',
    )
    .map((charm) => {
      const availability = charm.availabilityStatus || charm.availability_status ||
        charm.stockStatus || charm.stock_status || charm.status;

      return {
        id: String(charm.id),
        name: charm.name,
        category: charm.category || 'Other',
        availabilityStatus: ['IN_STOCK', 'OUT_OF_STOCK'].includes(availability)
          ? availability : 'UNKNOWN',
        imageUrl: charm.imageUrl || charm.image_url || null,
        thumbnailUrl: charm.thumbnailUrl || charm.thumbnail_url || null,
      };
    });
}

async function loadCatalog(signal) {
  if (!isCatalogPreview) {
    return normalizeCatalog(await getCharms({ signal }));
  }

  return new Promise((resolve) => {
    window.setTimeout(() => resolve(mockCatalog.map((charm) => ({ ...charm }))), MOCK_DELAY);
  });
}

function advanceMockStock() {
  const position = (mockUpdateIndex * 7 + 6) % mockCatalog.length;
  mockUpdateIndex += 1;
  const current = mockCatalog[position];
  const updated = {
    ...current,
    availabilityStatus: current.availabilityStatus === 'IN_STOCK' ? 'OUT_OF_STOCK' : 'IN_STOCK',
  };
  mockCatalog = mockCatalog.map((charm, index) => index === position ? updated : charm);
  return updated;
}

export function watchCatalog(onData, onError) {
  let closed = false;
  let loading = false;
  let controller;

  async function refresh() {
    if (loading || closed) return;
    loading = true;
    controller = new AbortController();

    try {
      const charms = await loadCatalog(controller.signal);
      if (!closed) onData(charms);
    } catch (error) {
      if (!closed && error.name !== 'AbortError') onError(error);
    } finally {
      loading = false;
    }
  }

  refresh();
  const interval = window.setInterval(() => {
    if (isCatalogPreview) advanceMockStock();
    refresh();
  }, REFRESH_INTERVAL);

  return () => {
    closed = true;
    window.clearInterval(interval);
    controller?.abort();
  };
}
