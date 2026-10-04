package com.abluepoint.summer.sample.organization;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.abluepoint.summer.sample.enterprise.EnterpriseRegistry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
class OrganizationDirectoryService {
    private static final String ENTERPRISE = "ent-acme";
    private static final String ORGANIZATION_READ = "enterprise.organization.read";
    private static final String ORGANIZATIONS_READ = "enterprise.organizations.read";
    private static final String ORGANIZATION_WRITE = "enterprise.organization.write";
    private static final String ORGANIZATIONS_WRITE = "enterprise.organizations.write";
    private static final int MAX_DEPTH = 8;

    private final Map<String, UnitRecord> units = new LinkedHashMap<>();
    private final Map<String, Assignment> assignments = new LinkedHashMap<>();
    private final Map<String, MemberRecord> members = Map.of(
            "member-alice", new MemberRecord("Alice Zhang", "alice@example.test", "ACTIVE"),
            "member-bob", new MemberRecord("Bob Li", "bob@example.test", "DISABLED")
    );
    private final Map<String, MutationReceipt> idempotency = new HashMap<>();
    private final Map<String, List<OrganizationAuditEvent>> audit = new HashMap<>();
    private final Map<String, CurrentOrganizationContext> contexts = new HashMap<>();
    private final EnterpriseRegistry enterprises;
    private final OrganizationUnitRepository unitRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationContextRepository contextRepository;

    OrganizationDirectoryService(EnterpriseRegistry enterprises, OrganizationUnitRepository unitRepository, OrganizationMembershipRepository membershipRepository, OrganizationContextRepository contextRepository) {
        this.enterprises = enterprises;
        this.unitRepository = unitRepository;
        this.membershipRepository = membershipRepository;
        this.contextRepository = contextRepository;
        units.put("org-acme", new UnitRecord(ENTERPRISE, "org-acme", null, "COMPANY", "Acme Corporation", "ACME", "member-alice", "Head office", 0, "ACTIVE"));
        units.put("org-engineering", new UnitRecord(ENTERPRISE, "org-engineering", "org-acme", "DEPARTMENT", "Engineering", "ENG", "member-alice", "Product engineering", 10, "ACTIVE"));
        units.put("org-operations", new UnitRecord(ENTERPRISE, "org-operations", "org-acme", "DEPARTMENT", "Operations", "OPS", null, "Business operations", 20, "ACTIVE"));
        persistSeed("org-acme", null, "COMPANY", "Acme Corporation", "ACME", "member-alice", "Head office", 0, "ACTIVE");
        persistSeed("org-engineering", "org-acme", "DEPARTMENT", "Engineering", "ENG", "member-alice", "Product engineering", 10, "ACTIVE");
        persistSeed("org-operations", "org-acme", "DEPARTMENT", "Operations", "OPS", null, "Business operations", 20, "ACTIVE");
        unitRepository.findAll().forEach(value -> units.put(value.unitId(), new UnitRecord(value.enterpriseId(), value.unitId(), value.parentId(), value.unitType(), value.name(), value.code(), value.leaderMemberId(), value.description(), value.sortOrder(), value.status())));
        assignments.put(assignmentKey("member-alice", "org-engineering"), new Assignment(ENTERPRISE, "org-engineering", "member-alice", true));
        membershipRepository.findAll().forEach(value -> assignments.put(assignmentKey(value.memberId(), value.organizationId()), new Assignment(value.enterpriseId(), value.organizationId(), value.memberId(), value.primaryOrganization())));
    }

    synchronized OrganizationTree tree(String enterpriseId, Authentication authentication) {
        requireEnterpriseRead(enterpriseId, authentication);
        List<OrganizationUnit> roots = unitsFor(enterpriseId).stream().filter(unit -> unit.parentId() == null).sorted(unitComparator())
                .map(unit -> treeNode(enterpriseId, unit)).toList();
        return new OrganizationTree(enterpriseId, enterpriseRevision(enterpriseId), roots);
    }

    synchronized OrganizationTree list(String enterpriseId, String parentId, String query, Authentication authentication) {
        requireEnterpriseRead(enterpriseId, authentication);
        List<OrganizationUnit> result = unitsFor(enterpriseId).stream()
                .filter(unit -> parentId == null || parentId.equals(unit.parentId()))
                .filter(unit -> query == null || query.isBlank() || unit.name().toLowerCase().contains(query.toLowerCase())
                        || (unit.code() != null && unit.code().toLowerCase().contains(query.toLowerCase())))
                .sorted(unitComparator()).map(unit -> flatNode(enterpriseId, unit)).toList();
        return new OrganizationTree(enterpriseId, revision(enterpriseId, "list", parentId + "|" + query + "|" + result), result);
    }

    synchronized OrganizationUnit detail(String enterpriseId, String unitId, Authentication authentication) {
        requireEnterpriseRead(enterpriseId, authentication);
        UnitRecord unit = unitFor(enterpriseId, unitId);
        return unit.toPublic(enterpriseId, memberCount(enterpriseId, unitId), List.of());
    }

    synchronized String unitEtag(String enterpriseId, String unitId) {
        return quote(unitRevision(unitFor(enterpriseId, unitId)));
    }

    synchronized String unitRevisionValue(String enterpriseId, String unitId) {
        return unitRevision(unitFor(enterpriseId, unitId));
    }

    synchronized OrganizationMemberList members(String enterpriseId, String organizationId, Authentication authentication) {
        requireEnterpriseRead(enterpriseId, authentication);
        unitFor(enterpriseId, organizationId);
        List<OrganizationMember> result = assignments.values().stream()
                .filter(assignment -> enterpriseId.equals(assignment.enterpriseId()) && organizationId.equals(assignment.organizationId()))
                .sorted(Comparator.comparing(Assignment::memberId)).map(assignment -> {
                    MemberRecord member = members.get(assignment.memberId());
                    return new OrganizationMember(assignment.enterpriseId(), assignment.organizationId(), assignment.memberId(),
                            member == null ? "" : member.displayName(), member == null ? "" : member.email(),
                            member == null ? "UNKNOWN" : member.status(), assignment.primary());
                }).toList();
        return new OrganizationMemberList(enterpriseId, organizationId, revision(enterpriseId, "members", organizationId + "|" + result), result);
    }

    @Transactional
    synchronized OrganizationUnit create(String enterpriseId, OrganizationUnitRequest request, String key, String requestId, Authentication authentication) {
        requireEnterpriseWrite(enterpriseId, authentication);
        requireKey(key);
        String fingerprint = fingerprint("create", request, null);
        MutationReceipt previous = idempotency.get(operationKey(enterpriseId, key));
        if (previous != null) return replay(previous, fingerprint);
        validateRequest(request, true);
        if (!enterprises.exists(enterpriseId)) throw new OrganizationConflictException("Enterprise does not exist");
        UnitRecord parent = request.parentId() == null ? null : units.get(request.parentId());
        if (request.parentId() != null && parent == null) throw new OrganizationConflictException("Parent organization must belong to the enterprise");
        if (parent != null && "DISABLED".equals(parent.status())) throw new OrganizationConflictException("Parent organization is disabled");
        ensureSiblingCodeAvailable(enterpriseId, request.parentId(), request.code(), null);
        if (parent != null && depth(parent.id()) >= MAX_DEPTH) throw new OrganizationConflictException("Organization tree depth exceeded");
        String id = "org-" + request.code().toLowerCase().replaceAll("[^a-z0-9]+", "-");
        if (units.containsKey(id)) throw new OrganizationConflictException("Organization code already exists");
        UnitRecord created = new UnitRecord(enterpriseId, id, request.parentId(), normalizeType(request.unitType()), request.name().trim(), request.code().trim(),
                request.leaderMemberId(), request.description(), request.sortOrder() == null ? 0 : request.sortOrder(), normalizeStatus(request.status()));
        units.put(id, created);
        unitRepository.save(new OrganizationUnitEntity(id, enterpriseId, created.parentId(), created.unitType(), created.name(), created.code(), created.leaderMemberId(), created.description(), created.sortOrder(), created.status()));
        OrganizationUnit result = created.toPublic(enterpriseId, 0, List.of());
        remember(enterpriseId, key, fingerprint, result);
        record(enterpriseId, id, "ORGANIZATION_CREATED", null, created.status(), requestId, authentication);
        return result;
    }

    @Transactional
    synchronized OrganizationUnit update(String enterpriseId, String unitId, OrganizationUnitRequest request, String ifMatch, String key,
                                          String requestId, Authentication authentication) {
        requireEnterpriseWrite(enterpriseId, authentication);
        requireKey(key);
        String fingerprint = fingerprint("update", request, ifMatch);
        MutationReceipt previous = idempotency.get(operationKey(enterpriseId, key));
        if (previous != null) return replay(previous, fingerprint);
        UnitRecord current = unitFor(enterpriseId, unitId);
        requireEtag(current, ifMatch);
        validateRequest(request, false);
        String parentId = request.parentId() == null ? current.parentId() : request.parentId();
        String code = request.code() == null ? current.code() : request.code().trim();
        String name = request.name() == null ? current.name() : request.name().trim();
        String type = request.unitType() == null ? current.unitType() : normalizeType(request.unitType());
        String status = request.status() == null ? current.status() : normalizeStatus(request.status());
        if (unitId.equals(parentId) || (parentId != null && isDescendant(unitId, parentId))) throw new OrganizationConflictException("Organization parent would create a cycle");
        if (parentId != null && units.get(parentId) == null) throw new OrganizationConflictException("Parent organization must belong to the enterprise");
        if (parentId != null && "DISABLED".equals(units.get(parentId).status())) throw new OrganizationConflictException("Parent organization is disabled");
        if (parentId != null && !unitId.equals(parentId) && depth(parentId) >= MAX_DEPTH) throw new OrganizationConflictException("Organization tree depth exceeded");
        ensureSiblingCodeAvailable(enterpriseId, parentId, code, unitId);
        UnitRecord updated = new UnitRecord(enterpriseId, unitId, parentId, type, name, code,
                request.leaderMemberId() == null ? current.leaderMemberId() : request.leaderMemberId(),
                request.description() == null ? current.description() : request.description(),
                request.sortOrder() == null ? current.sortOrder() : request.sortOrder(), status);
        units.put(unitId, updated);
        unitRepository.findByUnitId(unitId).ifPresent(unitRepository::delete);
        unitRepository.save(new OrganizationUnitEntity(unitId, enterpriseId, updated.parentId(), updated.unitType(), updated.name(), updated.code(), updated.leaderMemberId(), updated.description(), updated.sortOrder(), updated.status()));
        OrganizationUnit result = updated.toPublic(enterpriseId, memberCount(enterpriseId, unitId), List.of());
        remember(enterpriseId, key, fingerprint, result);
        record(enterpriseId, unitId, "ORGANIZATION_UPDATED", current.status(), updated.status(), requestId, authentication);
        return result;
    }

    @Transactional
    synchronized void delete(String enterpriseId, String unitId, String ifMatch, String key, String requestId, Authentication authentication) {
        requireEnterpriseWrite(enterpriseId, authentication);
        requireKey(key);
        String fingerprint = fingerprint("delete", unitId, ifMatch);
        MutationReceipt previous = idempotency.get(operationKey(enterpriseId, key));
        if (previous != null) {
            replay(previous, fingerprint);
            return;
        }
        UnitRecord current = unitFor(enterpriseId, unitId);
        requireEtag(current, ifMatch);
        if (units.values().stream().anyMatch(unit -> unitId.equals(unit.parentId()))) throw new OrganizationConflictException("Organization has child units");
        if (memberCount(enterpriseId, unitId) > 0) throw new OrganizationConflictException("Organization has active members");
        units.remove(unitId);
        unitRepository.findByUnitId(unitId).ifPresent(unitRepository::delete);
        remember(enterpriseId, key, fingerprint, Boolean.TRUE);
        record(enterpriseId, unitId, "ORGANIZATION_DELETED", current.status(), "DELETED", requestId, authentication);
    }

    @Transactional
    synchronized OrganizationAssignmentBatchResult assign(String enterpriseId, OrganizationAssignmentsRequest request, String ifMatch, String key,
                                                           String requestId, Authentication authentication) {
        requireEnterpriseWrite(enterpriseId, authentication);
        requireKey(key);
        String fingerprint = fingerprint("assign", request, ifMatch);
        MutationReceipt previous = idempotency.get(operationKey(enterpriseId, key));
        if (previous != null) return replay(previous, fingerprint);
        requireEtagForEnterprise(enterpriseId, ifMatch);
        if (request.memberIds().isEmpty()) {
            throw new OrganizationValidationException("memberIds are required");
        }
        if ((!request.organizationIds().isEmpty() && request.primaryOrganizationId() == null)
                || (request.primaryOrganizationId() != null && !request.organizationIds().contains(request.primaryOrganizationId()))) {
            throw new OrganizationValidationException("primaryOrganizationId must be one of organizationIds");
        }
        for (String memberId : request.memberIds()) {
            MemberRecord member = members.get(memberId);
            if (member == null || !"ACTIVE".equals(member.status())) throw new OrganizationConflictException("Member is not active in the enterprise");
        }
        for (String organizationId : request.organizationIds()) {
            UnitRecord organization = unitFor(enterpriseId, organizationId);
            if ("DISABLED".equals(organization.status())) throw new OrganizationConflictException("Cannot assign a member to a disabled organization");
        }
        for (String memberId : request.memberIds()) {
            assignments.entrySet().removeIf(entry -> memberId.equals(entry.getValue().memberId()) && enterpriseId.equals(entry.getValue().enterpriseId()));
            persistMemberships(enterpriseId, memberId, request.organizationIds(), request.primaryOrganizationId());
            for (String organizationId : request.organizationIds()) assignments.put(assignmentKey(memberId, organizationId),
                    new Assignment(enterpriseId, organizationId, memberId, organizationId.equals(request.primaryOrganizationId())));
            record(enterpriseId, memberId, "MEMBER_ORGANIZATION_ASSIGNMENTS_UPDATED", null, request.organizationIds().toString(), requestId, authentication);
        }
        OrganizationAssignmentBatchResult result = new OrganizationAssignmentBatchResult(enterpriseId, request.memberIds(), request.organizationIds(), request.primaryOrganizationId(), enterpriseRevision(enterpriseId));
        remember(enterpriseId, key, fingerprint, result);
        return result;
    }

    synchronized List<OrganizationAuditEvent> audit(String enterpriseId, String resourceId, Authentication authentication) {
        requireEnterpriseRead(enterpriseId, authentication);
        return List.copyOf(audit.getOrDefault(enterpriseId + ":" + resourceId, List.of()));
    }

    @Transactional
    synchronized CurrentOrganizationContext selectContext(String enterpriseId, String organizationId, Authentication authentication) {
        requireEnterpriseRead(enterpriseId, authentication);
        if (organizationId != null) unitFor(enterpriseId, organizationId);
        String subject = authentication.getName();
        CurrentOrganizationContext result = new CurrentOrganizationContext(subject, enterpriseId, organizationId, enterpriseRevision(enterpriseId));
        contexts.put(subject, result);
        contextRepository.findBySubject(subject).ifPresent(contextRepository::delete);
        contextRepository.save(new OrganizationContextEntity(subject, enterpriseId, organizationId, result.revision()));
        record(enterpriseId, subject, "ORGANIZATION_CONTEXT_SELECTED", null, organizationId, null, authentication);
        return result;
    }

    private void requireEnterpriseRead(String enterpriseId, Authentication authentication) {
        requireCapability(requireJwtMember(enterpriseId, authentication), enterpriseId, ORGANIZATION_READ, ORGANIZATIONS_READ);
    }

    private void requireEnterpriseWrite(String enterpriseId, Authentication authentication) {
        requireCapability(requireJwtMember(enterpriseId, authentication), enterpriseId, ORGANIZATION_WRITE, ORGANIZATIONS_WRITE);
    }

    private JwtAuthenticationToken requireJwtMember(String enterpriseId, Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !isActiveMember(jwt, enterpriseId)) throw new AccessDeniedException("The principal is not an active member of the enterprise context");
        return jwt;
    }

    private static void requireCapability(JwtAuthenticationToken jwt, String enterpriseId, String... required) {
        Object claim = jwt.getToken().getClaims().get("enterprise_capabilities");
        if (!(claim instanceof Map<?, ?> capabilityMap) || !capabilityMap.containsKey(enterpriseId)) throw new AccessDeniedException("The principal lacks organization capability");
        Object values = capabilityMap.get(enterpriseId);
        if (!(values instanceof Collection<?> collection) || collection.stream().map(String::valueOf).noneMatch(value -> List.of(required).contains(value))) throw new AccessDeniedException("The principal lacks organization capability");
    }

    private static boolean isActiveMember(JwtAuthenticationToken jwt, String enterpriseId) {
        Object claim = jwt.getToken().getClaims().get("enterprise_memberships");
        if (!(claim instanceof Collection<?> values)) return false;
        return values.stream().filter(Map.class::isInstance).map(Map.class::cast).anyMatch(value -> enterpriseId.equals(valueOf(value, "enterpriseId", "enterprise_id"))
                && "ACTIVE".equalsIgnoreCase(valueOf(value, "status", "membership_status")));
    }

    private UnitRecord unitFor(String enterpriseId, String unitId) {
        if (!enterprises.exists(enterpriseId)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        UnitRecord unit = units.get(unitId);
        if (unit != null && !enterpriseId.equals(unit.enterpriseId())) unit = null;
        if (unit == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        return unit;
    }

    private List<UnitRecord> unitsFor(String enterpriseId) { return enterprises.exists(enterpriseId) ? units.values().stream().filter(unit -> enterpriseId.equals(unit.enterpriseId())).toList() : List.of(); }

    private OrganizationUnit treeNode(String enterpriseId, UnitRecord unit) {
        List<OrganizationUnit> children = units.values().stream().filter(child -> enterpriseId.equals(child.enterpriseId()) && unit.id().equals(child.parentId())).sorted(unitComparator()).map(child -> treeNode(enterpriseId, child)).toList();
        return unit.toPublic(enterpriseId, memberCount(enterpriseId, unit.id()), children);
    }

    private OrganizationUnit flatNode(String enterpriseId, UnitRecord unit) { return unit.toPublic(enterpriseId, memberCount(enterpriseId, unit.id()), List.of()); }

    private int memberCount(String enterpriseId, String organizationId) {
        return (int) assignments.values().stream().filter(assignment -> enterpriseId.equals(assignment.enterpriseId()) && organizationId.equals(assignment.organizationId())
                && "ACTIVE".equals(members.getOrDefault(assignment.memberId(), new MemberRecord("", "", "UNKNOWN")).status())).count();
    }

    private static void validateRequest(OrganizationUnitRequest request, boolean create) {
        if (request == null) throw new OrganizationValidationException("Request body is required");
        if (create && (request.name() == null || request.name().isBlank() || request.code() == null || request.code().isBlank())) throw new OrganizationValidationException("name and code are required");
        if (request.name() != null && request.name().isBlank()) throw new OrganizationValidationException("name must not be blank");
        if (request.code() != null && request.code().isBlank()) throw new OrganizationValidationException("code must not be blank");
    }

    private static String normalizeType(String value) {
        String normalized = value == null || value.isBlank() ? "DEPARTMENT" : value.toUpperCase();
        if (!List.of("COMPANY", "DEPARTMENT", "TEAM").contains(normalized)) throw new OrganizationValidationException("unitType is invalid");
        return normalized;
    }

    private static String normalizeStatus(String value) {
        String normalized = value == null || value.isBlank() ? "ACTIVE" : value.toUpperCase();
        if (!List.of("ACTIVE", "DISABLED").contains(normalized)) throw new OrganizationValidationException("status is invalid");
        return normalized;
    }

    private void ensureSiblingCodeAvailable(String enterpriseId, String parentId, String code, String exceptId) {
        if (code == null) return;
        if (units.values().stream().anyMatch(unit -> enterpriseId.equals(unit.enterpriseId()) && (parentId == null ? unit.parentId() == null : parentId.equals(unit.parentId()))
                && !unit.id().equals(exceptId) && code.equalsIgnoreCase(unit.code()))) throw new OrganizationConflictException("Sibling organization code already exists");
    }

    private int depth(String unitId) {
        int depth = 1;
        UnitRecord current = units.get(unitId);
        while (current != null && current.parentId() != null) { depth++; current = units.get(current.parentId()); }
        return depth;
    }

    private boolean isDescendant(String ancestorId, String candidateId) {
        UnitRecord current = units.get(candidateId);
        while (current != null && current.parentId() != null) { if (ancestorId.equals(current.parentId())) return true; current = units.get(current.parentId()); }
        return false;
    }

    private void requireEtag(UnitRecord unit, String ifMatch) { if (ifMatch == null || !ifMatch.equals(quote(unitRevision(unit)))) throw new OrganizationPreconditionException(); }
    private void requireEtagForEnterprise(String enterpriseId, String ifMatch) { if (ifMatch == null || !ifMatch.equals(quote(enterpriseRevision(enterpriseId)))) throw new OrganizationPreconditionException(); }
    private static void requireKey(String key) { if (key == null || key.isBlank()) throw new OrganizationIdempotencyConflictException("Idempotency-Key is required"); }
    private String operationKey(String enterpriseId, String key) { return enterpriseId + ":" + key; }
    private void remember(String enterpriseId, String key, String fingerprint, Object result) { idempotency.put(operationKey(enterpriseId, key), new MutationReceipt(fingerprint, result)); }

    @SuppressWarnings("unchecked")
    private static <T> T replay(MutationReceipt receipt, String fingerprint) {
        if (!receipt.fingerprint().equals(fingerprint)) throw new OrganizationIdempotencyConflictException("Idempotency-Key was already used for another mutation");
        return (T) receipt.result();
    }

    private void record(String enterpriseId, String resourceId, String action, String before, String after, String requestId, Authentication authentication) {
        audit.computeIfAbsent(enterpriseId + ":" + resourceId, ignored -> new ArrayList<>()).add(new OrganizationAuditEvent(authentication.getName(), enterpriseId, resourceId, action, before, after, requestId));
    }

    private String enterpriseRevision(String enterpriseId) { return revision(enterpriseId, "enterprise", unitsFor(enterpriseId).toString() + assignments.toString()); }
    private String unitRevision(UnitRecord unit) { return revision(ENTERPRISE, "unit", unit.toString()); }
    private static String fingerprint(String operation, Object request, String ifMatch) { return operation + "|" + request + "|" + ifMatch; }
    private static String quote(String value) { return "\"" + value + "\""; }
    private static String assignmentKey(String memberId, String organizationId) { return memberId + ":" + organizationId; }
    private static String valueOf(Map<?, ?> map, String first, String second) { Object value = map.get(first); if (value == null) value = map.get(second); return value == null ? null : value.toString(); }
    private static Comparator<UnitRecord> unitComparator() { return Comparator.comparingInt(UnitRecord::sortOrder).thenComparing(UnitRecord::id); }

    private void persistMemberships(String enterpriseId, String memberId, List<String> organizationIds, String primaryOrganizationId) {
        membershipRepository.deleteByEnterpriseIdAndMemberId(enterpriseId, memberId);
        for (String organizationId : organizationIds) {
            membershipRepository.save(new OrganizationMembershipEntity(enterpriseId, memberId, organizationId, organizationId.equals(primaryOrganizationId)));
        }
    }

    private void persistSeed(String id, String parentId, String type, String name, String code, String leader, String description, int sortOrder, String status) {
        if (!unitRepository.existsByUnitId(id)) unitRepository.save(new OrganizationUnitEntity(id, ENTERPRISE, parentId, type, name, code, leader, description, sortOrder, status));
    }

    private static String revision(String enterpriseId, String kind, String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((enterpriseId + "|" + kind + "|" + value).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (Exception exception) { throw new IllegalStateException("SHA-256 is required", exception); }
    }

    record OrganizationAuditEvent(String actorSubject, String enterpriseId, String resourceId, String action, String beforeStatus, String afterStatus, String requestId) {}
    record CurrentOrganizationContext(String subject, String enterpriseId, String organizationId, String revision) {}
    private record MutationReceipt(String fingerprint, Object result) {}
    private record MemberRecord(String displayName, String email, String status) {}
    private record Assignment(String enterpriseId, String organizationId, String memberId, boolean primary) {}
    private record UnitRecord(String enterpriseId, String id, String parentId, String unitType, String name, String code, String leaderMemberId,
                              String description, int sortOrder, String status) {
        OrganizationUnit toPublic(String enterpriseId, int memberCount, List<OrganizationUnit> children) {
            return new OrganizationUnit(id, enterpriseId, parentId, unitType, name, code, leaderMemberId, description, sortOrder, status, memberCount, children);
        }
    }
}
