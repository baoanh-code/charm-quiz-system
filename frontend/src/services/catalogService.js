import { getCharms } from './api.js';
import { subscribeToCatalogUpdates } from './catalogRealtime.js';

const REALTIME_ERROR_STATUSES = new Set(['CHANNEL_ERROR', 'TIMED_OUT', 'CLOSED']);
const INVALIDATION_DELAY_MS = 120;
const categoryLabels = new Map([
  ['animal', 'Động vật'],
  ['animals', 'Động vật'],
  ['birth date', 'Ngày sinh'],
  ['color', 'Màu sắc'],
  ['colors', 'Màu sắc'],
  ['culture', 'Văn hóa'],
  ['general', 'Gợi ý thêm'],
  ['initial', 'Chữ cái đầu'],
  ['interest', 'Sở thích'],
  ['interests', 'Sở thích'],
  ['letter', 'Chữ cái'],
  ['letters', 'Chữ cái'],
  ['music', 'Âm nhạc'],
  ['other', 'Khác'],
  ['sport', 'Thể thao'],
  ['sports', 'Thể thao'],
  ['zodiac', 'Cung hoàng đạo'],
]);

export function formatCatalogCategory(category) {
  return categoryLabels.get(category.toLocaleLowerCase()) ?? category;
}

function isNullableString(value) {
  return value == null || typeof value === 'string';
}

export function normalizeCatalog(response) {
  if (!Array.isArray(response)) throw new Error('Unexpected catalog response');

  return response.map((charm) => {
    if (
      !charm ||
      charm.id == null ||
      typeof charm.name !== 'string' ||
      !charm.name.trim() ||
      !isNullableString(charm.description) ||
      typeof charm.imageUrl !== 'string' ||
      !isNullableString(charm.category) ||
      !['IN_STOCK', 'OUT_OF_STOCK'].includes(charm.stockStatus) ||
      typeof charm.available !== 'boolean' ||
      charm.available !== (charm.stockStatus === 'IN_STOCK')
    ) {
      throw new Error('Unexpected catalog item');
    }

    return {
      id: String(charm.id),
      name: charm.name,
      description: charm.description,
      category: charm.category || 'Khác',
      availabilityStatus: charm.stockStatus,
      available: charm.available,
      imageUrl: charm.imageUrl,
      thumbnailUrl: null,
    };
  });
}

export async function loadCatalog(signal) {
  return normalizeCatalog(await getCharms({ signal }));
}

export function watchCatalog(
  onData,
  onError,
  onRealtimeStatus,
  {
    fetchCatalog = loadCatalog,
    subscribe = subscribeToCatalogUpdates,
    schedule = globalThis.setTimeout,
    cancelScheduled = globalThis.clearTimeout,
  } = {},
) {
  let closed = false;
  let loading = false;
  let refreshPending = false;
  let hasData = false;
  let controller;
  let invalidationTimer;
  let stopRealtime = () => {};

  async function refresh() {
    if (closed) return;
    if (loading) {
      refreshPending = true;
      return;
    }

    loading = true;
    controller = new AbortController();

    try {
      const charms = await fetchCatalog(controller.signal);
      if (!closed) {
        hasData = true;
        onData(charms);
      }
    } catch (error) {
      if (!closed && error.name !== 'AbortError') onError(error, { hasData });
    } finally {
      loading = false;
      controller = undefined;
      if (!closed && refreshPending) {
        refreshPending = false;
        void refresh();
      }
    }
  }

  function queueInvalidationRefresh() {
    if (closed || invalidationTimer != null) return;
    invalidationTimer = schedule(() => {
      invalidationTimer = undefined;
      void refresh();
    }, INVALIDATION_DELAY_MS);
  }

  void refresh();

  try {
    stopRealtime = subscribe({
      onInvalidate: queueInvalidationRefresh,
      onStatus(status, error) {
        if (closed) return;
        onRealtimeStatus(status, error);
        if (status === 'SUBSCRIBED') void refresh();
      },
    });
  } catch (error) {
    onRealtimeStatus('CHANNEL_ERROR', error);
  }

  return () => {
    if (closed) return;
    closed = true;
    refreshPending = false;
    if (invalidationTimer != null) cancelScheduled(invalidationTimer);
    controller?.abort();
    stopRealtime();
  };
}

export function isRealtimeStale(status) {
  return REALTIME_ERROR_STATUSES.has(status);
}
