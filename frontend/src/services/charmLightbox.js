export function openCharmLightbox() {
  return true;
}

export function closeCharmLightbox() {
  return false;
}

export function closeCharmLightboxFromKey(currentState, key) {
  return key === 'Escape' ? false : currentState;
}

export function closeCharmLightboxFromBackdrop(currentState, clickedBackdrop) {
  return clickedBackdrop ? false : currentState;
}
