# Use Spring Security public-client PKCE for Summer UI

Summer UI will authenticate against the Summer backend's Spring Security Authorization Server using Authorization Code with PKCE. The core security auto-configuration and Summer Sample will be extended minimally to register a browser public client without exposing a client secret. Summer Sample removes the configured `summer-client` instance and registers only `summer-ui`, while the reusable Summer Security framework continues to support explicitly configured confidential clients and `client_credentials`. Demo identity data remains in memory for development.

## Considered Options

- Put a client secret in the browser: rejected because browser-delivered secrets are not confidential.
- Let Summer UI invent a separate authentication service: rejected because the backend already owns the Spring Security identity and resource-server boundary.
- Add a public PKCE client to the existing provider: accepted because it keeps authentication centralized and uses the standard browser flow while retaining the framework's optional service-to-service client capability.

## Login and callback boundary

Summer UI and the Authorization Server share the `/login` path through same-origin routing: `GET /login` renders the Arco Design Pro login page, while `POST /login` is handled by Spring Security form login. The browser never sends credentials to a custom JSON authentication endpoint. After Spring Security restores the saved authorization request, the Authorization Server redirects the authorization response to the Summer UI public callback route `/oauth2/callback`.

Development uses Vite proxying and production uses same-origin routing or a reverse proxy so that the Spring Security session, saved request, CSRF protection, authorization endpoints, and UI routes participate in one browser origin.

## Browser token lifecycle

Summer UI keeps the access token only in browser memory. Raw tokens are not persisted in Web Storage, IndexedDB, cookies, Redux persistence, logs, error messages, or Redux DevTools. Only short-lived PKCE transaction state (`state`, `code_verifier`, and the pre-login destination) may use `sessionStorage`, and it is removed after callback validation.

The public client does not request or receive a refresh token. A reload, expired token, or first API `401` starts a new Authorization Code with PKCE flow. The client prevents retry loops; an existing Spring Security login session may allow the new authorization request to complete without collecting credentials again.

## Token lifetimes

The `summer-ui` authorization code is valid for five minutes. Its access token and ID Token are each valid for fifteen minutes. The public client is not issued a refresh token. After expiry, Summer UI may start the Authorization Code with PKCE flow once; an existing Spring Security session can complete that authorization without another credential prompt, while retry-loop protection prevents repeated redirects.

## OpenID Connect profile

The Authorization Server enables OpenID Connect 1.0 for the Summer UI public client. Summer UI requests `openid profile`, validates the ID Token signature and its issuer, audience, expiry, and nonce, and treats `sub` as the stable security-subject identifier. The access token is used only to authorize API calls. No dynamic client registration is enabled, and no OIDC claim creates a relationship between the authenticated principal and Customer.

## Identity claims and display

The ID Token exposes only the standard identity claims needed by Summer UI: `sub`, `preferred_username`, and `name`. `sub` is the stable security-subject identifier, `preferred_username` is the Spring Security login name, and `name` is the display label. The UI header displays `name`, then falls back to `preferred_username`, then `sub`. Customer data, email, roles, authorities, and business permissions are not identity-profile claims. API authorization is derived from access-token scopes. Summer UI does not make an additional UserInfo request and uses only the validated ID Token for identity display.

## Client protocol implementation

Summer UI uses the pinned `oidc-client-ts` dependency instead of implementing OIDC and PKCE protocol mechanics directly. It is limited to discovery, authorization requests, PKCE transaction handling, callback validation, token expiry, and logout. Its user store is explicitly in-memory, its authorization transaction state uses `sessionStorage`, silent renewal is disabled, and it does not own business authorization, Customer state, or application routing.

## Logout boundary

Summer UI uses OpenID Connect RP-Initiated Logout rather than only clearing client-side state. When the user signs out, the UI first removes the in-memory access token, ID Token, and authenticated UI state, then uses `oidc-client-ts` to redirect to the Authorization Server's discovered end-session endpoint with the ID Token as `id_token_hint` and the registered `post_logout_redirect_uri`. The Authorization Server terminates the Spring Security login session and redirects to `/login?logout`. The UI also clears any remaining PKCE transaction state. This prevents a client-only "logout" from leaving the backend single-sign-on session active.

## SPA form-login CSRF boundary

Because `GET /login` is rendered by Summer UI while `POST /login` is handled by Spring Security, the Authorization Server exposes anonymous, non-cacheable `GET /csrf` for the SPA to obtain the current session's CSRF parameter name, header name, and token. The Arco login form submits credentials and the CSRF parameter as `application/x-www-form-urlencoded` to Spring Security's standard `POST /login`; there is no custom JSON credential endpoint. Authentication failure and successful logout return to `/login?error` and `/login?logout`, respectively.

## Development origin and client registration

During integrated UI development, the browser uses `http://127.0.0.1:5173` as its single visible origin. Vite serves Summer UI and proxies `/api/**`, `/oauth2/**`, `/.well-known/**`, `/connect/**`, `/csrf`, and only `POST /login` to the Summer Sample process on `http://127.0.0.1:8080`; `GET /login` remains the SPA route. The UI integration profile publishes `http://127.0.0.1:5173` as the issuer, while the backend's standalone default issuer remains `http://127.0.0.1:8080`.

A separate public client named `summer-ui` uses `http://127.0.0.1:5173/oauth2/callback` as its redirect URI and `http://127.0.0.1:5173/login?logout` as its post-logout redirect URI. Summer Sample registers only the public `summer-ui` client; it does not retain the configured confidential `summer-client` instance. Summer Security still supports confidential clients when another application configures one explicitly. Production supplies its HTTPS issuer and redirect URIs through environment-specific configuration rather than source constants.

## Browser origin boundary

Summer UI uses relative URLs and same-origin proxying for API, authorization, login, CSRF, and logout traffic. The backend does not add wildcard CORS or a Summer UI cross-origin exception. Direct access to the backend port remains available for backend tests and non-browser debugging, but it is not the browser integration path. A future cross-origin deployment requires a separate explicit origin allowlist and security review.

## Summer UI registered-client policy

The `summer-ui` registered client authenticates with `none`, supports only `authorization_code`, and requires PKCE with the `S256` challenge method. Its scopes are `openid`, `profile`, `customer.read`, and `customer.write`; it does not receive `offline_access`, `refresh_token`, `client_credentials`, or password grants. As a first-party management UI it does not require an authorization-consent screen. The generic framework capability for separately configured confidential clients does not grant those methods or grants to `summer-ui`.


## Customer scope enforcement

Customer list and detail reads require `SCOPE_customer.read`; create, update, activation changes, and permanent deletion require `SCOPE_customer.write`. The scopes remain independent, so write does not implicitly grant read. Summer UI requests both and uses the scopes actually present in the access token to guard Customer routes and write controls, but backend `@PreAuthorize` checks remain authoritative. In the current Sample every default authenticated user using `summer-ui` receives both scopes; user-role-to-scope mapping and read-only user administration are outside this scope.

## Login session policy

The Sample configures a 30-minute inactivity timeout for the Spring Security login session, does not enable remember-me, and allows multiple concurrent browser sessions without a maximum-session registry. Spring Security's session-fixation protection remains enabled. Logout invalidates the server session and removes `JSESSIONID`. The session cookie is `HttpOnly` and `SameSite=Lax`; it is `Secure` in production HTTPS and intentionally not `Secure` for local HTTP development.

When the login session expires, the next Authorization Code with PKCE authorization requires credential login again. A previously issued access token remains independently usable until its own expiry, for at most its fifteen-minute lifetime. A custom absolute-session-lifetime mechanism is outside this scope.
