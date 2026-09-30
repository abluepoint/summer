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
} from './recovery.mjs';

const storage = window.sessionStorage;
const STATE_PREFIX = 'oidc.';
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

export const oidcManager = new UserManager(oidcSettings);

export async function getAuthenticatedUser(): Promise<User | null> {
  const user = await oidcManager.getUser();
  return user && !user.expired ? user : null;
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
  const state = user.state as { returnTo?: string } | undefined;
  return { user, returnTo: safeReturnTo(state?.returnTo || DEFAULT_ROUTE) };
}

export function clearTemporaryAuthState() {
  storage.removeItem(DRAFT_KEY);
  for (let index = storage.length - 1; index >= 0; index--) {
    const key = storage.key(index);
    if (key?.startsWith(STATE_PREFIX)) storage.removeItem(key);
  }
}

export async function signOut() {
  const user = await oidcManager.getUser();
  await oidcManager.removeUser();
  clearTemporaryAuthState();
  await oidcManager.signoutRedirect({ id_token_hint: user?.id_token });
}

export async function getAccessTokenFromMemory() {
  return (await getAuthenticatedUser())?.access_token;
}

export { saveCustomerDraft, consumeCustomerDraft };
