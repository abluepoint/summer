# Use official Arco Design Pro as the Summer UI baseline

Summer UI will be created as an independent frontend at `/Users/bluepoint/Project/abluepoint/summer/summer-ui`, using the official Arco Design Pro React/Vite structure and patterns as its baseline. We deliberately do not copy or depend on `summer-sample-arco-pro`, so the UI follows the upstream Arco Design Pro approach rather than inheriting sample-specific modifications or uncommitted state; Summer-specific pages and backend integration will be added on top of that baseline.

## Considered Options

- Use `summer-sample-arco-pro` as the source: rejected because it is a local sample with project-specific changes and is not the requested official baseline.
- Use the official Arco Design Pro baseline: accepted for upstream alignment, reusable Pro conventions, and a clearer boundary between the reference template and Summer-specific code.

## Backend response integration

Summer UI will retain the official Arco Design Pro Axios organization while adapting only `/api/**` business requests to Summer's `ApiResponse<T>` envelope. Successful responses return `data` directly; application failures and non-2xx responses become a typed `SummerApiError` retaining `code`, `message`, `violations`, `requestId`, `timestamp`, and HTTP status. The Bearer token is read from in-memory authentication state by the request interceptor. OIDC protocol endpoints, authorization, token exchange, logout, and form-login submission bypass business-envelope handling; `GET /csrf` is also treated as a raw response.

This keeps protocol traffic separate from application traffic, preserves Summer's diagnostic metadata without exposing it as business state, and avoids persisting access tokens in Redux or browser storage.


## UI error mapping

Customer screens will map Summer errors consistently using existing Arco components rather than a custom error-display component. Validation violations from `400` responses are assigned to matching `CustomerForm` fields, while unmapped validation errors remain visible in a form-level `Alert`. A `401` clears in-memory authentication and may start PKCE authentication at most once; after authentication the UI restores the page and unsaved form state but never automatically replays a create, update, or delete request. A `403` presents an authorization result without forcing login. A `404` presents a missing-resource result on detail pages and preserves form state when an edit target disappears. A `409` maps known username or email conflicts to their fields and otherwise preserves the backend message at form level. Server and network failures use an Arco notification and expose a copy action for `requestId` when available.


## Mock boundary

The official Arco Design Pro mock infrastructure and its example-page behavior will remain available for local development so upstream routes continue to work. Mocking is development-only and must not intercept or provide fallback responses for Summer business or security endpoints, including `/api/customers/**`, `/oauth2/**`, `/.well-known/**`, `/connect/**`, `/csrf`, and `POST /login`; those paths always target the real Spring backend. Production builds do not enable the mock service, and final end-to-end acceptance uses the real backend rather than mock evidence. No additional mock framework will be introduced.


## Localization

Summer UI defaults to Simplified Chinese while retaining the official Arco Design Pro locale organization and language switch. Existing official example pages continue to use their upstream localized content. Summer-owned login, Customer navigation, list, form, detail, confirmation, validation, error, and authentication-state text will provide both `zh-CN` and `en-US` resources without adding another internationalization dependency. Recognized frontend error codes and validation rules use localized text, while the backend diagnostic message remains available rather than being discarded or rewritten.


## Navigation of upstream examples

All routes and pages that belong to the pinned official baseline and still build successfully will remain available, including their upstream permission and development-mock behavior. Their menu entries will be grouped after Summer business navigation under an `Arco Pro 示例` section instead of competing with primary business tasks. Summer login, OAuth callback, and Customer child routes remain outside that group; the authenticated default destination is `/customer/drawer`. Direct navigation to an original official route continues to work. The exact retained route inventory will be derived from the pinned upstream commit rather than reconstructed from memory.

## Verification and acceptance gates

The implementation is accepted only when backend tests, frontend static and unit checks, production builds, real end-to-end tests, and browser visual inspection pass as distinct gates. Backend coverage includes Customer validation and immutability, pagination/filtering/sorting, response envelopes, error mappings, scope enforcement, OAuth2/OIDC and PKCE behavior, CSRF form login, claims, token lifetimes, logout, session behavior, and PostgreSQL constraints through Testcontainers, including username reuse after physical deletion.

The frontend retains the pinned official baseline's lint, type-check, build, and test conventions. If that baseline has no suitable unit-test stack, Summer UI adds Vitest and Testing Library. Unit and integration coverage includes `CustomerForm`, request transformation, `/api/**` envelope handling, `SummerApiError`, in-memory authentication, PKCE single-restart protection, scope-aware routing and controls, dirty-form protection, and all three create/edit modes. Playwright drives the final end-to-end suite with development Mock disabled and real Summer backend and PostgreSQL services.

End-to-end acceptance covers form login and PKCE callback, Customer list/detail, Drawer/Modal/route-page create and edit, state preservation, deactivate/reactivate, exact-username deletion confirmation, logout, and session expiry. Retained upstream example routes receive smoke coverage. Desktop and narrow-screen layouts, keyboard/focus behavior, browser console errors, unexpected network failures, Mock interception boundaries, and accidental persistence or logging of credentials and tokens are inspected explicitly; one gate cannot substitute for another.

## Build and production deployment boundary

Summer UI remains an independently built Node/Vite project. The Summer backend Gradle build does not run the frontend package manager and does not embed the UI `dist` directory in the Summer Sample JAR. Local integration uses the Vite proxy; production serves the frontend and backend behind one HTTPS origin through an external reverse proxy. The implementation supplies a production frontend build and an example proxy-routing configuration, but Docker, Kubernetes, cloud-platform, and CI/CD deployment definitions are outside this scope.

The proxy sends `POST /login`, `/api/**`, `/oauth2/**`, `/.well-known/**`, `/connect/**`, and `/csrf` to the backend. It serves `GET /login` through the SPA and falls back to `index.html` for frontend deep links such as `/customer/**` and `/oauth2/callback`. Summer UI uses `window.location.origin` as the default OIDC authority with an environment override. Production configures the Authorization Server issuer, redirect URI, and post-logout redirect URI externally to the same public HTTPS origin.
