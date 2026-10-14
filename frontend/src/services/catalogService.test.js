import assert from 'node:assert/strict';
import test from 'node:test';
import { setImmediate } from 'node:timers';
import { isRealtimeStale, normalizeCatalog, watchCatalog } from './catalogService.js';

const flush = () => new Promise(setImmediate);

test('normalizes the exact catalog API contract and keeps out-of-stock charms visible', () => {
  const charms = normalizeCatalog([
    {
      id: 'charm-1',
      name: 'Music Charm',
      description: null,
      imageUrl: '/music.webp',
      category: null,
      stockStatus: 'IN_STOCK',
      available: true,
    },
    {
      id: 'charm-2',
      name: 'Cat Charm',
      description: 'A cat charm',
      imageUrl: '/cat.webp',
      category: 'Animals',
      stockStatus: 'OUT_OF_STOCK',
      available: false,
    },
  ]);

  assert.equal(charms.length, 2);
  assert.equal(charms[0].category, 'Other');
  assert.equal(charms[0].availabilityStatus, 'IN_STOCK');
  assert.equal(charms[1].availabilityStatus, 'OUT_OF_STOCK');
  assert.equal(charms[1].available, false);
});

test('rejects invalid or contradictory catalog stock data', () => {
  const base = {
    id: 'charm-1',
    name: 'Charm',
    description: null,
    imageUrl: '/charm.webp',
    category: null,
  };

  assert.throws(
    () => normalizeCatalog([{ ...base, stockStatus: 'UNKNOWN', available: false }]),
    /Unexpected catalog item/,
  );
  assert.throws(
    () => normalizeCatalog([{ ...base, stockStatus: 'OUT_OF_STOCK', available: true }]),
    /Unexpected catalog item/,
  );
  assert.throws(() => normalizeCatalog({ items: [] }), /Unexpected catalog response/);
});

test('rejects missing required catalog fields', () => {
  const valid = {
    id: 'charm-1',
    name: 'Charm',
    description: null,
    imageUrl: '/charm.webp',
    category: null,
    stockStatus: 'IN_STOCK',
    available: true,
  };

  assert.throws(() => normalizeCatalog([{ ...valid, id: null }]), /Unexpected catalog item/);
  assert.throws(() => normalizeCatalog([{ ...valid, name: '   ' }]), /Unexpected catalog item/);
  assert.throws(() => normalizeCatalog([{ ...valid, imageUrl: null }]), /Unexpected catalog item/);
});

test('refetches after subscribe and invalidation without overlapping requests', async () => {
  let subscription;
  let releaseFirst;
  let fetchCount = 0;
  let activeFetches = 0;
  let maxActiveFetches = 0;
  let stopped = false;
  const snapshots = [];
  const statuses = [];

  const firstRequest = new Promise((resolve) => { releaseFirst = resolve; });
  const stop = watchCatalog(
    (charms) => snapshots.push(charms),
    (error) => { throw error; },
    (status) => statuses.push(status),
    {
      async fetchCatalog() {
        fetchCount += 1;
        activeFetches += 1;
        maxActiveFetches = Math.max(maxActiveFetches, activeFetches);
        if (fetchCount === 1) await firstRequest;
        activeFetches -= 1;
        return [{ id: `snapshot-${fetchCount}` }];
      },
      subscribe(callbacks) {
        subscription = callbacks;
        return () => { stopped = true; };
      },
      schedule(callback) {
        queueMicrotask(callback);
        return 1;
      },
      cancelScheduled() {},
    },
  );

  subscription.onStatus('SUBSCRIBED');
  subscription.onInvalidate({ ignored: true });
  releaseFirst();
  await flush();
  await flush();

  assert.equal(fetchCount, 2);
  assert.equal(maxActiveFetches, 1);
  assert.equal(snapshots.length, 2);
  assert.deepEqual(statuses, ['SUBSCRIBED']);

  stop();
  assert.equal(stopped, true);
});

test('classifies realtime failure statuses as stale', () => {
  assert.equal(isRealtimeStale('SUBSCRIBED'), false);
  assert.equal(isRealtimeStale('CHANNEL_ERROR'), true);
  assert.equal(isRealtimeStale('TIMED_OUT'), true);
  assert.equal(isRealtimeStale('CLOSED'), true);
});

test('cleanup aborts the active fetch and cancels queued invalidation work', async () => {
  let subscription;
  let scheduledCallback;
  let cancelledTimer;
  let aborted = false;
  let stopped = false;

  const stop = watchCatalog(
    () => {},
    () => {},
    () => {},
    {
      fetchCatalog(signal) {
        return new Promise((_resolve, reject) => {
          signal.addEventListener('abort', () => {
            aborted = true;
            reject(new DOMException('Aborted', 'AbortError'));
          });
        });
      },
      subscribe(callbacks) {
        subscription = callbacks;
        return () => { stopped = true; };
      },
      schedule(callback) {
        scheduledCallback = callback;
        return 42;
      },
      cancelScheduled(timer) {
        cancelledTimer = timer;
      },
    },
  );

  subscription.onInvalidate();
  stop();
  scheduledCallback();
  await flush();

  assert.equal(cancelledTimer, 42);
  assert.equal(aborted, true);
  assert.equal(stopped, true);
});

test('coalesces rapid invalidations without overlapping fetches and cleans up once', async () => {
  let subscription;
  let subscribeCount = 0;
  let cleanupCount = 0;
  let fetchCount = 0;
  let activeFetches = 0;
  let maxActiveFetches = 0;
  const resolvers = [];
  const scheduled = [];

  const stop = watchCatalog(
    () => {},
    (error) => { throw error; },
    () => {},
    {
      fetchCatalog() {
        fetchCount += 1;
        activeFetches += 1;
        maxActiveFetches = Math.max(maxActiveFetches, activeFetches);
        return new Promise((resolve) => {
          resolvers.push(() => {
            activeFetches -= 1;
            resolve([]);
          });
        });
      },
      subscribe(callbacks) {
        subscribeCount += 1;
        subscription = callbacks;
        return () => { cleanupCount += 1; };
      },
      schedule(callback) {
        scheduled.push(callback);
        return scheduled.length;
      },
      cancelScheduled() {},
    },
  );

  subscription.onInvalidate();
  subscription.onInvalidate();
  subscription.onInvalidate();
  assert.equal(scheduled.length, 1);
  scheduled.shift()();
  resolvers.shift()();
  await flush();

  subscription.onInvalidate();
  subscription.onInvalidate();
  assert.equal(scheduled.length, 1);
  scheduled.shift()();
  resolvers.shift()();
  await flush();
  resolvers.shift()();
  await flush();

  assert.equal(fetchCount, 3);
  assert.equal(maxActiveFetches, 1);
  assert.equal(subscribeCount, 1);

  stop();
  stop();
  assert.equal(cleanupCount, 1);
});
