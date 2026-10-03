package com.abluepoint.summer.sample.authorization;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
final class AuthorizationService {
    private static final String ENTERPRISE = "ent-acme";
    private final Map<String, CapabilityRecord> capabilities = new LinkedHashMap<>();
    private final Map<String, RoleRecord> roles = new LinkedHashMap<>();
    private final Map<String, BundleRecord> bundles = new LinkedHashMap<>();
    private final Map<String, Receipt> receipts = new HashMap<>();
    private final Map<String, List<Map<String, Object>>> audit = new HashMap<>();

    AuthorizationService() {
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
        capabilities.put(id, c); CapabilityView out = capabilityView(c); remember(e, key, fp, out); record(e, id, "CAPABILITY_CREATED", rid, a); return out;
    }

    synchronized CapabilityView createPlatformCapability(CapabilityCreateRequest req, String key, String rid, Authentication a) {
        if (!(a instanceof JwtAuthenticationToken jwt) || !hasPlatform(jwt, "platform.capabilities.write")
                || jwt.getAuthorities().stream().noneMatch(x -> "SCOPE_platform.capabilities.write".equals(x.getAuthority()))) throw new AccessDeniedException("Platform capability denied");
        requireKey(key); String fp = fingerprint(req); Receipt prior = receipts.get(op("platform", key)); if (prior != null) return replay(prior, fp);
        if (req == null || blank(req.code()) || blank(req.resource()) || blank(req.action())) throw new AuthorizationValidationException("code, resource and action are required");
        String id = "cap-" + UUID.randomUUID().toString().substring(0, 8); CapabilityRecord c = new CapabilityRecord(id, null, "PLATFORM", req.code().trim(), req.resource().trim(), req.action().trim(), req.riskLevel() == null ? "LOW" : req.riskLevel().toUpperCase(Locale.ROOT), "ACTIVE", 1);
        if (capabilities.values().stream().anyMatch(x -> x.code.equals(c.code))) throw new AuthorizationConflictException("Capability code already exists"); capabilities.put(id, c); CapabilityView out = capabilityView(c); remember("platform", key, fp, out); record("platform", id, "CAPABILITY_CREATED", rid, a); return out;
    }

    synchronized CapabilityView setCapabilityStatus(String e, String id, String status, String ifm, String key, String rid, Authentication a) {
        require(e, a, "enterprise.capabilities.write"); requireKey(key); String fp = fingerprint(id, status, ifm); Receipt prior = receipts.get(op(e, key)); if (prior != null) return replay(prior, fp);
        CapabilityRecord c = capability(e, id); etag(c.version, ifm); if (status == null || !Set.of("ACTIVE", "DISABLED").contains(status)) throw new AuthorizationValidationException("Invalid capability status"); c.status = status; c.version++;
        CapabilityView out = capabilityView(c); remember(e, key, fp, out); record(e, id, "CAPABILITY_" + status, rid, a); return out;
    }

    synchronized List<RoleView> roles(String e, Authentication a) { require(e, a, "enterprise.roles.read"); return roles.values().stream().filter(x -> e.equals(x.enterpriseId)).map(this::roleView).toList(); }
    synchronized RoleView role(String e, String id, Authentication a) { require(e, a, "enterprise.roles.read"); return roleView(role(e, id)); }
    synchronized RoleView createRole(String e, RoleCreateRequest req, String key, String rid, Authentication a) {
        require(e, a, "enterprise.roles.write"); requireKey(key); String fp = fingerprint(req); Receipt prior = receipts.get(op(e, key)); if (prior != null) return replay(prior, fp);
        validateRole(req == null ? null : req.name(), req == null ? null : req.code()); if (req == null) throw new AuthorizationValidationException("Role is required"); List<String> ids = resolveCapabilities(e, req.capabilityIds(), req.bundleId());
        if (roles.values().stream().anyMatch(x -> e.equals(x.enterpriseId) && x.code.equals(req.code()))) throw new AuthorizationConflictException("Role code already exists");
        RoleRecord r = new RoleRecord("role-" + UUID.randomUUID().toString().substring(0, 8), e, req.name().trim(), req.code().trim(), false, "ACTIVE", ids, 1, req.bundleId()); roles.put(r.id, r); RoleView out = roleView(r); remember(e, key, fp, out); record(e, r.id, "ROLE_CREATED", rid, a); return out;
    }

    synchronized RoleView updateRole(String e, String id, RoleUpdateRequest req, String ifm, String key, String rid, Authentication a) {
        require(e, a, "enterprise.roles.write"); requireKey(key); String fp = fingerprint(req, ifm); Receipt prior = receipts.get(op(e, key)); if (prior != null) return replay(prior, fp); RoleRecord r = role(e, id); etag(r.version, ifm);
        if (req == null) throw new AuthorizationValidationException("Role update is required");
        if (req != null && req.name() != null) { if (blank(req.name())) throw new AuthorizationValidationException("name is required"); r.name = req.name().trim(); }
        if (req != null && req.code() != null) { if (blank(req.code())) throw new AuthorizationValidationException("code is required"); r.code = req.code().trim(); }
        if (req != null && req.status() != null) r.status = req.status(); if (req != null && (req.capabilityIds() != null || req.bundleId() != null)) r.capabilityIds = resolveCapabilities(e, req.capabilityIds(), req.bundleId()); if (req != null && req.bundleId() != null) r.bundleId = req.bundleId(); r.version++;
        RoleView out = roleView(r); remember(e, key, fp, out); record(e, id, "ROLE_UPDATED", rid, a); return out;
    }

    synchronized RoleView setRoleCapabilities(String e, String id, RoleCapabilitiesRequest req, String ifm, String key, String rid, Authentication a) {
        require(e, a, "enterprise.roles.write"); requireKey(key); String fp = fingerprint(req, ifm); Receipt prior = receipts.get(op(e, key)); if (prior != null) return replay(prior, fp); RoleRecord r = role(e, id); etag(r.version, ifm); if (req == null) throw new AuthorizationValidationException("Role capabilities are required"); r.capabilityIds = resolveCapabilities(e, req.capabilityIds(), null); r.bundleId = null; r.version++; RoleView out = roleView(r); remember(e, key, fp, out); record(e, id, "ROLE_CAPABILITIES_UPDATED", rid, a); return out;
    }

    synchronized List<BundleView> bundles(String e, Authentication a) { require(e, a, "enterprise.bundles.read"); return bundles.values().stream().filter(x -> e.equals(x.enterpriseId)).map(this::bundleView).toList(); }
    synchronized BundleView bundle(String e, String id, Authentication a) { require(e, a, "enterprise.bundles.read"); return bundleView(bundle(e, id)); }
    synchronized BundleView createBundle(String e, BundleCreateRequest req, String key, String rid, Authentication a) {
        require(e, a, "enterprise.bundles.write"); requireKey(key); String fp = fingerprint(req); Receipt prior = receipts.get(op(e, key)); if (prior != null) return replay(prior, fp); validateNameCode(req == null ? null : req.name(), req == null ? null : req.code()); if (req == null) throw new AuthorizationValidationException("Bundle is required"); List<String> ids = resolveCapabilities(e, req.capabilityIds(), null);
        if (bundles.values().stream().anyMatch(x -> e.equals(x.enterpriseId) && x.code.equals(req.code()))) throw new AuthorizationConflictException("Bundle code already exists"); BundleRecord b = new BundleRecord("bundle-" + UUID.randomUUID().toString().substring(0, 8), e, req.name().trim(), req.code().trim(), "ACTIVE", ids, 1, req.changeSummary() == null ? "initial" : req.changeSummary()); bundles.put(b.id, b); b.history.add(new BundleVersionView(b.id, 1, ids, b.changeSummary)); BundleView out = bundleView(b); remember(e, key, fp, out); record(e, b.id, "BUNDLE_CREATED", rid, a); return out;
    }

    synchronized BundleView updateBundle(String e, String id, BundleUpdateRequest req, String ifm, String key, String rid, Authentication a) {
        require(e, a, "enterprise.bundles.write"); requireKey(key); String fp = fingerprint(req, ifm); Receipt prior = receipts.get(op(e, key)); if (prior != null) return replay(prior, fp); BundleRecord b = bundle(e, id); etag(b.version, ifm); if (req == null) throw new AuthorizationValidationException("Bundle update is required"); if (req.name() != null) { if (blank(req.name())) throw new AuthorizationValidationException("name is required"); b.name = req.name().trim(); } if (req.code() != null) { if (blank(req.code())) throw new AuthorizationValidationException("code is required"); b.code = req.code().trim(); } if (req.status() != null) b.status = req.status(); if (req.capabilityIds() != null) b.capabilityIds = resolveCapabilities(e, req.capabilityIds(), null); b.changeSummary = req.changeSummary() == null ? "updated" : req.changeSummary(); b.version++; b.history.add(new BundleVersionView(b.id, b.version, b.capabilityIds, b.changeSummary)); BundleView out = bundleView(b); remember(e, key, fp, out); record(e, id, "BUNDLE_UPDATED", rid, a); return out;
    }

    synchronized List<BundleVersionView> bundleVersions(String e, String id, Authentication a) { require(e, a, "enterprise.bundles.read"); return List.copyOf(bundle(e, id).history); }
    synchronized List<Map<String, Object>> audit(String e, String id, Authentication a) { require(e, a, "enterprise.roles.read"); return List.copyOf(audit.getOrDefault(e + ":" + id, List.of())); }

    private List<String> resolveCapabilities(String e, List<String> ids, String bundleId) { if (bundleId != null) { BundleRecord b = bundle(e, bundleId); if (!"ACTIVE".equals(b.status)) throw new AuthorizationValidationException("Bundle is disabled"); return List.copyOf(b.capabilityIds); } if (ids == null) return List.of(); LinkedHashSet<String> out = new LinkedHashSet<>(); for (String id : ids) { CapabilityRecord c = capability(e, id); if (c == null) throw new AuthorizationValidationException("Unknown capability"); if (!"ACTIVE".equals(c.status)) throw new AuthorizationValidationException("Capability is disabled"); out.add(c.id); } return List.copyOf(out); }
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
    private static String op(String e, String k) { return e + ":" + k; }
    private static String fingerprint(Object... v) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Arrays.deepToString(v).getBytes(StandardCharsets.UTF_8))); } catch (Exception x) { throw new IllegalStateException(x); } }
    @SuppressWarnings("unchecked") private <T> T replay(Receipt p, String fp) { if (!p.fingerprint.equals(fp)) throw new AuthorizationIdempotencyConflictException("Idempotency key reused"); return (T) p.result; }
    private void remember(String e, String k, String fp, Object result) { receipts.put(op(e, k), new Receipt(fp, result)); }
    private void record(String e, String id, String action, String rid, Authentication a) { audit.computeIfAbsent(e + ":" + id, x -> new ArrayList<>()).add(Map.of("action", action, "requestId", rid == null ? "" : rid, "principal", a == null ? "" : a.getName(), "at", Instant.now().toString())); }
    private void seed(String id, String code, String resource, String action, String risk) { seed(id, code, resource, action, risk, "ENTERPRISE"); }
    private void seed(String id, String code, String resource, String action, String risk, String scope) { capabilities.put(id, new CapabilityRecord(id, null, scope, code, resource, action, risk, "ACTIVE", 1)); }
    private CapabilityView capabilityView(CapabilityRecord c) { return new CapabilityView(c.id, c.enterpriseId, c.scope, c.code, c.resource, c.action, c.risk, c.status, c.version); }
    private RoleView roleView(RoleRecord r) { List<String> ids = r.capabilityIds; if (r.bundleId != null) { BundleRecord b = bundles.get(r.bundleId); if (b != null && r.enterpriseId.equals(b.enterpriseId) && "ACTIVE".equals(b.status)) ids = b.capabilityIds; } return new RoleView(r.id, r.enterpriseId, r.name, r.code, r.builtIn, r.status, List.copyOf(ids), r.version, r.bundleId); }
    private BundleView bundleView(BundleRecord b) { return new BundleView(b.id, b.enterpriseId, b.name, b.code, b.status, List.copyOf(b.capabilityIds), b.version, b.changeSummary); }
    private static long revision(List<?> x) { return x.hashCode() & 0x7fffffffL; }
    private static final class CapabilityRecord { final String id, enterpriseId, scope, code, resource, action, risk; String status; long version; CapabilityRecord(String i, String e, String s, String c, String r, String a, String k, String st, long v) { id=i; enterpriseId=e; scope=s; code=c; resource=r; action=a; risk=k; status=st; version=v; } }
    private static final class RoleRecord { final String id, enterpriseId; String name, code, status, bundleId; final boolean builtIn; List<String> capabilityIds; long version; RoleRecord(String i,String e,String n,String c,boolean b,String s,List<String> ids,long v,String bid){id=i;enterpriseId=e;name=n;code=c;builtIn=b;status=s;capabilityIds=ids;version=v;bundleId=bid;} }
    private static final class BundleRecord { final String id, enterpriseId; String name, code, status, changeSummary; List<String> capabilityIds; long version; final List<BundleVersionView> history = new ArrayList<>(); BundleRecord(String i,String e,String n,String c,String s,List<String> ids,long v,String summary){id=i;enterpriseId=e;name=n;code=c;status=s;capabilityIds=ids;version=v;changeSummary=summary;} }
    private record Receipt(String fingerprint, Object result) {}
}
