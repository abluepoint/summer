# Summer UI authentication recovery (#86)

## Requirement trace

| #86 acceptance item | Implementation | Verification |
| --- | --- | --- |
| Browser logout clears local authentication and invokes RP logout | `signOut` clears Redux identity, the in-memory OIDC user, PKCE transactions, draft and recovery markers before `signoutRedirect` uses the ID Token hint | `scripts/e2e-auth.mjs` observes one `/connect/logout` request, checks browser storage after return to `/login?logout`, then confirms a protected route requires sign-in again |
| A 401 restarts authentication at most once; a 403 preserves authentication | The `/api/**` Axios response interceptor starts one PKCE redirect on 401. Its session marker remains through callback and clears only after a successful business response. A 403 displays an authorization message and leaves the OIDC user intact | `scripts/verify-auth.mjs` checks the guard; `scripts/e2e-auth-recovery.mjs` observes two controlled 401 responses but only one authorization request, and verifies that 403 leaves the avatar and session available |
| Restore view and unsaved Customer draft | The OIDC transaction carries a validated same-origin return path. Before redirect, a mounted Customer form can register a draft provider; only Customer fields are saved in `sessionStorage` and `consumeCustomerDraft` reads them once after callback | Browser recovery returns to the original Customer view and query; `scripts/verify-auth.mjs` checks allowed draft fields and single-use consumption. Customer forms are still pending #88, so actual form draft restoration remains unverified |
| Missing/expired token and network failure are actionable | The request interceptor blocks `/api/**` without a token and begins a bounded login. The resumed view receives a localized missing/expired notice. Failed network calls display a localized retry message | `scripts/e2e-auth-recovery.mjs` removes or expires the in-memory user and checks both notices after recovery; it also aborts a Customer request and checks the network message |
| Never replay writes | The response interceptor always rejects the original failed request. It never calls Axios again, and the recovery notice asks users to review and resubmit changes manually | `scripts/e2e-auth-recovery.mjs` intercepts an Axios POST with 401 and confirms one POST after reauthentication and view restoration; no backend write is made |

## Review evidence and limits

The code is confined to `summer-ui` and this guide. Protocol calls, form login and CSRF are outside the business API interceptor. No token is persisted; only the existing OIDC transaction and a narrow Customer draft may be stored in `sessionStorage`. The recovery marker prevents repeated 401 redirects until a successful business response. The browser suite requires the Summer backend and its UI integration profile; static and unit checks do not prove server session termination.

The #86 diff was reviewed against `AGENTS.md`, ADR 0014/0015, and the issue. No documented-standard violation or unrequested backend change was found. After #87 integration, Customer list/detail exist but no create/edit form yet registers a draft provider or consumes the saved draft. The #86 recovery interface and single-use draft tests are ready for #88.

With the Sample backend on 8080 and Vite on 5173, `yarn test:e2e:auth`, `yarn test:e2e:customer`, and `yarn test:e2e:recovery` passed in a real Chromium browser. The 401/403/network/write faults are controlled browser responses; the normal login, logout, Customer reads, and reauthentication use the real backend. On the integrated #87 tree, `yarn test` passed 9 tests with 0 failures/skips, `yarn typecheck` passed, `yarn lint` had 0 errors and 40 existing warnings, and the new browser script passed `node --check`. The earlier isolated #86 `yarn build` passed before #87 was integrated; the integrated build is covered by #87's verification record.

## Rollback

Revert the #86 commits and the browser evidence follow-up commit. This removes the request interceptor and recovery markers while returning browser logout to the #85 implementation. No database migration or server configuration is involved. A browser with an old recovery marker can remove the `summer.auth.*` session keys or close its tab; these keys contain no tokens.
