import assert from 'node:assert/strict';
import test from 'node:test';
import { setImmediate } from 'node:timers';
import { makeMockCatalog, normalizeCatalog, watchCatalog } from './catalogService.js';

test('sample catalog has more than 50 uniquely identified charms and both stock states', () => {
  const charms = makeMockCatalog();
  assert.ok(charms.length >= 50);
  assert.equal(new Set(charms.map((charm) => charm.id)).size, charms.length);
  assert.ok(charms.some((charm) => charm.availabilityStatus === 'IN_STOCK'));
  assert.ok(charms.some((charm) => charm.availabilityStatus === 'OUT_OF_STOCK'));
});

test('normalizes public API charms without hiding out-of-stock items', () => {
  const charms = normalizeCatalog([
    { id: 1, name: 'Cat Charm', category: 'Animals', status: 'ACTIVE', availability_status: 'OUT_OF_STOCK', image_url: '/cat.webp' },
    { id: 2, name: 'Hidden Charm', status: 'INACTIVE' },
    { id: 3, name: 'Private Charm', isPublic: false },
    { id: 4, name: 'New Charm', status: 'ACTIVE' },
  ]);

  assert.equal(charms.length, 2);
  assert.deepEqual(charms.map((charm) => charm.availabilityStatus), ['OUT_OF_STOCK', 'UNKNOWN']);
  assert.equal(charms[0].imageUrl, '/cat.webp');
});

test('rejects responses that cannot be rendered as a catalog', () => {
  assert.throws(() => normalizeCatalog({ unexpected: true }), /Unexpected catalog response/);
});

test('watcher emits a stock change without reloading the page', async () => {
  const originalWindow = globalThis.window;
  let tick;
  globalThis.window = {
    setTimeout(callback) { callback(); },
    setInterval(callback) { tick = callback; return 1; },
    clearInterval() {},
  };

  try {
    const snapshots = [];
    const stop = watchCatalog((charms) => snapshots.push(charms), (error) => { throw error; });
    await new Promise(setImmediate);
    tick();
    await new Promise(setImmediate);
    stop();

    assert.equal(snapshots.length, 2);
    const changed = snapshots[1].find((charm, index) =>
      charm.availabilityStatus !== snapshots[0][index].availabilityStatus,
    );
    assert.ok(changed);
    assert.equal(snapshots[0].length, snapshots[1].length);
  } finally {
    globalThis.window = originalWindow;
  }
});
