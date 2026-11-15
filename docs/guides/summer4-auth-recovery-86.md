# Summer UI authentication recovery (#86)

## Requirement trace

| #86 acceptance item | Implementation | Verification |
| --- | --- | --- |
| Browser logout clears local authentication and invokes RP logout | `signOut` clears Redux identity, the in-memory OIDC user, PKCE transactions, draft and recovery markers before `signoutRedirect` uses the ID Token hint | `scripts/e2e-auth.mjs` observes one `/connect/logout` request, checks browser storage after return to `/login?logout`, then confirms a protected route requires sign-in again |
| A 401 restarts authentication at most once; a 403 preserves authentication | The `/api/**` Axios response interceptor starts one PKCE redirect on 401. Its session marker remains through callback and clears only after a successful business response. A 403 displays an authorization message and leaves the OIDC user intact | `scripts/verify-auth.mjs` checks the guard; `scripts/e2e-auth-recovery.mjs` observes two controlled 401 responses but only one authorization request, and verifies that 403 leaves the avatar and session available |
| Restore view and unsaved Customer draft | The OIDC transaction carries a validated same-origin return path. Before redirect, the Customer drawer registers a draft provider; only Customer fields are saved in `sessionStorage` and `consumeCustomerDraft` reads them once after callback | After #88 integration, `scripts/e2e-auth-recovery.mjs` confirms username, email, name and the exact list URL survive a POST 401 and PKCE callback; it edits the restored name before a manual retry |
| Missing/expired token and network failure are actionable | The request interceptor blocks `/api/**` without a token and begins a bounded login. The resumed view receives a localized missing/expired notice. Failed network calls display a localized retry message | `scripts/e2e-auth-recovery.mjs` removes or expires the in-memory user and checks both notices after recovery; it also aborts a Customer request and checks the network message |
| Never replay writes | The response interceptor always rejects the original failed request. It never calls Axios again, and the recovery notice asks users to review and resubmit changes manually | `scripts/e2e-auth-recovery.mjs` checks that the restored drawer has sent only the intercepted POST, then clicks Create manually, confirms exactly one new POST, reads the edited Customer from the real backend and deletes it with HTTP 204 |

## Review evidence and limits

The code is confined to `summer-ui` and this guide. Protocol calls, form login and CSRF are outside the business API interceptor. No token is persisted; only the existing OIDC transaction and a narrow Customer draft may be stored in `sessionStorage`. The recovery marker prevents repeated 401 redirects until a successful business response. The browser suite requires the Summer backend and its UI integration profile; static and unit checks do not prove server session termination.

The #86 diff was reviewed against `AGENTS.md`, ADR 0014/0015, and the issue. No documented-standard violation or unrequested backend change was found. #88 now registers the drawer draft provider and consumes the saved draft on remount. Its own `test:e2e:customer-create` verifies the three restored fields and one intercepted POST; the #86 follow-up additionally verifies the restored draft remains editable and can be submitted manually without an automatic replay.

With the Sample backend on 8080 and Vite on 5173, `yarn test:e2e:auth`, `yarn test:e2e:customer`, and `yarn test:e2e:recovery` passed in a real Chromium browser. The 401/403/network/write faults are controlled browser responses; the normal login, logout, Customer reads, and reauthentication use the real backend. On the integrated #87 tree, `yarn test` passed 9 tests with 0 failures/skips, `yarn typecheck` passed, `yarn lint` had 0 errors and 40 existing warnings, and the new browser script passed `node --check`. The earlier isolated #86 `yarn build` passed before #87 was integrated; the integrated build is covered by #87's verification record.

After #88 integration, `yarn test:e2e:recovery` and #88's `yarn test:e2e:customer-create` both passed against the real Sample and Vite proxy. The intercepted first POST reached no backend write; the manual second POST persisted the edited Customer, which was read back and removed with HTTP 204. Controlled 401 responses verify frontend recovery, while the authorization redirect, callback, successful create/read/delete and logout use real backend behavior. No test Customer remains.

On that same integrated tree, `yarn test` passed 13 tests with 0 failures/skips, `yarn typecheck` passed, and the extended browser script passed `node --check`. This continuation changed only the #86 browser test and its evidence document.

## Interrupted login recovery (2026-10-19)

An abandoned authorization navigation can leave the automatic-login guard in the current tab. The authenticated shell and Customer route now expose a localized **Sign in again** action when login cannot start or the automatic guard is exhausted. This explicit action begins a new bounded recovery cycle, removes the stale in-memory user, and starts a fresh PKCE flow with the current same-origin path, query and fragment as its return destination. Concurrent clicks start only one redirect; a failed redirect leaves the action available again.

Submitting the normal login form also resets the automatic-recovery guards before obtaining CSRF and posting to Spring Security. It preserves an in-flight PKCE transaction and the existing narrow Customer draft. Automatic 401 recovery remains single-use until a successful business response, tokens remain in memory, and failed business writes are never replayed.

`node --test scripts/verify-auth.mjs scripts/verify-auth-manual-recovery.mjs` passes 10 tests with no failures/skips. The shell lifecycle suite additionally covers the rendered retry action and redirect failure, passing 10 tests. `npm test`, `npm run typecheck`, and `npm run build` passed. Targeted ESLint without formatting checks has no errors and one existing hook dependency warning. In the real Edge tab that displayed the original dead-end message, clicking the new action completed the real backend PKCE callback, restored `/messages`, and re-established the WebSocket as `OPEN`. No task was created or message sent during this verification.

## Refresh loading feedback (2026-10-19)

Refreshing discards the in-memory token by design (ADR 0015), so an existing Servlet session completes another PKCE flow. The previous UI showed an empty initial root, an unpositioned shell spinner, the callback's “Completing sign in” message, another shell spinner while menus loaded, then the route chunk and business-data loaders. These sequential boundaries made ordinary startup look like repeated sign-in. The controlled shell test confirms one initial menu read and that background permission reads retain the mounted page; it does not indicate a redirect loop.

The initial HTML, callback, and authenticated shell now share the same centered, neutral “Loading…” surface and early CSS. It is present before JavaScript loads, remains visually consistent while authentication and menus settle, and gives way to the application once ready. Route-code and business-data loading remain inside the application content. The login form and callback error actions are unchanged, and no token persistence or authentication-policy change is introduced.

`node --test scripts/verify-startup-loading.mjs scripts/verify-shared-enterprise-entry.mjs` passes 13 tests, including callback wording, matching initial markup, and page retention during background reads. Full `npm test`, type checking, targeted ESLint and the startup stylesheet lint pass. Real Edge refresh displayed “正在加载…”, returned to the original Customer insights route, and menu navigation to realtime messages reached `OPEN`.

## Shared loading appearance (2026-10-19)

`AppLoading` provides both the full-screen startup surface and a centered content-section surface. Route chunks and standalone page loaders use the section variant; specific progress wording remains available. Arco table, button and overlay loaders share the same CSS ring, spacing and theme colors, with animation disabled when reduced motion is requested. Widget loading behavior and skeleton placeholders retain their existing purpose.

The startup suite adds a rendered section-loader case, covering its status role, ring and optional progress text. Together with the shell lifecycle suite it passes 14 tests. Type checking, the full frontend suite and targeted CSS lint also pass.

## Rollback

Revert the #86 commits and the browser evidence follow-up commit. This removes the request interceptor and recovery markers while returning browser logout to the #85 implementation. No database migration or server configuration is involved. A browser with an old recovery marker can remove the `summer.auth.*` session keys or close its tab; these keys contain no tokens.
