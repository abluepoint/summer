# Summer UI authentication recovery (#86)

## Requirement trace

| #86 acceptance item | Implementation | Verification |
| --- | --- | --- |
| Browser logout clears local authentication and invokes RP logout | `signOut` clears Redux identity, the in-memory OIDC user, PKCE transactions, draft and recovery markers before `signoutRedirect` uses the ID Token hint | `scripts/e2e-auth.mjs` observes one `/connect/logout` request, checks browser storage after return to `/login?logout`, then confirms a protected route requires sign-in again |
| A 401 restarts authentication at most once; a 403 preserves authentication | The `/api/**` Axios response interceptor starts one PKCE redirect on 401. Its session marker remains through callback and clears only after a successful business response. A 403 displays an authorization message and leaves the OIDC user intact | `scripts/verify-auth.mjs` checks single-use restart and status classification; browser integration requires the running backend |
| Restore view and unsaved Customer draft | The OIDC transaction carries a validated same-origin return path. Before redirect, a mounted Customer form can register a draft provider; only Customer fields are saved in `sessionStorage` and `consumeCustomerDraft` reads them once after callback | `scripts/verify-auth.mjs` checks path validation, allowed draft fields and single-use consumption. Current Customer routes are placeholders; their forms must register the provider and consume the draft when implemented |
| Missing/expired token and network failure are actionable | The request interceptor blocks `/api/**` without a token and begins a bounded login. The resumed view receives a localized missing/expired notice. Failed network calls display a localized retry message | `scripts/verify-auth.mjs` covers failure classification and login guard; `yarn typecheck`, `yarn lint`, and `yarn build` validate wiring |
| Never replay writes | The response interceptor always rejects the original failed request. It never calls Axios again, and the recovery notice asks users to review and resubmit changes manually | Source review of `src/auth/requests.ts`; Customer create/edit/delete browser verification follows the Customer UI ticket |

## Review evidence and limits

The code is confined to `summer-ui` and this guide. Protocol calls, form login and CSRF are outside the business API interceptor. No token is persisted; only the existing OIDC transaction and a narrow Customer draft may be stored in `sessionStorage`. The recovery marker prevents repeated 401 redirects until a successful business response. The browser suite requires the Summer backend and its UI integration profile; static and unit checks do not prove server session termination.

## Rollback

Revert the #86 commit. This removes the request interceptor and recovery markers while returning browser logout to the #85 implementation. No database migration or server configuration is involved. A browser with an old recovery marker can remove the `summer.auth.*` session keys or close its tab; these keys contain no tokens.
