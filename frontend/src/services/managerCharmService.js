import { makeMockCatalog } from './catalogService.js';

export function makePreviewManagerCharms() {
  return makeMockCatalog().map((charm, index) => ({
    id: charm.id,
    name: charm.name,
    description: `${charm.name} from the ${charm.category.toLowerCase()} collection.`,
    category: charm.category,
    tags: [charm.category.toLowerCase()],
    imageUrl: charm.imageUrl ?? '',
    status: index % 9 === 0 ? 'INACTIVE' : 'ACTIVE',
    stockStatus: charm.availabilityStatus,
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
