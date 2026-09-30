# Summer UI browser authentication (#85)

Summer UI uses the `summer-ui` public client from Summer Sample. `oidc-client-ts`
performs Authorization Code + PKCE (S256), state, nonce, code exchange, and ID
Token validation. The browser sends credentials only through Spring Security's
standard `POST /login`, after obtaining the session's raw `GET /csrf` token.
The UI never calls the former mocked JSON login endpoint.

## Local integration

Start Summer Sample with `SUMMER_SAMPLE_ISSUER=http://127.0.0.1:5173`, then run
`yarn dev` in `summer-ui`. Open `http://127.0.0.1:5173/customer/drawer`. The
single browser origin is `127.0.0.1:5173`; Vite proxies `/api/**`, the OIDC
protocol paths, `/csrf`, and only `POST /login` to `127.0.0.1:8080`. It serves
`GET /login` and `/oauth2/callback` as SPA routes. The matching redirect and
post-logout URIs are registered in Summer Sample's `application.yml`.
Set `SUMMER_BACKEND_ORIGIN` before starting Vite when the backend does not
listen on the default `http://127.0.0.1:8080`. Set `VITE_ENABLE_MOCK=false` for
integrated browser acceptance; it disables the upstream example mocks while
leaving their default development behavior available for template demos.

The authority, client ID, callback URI, and post-logout URI can be set with
`VITE_OIDC_AUTHORITY`, `VITE_OIDC_CLIENT_ID`, `VITE_OIDC_REDIRECT_URI`, and
`VITE_OIDC_POST_LOGOUT_REDIRECT_URI`. For production, deploy the frontend and
backend behind one HTTPS origin and route the same paths to their respective
services. Do not place credentials or a client secret in Vite variables.

## Recovery and storage

The validated OIDC transaction holds the pre-login relative route, including
query and hash. A successful callback navigates within the SPA so its in-memory
user survives. Only OIDC transaction state and an allowlisted
Customer draft use `sessionStorage`; access and ID Tokens stay in
`oidc-client-ts`'s in-memory user store. Create recovery stores the three form
fields. Edit recovery also stores the target username and original email/name
and the Drawer/Modal mode so the restored form can calculate a minimal PATCH
and reopen the same form. The draft remains until
the user saves or discards it. Callback failure and logout clear transaction
and draft state. Reloading a protected route starts a new PKCE flow; no write
request is replayed. UserInfo is not requested.

## Verification and rollback

- `yarn test`: baseline contract and route/draft recovery tests.
- `yarn typecheck`, `yarn lint`, `yarn lint:style`, `yarn build`: frontend gates.
- `./gradlew :summer-sample:test --tests '*PublicClientPkceIntegrationTest'`:
  server code exchange with S256, claims, and raw CSRF response.
- `SUMMER_E2E_PASSWORD_FILE=/path/to/private-file yarn test:e2e:auth`:
  with the real backend, Vite, and Chrome running, checks login, original route,
  empty token storage, OIDC logout, and rejection of missing state, mismatched
  nonce, and altered verifier. The password file is private, untracked, and not
  printed by the test.
- Desktop (1365×800) and narrow (390×844) login screenshots were rendered and
  checked for legible text, form visibility, and overflow; the narrow layout
  hides the baseline promotional banner.

On 2026-09-30, the frontend recovery suite passed 3/3, typecheck and stylelint
passed, lint exited with 0 errors and 40 existing example-page warnings, and
the production build passed. The backend PKCE integration class passed 2/2.
The browser suite passed the successful login/logout path and three invalid
callback variants against Summer Sample's H2-backed local process. PostgreSQL
and the later Customer UI flows are covered by the #93 deployment acceptance
record in `docs/guides/summer4-deployment-93.md`.

The #85 implementation can be reverted as one commit. Stop the UI and backend
first, revert that commit, reinstall with the prior `yarn.lock`, and restart.
The backend public client from #80 remains available; reverting #85 restores
only the frontend's prior placeholder routes and mock login baseline.
