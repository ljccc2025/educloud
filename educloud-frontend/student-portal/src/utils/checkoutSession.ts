const INTENT_PREFIX = 'educloud:checkout-intent:';

export function getCheckoutIntentKey(courseId: number) {
  const storageKey = `${INTENT_PREFIX}${courseId}`;
  const existing = window.sessionStorage.getItem(storageKey);
  if (existing) return existing;

  const created = window.crypto.randomUUID();
  window.sessionStorage.setItem(storageKey, created);
  return created;
}

export function clearCheckoutIntentKey(courseId: number) {
  window.sessionStorage.removeItem(`${INTENT_PREFIX}${courseId}`);
}

export function getSafeInternalRedirect(value: string | null, fallback = '/') {
  if (!value || !value.startsWith('/') || value.startsWith('//')) {
    return fallback;
  }

  try {
    const url = new URL(value, window.location.origin);
    return url.origin === window.location.origin
      ? `${url.pathname}${url.search}${url.hash}`
      : fallback;
  } catch {
    return fallback;
  }
}
