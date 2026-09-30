export const DEFAULT_ROUTE = '/customer/drawer';
export const DRAFT_KEY = 'summer.pkce.customer-draft';
export const LOGIN_START_KEY = 'summer.auth.login-started';
export const UNAUTHORIZED_RESTART_KEY = 'summer.auth.unauthorized-restarted';

export function claimLoginStart(storage) {
  if (storage.getItem(LOGIN_START_KEY)) return false;
  storage.setItem(LOGIN_START_KEY, '1');
  return true;
}

export function completeLoginStart(storage) {
  storage.removeItem(LOGIN_START_KEY);
}

export function claimUnauthorizedRestart(storage) {
  if (storage.getItem(UNAUTHORIZED_RESTART_KEY)) return false;
  storage.setItem(UNAUTHORIZED_RESTART_KEY, '1');
  return true;
}

export function completeAuthenticatedRequest(storage) {
  storage.removeItem(UNAUTHORIZED_RESTART_KEY);
}

export function clearRecoveryState(storage) {
  storage.removeItem(LOGIN_START_KEY);
  storage.removeItem(UNAUTHORIZED_RESTART_KEY);
}

export function isBusinessApi(url) {
  return typeof url === 'string' && /^\/api(?:\/|\?|$)/.test(url);
}

export function classifyBusinessFailure(status, hasResponse) {
  if (!hasResponse) return 'network';
  if (status === 401) return 'unauthorized';
  if (status === 403) return 'forbidden';
  return 'other';
}

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

// Only Customer form fields and edit baseline metadata are eligible for recovery. Credentials and tokens
// cannot be passed through this interface or copied into browser storage.
export function saveCustomerDraft(storage, draft) {
  const allowed = {};
  for (const key of [
    'username',
    'email',
    'displayName',
    'active',
    'editUsername',
    'originalEmail',
    'originalDisplayName',
    'mode',
  ]) {
    if (
      (key === 'mode' && ['drawer', 'modal'].includes(draft?.[key])) ||
      (key !== 'mode' && typeof draft?.[key] === 'string') ||
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
  return parseCustomerDraft(value);
}

function parseCustomerDraft(value) {
  if (!value) return undefined;
  try {
    return JSON.parse(value);
  } catch {
    return undefined;
  }
}

// Keep a recovered draft available across route remounts. The form removes it
// only after the user explicitly saves or discards the recovered work.
export function readCustomerDraft(storage) {
  return parseCustomerDraft(storage.getItem(DRAFT_KEY));
}
