# AICT Identity Console 完整产品与技术规格

> 状态说明（2026-09-30）：本文保留原始需求与方案供追溯；其中独立 `aict-identity`、Vben/Vue 前端工程选择已由 [ADR 0023](../adr/0023-consolidate-identity-frontend-in-summer-ui.md) 替代。身份管理前端改在 `summer-ui/` 实现。业务需求不因前端合并而自动完成；BFF 与现有 public-client PKCE 的安全边界仍须单独对齐。

## Problem Statement

AICT 需要把新引入的 Vben 前端平台建设成可长期演进的统一身份管理产品，而不是在上游 Demo 应用中直接堆叠业务页面。当前上游应用仍采用模板化登录、前端 token、用户信息、权限码和后端菜单假设，与 Summer 原生响应契约、机密 OAuth2 客户端模型、显式企业隔离、动作级 Capability、条件写和 Agent/MCP 身份链不一致；产品子应用、同源 BFF、测试接缝和可执行 CI 也尚未建立。

如果直接并行铺开所有页面，运行时不匹配、会话安全、跨企业串租户、前后端授权漂移、重复写、并发覆盖和 Agent 代表人调用 MCP 时的身份混淆会在后期集中暴露。项目需要一份贯通产品体验、应用边界、BFF、IAM 领域能力、安全契约、测试门禁和实施顺序的完整规格，使后续执行代理可以按纵向切片交付，而不必重新解释已经确认的架构决策。

## Solution

建设一个独立的 AICT Identity Console 产品子应用。该 Product Application 复用 Vben Platform 的产品无关能力，但拥有独立入口、Route Catalog、配置、测试、构建、CI 和部署产物；现有上游应用、Demo、backend mock、文档和 playground 保持不变，继续作为示例或平台验证资产。

控制台通过同源 BFF 接入 Summer IAM。OAuth2/OIDC 客户端凭据和 token 只保存在服务端，浏览器仅持有安全的 Product Session Cookie，并使用 Cookie-to-header CSRF token 保护全部状态变更。应用由一个 Session Snapshot 启动，分别加载 Platform Operations Workspace 或带显式 Enterprise Context 的 Enterprise Workspace Capability Set。前端拥有导航结构，服务端拥有授权真相；UI 只消费动作级 Capability Code，不使用角色名、OAuth scope 或服务端菜单树判权。

产品采用垂直 Module 和 tracer-bullet 交付方式：先固定运行时并建立未改动上游基线，再创建隔离子应用，打通会话、双工作区、企业上下文和 Capability，随后以企业成员目录完成第一个全链路切片，再交付平台企业审核，最终扩展组织、邀请、应用、授权策略、Agent/MCP 和审计。每个增量必须可构建、可测试、可演示，并与所依赖的 IAM 后端契约同步交付。

## User Stories

1. As a platform operator, I want to sign in through the organization’s OIDC flow, so that no OAuth token needs to be stored in my browser.
2. As a platform operator, I want to see my Product Session state after returning from OIDC, so that I know which identity and workspaces are available.
3. As a platform operator, I want to enter the Platform Operations Workspace without selecting an enterprise, so that system governance is not confused with tenant administration.
4. As a platform operator, I want to see only platform routes and actions granted by my Capability Set, so that unavailable operations are not presented as usable.
5. As a platform operator, I want to review pending enterprise qualifications, so that valid enterprises can be activated.
6. As a platform operator, I want to approve an enterprise qualification with a confirmed server response, so that the UI never reports success before activation is durable.
7. As a platform operator, I want to reject an enterprise qualification with a reason, so that the enterprise can understand and correct the submission.
8. As a platform operator, I want stale qualification updates to be rejected, so that I do not overwrite another reviewer’s decision.
9. As a platform operator, I want platform actions to be audited with request and trace identifiers, so that governance decisions can be investigated.
10. As an enterprise administrator, I want to select an enterprise membership from my Session Snapshot, so that I can enter the correct Enterprise Workspace.
11. As an enterprise administrator, I want the selected enterprise to be explicit in the URL, so that deep links and multiple browser tabs remain tenant-safe.
12. As an enterprise administrator, I want every tenant request to carry the enterprise identifier, so that the server can authorize the Enterprise Context independently.
13. As an enterprise administrator, I want switching enterprises to reload the correct Capability Set and remote data, so that data from one enterprise is never reused in another.
14. As an enterprise administrator, I want unauthorized enterprise deep links to fail closed, so that guessing an enterprise identifier cannot cross tenant boundaries.
15. As an enterprise administrator, I want to view enterprise profile and qualification status, so that I can maintain the organization’s registered information.
16. As an enterprise administrator, I want to edit enterprise profile fields with conditional writes, so that concurrent changes are detected instead of silently overwritten.
17. As an enterprise administrator, I want to see a paginated member directory, so that large enterprises remain manageable.
18. As an enterprise administrator, I want to filter and sort members, so that I can quickly find identities requiring action.
19. As an enterprise administrator, I want to open a member’s details, so that I can review account, organization, position, role, and credential summaries.
20. As an enterprise administrator, I want to enable an eligible member, so that the member can regain enterprise access.
21. As an enterprise administrator, I want to disable a member, so that enterprise access and active enterprise-scoped sessions are revoked without deleting history.
22. As an enterprise administrator, I want disabling a member to refresh lists, details, Session Snapshots, and Capability Sets where relevant, so that stale authorization is removed quickly.
23. As an enterprise administrator, I want to process member resignation with handover checks, so that owned organizations, applications, and Agents are not orphaned.
24. As an enterprise administrator, I want to add a member while reusing an existing global UserAccount when appropriate, so that one person is not represented by duplicate identities.
25. As an enterprise administrator, I want to import members asynchronously, so that large imports do not hold an HTTP transaction open.
26. As an enterprise administrator, I want to export member data through an asynchronous or bounded flow, so that large result sets remain reliable and auditable.
27. As an enterprise administrator, I want personal information masked by default in lists, so that routine administration exposes the minimum necessary data.
28. As an organization administrator, I want to view an enterprise organization tree, so that I can understand reporting and membership structure.
29. As an organization administrator, I want to create a department under a valid parent, so that the organization can evolve without breaking tenant boundaries.
30. As an organization administrator, I want sibling organization codes to be unique, so that integrations can refer to stable units.
31. As an organization administrator, I want to move or edit an organization unit with concurrency protection, so that simultaneous structural changes do not overwrite one another.
32. As an organization administrator, I want deletion blocked when a unit has children or effective members, so that organization relationships are not silently lost.
33. As an organization administrator, I want to assign one primary organization and optional concurrent assignments to a member, so that real employment structures can be represented.
34. As an organization administrator, I want to manage positions separately from roles, so that job structure is not confused with authorization.
35. As a security administrator, I want to create enterprise roles as configurable bundles of Capability Codes, so that permissions are governed without hard-coding role names in the UI.
36. As a security administrator, I want to assign and revoke roles with validity periods, so that temporary access expires predictably.
37. As a security administrator, I want policies to further allow or deny actions beyond role bundles, so that contextual authorization can be expressed.
38. As an enterprise administrator, I want to send a targeted invitation, so that a known person can join with predefined organization, position, and role assignments.
39. As an enterprise administrator, I want to create a bounded invitation link, so that controlled self-service onboarding is possible.
40. As an invitee, I want to accept a valid invitation idempotently, so that retries do not create duplicate memberships.
41. As an enterprise administrator, I want to revoke an invitation, so that leaked or obsolete invitations cannot be used.
42. As an applicant, I want to apply to join an enterprise, so that enterprises can support approval-based onboarding.
43. As an enterprise administrator, I want to approve or reject join applications, so that membership creation remains governed and auditable.
44. As an application administrator, I want to register an Application as a distinct Principal, so that its identity, configuration, credentials, and grants have clear ownership.
45. As an application administrator, I want to configure secure OIDC/OAuth2 combinations, so that unsafe grant, response, authentication, redirect, and signing choices are rejected.
46. As an application administrator, I want to see when an Application is misconfigured, so that it cannot be mistaken for a production-ready client.
47. As an application administrator, I want to grant Application access to all members, organizations, roles, or individual members, so that business access rules can be represented.
48. As an application administrator, I want explicit deny grants to take precedence at the same level, so that exclusions are enforceable.
49. As an application administrator, I want to rotate credentials with a controlled grace period, so that clients can migrate without exposing secrets or causing abrupt outages.
50. As an application administrator, I want newly created secret material displayed only once, so that long-term plaintext storage is avoided.
51. As an application administrator, I want to revoke a credential and invalidate affected sessions, so that compromised credentials stop working promptly.
52. As an Agent owner, I want to register an Agent as a machine Principal separate from an Application and a human, so that its actions have a distinct accountable identity.
53. As an Agent owner, I want to assign an accountable member and a minimum Agent Grant, so that an Agent never receives unlimited authority by default.
54. As an Agent owner, I want to define credential rotation, active credential count, IP, and mTLS requirements, so that Agent authentication follows enterprise risk policy.
55. As an Agent owner, I want to enable, disable, and revoke an Agent with server-confirmed results, so that machine access state is reliable.
56. As a runtime security operator, I want every running AgentInstance authenticated through verifiable workload identity or attestation, so that a process cannot gain authority by self-reporting an Agent identifier.
57. As a runtime security operator, I want AgentInstance credentials to be short-lived and revocable, so that compromise is bounded to a specific workload instance.
58. As a human delegator, I want to delegate only selected tools, resources, capabilities, risk levels, and time windows to an Agent, so that delegated authority is narrower than my own authority.
59. As a human delegator, I want redelegation disabled by default, so that an Agent cannot silently expand its delegation chain.
60. As a security administrator, I want every child delegation to shrink scope, lifetime, risk, and depth, so that delegation never escalates privileges.
61. As a workflow operator, I want trusted schedulers, workflows, interactive sessions, or parent Agents to create AgentExecutionContext records, so that initiator and effective subject cannot be forged by the runtime.
62. As an auditor, I want each Agent task to preserve initiator, actor, AgentInstance, effective subject, delegation, client, transport actor, resource server, and tool identity, so that responsibility can be reconstructed.
63. As a connection service operator, I want the connection service registered as its own Application Principal, so that transport execution is not confused with the human or Agent actor.
64. As a connection service operator, I want to synchronize MCP Client, Server, Tool, schema digest, and catalog revision without sending secrets or tokens, so that IAM can authorize against a safe directory projection.
65. As a connection service operator, I want repeated catalog revisions handled idempotently and conflicting revisions rejected, so that stale discovery data cannot overwrite current metadata.
66. As a security administrator, I want IAM-owned risk, Capability, and Policy settings preserved during catalog synchronization, so that discovery metadata cannot rewrite authorization policy.
67. As an Agent runtime, I want a single execution decision for a specific connection, tool, schema, argument digest, and execution context, so that the exact intended action is evaluated.
68. As an Agent runtime, I want authorization uncertainty to return DENY or ERROR, so that outages never degrade into permissive behavior.
69. As a connection service, I want an ALLOW decision to produce a short-lived, single-use, audience-bound execution permit, so that authorization cannot be replayed for another tool or connection.
70. As a connection service, I want to atomically claim a permit before calling an MCP Server, so that concurrent nodes cannot execute the same authorization twice.
71. As a connection service, I want the permit bound to connection revision, schema digest, argument digest, AgentInstance, and execution context, so that configuration or input changes invalidate it.
72. As a connection service, I want to obtain a resource-specific OAuth token for the target MCP Server, so that inbound tokens are never forwarded across audiences.
73. As a high-risk operation approver, I want an approval request bound to the exact subject chain, tool, resource, arguments, and expiry, so that approval cannot be reused for a changed operation.
74. As a high-risk operation approver, I want to approve or reject only when I have the required Capability and a fresh authenticated session, so that approval itself is protected.
75. As a connection service, I want completion callbacks to be idempotent, so that retrying a result report does not repeat the tool invocation.
76. As an auditor, I want uncertain execution outcomes recorded as EXECUTION_UNKNOWN, so that ambiguous timeouts are not reported as safe failures or retried blindly.
77. As an auditor, I want to search MCP invocations by person, Agent, instance, Server, Tool, decision, request identifier, and time, so that investigations can follow the full identity chain.
78. As a policy author, I want to edit a draft Policy without changing the currently published version, so that production authorization remains stable.
79. As a policy author, I want published PolicyVersion records to be immutable, so that historical decisions can be reproduced.
80. As a policy author, I want to run test cases before publishing, so that expected ALLOW, DENY, and ERROR outcomes are verified.
81. As a policy author, I want to publish, pause, revoke, and copy policies, so that the policy lifecycle matches operational needs.
82. As a resource service, I want low-latency authorization evaluation with a stable reason code and policy version, so that decisions are enforceable and explainable.
83. As an auditor, I want AuditEvent records to be append-only and linked to request, trace, actor, resource, action, decision, and policy version, so that evidence cannot be silently rewritten.
84. As an auditor, I want sensitive passwords, tokens, secrets, private keys, verification codes, full identity numbers, and unnecessary personal data excluded from audit details, so that observability does not become a data-leak channel.
85. As a support engineer, I want structured Summer errors and request identifiers displayed safely, so that incidents can be diagnosed without exposing credentials.
86. As a user, I want authentication loss to redirect or recover predictably, so that an expired Product Session does not leave the UI in a misleading state.
87. As a user, I want forbidden actions to invalidate stale capabilities and show an actionable error, so that revoked access disappears quickly.
88. As a user, I want concurrent update conflicts to show that data changed and require a refresh, so that my edits do not overwrite newer data.
89. As a user, I want mutation retries to reuse a stable Idempotency-Key, so that transient network failures do not duplicate commands.
90. As a user, I want security-sensitive actions to remain pending until the server confirms them, so that the UI never presents optimistic authorization success.
91. As a frontend maintainer, I want the Route Catalog versioned with the Product Application, so that Vue component structure is not coupled to IAM services.
92. As a frontend maintainer, I want all remote IAM state owned by TanStack Vue Query, so that caching, invalidation, retries, and request deduplication are consistent.
93. As a frontend maintainer, I want Pinia limited to Vben Platform and local interaction state, so that remote entities are not duplicated across stores.
94. As a frontend maintainer, I want query keys scoped by workspace and Enterprise Context, so that cached records cannot cross tenant boundaries.
95. As a frontend maintainer, I want Session Snapshot and Capability Set held only in memory and conditionally revalidated, so that persistent browser storage never becomes an authorization source.
96. As a frontend maintainer, I want the request adapter to consume Summer’s native response envelope, so that the backend does not maintain a Vben-only compatibility protocol.
97. As a frontend maintainer, I want OAuth2/OIDC endpoints to bypass the application response envelope, so that protocol compliance is preserved.
98. As a frontend maintainer, I want IAM behavior organized into deep vertical Modules, so that routes consume small interfaces instead of orchestrating repositories and HTTP details.
99. As a platform maintainer, I want upstream Vben applications and playgrounds preserved, so that platform verification and future upstream synchronization remain possible.
100. As a platform maintainer, I want product code isolated from Vben Platform packages, so that product concepts do not contaminate the upstream-derived foundation.
101. As a release engineer, I want local and CI runtime versions pinned, so that dependency and build results are reproducible.
102. As a release engineer, I want the untouched upstream baseline recorded before product work, so that pre-existing failures are distinguished from product regressions.
103. As a release engineer, I want every product pull request to run lint, type checks, unit/component tests, BFF contracts, production build, and critical E2E, so that unsafe changes cannot merge silently.
104. As a release engineer, I want upstream integration branches to additionally test shared packages and playground E2E, so that platform synchronization does not break reusable capabilities.
105. As a product owner, I want delivery organized as demonstrable tracer-bullet increments, so that the highest authentication, tenant, authorization, and deployment risks are proven early.

## Implementation Decisions

1. The Product Application is the AICT Identity Console, not a generic Summer Sample administration UI or a renamed Vben Demo.
2. Create an isolated workspace application named `aict-identity` under the repository’s application workspace, with its own entry, routes, product configuration, tests, build target, CI target, and deployment artifact.
3. Keep the existing Vben application variants, backend mock, documentation, and playground unchanged as upstream examples or Vben Platform verification assets; the earlier repository-pruning strategy is superseded.
4. Shared upstream-derived packages remain the Vben Platform and must stay product-neutral. Product-specific behavior belongs to the Product Application or, only after genuine reuse appears, a product-owned Business Package.
5. Deliver one deployable Product Application with two isolated areas: Platform Operations Workspace and Enterprise Workspace.
6. Platform Operations Workspace owns system-level enterprise review and platform governance and never inherits an Enterprise Context.
7. Enterprise Workspace owns tenant-scoped identity, organization, invitation, Application, Agent, policy, MCP, and audit administration.
8. Enterprise Context is explicit in routes and tenant API paths. Product Session state must not contain a mutable “current enterprise”.
9. BFF and resource services independently validate enterprise membership and authorization on every request, including deep-linked and multi-tab requests.
10. The Product Application owns the Route Catalog, workspace navigation, page composition, and route-to-Capability mapping.
11. The server does not return Vue component paths, implementation-shaped menu trees, or frontend route definitions.
12. The UI consumes stable action-level Capability Codes in `{workspace}.{resource}.{action}` form.
13. Roles are configurable bundles of Capability Codes. Authorization Policies may further allow or deny actions. OAuth scope remains a protocol delegation boundary and is not a UI permission.
14. Hiding a route or control is a usability behavior only; the BFF and resource service remain authoritative for every authorization decision.
15. Browser authentication uses a same-origin BFF. OAuth2/OIDC client credentials, access tokens, and refresh tokens never enter browser storage.
16. The browser holds only a server-managed Product Session Cookie configured with HttpOnly, Secure, and an OIDC-compatible SameSite policy.
17. All state-changing BFF operations, including logout, require a readable CSRF Cookie copied into a request header and validated with Origin checking as defense in depth.
18. OIDC redirects establish authentication; deleting the Product Session coordinates local session destruction and protocol logout/revocation as applicable.
19. Application bootstrap uses one Session Snapshot containing the current principal, Product Session lifetime, available workspaces, and enterprise memberships, without OAuth tokens or navigation data.
20. Platform and enterprise Capability Sets are separate contracts; enterprise Capability requests always identify the Enterprise Context explicitly.
21. Session Snapshot and Capability Set responses expose ETag or an equivalent server revision for conditional revalidation.
22. Session Snapshot, Capability Set, and all other BFF/IAM remote state are owned exclusively by TanStack Vue Query.
23. Pinia remains limited to Vben Platform state and local UI interaction state and must not duplicate remote entities, member lists, Session Snapshot, or Capability Set.
24. Query keys include workspace and, where applicable, enterprise identifier. Cross-enterprise reuse of tenant data is prohibited.
25. Session and capability data remain in memory only. They are revalidated after short freshness windows, window focus, workspace navigation, and Enterprise Context navigation.
26. A `401` invalidates the Product Session and affected remote data. A `403` invalidates the relevant Capability Set and affected queries before presenting the authorization result.
27. The request adapter consumes Summer’s native `success/data/error/requestId/timestamp` Unified response for management APIs.
28. Structured business and framework errors retain code, message, violations, and request identifier for user feedback and support correlation.
29. OAuth2/OIDC, discovery, token, logout, binary, streaming, and other protocol/resource responses remain raw and are not forced into the Unified response.
30. All management mutations send an `Idempotency-Key`. Retries of the same user command reuse the same key and request meaning.
31. Updates or deletes of an existing IAM resource also send `If-Match` based on the current resource ETag. Stale versions return `412 Precondition Failed` and require refresh or explicit user reconciliation.
32. Authorization-sensitive mutations wait for the server result and then invalidate affected queries. They do not display optimistic success.
33. IAM UI behavior is organized as vertical Modules aligned with Tenant Directory, Identity Directory, Organization Directory, Invitation Workflow, Client Registry, Agent Registry, Connection Service Trust, MCP Registry, Delegation Authority, Authorization Policy, and Audit Ledger.
34. Each vertical Module hides request orchestration, response adaptation, validation, Vue views, remote-state rules, and interaction behavior behind a small interface consumed by the Route Catalog.
35. A vertical Module is promoted to a Business Package only when a second real Product Application needs it; workspace package extraction is not used as speculative layering.
36. IAM business models remain outside Summer Security. Summer Security provides reusable Resource Server, OIDC, and optional Authorization Server capabilities only.
37. The IAM backend uses UUID internal identifiers and DID values as externally stable identity identifiers; DIDs are not database foreign keys.
38. UserAccount represents a global natural-person login identity, while EnterpriseMember represents that account’s membership within one Enterprise.
39. Principal provides common identity semantics for USER, APPLICATION, and AGENT subjects. MCP Server service identity reuses its Application Principal rather than introducing an overlapping principal type.
40. Credential secrets and private material are never stored in plaintext business records. Persistent records contain algorithm, fingerprint, state, validity, and controlled secret references.
41. Enterprise, membership, organization, invitation, Application, Agent, Policy, MCP, and audit records retain explicit lifecycle states instead of collapsing disabled, resigned, revoked, expired, and deleted into one flag.
42. All tenant-owned data contains an enterprise identifier, and both query and authorization layers enforce tenant isolation.
43. Enterprise qualification approval activates the Enterprise and emits an activation event. Rejection preserves review history and reason.
44. Member onboarding reuses an existing UserAccount when identity matching succeeds and atomically creates EnterpriseMember plus default organization, position, and role relationships.
45. Member disabling ends effective relationships and triggers enterprise-scoped session and authorization revocation while retaining identity and audit history.
46. Member resignation triggers ownership and handover checks for organizations, Applications, and Agents.
47. Organization trees use explicit parent relationships, tenant-local codes, bounded depth, historical assignments, and guarded deletion.
48. Positions and Roles remain distinct models. Membership-to-organization, position, and role relationships are explicit and time-bounded.
49. Invitation tokens are stored as hashes, are bounded by target, usage, and expiry, and are accepted idempotently.
50. Applications are Principals with OAuthClientConfig, ApplicationGrant, Consent, Credential, state, and optimistic version semantics.
51. Production OAuth configuration defaults to Authorization Code with PKCE for web clients, exact redirect matching, safe client authentication, refresh-token rotation, and rejection of unsafe legacy protocol combinations.
52. Production Authorization Server behavior uses persistent client, authorization, consent, and key adapters; Summer Sample in-memory identities and keys are never treated as production identity storage.
53. Agents are machine Principals with accountable owners, minimum Agent Grants, credential policy, and explicit lifecycle state.
54. AgentInstance represents a short-lived authenticated workload instance and must be derived from verifiable workload identity or attestation rather than a self-reported Agent identifier.
55. AgentExecutionContext is created only by trusted interactive sessions, schedulers, workflows, or delegated parent Agents and binds initiator, effective subject, source, request digest, enterprise, and DelegationGrant.
56. MCP identity distinguishes initiator, actor, AgentInstance, effective subject, delegator, OAuth client, transport actor, resource server, and tool for every invocation.
57. DelegationGrant expresses explicit, time-bounded, resource-bounded, tool-bounded, capability-bounded, and risk-bounded authority. Redelegation is disabled by default and can only reduce authority.
58. The independent connection service owns physical endpoints, secrets, token vault, OAuth/MCP handshakes, sessions, retries, timeouts, discovery, and tool execution.
59. IAM owns identities, Capability and Policy decisions, delegation, risk, HumanApproval, single-use execution permits, and audit evidence.
60. ConnectionService is authenticated as its own Application Principal through mTLS or private-key client authentication and cannot impersonate the human, Agent, or MCP Server subject.
61. MCP catalog synchronization carries only safe Client, Server, Tool, schema digest, catalog digest, and revision projections; secret, refresh token, private key, and reusable authorization material are forbidden.
62. Catalog revisions move monotonically. Same revision and digest is an idempotent replay; same revision with a different digest is a conflict; changed schema or catalog invalidates incompatible outstanding permits.
63. IAM-owned Tool risk, required scope, Capability, approval, and Policy fields are never overwritten by connection discovery metadata.
64. Runtime permission is the intersection of effective-subject permission, Agent Grant, DelegationGrant, token scope and audience, Tool Capability and Policy, and HumanApproval when required.
65. Missing, uncertain, expired, revoked, or mismatched identity and authorization inputs fail closed as DENY or ERROR.
66. ALLOW creates an immutable McpInvocation authorization record and a short-lived, single-use McpExecutionPermit in the same transaction.
67. Execution permits bind the intended connection and revision, Tool and schema digest, argument digest, execution context, AgentInstance, audience, expiry, and optional proof-of-possession key.
68. The connection service atomically claims a permit before tool execution and obtains or exchanges a token scoped to the exact MCP Server resource; inbound tokens are never forwarded.
69. HumanApproval is bound to one defined identity chain, Tool, resource, argument digest, risk, approver, and expiry. Any meaningful change requires a new decision and approval.
70. Completion and failure callbacks are idempotent and store only execution status, result digest, remote request reference, latency, and other non-sensitive evidence by default.
71. An ambiguous post-send timeout is recorded as EXECUTION_UNKNOWN and reconciled; a non-idempotent Tool is not automatically retried.
72. Policies use editable drafts and immutable published PolicyVersion records. Publication requires static validation and passing PolicyTestCase results.
73. Runtime authorization returns ALLOW, DENY, or ERROR plus stable reason code and policy version. Uncertainty and timeouts fail closed.
74. AuditEvent is append-only, tenant-scoped where applicable, request- and trace-correlated, and explicitly excludes credentials and unnecessary personal data.
75. Cross-module state changes use local transactions within an aggregate and transactional outbox events across modules.
76. PostgreSQL is the authority for business and protocol persistence; Redis is limited to short-lived challenges, session indexes, hot authorization state, and rate limiting; controlled KMS/HSM/Vault facilities own secrets and keys.
77. The IAM management application begins as a modular monolith. Authorization Server may initially share a process behind a separate security chain, while the connection service remains an independent runtime boundary.
78. The implementation order is risk-first: pin runtime and baseline; create the isolated Product Application; establish BFF/session/workspace/context/capability contracts; deliver the member-directory tracer; deliver enterprise review; then extend organization, invitation, Application, policy, Agent/MCP, and audit Modules.
79. Local and CI runtime use Node `24.16.0` with Corepack-provided `pnpm@11.16.0`; dependency initialization uses a frozen lockfile and does not silently rewrite it.
80. Before product behavior is added, record the untouched upstream type-check, unit-test, and production application build results. Existing failures are classified separately from product regressions.
81. Product CI must run for the actual repository rather than remaining disabled by an upstream-repository condition.
82. Normal product pull requests require formatting and linting, TypeScript checking, unit/component tests, BFF contracts, production `aict-identity` build, and critical Playwright smoke tests.
83. Upstream integration work additionally runs the shared package suite and playground E2E before the Vben Platform baseline is accepted.
84. Dependency upgrades are isolated from product changes so that runtime, upstream, and business regressions can be attributed accurately.

## Testing Decisions

1. Tests assert externally observable behavior and security contracts rather than component internals, private helper calls, store layout, or incidental DOM structure.
2. The highest acceptance seam is a real Playwright journey against the deployed Product Application, same-origin BFF, and Summer IAM test environment.
3. The primary integration seam for Vue Modules is the BFF HTTP boundary. Tests stub or control HTTP contracts, not internal composables or repositories.
4. Pure unit tests are reserved for deterministic protocol rules such as Unified response adaptation, structured error mapping, query-key construction, Capability evaluation, cache invalidation selection, CSRF header generation, Idempotency-Key reuse, and If-Match generation.
5. Route Catalog tests verify that workspace and enterprise routes require the correct Capability Codes without treating route visibility as server authorization.
6. Session tests cover authenticated bootstrap, unauthenticated redirect, expired session, logout, ETag revalidation, window focus, workspace navigation, and `401` invalidation.
7. Capability tests cover platform and enterprise separation, ETag revalidation, `403` invalidation, revoked membership, changed policy, and enterprise-keyed caching.
8. Enterprise Context tests use two enterprises and concurrent tabs to prove that routes, requests, queries, mutations, and invalidation never cross tenant boundaries.
9. Request-adapter tests verify that management APIs consume Summer Unified response while OAuth2/OIDC and raw resource responses remain unwrapped.
10. Error tests verify validation violations, business error codes, framework errors, request identifiers, network failures, `401`, `403`, `409`, `412`, and safe support messaging.
11. Mutation tests prove stable Idempotency-Key reuse after uncertain transport failure and prove that a distinct user command receives a distinct key.
12. Conditional-write tests prove ETag propagation, `If-Match`, `412` recovery, conflict messaging, refresh, and prevention of silent overwrite.
13. Security-sensitive mutation tests prove that the UI remains pending until server confirmation and never exposes optimistic success.
14. CSRF integration and E2E tests cover missing, mismatched, stale, and valid cookie/header pairs, Origin validation, and logout protection.
15. Member-directory integration tests cover pagination, filtering, sorting, empty state, detail, enable, disable, unauthorized action, stale version, server failure, and cache refresh.
16. Member-directory Playwright tests form the first full tracer and verify explicit enterprise routes, Capability-controlled actions, server authorization, idempotency, conditional writes, and audit correlation.
17. Platform enterprise-review tests prove that the workflow has no Enterprise Context, supports approve/reject, enforces platform capabilities, detects stale review state, and produces audit evidence.
18. Organization tests cover tree loading, parent validation, sibling code conflicts, movement, concurrent updates, member assignments, and guarded deletion.
19. Invitation and join-application tests cover token expiry, target mismatch, max uses, idempotent acceptance, duplicate membership prevention, approve/reject, and revocation.
20. Application and OAuth configuration tests cover safe and unsafe protocol combinations, exact redirect matching, credential one-time display, grace-period rotation, revocation, and Application state transitions.
21. Agent tests distinguish Agent, owner, AgentInstance, workload identity, Agent Grant, and Credential; self-reported identity must fail.
22. Delegation tests prove scope intersection, expiry, revocation propagation, default no-redelegation, maximum depth, and monotonic permission decay.
23. IAM—connection service consumer contract tests cover authenticated service identity, catalog idempotency, revision conflict, schema change, prohibited secret fields, and preservation of IAM-owned risk and Capability settings.
24. MCP decision tests cover ALLOW, DENY, APPROVAL_REQUIRED, and ERROR for human direct calls, human-delegated Agent calls, autonomous Agent calls, and Agent redelegation.
25. Permit tests cover audience, expiry, proof-of-possession, connection revision, schema digest, argument digest, concurrent claim, replay, and revocation.
26. Completion tests cover idempotent callback replay, wrong service audience, unknown permit, success, explicit failure, EXECUTION_UNKNOWN, and reconciliation without duplicate Tool execution.
27. HumanApproval tests prove exact request binding, approver Capability, session freshness, expiry, rejection, single consumption, and invalidation after any subject, Tool, resource, or argument change.
28. Policy tests cover draft editing, immutable publication, static validation, test-case pass/fail, atomic current-version switch, pause, revoke, and stable reason codes.
29. Audit tests verify append-only semantics, tenant filtering, identity-chain reconstruction, request/trace linkage, and exclusion of password, token, secret, private key, verification code, and sensitive body content.
30. Backend integration tests use real database boundaries for uniqueness, optimistic locking, tenant filtering, outbox atomicity, member state transitions, policy publication, permit claim, and idempotent result recording.
31. HTTP E2E tests verify Summer MVC Unified response and error behavior for management endpoints and raw protocol behavior for OAuth2/OIDC endpoints.
32. CI evidence records the exact command, target suite, test count, failures, errors, and skipped count; build success, static checks, unit tests, integration tests, E2E, and runtime health are reported separately.
33. The untouched upstream baseline is captured before product implementation and is not retroactively reported as an AICT product test pass.
34. No global coverage percentage substitutes for explicit authentication, tenant isolation, authorization, credential, concurrency, and MCP identity-chain scenarios.
35. Prior art includes the repository’s existing shared-package Vitest suite, TypeScript checks, playground Playwright coverage, Summer HTTP E2E style, database integration tests, and architecture-boundary tests; new product tests extend these seams instead of inventing unrelated harnesses.

## Out of Scope

- Removing, rewriting, or rebranding existing upstream Vben applications, Demo content, backend mock, documentation, or playground assets.
- Moving product-specific IAM behavior into Vben Platform packages.
- Extracting vertical Modules into Business Packages before a second real Product Application requires reuse.
- Storing OAuth access tokens, refresh tokens, client secrets, Agent secrets, private keys, Session Snapshot, or Capability Set in browser persistent storage.
- Using server-provided Vue component paths, menu trees, role names, or OAuth scopes as the Product Application’s navigation or UI authorization model.
- Treating hidden UI controls as sufficient security enforcement.
- Placing enterprise, member, organization, invitation, Application, Agent, policy, MCP, or audit business models inside the Summer Security framework capability.
- Making IAM the MCP Tool data plane or the owner of connection endpoints, reusable tokens, physical sessions, health checks, Tool results, retries, and transport credentials.
- Automatically retrying non-idempotent MCP Tool calls after an ambiguous execution result.
- Immediate microservice decomposition of the IAM modular monolith or premature separation of every domain Module into its own deployable service.
- Automatic tracking or merging of upstream Vben `main`; upstream adoption remains a deliberate, tested integration activity.
- Broad dependency upgrades mixed into Product Application implementation.
- Full external IdP federation, SCIM provisioning, automated joiner/mover/leaver integration, long-term audit analytics, and scale optimization in the initial tracer slices.
- A native mobile client or consumer self-service portal; this specification covers the administration Product Application and its server boundaries.
- Pixel-perfect confirmation of prototype navigation that could not be inspected through node-level Figma metadata; unresolved visual details require later product-owner confirmation without weakening the technical contracts.
- A repository-wide coverage percentage target.

## Further Notes

- The testing seams were already confirmed during architecture design: unit rules, Vue Module integration at the BFF HTTP boundary, and real same-origin BFF/Summer Playwright E2E.
- The current Vben baseline is version `5.7.0`. The repository declares Node `24.16.0` and `pnpm@11.16.0`, while the currently observed local Node runtime is `v26.5.0`; the runtime must be corrected before baseline evidence is recorded.
- The isolated `aict-identity` Product Application has not yet been created, dependencies have not been installed in this workstream, and no upstream baseline has yet been claimed as passing.
- Existing upstream CI jobs are guarded to run only for the original upstream repository. Product CI must remove or replace that condition for the actual AICT repository while preserving least-privilege workflow permissions.
- The IAM technical design remains the detailed source for data fields, relationships, endpoint inventory, MCP sequence, security baseline, performance targets, and domain implementation phases. This specification is the execution-facing product contract that connects those backend details to the Product Application.
- Initial performance targets include management list P95 below 500 ms, cached authorization P95 below 20 ms, MCP identity/delegation decision P95 below 30 ms, IAM decision plus permit issuance P95 below 50 ms, and permit claim P95 below 20 ms, excluding external Tool execution.
- The first demonstrable release is not “all pages scaffolded”; it is the member-directory tracer running through the real Product Application, BFF, IAM authorization, enterprise isolation, conditional mutation, cache invalidation, audit correlation, and CI gates.
- The second demonstrable release adds Platform Operations enterprise review and proves that platform governance remains isolated from Enterprise Context.
- Subsequent releases extend organization, invitation, Application/OAuth, Authorization Policy, Agent/MCP, and Audit Ledger while preserving the same security and testing contracts.
- Every phase that changes backend behavior also delivers database migration, Interface contract, integration tests, HTTP E2E, permission-matrix tests, audit verification, and credential non-disclosure checks.
