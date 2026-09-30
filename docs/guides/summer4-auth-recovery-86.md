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

## Rollback

Revert the #86 commits and the browser evidence follow-up commit. This removes the request interceptor and recovery markers while returning browser logout to the #85 implementation. No database migration or server configuration is involved. A browser with an old recovery marker can remove the `summer.auth.*` session keys or close its tab; these keys contain no tokens.
