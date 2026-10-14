import assert from 'node:assert/strict';
import test from 'node:test';
import { filterManagerCharms, makePreviewManagerCharms } from './managerCharmService.js';

test('manager preview includes both visibility and stock states', () => {
  const charms = makePreviewManagerCharms();

  assert.ok(charms.length > 50);
  assert.ok(charms.some((charm) => charm.status === 'ACTIVE'));
  assert.ok(charms.some((charm) => charm.status === 'INACTIVE'));
  assert.ok(charms.some((charm) => charm.stockStatus === 'IN_STOCK'));
  assert.ok(charms.some((charm) => charm.stockStatus === 'OUT_OF_STOCK'));
});

test('manager search and filters combine without hiding inactive charms', () => {
  const charms = [
    { name: 'Lotus Charm', category: 'Flowers', status: 'INACTIVE', stockStatus: 'OUT_OF_STOCK' },
    { name: 'Lotus Charm', category: 'Flowers', status: 'ACTIVE', stockStatus: 'IN_STOCK' },
    { name: 'Dragon Charm', category: 'Animals', status: 'INACTIVE', stockStatus: 'OUT_OF_STOCK' },
  ];

  assert.deepEqual(filterManagerCharms(charms, {
    search: ' lotus ',
    category: 'Flowers',
    status: 'INACTIVE',
    stockStatus: 'OUT_OF_STOCK',
  }), [charms[0]]);
});
