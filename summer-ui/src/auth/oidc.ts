import {
  InMemoryWebStorage,
  User,
  UserManager,
  UserManagerSettings,
  WebStorageStateStore,
} from 'oidc-client-ts';
import {
  DRAFT_KEY,
  DEFAULT_ROUTE,
  safeReturnTo,
  saveCustomerDraft,
  consumeCustomerDraft,
  readCustomerDraft,
  LOGIN_START_KEY,
  claimLoginStart,
  completeLoginStart,
  claimUnauthorizedRestart,
  clearRecoveryState,
} from './recovery.mjs';

const storage = window.sessionStorage;
const STATE_PREFIX = 'oidc.';
const AUTH_NOTICE_KEY = 'summer.auth.notice';
let clearAuthenticatedUi: (() => void) | undefined;
let customerDraftProvider: (() => object) | undefined;
let redirecting = false;
const setting = (value: unknown, fallback: string) =>
  typeof value === 'string' && value ? value : fallback;

export const oidcSettings: UserManagerSettings = {
  authority: setting(
    import.meta.env.VITE_OIDC_AUTHORITY,
    window.location.origin
  ),
  client_id: setting(import.meta.env.VITE_OIDC_CLIENT_ID, 'summer-ui'),
  redirect_uri: setting(
    import.meta.env.VITE_OIDC_REDIRECT_URI,
    `${window.location.origin}/oauth2/callback`
  ),
  post_logout_redirect_uri: setting(
    import.meta.env.VITE_OIDC_POST_LOGOUT_REDIRECT_URI,
    `${window.location.origin}/login?logout`
  ),
  response_type: 'code',
  scope: 'openid profile customer.read customer.write',
  userStore: new WebStorageStateStore({ store: new InMemoryWebStorage() }),
  stateStore: new WebStorageStateStore({ store: storage }),
  automaticSilentRenew: false,
  monitorSession: false,
  loadUserInfo: false,
};

type SummerAuthWindow = Window & {
  __summerOidcManager?: UserManager;
};

// Vite's module graph can evaluate this module more than once during dev/HMR.
// Keep the manager on the window so every import shares one authenticated
// session and one redirect navigator for the current browser context.
const authWindow = window as SummerAuthWindow;
export const oidcManager =
  authWindow.__summerOidcManager ??
  (authWindow.__summerOidcManager = new UserManager(oidcSettings));

export async function getAuthenticatedUser(): Promise<User | null> {
  const user = await oidcManager.getUser();
  return user && !user.expired ? user : null;
}

export function onAuthenticatedUiClear(handler: () => void) {
  clearAuthenticatedUi = handler;
}

export function isAuthRedirecting() {
  return redirecting;
}

export function registerCustomerDraftProvider(provider: () => object) {
  customerDraftProvider = provider;
  return () => {
    if (customerDraftProvider === provider) customerDraftProvider = undefined;
  };
}

function preserveCustomerDraft() {
  if (!customerDraftProvider) return;
  try {
    saveCustomerDraft(storage, customerDraftProvider());
  } catch {
    // A form snapshot must not prevent authentication recovery.
  }
}

export async function authenticationStatus() {
  const user = await oidcManager.getUser();
  return user ? (user.expired ? 'expired' : 'ready') : 'missing';
}

export async function startLogin(
  returnTo = `${window.location.pathname}${window.location.search}${window.location.hash}`
) {
  const nonce = Array.from(crypto.getRandomValues(new Uint8Array(32)), (byte) =>
    byte.toString(16).padStart(2, '0')
  ).join('');
  await oidcManager.signinRedirect({
    state: { returnTo: safeReturnTo(returnTo) },
    nonce,
  });
}

export async function completeLoginCallback(url = window.location.href) {
  const user = await oidcManager.signinRedirectCallback(url);
  completeLoginStart(storage);
  const state = user.state as { returnTo?: string } | undefined;
  return { user, returnTo: safeReturnTo(state?.returnTo || DEFAULT_ROUTE) };
}

export function clearTemporaryAuthState() {
  storage.removeItem(DRAFT_KEY);
  storage.removeItem(AUTH_NOTICE_KEY);
  clearRecoveryState(storage);
  for (let index = storage.length - 1; index >= 0; index--) {
    const key = storage.key(index);
    if (key?.startsWith(STATE_PREFIX)) storage.removeItem(key);
  }
}

export async function signOut() {
  const user = await oidcManager.getUser().catch(() => null);
  redirecting = true;
  clearAuthenticatedUi?.();
  try {
    await oidcManager.removeUser();
  } finally {
    clearTemporaryAuthState();
  }
  let timeoutId: number | undefined;
  try {
    await Promise.race([
      oidcManager.signoutRedirect({ id_token_hint: user?.id_token }),
      new Promise<never>((_, reject) => {
        timeoutId = window.setTimeout(() => {
          reject(new Error('OIDC end-session navigation timed out'));
        }, 8000);
      }),
    ]);
  } finally {
    if (timeoutId !== undefined) window.clearTimeout(timeoutId);
  }
}

export function consumeAuthNotice() {
  const notice = storage.getItem(AUTH_NOTICE_KEY);
  storage.removeItem(AUTH_NOTICE_KEY);
  return notice;
}

export type LoginUnavailableOptions = {
  notify?: boolean;
};

export async function startLoginForUnavailable(
  reason: 'missing' | 'expired',
  options: LoginUnavailableOptions = {}
) {
  if (redirecting || !claimLoginStart(storage)) return false;
  redirecting = true;
  preserveCustomerDraft();
  if (options.notify === false) storage.removeItem(AUTH_NOTICE_KEY);
  else storage.setItem(AUTH_NOTICE_KEY, reason);
  try {
    await startLogin();
    return true;
  } catch (error) {
    storage.removeItem(LOGIN_START_KEY);
    redirecting = false;
    throw error;
  }
}

export async function restartLoginAfterUnauthorized() {
  if (redirecting || !claimUnauthorizedRestart(storage)) return false;
  redirecting = true;
  preserveCustomerDraft();
  storage.setItem(AUTH_NOTICE_KEY, 'unauthorized');
  clearAuthenticatedUi?.();
  try {
    await oidcManager.removeUser();
    await startLogin();
    return true;
  } catch (error) {
    redirecting = false;
    throw error;
  }
}

export async function getAccessTokenFromMemory() {
  return (await getAuthenticatedUser())?.access_token;
}

export {
  saveCustomerDraft,
  consumeCustomerDraft,
  readCustomerDraft,
  DRAFT_KEY,
};
