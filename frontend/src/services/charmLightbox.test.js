import assert from 'node:assert/strict';
import test from 'node:test';
import {
  closeCharmLightbox,
  closeCharmLightboxFromBackdrop,
  closeCharmLightboxFromKey,
  openCharmLightbox,
} from './charmLightbox.js';

test('image activation opens the charm preview', () => {
  assert.equal(openCharmLightbox(), true);
});

test('Escape closes an open charm preview', () => {
  assert.equal(closeCharmLightboxFromKey(true, 'Escape'), false);
  assert.equal(closeCharmLightboxFromKey(true, 'Enter'), true);
});

test('backdrop click closes while panel click keeps the preview open', () => {
  assert.equal(closeCharmLightboxFromBackdrop(true, true), false);
  assert.equal(closeCharmLightboxFromBackdrop(true, false), true);
});

test('close button closes the charm preview', () => {
  assert.equal(closeCharmLightbox(), false);
});
