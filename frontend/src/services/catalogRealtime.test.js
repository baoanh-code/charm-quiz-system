import assert from 'node:assert/strict';
import test from 'node:test';
import { subscribeToCatalogUpdates } from './catalogRealtime.js';

function fakeRealtimeClient() {
  const state = { channelNames: [], removed: [] };
  const channel = {
    on(type, config, callback) {
      state.binding = { type, config, callback };
      return channel;
    },
    subscribe(callback) {
      state.statusCallback = callback;
      return channel;
    },
  };
  const client = {
    channel(name) {
      state.channelNames.push(name);
      return channel;
    },
    removeChannel(value) {
      state.removed.push(value);
      return Promise.resolve('ok');
    },
  };
  return { client, channel, state };
}

test('subscribes once to active charm updates and treats payloads as invalidation only', () => {
  const { client, channel, state } = fakeRealtimeClient();
  const invalidations = [];
  const statuses = [];

  const stop = subscribeToCatalogUpdates({
    client,
    onInvalidate: () => invalidations.push('refresh'),
    onStatus: (status) => statuses.push(status),
  });

  assert.deepEqual(state.channelNames, ['customer-catalog-stock']);
  assert.equal(state.binding.type, 'postgres_changes');
  assert.deepEqual(state.binding.config, {
    event: 'UPDATE',
    schema: 'public',
    table: 'charm',
    filter: 'status=eq.ACTIVE',
  });

  state.binding.callback({ new: { id: 'ignored', stock_status: 'OUT_OF_STOCK' } });
  state.statusCallback('SUBSCRIBED');
  state.statusCallback('CHANNEL_ERROR', new Error('offline'));

  assert.deepEqual(invalidations, ['refresh']);
  assert.deepEqual(statuses, ['SUBSCRIBED', 'CHANNEL_ERROR']);

  stop();
  assert.deepEqual(state.removed, [channel]);
  state.binding.callback({ new: { id: 'ignored-again' } });
  state.statusCallback('CLOSED');
  assert.deepEqual(invalidations, ['refresh']);
  assert.deepEqual(statuses, ['SUBSCRIBED', 'CHANNEL_ERROR']);
});

test('starting a replacement subscription removes the previous channel', () => {
  const first = fakeRealtimeClient();
  const second = fakeRealtimeClient();

  subscribeToCatalogUpdates({
    client: first.client,
    onInvalidate() {},
    onStatus() {},
  });
  const stopSecond = subscribeToCatalogUpdates({
    client: second.client,
    onInvalidate() {},
    onStatus() {},
  });

  assert.equal(first.state.removed.length, 1);
  assert.equal(second.state.channelNames.length, 1);
  stopSecond();
});
