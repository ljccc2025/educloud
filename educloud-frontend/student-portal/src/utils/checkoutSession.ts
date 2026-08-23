const INTENT_PREFIX = 'educloud:checkout-intent:';

function safeRandomUuid(): string {
  // crypto.randomUUID 仅在安全上下文（https/localhost）可用；局域网 http 必须回退。
  try {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID();
    }
  } catch {
    // 非安全上下文兜底
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export function getCheckoutIntentKey(courseId: string) {
  const storageKey = `${INTENT_PREFIX}${courseId}`;
  const existing = window.sessionStorage.getItem(storageKey);
  if (existing) return existing;

  const created = safeRandomUuid();
  window.sessionStorage.setItem(storageKey, created);
  return created;
}

export function clearCheckoutIntentKey(courseId: string) {
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