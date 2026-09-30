export const DEFAULT_ROUTE = '/customer/drawer';
export const DRAFT_KEY = 'summer.pkce.customer-draft';

export function safeReturnTo(value) {
  if (
    typeof value !== 'string' ||
    !value.startsWith('/') ||
    value.startsWith('//') ||
    value.includes('\\')
  ) {
    return DEFAULT_ROUTE;
  }
  try {
    const url = new URL(value, 'http://summer.invalid');
    if (
      url.origin !== 'http://summer.invalid' ||
      url.pathname === '/oauth2/callback' ||
      url.pathname === '/login'
    ) {
      return DEFAULT_ROUTE;
    }
    return `${url.pathname}${url.search}${url.hash}`;
  } catch {
    return DEFAULT_ROUTE;
  }
}

// Only Customer form fields are eligible for recovery. Credentials and tokens
// cannot be passed through this interface or copied into browser storage.
export function saveCustomerDraft(storage, draft) {
  const allowed = {};
  for (const key of ['username', 'email', 'displayName', 'active']) {
    if (
      typeof draft?.[key] === 'string' ||
      (key === 'active' && typeof draft?.[key] === 'boolean')
    ) {
      allowed[key] = draft[key];
    }
  }
  storage.setItem(DRAFT_KEY, JSON.stringify(allowed));
}

export function consumeCustomerDraft(storage) {
  const value = storage.getItem(DRAFT_KEY);
  storage.removeItem(DRAFT_KEY);
  if (!value) return undefined;
  try {
    return JSON.parse(value);
  } catch {
    return undefined;
  }
}
