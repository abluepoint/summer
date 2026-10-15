package com.abluepoint.summer.sample.authorization;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.DependsOn;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.PostConstruct;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
@DependsOn("flywayMigrationInitializer")
public class AuthorizationService {
    private static final String ENTERPRISE = "ent-acme";
    private static final Set<String> BOOTSTRAP_PERMISSIONS = Set.of(
            "platform.interface-resources.read", "platform.menu.write",
            "enterprise.roles.read", "enterprise.roles.write", "enterprise.menu.write",
            "enterprise.capabilities.read", "enterprise.capabilities.write",
            "platform.capabilities.read", "platform.capabilities.write",
            "GET:/api/v1/enterprises/{enterpriseId}/capabilities/catalog",
            "GET:/api/v1/platform/capabilities/catalog",
            "POST:/api/v1/enterprises/{enterpriseId}/capabilities",
            "POST:/api/v1/platform/capabilities",
            "GET:/api/v1/enterprises/{enterpriseId}/roles",
            "GET:/api/v1/enterprises/{enterpriseId}/roles/{id}",
            "POST:/api/v1/enterprises/{enterpriseId}/roles",
            "PATCH:/api/v1/enterprises/{enterpriseId}/roles/{id}",
            "GET:/api/v1/enterprises/{enterpriseId}/roles/{id}/capabilities",
            "PUT:/api/v1/enterprises/{enterpriseId}/roles/{id}/capabilities",
            "GET:/api/v1/enterprises/{enterpriseId}/users/{username}/roles",
            "PUT:/api/v1/enterprises/{enterpriseId}/users/{username}/roles");
    @org.springframework.beans.factory.annotation.Value("${summer.sample.authorization.legacy-scope-compatibility:false}")
    private boolean legacyScopeCompatibility;
    private final Map<String, CapabilityRecord> capabilities = new LinkedHashMap<>();
    private final Map<String, RoleRecord> roles = new LinkedHashMap<>();
    private final Map<String, BundleRecord> bundles = new LinkedHashMap<>();
    private final Map<String, UserAssignmentRecord> userAssignments = new LinkedHashMap<>();
    private final Map<String, Receipt> receipts = new HashMap<>();
    private final AuthorizationAuditStore auditStore;
    private final AuthorizationUserBindingRepository bindingRepository;
    private final AuthorizationCapabilityRepository capabilityRepository;
    private final AuthorizationRoleRepository roleRepository;
    private final AuthorizationChangeEventService changeEvents;

    public AuthorizationService(AuthorizationUserBindingRepository bindingRepository, AuthorizationCapabilityRepository capabilityRepository, AuthorizationRoleRepository roleRepository, AuthorizationChangeEventService changeEvents, AuthorizationAuditStore auditStore) {
        this.bindingRepository = bindingRepository;
        this.capabilityRepository = capabilityRepository;
        this.roleRepository = roleRepository;
        this.changeEvents = changeEvents;
        this.auditStore = auditStore;
        seed("cap-platform-enterprise-review-read", "platform.enterprise-review.read", "enterprise-review", "read", "MEDIUM", "PLATFORM");
        seed("cap-platform-enterprise-review-approve", "platform.enterprise-review.approve", "enterprise-review", "approve", "HIGH", "PLATFORM");
        seed("cap-enterprise-members-read", "enterprise.members.read", "members", "read", "LOW");
        seed("cap-enterprise-members-write", "enterprise.members.write", "members", "write", "MEDIUM");
        seed("cap-enterprise-roles-read", "enterprise.roles.read", "roles", "read", "LOW");
        seed("cap-enterprise-roles-write", "enterprise.roles.write", "roles", "write", "HIGH");
        seed("cap-enterprise-capabilities-read", "enterprise.capabilities.read", "capabilities", "read", "LOW");
        seed("cap-enterprise-capabilities-write", "enterprise.capabilities.write", "capabilities", "write", "HIGH");
        seed("cap-enterprise-bundles-read", "enterprise.bundles.read", "capability-bundles", "read", "LOW");
        seed("cap-enterprise-bundles-write", "enterprise.bundles.write", "capability-bundles", "write", "HIGH");
        seed("cap-enterprise-menu-read", "enterprise.menu.read", "menu-manifest", "read", "LOW");
        seed("cap-enterprise-menu-write", "enterprise.menu.write", "menu-manifest", "write", "HIGH");
        seed("cap-platform-menu-write", "platform.menu.write", "menu-manifest", "write", "HIGH", "PLATFORM");
        seed("cap-platform-interface-resources-read", "platform.interface-resources.read", "interface-resources", "read", "HIGH", "PLATFORM");
        seed("cap-platform-capabilities-read", "platform.capabilities.read", "capabilities", "read", "HIGH", "PLATFORM");
        seed("cap-platform-capabilities-write", "platform.capabilities.write", "capabilities", "write", "HIGH", "PLATFORM");
        seed("cap-enterprise-tasks-read", "enterprise.tasks.read", "tasks", "read", "LOW");
        seed("cap-enterprise-tasks-write", "enterprise.tasks.write", "tasks", "write", "HIGH");
    }

    @PostConstruct
    void loadUserAssignments() {
        capabilityRepository.findAllByOrderByCapabilityIdAsc().forEach(entity -> {
            // Upgraded installations may already own this code under a different ID.
            // Persistence wins over defaults, including disabled state and version.
            capabilities.values().removeIf(capability -> entity.code().equals(capability.code)
                    && !entity.capabilityId().equals(capability.id));
            capabilities.put(entity.capabilityId(), new CapabilityRecord(entity.capabilityId(), entity.enterpriseId(), entity.scope(), entity.code(), entity.resource(), entity.action(), entity.riskLevel(), entity.status(), entity.version()));
        });
        roleRepository.findAllByOrderByEnterpriseIdAscCodeAsc().forEach(entity -> roles.put(entity.roleId(), new RoleRecord(entity.roleId(), entity.enterpriseId(), entity.name(), entity.code(), entity.builtIn(), entity.status(), split(entity.capabilityIds()), entity.version(), entity.bundleId())));
        bindingRepository.findAllByOrderByEnterpriseIdAscUsernameAsc().forEach(entity ->
                userAssignments.put(entity.enterpriseId() + ":" + entity.username(),
                        new UserAssignmentRecord(entity.username(), entity.enterpriseId(), bindingList(entity.roleIds()),
                                bindingList(entity.deniedCapabilityCodes()), entity.version())));
    }

    /** Reloads node-local authorization projections after a change event. */
    public synchronized void reloadPersistentState() {
        roles.clear();
        userAssignments.clear();
        loadUserAssignments();
    }

    synchronized CapabilityCatalogView capabilities(String enterpriseId, Authentication auth) {
        require(enterpriseId, auth, "enterprise.capabilities.read");
        List<CapabilityView> values = capabilities.values().stream()
                .filter(c -> "ENTERPRISE".equals(c.scope) && (c.enterpriseId == null || enterpriseId.equals(c.enterpriseId)))
                .map(this::capabilityView).toList();
        return new CapabilityCatalogView("enterprise", enterpriseId, values, revision(values));
    }

    synchronized CapabilityCatalogView platformCapabilities(Authentication auth) {
        if (!(auth instanceof JwtAuthenticationToken jwt) || !hasPlatform(jwt, "platform.capabilities.read")
                || jwt.getAuthorities().stream().noneMatch(x -> "SCOPE_platform.capabilities.read".equals(x.getAuthority()))) {
            throw new AccessDeniedException("Platform capability denied");
        }
        List<CapabilityView> values = capabilities.values().stream()
                .filter(c -> "PLATFORM".equals(c.scope)).map(this::capabilityView).toList();
        return new CapabilityCatalogView("platform", null, values, revision(values));
    }

    @Transactional
    synchronized CapabilityView createCapability(String e, CapabilityCreateRequest req, String key, String rid, Authentication a) {
        require(e, a, "enterprise.capabilities.write"); requireKey(key);
        String fp = fingerprint(req); Receipt prior = receipts.get(op(e, key)); if (prior != null) return replay(prior, fp);
        if (req == null || blank(req.code()) || blank(req.resource()) || blank(req.action())) throw new AuthorizationValidationException("code, resource and action are required");
        String scope = req.scope() == null ? "ENTERPRISE" : req.scope().toUpperCase(Locale.ROOT);
        if (!Set.of("ENTERPRISE", "PLATFORM").contains(scope)) throw new AuthorizationValidationException("Invalid capability scope");
        if ("PLATFORM".equals(scope) && !isPlatform(a)) throw new AccessDeniedException("Platform capability requires platform principal");
        String id = "cap-" + UUID.randomUUID().toString().substring(0, 8);
        CapabilityRecord c = new CapabilityRecord(id, "PLATFORM".equals(scope) ? null : e, scope, req.code().trim(), req.resource().trim(), req.action().trim(), req.riskLevel() == null ? "LOW" : req.riskLevel().toUpperCase(Locale.ROOT), "ACTIVE", 1);
        if (capabilities.values().stream().anyMatch(x -> x.code.equals(c.code))) throw new AuthorizationConflictException("Capability code already exists");
        capabilities.put(id, c); capabilityRepository.save(new AuthorizationCapabilityEntity(c.id, c.enterpriseId, c.scope, c.code, c.resource, c.action, c.risk, c.status, c.version)); CapabilityView out = capabilityView(c); remember(e, key, fp, out); record(e, id, "CAPABILITY_CREATED", rid, a); changeEvents.enqueue(e, "CAPABILITY", id, "CAPABILITY_CREATED", c.version, rid); return out;
    }

    @Transactional
    synchronized CapabilityView createPlatformCapability(CapabilityCreateRequest req, String key, String rid, Authentication a) {
        if (!(a instanceof JwtAuthenticationToken jwt) || !hasPlatform(jwt, "platform.capabilities.write")
                || jwt.getAuthorities().stream().noneMatch(x -> "SCOPE_platform.capabilities.write".equals(x.getAuthority()))) throw new AccessDeniedException("Platform capability denied");
        requireKey(key); String fp = fingerprint(req); Receipt prior = receipts.get(op("platform", key)); if (prior != null) return replay(prior, fp);
        if (req == null || blank(req.code()) || blank(req.resource()) || blank(req.action())) throw new AuthorizationValidationException("code, resource and action are required");
        String id = "cap-" + UUID.randomUUID().toString().substring(0, 8); CapabilityRecord c = new CapabilityRecord(id, null, "PLATFORM", req.code().trim(), req.resource().trim(), req.action().trim(), req.riskLevel() == null ? "LOW" : req.riskLevel().toUpperCase(Locale.ROOT), "ACTIVE", 1);
        if (capabilities.values().stream().anyMatch(x -> x.code.equals(c.code))) throw new AuthorizationConflictException("Capability code already exists"); capabilities.put(id, c); capabilityRepository.save(new AuthorizationCapabilityEntity(c.id, c.enterpriseId, c.scope, c.code, c.resource, c.action, c.risk, c.status, c.version)); CapabilityView out = capabilityView(c); remember("platform", key, fp, out); record("platform", id, "CAPABILITY_CREATED", rid, a); changeEvents.enqueue("platform", "CAPABILITY", id, "CAPABILITY_CREATED", c.version, rid); return out;
    }

    @Transactional
    synchronized CapabilityView setCapabilityStatus(String e, String id, String status, String ifm, String key, String rid, Authentication a) {
        require(e, a, "enterprise.capabilities.write"); requireKey(key); String fp = fingerprint(id, status, ifm); Receipt prior = receipts.get(op(e, key)); if (prior != null) return replay(prior, fp);
        CapabilityRecord c = capability(e, id); etag(c.version, ifm); if (status == null || !Set.of("ACTIVE", "DISABLED").contains(status)) throw new AuthorizationValidationException("Invalid capability status"); c.status = status; c.version++;
        capabilityRepository.findById(c.id).ifPresent(entity -> { entity.updateStatus(status, c.version); capabilityRepository.save(entity); }); CapabilityView out = capabilityView(c); remember(e, key, fp, out); record(e, id, "CAPABILITY_" + status, rid, a); changeEvents.enqueue(e, "CAPABILITY", id, "CAPABILITY_" + status, c.version, rid); return out;
    }

    synchronized List<RoleView> roles(String e, Authentication a) { require(e, a, "enterprise.roles.read"); return roles.values().stream().filter(x -> e.equals(x.enterpriseId)).map(this::roleView).toList(); }
    synchronized RoleView role(String e, String id, Authentication a) { require(e, a, "enterprise.roles.read"); return roleView(role(e, id)); }
    @Transactional
    synchronized RoleView createRole(String e, RoleCreateRequest req, String key, String rid, Authentication a) {
        require(e, a, "enterprise.roles.write"); requireKey(key); String fp = fingerprint(req); Receipt prior = receipts.get(op(e, key)); if (prior != null) return replay(prior, fp);
        validateRole(req == null ? null : req.name(), req == null ? null : req.code()); if (req == null) throw new AuthorizationValidationException("Role is required"); List<String> ids = resolveCapabilities(e, req.capabilityIds(), req.bundleId());
        if (roles.values().stream().anyMatch(x -> e.equals(x.enterpriseId) && x.code.equals(req.code()))) throw new AuthorizationConflictException("Role code already exists");
        RoleRecord r = new RoleRecord("role-" + UUID.randomUUID().toString().substring(0, 8), e, req.name().trim(), req.code().trim(), false, "ACTIVE", ids, 1, req.bundleId()); roles.put(r.id, r); persistRole(r); RoleView out = roleView(r); remember(e, key, fp, out); record(e, r.id, "ROLE_CREATED", rid, a); changeEvents.enqueue(e, "ROLE", r.id, "ROLE_CREATED", r.version, rid); return out;
    }

    @Transactional
    synchronized RoleView updateRole(String e, String id, RoleUpdateRequest req, String ifm, String key, String rid, Authentication a) {
        require(e, a, "enterprise.roles.write"); requireKey(key); String fp = fingerprint(req, ifm); Receipt prior = receipts.get(op(e, key)); if (prior != null) return replay(prior, fp); RoleRecord r = role(e, id); etag(r.version, ifm);
        if (req == null) throw new AuthorizationValidationException("Role update is required");
        if (req != null && req.name() != null) { if (blank(req.name())) throw new AuthorizationValidationException("name is required"); r.name = req.name().trim(); }
        if (req != null && req.code() != null) { if (blank(req.code())) throw new AuthorizationValidationException("code is required"); r.code = req.code().trim(); }
        if (req != null && req.status() != null) r.status = req.status(); if (req != null && (req.capabilityIds() != null || req.bundleId() != null)) r.capabilityIds = resolveCapabilities(e, req.capabilityIds(), req.bundleId()); if (req != null && req.bundleId() != null) r.bundleId = req.bundleId(); r.version++;
        persistRole(r);
        RoleView out = roleView(r); remember(e, key, fp, out); record(e, id, "ROLE_UPDATED", rid, a); changeEvents.enqueue(e, "ROLE", id, "ROLE_UPDATED", r.version, rid); return out;
    }

    @Transactional
    synchronized RoleView setRoleCapabilities(String e, String id, RoleCapabilitiesRequest req, String ifm, String key, String rid, Authentication a) {
        require(e, a, "enterprise.roles.write"); requireKey(key); String fp = fingerprint(req, ifm); Receipt prior = receipts.get(op(e, key)); if (prior != null) return replay(prior, fp); RoleRecord r = role(e, id); etag(r.version, ifm); if (req == null) throw new AuthorizationValidationException("Role capabilities are required"); r.capabilityIds = resolveCapabilities(e, req.capabilityIds(), null); r.bundleId = null; r.version++; persistRole(r); RoleView out = roleView(r); remember(e, key, fp, out); record(e, id, "ROLE_CAPABILITIES_UPDATED", rid, a); changeEvents.enqueue(e, "ROLE", id, "ROLE_CAPABILITIES_UPDATED", r.version, rid); return out;
    }

    synchronized List<BundleView> bundles(String e, Authentication a) { require(e, a, "enterprise.bundles.read"); return bundles.values().stream().filter(x -> e.equals(x.enterpriseId)).map(this::bundleView).toList(); }
    synchronized BundleView bundle(String e, String id, Authentication a) { require(e, a, "enterprise.bundles.read"); return bundleView(bundle(e, id)); }
    @Transactional
    synchronized BundleView createBundle(String e, BundleCreateRequest req, String key, String rid, Authentication a) {
        require(e, a, "enterprise.bundles.write"); requireKey(key); String fp = fingerprint(req); Receipt prior = receipts.get(op(e, key)); if (prior != null) return replay(prior, fp); validateNameCode(req == null ? null : req.name(), req == null ? null : req.code()); if (req == null) throw new AuthorizationValidationException("Bundle is required"); List<String> ids = resolveCapabilities(e, req.capabilityIds(), null);
        if (bundles.values().stream().anyMatch(x -> e.equals(x.enterpriseId) && x.code.equals(req.code()))) throw new AuthorizationConflictException("Bundle code already exists"); BundleRecord b = new BundleRecord("bundle-" + UUID.randomUUID().toString().substring(0, 8), e, req.name().trim(), req.code().trim(), "ACTIVE", ids, 1, req.changeSummary() == null ? "initial" : req.changeSummary()); bundles.put(b.id, b); b.history.add(new BundleVersionView(b.id, 1, ids, b.changeSummary)); BundleView out = bundleView(b); remember(e, key, fp, out); record(e, b.id, "BUNDLE_CREATED", rid, a); changeEvents.enqueue(e, "BUNDLE", b.id, "BUNDLE_CREATED", b.version, rid); return out;
    }

    @Transactional
    synchronized BundleView updateBundle(String e, String id, BundleUpdateRequest req, String ifm, String key, String rid, Authentication a) {
        require(e, a, "enterprise.bundles.write"); requireKey(key); String fp = fingerprint(req, ifm); Receipt prior = receipts.get(op(e, key)); if (prior != null) return replay(prior, fp); BundleRecord b = bundle(e, id); etag(b.version, ifm); if (req == null) throw new AuthorizationValidationException("Bundle update is required"); if (req.name() != null) { if (blank(req.name())) throw new AuthorizationValidationException("name is required"); b.name = req.name().trim(); } if (req.code() != null) { if (blank(req.code())) throw new AuthorizationValidationException("code is required"); b.code = req.code().trim(); } if (req.status() != null) b.status = req.status(); if (req.capabilityIds() != null) b.capabilityIds = resolveCapabilities(e, req.capabilityIds(), null); b.changeSummary = req.changeSummary() == null ? "updated" : req.changeSummary(); b.version++; b.history.add(new BundleVersionView(b.id, b.version, b.capabilityIds, b.changeSummary)); BundleView out = bundleView(b); remember(e, key, fp, out); record(e, id, "BUNDLE_UPDATED", rid, a); changeEvents.enqueue(e, "BUNDLE", id, "BUNDLE_UPDATED", b.version, rid); return out;
    }

    synchronized List<BundleVersionView> bundleVersions(String e, String id, Authentication a) { require(e, a, "enterprise.bundles.read"); return List.copyOf(bundle(e, id).history); }
    synchronized List<Map<String, Object>> audit(String e, String id, Authentication a) {
        require(e, a, "enterprise.roles.read");
        return auditStore.read(e, id);
    }

    @Transactional
    synchronized UserRoleAssignmentView assignUserRoles(String enterpriseId, String username,
                                                         UserRoleAssignmentRequest request, String ifMatch,
                                                         String key, String requestId, Authentication authentication) {
        require(enterpriseId, authentication, "enterprise.roles.write");
        requireKey(key);
        if (username == null || username.isBlank()) throw new AuthorizationValidationException("username is required");
        String assignmentKey = enterpriseId + ":" + username;
        String fingerprint = fingerprint(username, request, ifMatch);
        Receipt previous = receipts.get("user-assignment:" + assignmentKey + ":" + key);
        if (previous != null) return replay(previous, fingerprint);
        UserAssignmentRecord current = userAssignments.get(assignmentKey);
        long version = current == null ? 1 : current.version;
        if (current != null) etag(current.version, ifMatch);
        else if (ifMatch != null && !ifMatch.equals("\"1\"")) throw new AuthorizationPreconditionException("The resource has changed");
        if (request == null) throw new AuthorizationValidationException("Role assignment is required");
        List<String> roleIds = request.roleIds() == null ? List.of() : request.roleIds().stream().map(String::trim).filter(s -> !s.isBlank()).distinct().toList();
        for (String roleId : roleIds) role(enterpriseId, roleId);
        List<String> denied = request.deniedCapabilityCodes() == null ? List.of() : request.deniedCapabilityCodes().stream().map(String::trim).filter(s -> !s.isBlank()).distinct().toList();
        UserAssignmentRecord updated = new UserAssignmentRecord(username, enterpriseId, roleIds, denied, version);
        if (current != null) updated.version = current.version + 1;
        userAssignments.put(assignmentKey, updated);
        AuthorizationUserBindingEntity entity = bindingRepository.findByEnterpriseIdAndUsername(enterpriseId, username)
                .orElseGet(() -> new AuthorizationUserBindingEntity("binding-" + Integer.toUnsignedString(assignmentKey.hashCode()), enterpriseId, username, "", "", updated.version));
        entity.update(jsonList(updated.roleIds), jsonList(updated.deniedCodes), updated.version);
        bindingRepository.save(entity);
        UserRoleAssignmentView view = userAssignmentView(updated);
        receipts.put("user-assignment:" + assignmentKey + ":" + key, new Receipt(fingerprint, view));
        record(enterpriseId, "user:" + username, "USER_ROLES_UPDATED", requestId, authentication);
        changeEvents.enqueue(enterpriseId, "USER_BINDING", username, "USER_ROLES_UPDATED", updated.version, requestId);
        return view;
    }

    synchronized UserRoleAssignmentView userAssignment(String enterpriseId, String username, Authentication authentication) {
        require(enterpriseId, authentication, "enterprise.roles.read");
        UserAssignmentRecord record = userAssignments.get(enterpriseId + ":" + username);
        if (record == null) throw new AuthorizationConflictException("User assignment not found");
        return userAssignmentView(record);
    }

    /** Evaluates persisted grants; unassigned subjects fail closed by default. */
    public synchronized Decision decision(Authentication authentication, String enterpriseId, String permissionCode, String requestId) {
        if (!(authentication instanceof JwtAuthenticationToken jwt) || enterpriseId == null || permissionCode == null) {
            return auditedDecision(authentication, enterpriseId, permissionCode, requestId, false, "DEFAULT_DENY");
        }
        reloadPersistentState();
        UserAssignmentRecord assignment = userAssignments.get(enterpriseId + ":" + authentication.getName());
        if (assignment == null) {
            boolean managed = Boolean.TRUE.equals(jwt.getToken().getClaims().get("summer_managed_grants"));
            boolean local = "local-user".equals(jwt.getToken().getClaimAsString("summer_identity_source"));
            if (local && !managed && ENTERPRISE.equals(enterpriseId) && active(jwt, enterpriseId)
                    && has(jwt, enterpriseId, "enterprise.roles.write")
                    && hasPlatform(jwt, "platform.capabilities.write")
                    && BOOTSTRAP_PERMISSIONS.contains(permissionCode)) {
                return auditedDecision(authentication, enterpriseId, permissionCode, requestId, true, "BOOTSTRAP_ADMIN");
            }
            if (legacyScopeCompatibility && !local && !managed) return auditedDecision(authentication, enterpriseId, permissionCode, requestId, true, "LEGACY_SCOPE");
            return auditedDecision(authentication, enterpriseId, permissionCode, requestId, false, "DEFAULT_DENY");
        }
        if (!active(jwt, enterpriseId)) return auditedDecision(authentication, enterpriseId, permissionCode, requestId, false, "INACTIVE_MEMBERSHIP");
        if (assignment.deniedCodes.contains(permissionCode)) {
            return auditedDecision(authentication, enterpriseId, permissionCode, requestId, false, "USER_EXPLICIT_DENY");
        }
        boolean allowed = assignment.roleIds.stream().map(roles::get).filter(Objects::nonNull)
                .filter(role -> enterpriseId.equals(role.enterpriseId) && "ACTIVE".equals(role.status))
                .flatMap(role -> role.capabilityIds.stream()).map(id -> capabilities.get(id))
                .filter(Objects::nonNull).filter(capability -> "ACTIVE".equals(capability.status)
                        && (capability.enterpriseId == null || enterpriseId.equals(capability.enterpriseId)))
                .anyMatch(capability -> permissionCode.equals(capability.code) || permissionCode.equals(capability.resource));
        String reason = allowed ? "ROLE_ALLOW" : "DEFAULT_DENY";
        return auditedDecision(authentication, enterpriseId, permissionCode, requestId, allowed, reason);
    }

    private Decision auditedDecision(Authentication authentication, String enterpriseId, String permissionCode,
                                      String requestId, boolean allowed, String reason) {
        String principal = authentication == null ? "" : authentication.getName();
        record(enterpriseId, "user:" + principal, allowed ? "AUTHORIZATION_ALLOWED" : "AUTHORIZATION_DENIED", requestId, authentication,
                Map.of("permissionCode", permissionCode == null ? "" : permissionCode,
                        "decision", allowed ? "ALLOW" : "DENY", "reasonCode", reason));
        return new Decision(allowed, reason);
    }

    public void requireEnterpriseMenu(String enterpriseId, Authentication authentication, String capability) {
        require(enterpriseId, authentication, capability);
    }

    public void requireEnterpriseMembership(String enterpriseId, Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !ENTERPRISE.equals(enterpriseId) || !active(jwt, enterpriseId)) {
            throw new AccessDeniedException("Enterprise membership required");
        }
    }

    public void requirePlatformMenu(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwt)
                || !hasPlatform(jwt, "platform.menu.write")
                || jwt.getAuthorities().stream().noneMatch(x -> "SCOPE_platform.menu.write".equals(x.getAuthority()))) {
            throw new AccessDeniedException("Platform menu capability denied");
        }
    }

    private List<String> resolveCapabilities(String enterpriseId, List<String> ids, String bundleId) {
        if (bundleId != null) {
            BundleRecord bundle = bundle(enterpriseId, bundleId);
            if (!"ACTIVE".equals(bundle.status)) throw new AuthorizationValidationException("Bundle is disabled");
            return List.copyOf(bundle.capabilityIds);
        }
        if (ids == null) return List.of();
        LinkedHashSet<String> resolved = new LinkedHashSet<>();
        for (String id : ids) {
            if (id == null || id.isBlank()) throw new AuthorizationValidationException("Capability is required");
            CapabilityRecord capability;
            try {
                capability = capability(enterpriseId, id);
            } catch (AuthorizationConflictException unknown) {
                capability = new CapabilityRecord("cap-resource-" + Integer.toUnsignedString(id.hashCode()),
                        enterpriseId, "ENTERPRISE", id.trim(), id.trim(), "invoke", "NORMAL", "ACTIVE", 1);
            }
            if (!"ACTIVE".equals(capability.status)) throw new AuthorizationValidationException("Capability is disabled");
            // Selected built-in grants must survive a fresh login, whose claims are read from the database.
            if (!capabilityRepository.existsById(capability.id)) {
                if ("ENTERPRISE".equals(capability.scope) && capability.enterpriseId == null) {
                    capability = new CapabilityRecord(capability.id, enterpriseId, capability.scope, capability.code,
                            capability.resource, capability.action, capability.risk, capability.status, capability.version);
                }
                capabilityRepository.save(new AuthorizationCapabilityEntity(capability.id, capability.enterpriseId,
                        capability.scope, capability.code, capability.resource, capability.action,
                        capability.risk, capability.status, capability.version));
            }
            capabilities.put(capability.id, capability);
            resolved.add(capability.id);
        }
        return List.copyOf(resolved);
    }
    private CapabilityRecord capability(String e, String id) { CapabilityRecord c = capabilities.get(id); if (c == null) c = capabilities.values().stream().filter(x -> x.code.equals(id)).findFirst().orElse(null); if (c == null || (c.enterpriseId != null && !e.equals(c.enterpriseId))) throw new AuthorizationConflictException("Capability not found"); return c; }
    private RoleRecord role(String e, String id) { RoleRecord r = roles.get(id); if (r == null || !e.equals(r.enterpriseId)) throw new AuthorizationConflictException("Role not found"); return r; }
    private BundleRecord bundle(String e, String id) { BundleRecord b = bundles.get(id); if (b == null || !e.equals(b.enterpriseId)) throw new AuthorizationConflictException("Bundle not found"); return b; }
    private void validateRole(String n, String c) { validateNameCode(n, c); }
    private void validateNameCode(String n, String c) { if (blank(n) || blank(c)) throw new AuthorizationValidationException("name and code are required"); }
    private boolean isPlatform(Authentication a) { return a instanceof JwtAuthenticationToken j && j.getToken().getClaims().containsKey("platform_capabilities"); }
    private boolean hasPlatform(JwtAuthenticationToken j, String capability) { Object values = j.getToken().getClaims().get("platform_capabilities"); return values instanceof Collection<?> c && c.stream().map(String::valueOf).anyMatch(capability::equals); }
    private void require(String e, Authentication a, String capability) { if (!ENTERPRISE.equals(e) || !(a instanceof JwtAuthenticationToken jwt) || !active(jwt, e) || !has(jwt, e, capability) || jwt.getAuthorities().stream().noneMatch(x -> ("SCOPE_" + capability).equals(x.getAuthority()))) throw new AccessDeniedException("Authorization capability denied"); }
    private boolean active(JwtAuthenticationToken j, String e) { Object c = j.getToken().getClaims().get("enterprise_memberships"); return c instanceof Collection<?> v && v.stream().filter(Map.class::isInstance).map(value -> (Map<?, ?>) value).anyMatch(m -> e.equals(stringValue(m, "enterpriseId", "enterprise_id")) && "ACTIVE".equalsIgnoreCase(stringValue(m, "status", "membership_status"))); }
    private static String stringValue(Map<?, ?> values, String first, String second) { Object value = values.get(first); if (value == null) value = values.get(second); return value == null ? null : value.toString(); }
    private boolean has(JwtAuthenticationToken j, String e, String cap) { Object c = j.getToken().getClaims().get("enterprise_capabilities"); if (!(c instanceof Map<?, ?> m)) return false; Object v = m.get(e); return v instanceof Collection<?> col && col.stream().map(String::valueOf).anyMatch(cap::equals); }
    private static void requireKey(String k) { if (k == null || k.isBlank()) throw new AuthorizationValidationException("Idempotency-Key is required"); }
    private static void etag(long v, String h) { if (h == null || !h.equals("\"" + v + "\"")) throw new AuthorizationPreconditionException("The resource has changed"); }
    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static String join(List<String> values) { return String.join("\n", values); }
    private static List<String> split(String value) { return value == null || value.isBlank() ? List.of() : Arrays.stream(value.split("\\n", -1)).filter(s -> !s.isBlank()).toList(); }
    private static String jsonList(List<String> values) {
        return new tools.jackson.databind.ObjectMapper().writeValueAsString(values);
    }
    private static List<String> bindingList(String value) {
        // H2 and older local projections used newline lists; MySQL requires JSON arrays.
        if (value == null || !value.stripLeading().startsWith("[")) return split(value);
        var node = new tools.jackson.databind.ObjectMapper().readTree(value);
        List<String> values = new ArrayList<>();
        for (var item : node) {
            if (!item.isString()) throw new IllegalStateException("Invalid persisted authorization binding");
            if (!item.asText().isBlank()) values.add(item.asText());
        }
        return List.copyOf(values);
    }
    private static String op(String e, String k) { return e + ":" + k; }
    private static String fingerprint(Object... v) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Arrays.deepToString(v).getBytes(StandardCharsets.UTF_8))); } catch (Exception x) { throw new IllegalStateException(x); } }
    @SuppressWarnings("unchecked") private <T> T replay(Receipt p, String fp) { if (!p.fingerprint.equals(fp)) throw new AuthorizationIdempotencyConflictException("Idempotency key reused"); return (T) p.result; }
    private void remember(String e, String k, String fp, Object result) { receipts.put(op(e, k), new Receipt(fp, result)); }
    private void record(String e, String id, String action, String rid, Authentication a) { auditStore.mutation(e, id, action, rid, a == null ? "" : a.getName()); }
    private void record(String e, String id, String action, String rid, Authentication a, Map<String, Object> details) { auditStore.decision(e, id, action, rid, a == null ? "" : a.getName(), details); }
    private void seed(String id, String code, String resource, String action, String risk) { seed(id, code, resource, action, risk, "ENTERPRISE"); }
    private void seed(String id, String code, String resource, String action, String risk, String scope) { capabilities.put(id, new CapabilityRecord(id, null, scope, code, resource, action, risk, "ACTIVE", 1)); }
    private CapabilityView capabilityView(CapabilityRecord c) { return new CapabilityView(c.id, c.enterpriseId, c.scope, c.code, c.resource, c.action, c.risk, c.status, c.version); }
    private RoleView roleView(RoleRecord r) { List<String> ids = r.capabilityIds; if (r.bundleId != null) { BundleRecord b = bundles.get(r.bundleId); if (b != null && r.enterpriseId.equals(b.enterpriseId) && "ACTIVE".equals(b.status)) ids = b.capabilityIds; } return new RoleView(r.id, r.enterpriseId, r.name, r.code, r.builtIn, r.status, List.copyOf(ids), r.version, r.bundleId); }
    private BundleView bundleView(BundleRecord b) { return new BundleView(b.id, b.enterpriseId, b.name, b.code, b.status, List.copyOf(b.capabilityIds), b.version, b.changeSummary); }
    private void persistRole(RoleRecord r) {
        AuthorizationRoleEntity entity = roleRepository.findById(r.id).orElseGet(() -> new AuthorizationRoleEntity(r.id, r.enterpriseId, r.name, r.code, r.builtIn, r.status, join(r.capabilityIds), capabilityCodes(r.capabilityIds), r.bundleId, r.version));
        entity.update(r.name, r.code, r.status, join(r.capabilityIds), capabilityCodes(r.capabilityIds), r.bundleId, r.version);
        roleRepository.save(entity);
    }
    private String capabilityCodes(List<String> ids) { return ids.stream().map(capabilities::get).filter(Objects::nonNull).map(c -> c.code).collect(java.util.stream.Collectors.joining("\n")); }

    private static long revision(List<?> x) { return x.hashCode() & 0x7fffffffL; }
    private static final class CapabilityRecord { final String id, enterpriseId, scope, code, resource, action, risk; String status; long version; CapabilityRecord(String i, String e, String s, String c, String r, String a, String k, String st, long v) { id=i; enterpriseId=e; scope=s; code=c; resource=r; action=a; risk=k; status=st; version=v; } }
    private static final class RoleRecord { final String id, enterpriseId; String name, code, status, bundleId; final boolean builtIn; List<String> capabilityIds; long version; RoleRecord(String i,String e,String n,String c,boolean b,String s,List<String> ids,long v,String bid){id=i;enterpriseId=e;name=n;code=c;builtIn=b;status=s;capabilityIds=ids;version=v;bundleId=bid;} }
    private static final class BundleRecord { final String id, enterpriseId; String name, code, status, changeSummary; List<String> capabilityIds; long version; final List<BundleVersionView> history = new ArrayList<>(); BundleRecord(String i,String e,String n,String c,String s,List<String> ids,long v,String summary){id=i;enterpriseId=e;name=n;code=c;status=s;capabilityIds=ids;version=v;changeSummary=summary;} }
    private record Receipt(String fingerprint, Object result) {}
    private UserRoleAssignmentView userAssignmentView(UserAssignmentRecord value) { return new UserRoleAssignmentView(value.username, value.enterpriseId, List.copyOf(value.roleIds), List.copyOf(value.deniedCodes), value.version); }
    public record Decision(boolean allowed, String reasonCode) {}
    private static final class UserAssignmentRecord { final String username, enterpriseId; final List<String> roleIds, deniedCodes; long version; UserAssignmentRecord(String u, String e, List<String> r, List<String> d, long v) { username=u; enterpriseId=e; roleIds=List.copyOf(r); deniedCodes=List.copyOf(d); version=v; } }
}
