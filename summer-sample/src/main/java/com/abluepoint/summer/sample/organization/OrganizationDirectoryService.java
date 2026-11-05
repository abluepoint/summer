package com.abluepoint.summer.sample.organization;

import com.abluepoint.summer.sample.authorization.domain.data.OrganizationAuthorizationChange;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.enterprise.DirectoryErrorCode;
import static com.abluepoint.summer.sample.error.SampleTransportErrorCode.PRECONDITION_FAILED;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.abluepoint.summer.sample.enterprise.EnterpriseRegistry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.Comparator;
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

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.context.ApplicationEventPublisher publisher;

    private final Map<String, UnitRecord> units = new LinkedHashMap<>();
    private final Map<String, Assignment> assignments = new LinkedHashMap<>();
    private final com.abluepoint.summer.sample.member.MemberDirectory memberDirectory;
    private final com.abluepoint.summer.sample.enterprise.DirectoryJournal journal;
    private final EnterpriseRegistry enterprises;
    private final OrganizationUnitRepository unitRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationContextRepository contextRepository;
    private final com.abluepoint.summer.sample.authorization.AuthorizationService authorization;

    OrganizationDirectoryService(EnterpriseRegistry enterprises, OrganizationUnitRepository unitRepository, OrganizationMembershipRepository membershipRepository, OrganizationContextRepository contextRepository, com.abluepoint.summer.sample.member.MemberDirectory memberDirectory, com.abluepoint.summer.sample.enterprise.DirectoryJournal journal, com.abluepoint.summer.sample.authorization.AuthorizationService authorization) {
        this.authorization = authorization;
        this.journal = journal;
        this.memberDirectory = memberDirectory;
        this.enterprises = enterprises;
        this.unitRepository = unitRepository;
        this.membershipRepository = membershipRepository;
        this.contextRepository = contextRepository;
        boolean seedAssignments = !unitRepository.existsByUnitId("org-acme");
        units.put("org-acme", new UnitRecord(ENTERPRISE, "org-acme", null, "COMPANY", "Acme Corporation", "ACME", "member-alice", "Head office", 0, "ACTIVE", 0));
        units.put("org-engineering", new UnitRecord(ENTERPRISE, "org-engineering", "org-acme", "DEPARTMENT", "Engineering", "ENG", "member-alice", "Product engineering", 10, "ACTIVE", 0));
        units.put("org-operations", new UnitRecord(ENTERPRISE, "org-operations", "org-acme", "DEPARTMENT", "Operations", "OPS", null, "Business operations", 20, "ACTIVE", 0));
        persistSeed("org-acme", null, "COMPANY", "Acme Corporation", "ACME", "member-alice", "Head office", 0, "ACTIVE");
        persistSeed("org-engineering", "org-acme", "DEPARTMENT", "Engineering", "ENG", "member-alice", "Product engineering", 10, "ACTIVE");
        persistSeed("org-operations", "org-acme", "DEPARTMENT", "Operations", "OPS", null, "Business operations", 20, "ACTIVE");
        if (seedAssignments) membershipRepository.save(new OrganizationMembershipEntity(ENTERPRISE, "member-alice", "org-engineering", true));
        refresh();
    }

    synchronized OrganizationTree tree(String enterpriseId, Authentication authentication) {
        requireEnterpriseRead(enterpriseId, authentication);
        var visible = readableUnits(enterpriseId, authentication);
        var ids = visible.stream().map(UnitRecord::id).collect(java.util.stream.Collectors.toSet());
        List<OrganizationUnit> roots = visible.stream().filter(unit -> unit.parentId() == null || !ids.contains(unit.parentId())).sorted(unitComparator())
                .map(unit -> treeNode(enterpriseId, unit, ids)).toList();
        return new OrganizationTree(enterpriseId, enterpriseRevision(enterpriseId), roots);
    }

    synchronized OrganizationTree list(String enterpriseId, String parentId, String query, Authentication authentication) {
        requireEnterpriseRead(enterpriseId, authentication);
        List<OrganizationUnit> result = readableUnits(enterpriseId, authentication).stream()
                .filter(unit -> parentId == null || parentId.equals(unit.parentId()))
                .filter(unit -> query == null || query.isBlank() || unit.name().toLowerCase().contains(query.toLowerCase())
                        || (unit.code() != null && unit.code().toLowerCase().contains(query.toLowerCase())))
                .sorted(unitComparator()).map(unit -> flatNode(enterpriseId, unit)).toList();
        return new OrganizationTree(enterpriseId, revision(enterpriseId, "list", parentId + "|" + query + "|" + result), result);
    }

    synchronized OrganizationUnit detail(String enterpriseId, String unitId, Authentication authentication) {
        requireEnterpriseRead(enterpriseId, authentication);
        UnitRecord unit = unitFor(enterpriseId, unitId);
        requireUnitRead(enterpriseId, unitId, authentication);
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
        requireUnitRead(enterpriseId, organizationId, authentication);
        List<OrganizationMember> result = assignments.values().stream()
                .filter(assignment -> enterpriseId.equals(assignment.enterpriseId()) && organizationId.equals(assignment.organizationId()))
                .sorted(Comparator.comparing(Assignment::memberId)).map(assignment -> {
                    var member = memberDirectory.find(enterpriseId, assignment.memberId()).orElse(null);
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
        var previous = journal.find(enterpriseId, "organization.mutation", key).orElse(null);
        if (previous != null) return replay(previous, fingerprint, OrganizationUnit.class);
        validateRequest(request, true);
        if (!enterprises.exists(enterpriseId)) throw SummerException.withMessage(OrganizationErrorCode.CONFLICT, "Enterprise does not exist");
        UnitRecord parent = request.parentId() == null ? null : units.get(request.parentId());
        if (request.parentId() != null && (parent == null || !enterpriseId.equals(parent.enterpriseId()))) throw SummerException.withMessage(OrganizationErrorCode.CONFLICT, "Parent organization must belong to the enterprise");
        if (parent != null && "DISABLED".equals(parent.status())) throw SummerException.withMessage(OrganizationErrorCode.CONFLICT, "Parent organization is disabled");
        ensureSiblingCodeAvailable(enterpriseId, request.parentId(), request.code(), null);
        if (parent != null && depth(parent.id()) >= MAX_DEPTH) throw SummerException.withMessage(OrganizationErrorCode.CONFLICT, "Organization tree depth exceeded");
        String id = "org-" + java.util.UUID.randomUUID();
        if (units.containsKey(id)) throw SummerException.withMessage(OrganizationErrorCode.CONFLICT, "Organization code already exists");
        UnitRecord created = new UnitRecord(enterpriseId, id, request.parentId(), normalizeType(request.unitType()), request.name().trim(), request.code().trim(),
                request.leaderMemberId(), request.description(), request.sortOrder() == null ? 0 : request.sortOrder(), normalizeStatus(request.status()), 0);
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
        String fingerprint = fingerprint("update:" + unitId, request, ifMatch);
        var previous = journal.find(enterpriseId, "organization.mutation", key).orElse(null);
        if (previous != null) return replay(previous, fingerprint, OrganizationUnit.class);
        UnitRecord current = unitFor(enterpriseId, unitId);
        requireEtag(current, ifMatch);
        validateRequest(request, false);
        String parentId = request.parentIdPresent() ? request.parentId() : current.parentId();
        String code = request.code() == null ? current.code() : request.code().trim();
        String name = request.name() == null ? current.name() : request.name().trim();
        String type = request.unitType() == null ? current.unitType() : normalizeType(request.unitType());
        String status = request.status() == null ? current.status() : normalizeStatus(request.status());
        if (unitId.equals(parentId) || (parentId != null && isDescendant(unitId, parentId))) throw SummerException.withMessage(OrganizationErrorCode.CONFLICT, "Organization parent would create a cycle");
        if (parentId != null && (units.get(parentId) == null || !enterpriseId.equals(units.get(parentId).enterpriseId()))) throw SummerException.withMessage(OrganizationErrorCode.CONFLICT, "Parent organization must belong to the enterprise");
        if (parentId != null && "DISABLED".equals(units.get(parentId).status())) throw SummerException.withMessage(OrganizationErrorCode.CONFLICT, "Parent organization is disabled");
        if ((parentId == null ? 0 : depth(parentId)) + subtreeHeight(unitId) > MAX_DEPTH) throw SummerException.withMessage(OrganizationErrorCode.CONFLICT, "Organization tree depth exceeded");
        ensureSiblingCodeAvailable(enterpriseId, parentId, code, unitId);
        UnitRecord updated = new UnitRecord(enterpriseId, unitId, parentId, type, name, code,
                request.leaderMemberId() == null ? current.leaderMemberId() : request.leaderMemberId(),
                request.description() == null ? current.description() : request.description(),
                request.sortOrder() == null ? current.sortOrder() : request.sortOrder(), status, current.version() + 1);
        var entity = unitRepository.findByUnitId(unitId).orElseThrow();
        entity.update(updated.parentId(), updated.unitType(), updated.name(), updated.code(), updated.leaderMemberId(), updated.description(), updated.sortOrder(), updated.status());
        unitRepository.saveAndFlush(entity);
        updated = updated.withVersion(entity.version());
        units.put(unitId, updated);
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
        var previous = journal.find(enterpriseId, "organization.mutation", key).orElse(null);
        if (previous != null) {
            replay(previous, fingerprint, Boolean.class);
            return;
        }
        UnitRecord current = unitFor(enterpriseId, unitId);
        requireEtag(current, ifMatch);
        if (units.values().stream().anyMatch(unit -> unitId.equals(unit.parentId()))) throw SummerException.withMessage(OrganizationErrorCode.CONFLICT, "Organization has child units");
        if (memberCount(enterpriseId, unitId) > 0) throw SummerException.withMessage(OrganizationErrorCode.CONFLICT, "Organization has active members");
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
        var previous = journal.find(enterpriseId, "organization.mutation", key).orElse(null);
        if (previous != null) return replay(previous, fingerprint, OrganizationAssignmentBatchResult.class);
        requireEtagForEnterprise(enterpriseId, ifMatch);
        if (request == null || request.memberIds() == null || request.organizationIds() == null || request.memberIds().isEmpty() || request.memberIds().stream().anyMatch(java.util.Objects::isNull) || request.organizationIds().stream().anyMatch(java.util.Objects::isNull)) {
            throw SummerException.withMessage(OrganizationErrorCode.INVALID, "memberIds are required");
        }
        if ((!request.organizationIds().isEmpty() && request.primaryOrganizationId() == null)
                || (request.primaryOrganizationId() != null && !request.organizationIds().contains(request.primaryOrganizationId()))) {
            throw SummerException.withMessage(OrganizationErrorCode.INVALID, "primaryOrganizationId must be one of organizationIds");
        }
        for (String memberId : request.memberIds().stream().distinct().toList()) {
            var member = memberDirectory.find(enterpriseId, memberId).orElse(null);
            if (member == null || !"ACTIVE".equals(member.status())) throw SummerException.withMessage(OrganizationErrorCode.CONFLICT, "Member is not active in the enterprise");
        }
        for (String organizationId : request.organizationIds().stream().distinct().toList()) {
            UnitRecord organization = unitFor(enterpriseId, organizationId);
            if ("DISABLED".equals(organization.status())) throw SummerException.withMessage(OrganizationErrorCode.CONFLICT, "Cannot assign a member to a disabled organization");
        }
        for (String memberId : request.memberIds().stream().distinct().toList()) {
            assignments.entrySet().removeIf(entry -> memberId.equals(entry.getValue().memberId()) && enterpriseId.equals(entry.getValue().enterpriseId()));
            persistMemberships(enterpriseId, memberId, request.organizationIds(), request.primaryOrganizationId());
            for (String organizationId : request.organizationIds().stream().distinct().toList()) assignments.put(assignmentKey(memberId, organizationId),
                    new Assignment(enterpriseId, organizationId, memberId, organizationId.equals(request.primaryOrganizationId())));
            record(enterpriseId, memberId, "MEMBER_ORGANIZATION_ASSIGNMENTS_UPDATED", null, request.organizationIds().toString(), requestId, authentication);
        }
        OrganizationAssignmentBatchResult result = new OrganizationAssignmentBatchResult(enterpriseId, request.memberIds(), request.organizationIds(), request.primaryOrganizationId(), enterpriseRevision(enterpriseId));
        remember(enterpriseId, key, fingerprint, result);
        return result;
    }

    synchronized List<OrganizationAuditEvent> audit(String enterpriseId, String resourceId, Authentication authentication) {
        requireEnterpriseRead(enterpriseId, authentication);
        if (managesOrganizations(enterpriseId, authentication))
            return journal.audit(enterpriseId, "organization.audit", resourceId, OrganizationAuditEvent.class);
        // Unit IDs, member IDs and subjects share the historical resource column. A subject
        // matching a unit ID must not turn an unassigned organization's audit into "own" data.
        if (units.containsKey(resourceId)) {
            requireUnitRead(enterpriseId, resourceId, authentication);
            return journal.audit(enterpriseId, "organization.audit", resourceId, OrganizationAuditEvent.class).stream()
                    .filter(event -> !"ORGANIZATION_CONTEXT_SELECTED".equals(event.action())
                            && !"MEMBER_ORGANIZATION_ASSIGNMENTS_UPDATED".equals(event.action())).toList();
        }
        var member = memberDirectory.forSubject(enterpriseId, authentication.getName());
        boolean own = authentication.getName().equals(resourceId) || member.map(m -> m.memberId().equals(resourceId)).orElse(false);
        if (!own) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        return journal.audit(enterpriseId, "organization.audit", resourceId, OrganizationAuditEvent.class).stream()
                .filter(event -> ("ORGANIZATION_CONTEXT_SELECTED".equals(event.action()) && authentication.getName().equals(event.actorSubject()))
                        || ("MEMBER_ORGANIZATION_ASSIGNMENTS_UPDATED".equals(event.action()) && member.map(m -> m.memberId().equals(resourceId)).orElse(false))).toList();
    }

    synchronized CurrentOrganizationContext currentContext(String enterpriseId, Authentication authentication) {
        requireEnterpriseRead(enterpriseId, authentication);
        String organizationId = contextRepository.findBySubject(authentication.getName())
                .filter(context -> enterpriseId.equals(context.enterpriseId)).map(context -> context.organizationId).orElse(null);
        if (organizationId != null && !selectable(enterpriseId, organizationId, authentication.getName())) organizationId = null;
        return new CurrentOrganizationContext(authentication.getName(), enterpriseId, organizationId, enterpriseRevision(enterpriseId));
    }

    @Transactional
    synchronized CurrentOrganizationContext selectContext(String enterpriseId, String organizationId, Authentication authentication) {
        requireEnterpriseRead(enterpriseId, authentication);
        publisher.publishEvent(new OrganizationAuthorizationChange(enterpriseId, enterpriseId, "", null, true));
        enterprises.lock(enterpriseId);
        refresh();
        if (organizationId != null) {
            unitFor(enterpriseId, organizationId);
            if (!selectable(enterpriseId, organizationId, authentication.getName())) throw new AccessDeniedException("An active assigned membership is required");
        }
        String subject = authentication.getName();
        CurrentOrganizationContext result = new CurrentOrganizationContext(subject, enterpriseId, organizationId, enterpriseRevision(enterpriseId));
        var context = contextRepository.findBySubject(subject).orElseGet(() -> new OrganizationContextEntity(subject, enterpriseId, organizationId, result.revision()));
        context.enterpriseId = enterpriseId; context.organizationId = organizationId; context.revision = result.revision();
        contextRepository.saveAndFlush(context);
        record(enterpriseId, subject, "ORGANIZATION_CONTEXT_SELECTED", null, organizationId, null, authentication);
        return result;
    }

    private boolean selectable(String enterpriseId, String organizationId, String subject) {
        var member = memberDirectory.forSubject(enterpriseId, subject).filter(value -> "ACTIVE".equals(value.status()));
        var unit = units.get(organizationId);
        return member.isPresent() && unit != null && enterpriseId.equals(unit.enterpriseId()) && "ACTIVE".equals(unit.status())
                && assignments.values().stream().anyMatch(a -> enterpriseId.equals(a.enterpriseId()) && organizationId.equals(a.organizationId()) && member.get().memberId().equals(a.memberId()));
    }

    private void refresh() {
        units.clear();
        unitRepository.findAll().forEach(value -> units.put(value.unitId(), new UnitRecord(value.enterpriseId(), value.unitId(), value.parentId(), value.unitType(), value.name(), value.code(), value.leaderMemberId(), value.description(), value.sortOrder(), value.status(), value.version())));
        assignments.clear();
        membershipRepository.findAll().forEach(value -> assignments.put(assignmentKey(value.memberId(), value.organizationId()), new Assignment(value.enterpriseId(), value.organizationId(), value.memberId(), value.primaryOrganization())));
    }

    private void requireEnterpriseRead(String enterpriseId, Authentication authentication) {
        requireCapability(requireJwtMember(enterpriseId, authentication), enterpriseId, ORGANIZATION_READ, ORGANIZATIONS_READ);
        refresh();
    }

    private void requireEnterpriseWrite(String enterpriseId, Authentication authentication) {
        requireCapability(requireJwtMember(enterpriseId, authentication), enterpriseId, ORGANIZATION_WRITE, ORGANIZATIONS_WRITE);
        publisher.publishEvent(new OrganizationAuthorizationChange(enterpriseId, enterpriseId, "", null, true));
        enterprises.lock(enterpriseId);
        refresh();
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

    private OrganizationUnit treeNode(String enterpriseId, UnitRecord unit, java.util.Set<String> visible) {
        List<OrganizationUnit> children = units.values().stream().filter(child -> enterpriseId.equals(child.enterpriseId()) && visible.contains(child.id()) && unit.id().equals(child.parentId())).sorted(unitComparator()).map(child -> treeNode(enterpriseId, child, visible)).toList();
        return unit.toPublic(enterpriseId, memberCount(enterpriseId, unit.id()), children);
    }

    private boolean managesOrganizations(String enterpriseId, Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwt)) return false;
        try { requireCapability(jwt, enterpriseId, ORGANIZATION_WRITE, ORGANIZATIONS_WRITE); }
        catch (AccessDeniedException denied) { return false; }
        // Check the persisted grant as well, so a stale write claim cannot broaden a reader's view.
        return authorization.decision(authentication, enterpriseId, ORGANIZATION_WRITE, null).allowed();
    }

    private List<UnitRecord> readableUnits(String enterpriseId, Authentication authentication) {
        if (managesOrganizations(enterpriseId, authentication)) return unitsFor(enterpriseId);
        return unitsFor(enterpriseId).stream().filter(unit -> selectable(enterpriseId, unit.id(), authentication.getName())).toList();
    }

    private void requireUnitRead(String enterpriseId, String unitId, Authentication authentication) {
        if (!managesOrganizations(enterpriseId, authentication) && !selectable(enterpriseId, unitId, authentication.getName()))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
    }

    private OrganizationUnit flatNode(String enterpriseId, UnitRecord unit) { return unit.toPublic(enterpriseId, memberCount(enterpriseId, unit.id()), List.of()); }

    private int memberCount(String enterpriseId, String organizationId) {
        return (int) assignments.values().stream().filter(assignment -> enterpriseId.equals(assignment.enterpriseId()) && organizationId.equals(assignment.organizationId())
                && memberDirectory.find(enterpriseId, assignment.memberId()).map(member -> "ACTIVE".equals(member.status())).orElse(false)).count();
    }

    private static void validateRequest(OrganizationUnitRequest request, boolean create) {
        if (request == null) throw SummerException.withMessage(OrganizationErrorCode.INVALID, "Request body is required");
        if (create && (request.name() == null || request.name().isBlank() || request.code() == null || request.code().isBlank())) throw SummerException.withMessage(OrganizationErrorCode.INVALID, "name and code are required");
        if (request.name() != null && request.name().isBlank()) throw SummerException.withMessage(OrganizationErrorCode.INVALID, "name must not be blank");
        if (request.code() != null && request.code().isBlank()) throw SummerException.withMessage(OrganizationErrorCode.INVALID, "code must not be blank");
    }

    private static String normalizeType(String value) {
        String normalized = value == null || value.isBlank() ? "DEPARTMENT" : value.toUpperCase();
        if (!List.of("COMPANY", "DEPARTMENT", "TEAM").contains(normalized)) throw SummerException.withMessage(OrganizationErrorCode.INVALID, "unitType is invalid");
        return normalized;
    }

    private static String normalizeStatus(String value) {
        String normalized = value == null || value.isBlank() ? "ACTIVE" : value.toUpperCase();
        if (!List.of("ACTIVE", "DISABLED").contains(normalized)) throw SummerException.withMessage(OrganizationErrorCode.INVALID, "status is invalid");
        return normalized;
    }

    private void ensureSiblingCodeAvailable(String enterpriseId, String parentId, String code, String exceptId) {
        if (code == null) return;
        if (units.values().stream().anyMatch(unit -> enterpriseId.equals(unit.enterpriseId()) && (parentId == null ? unit.parentId() == null : parentId.equals(unit.parentId()))
                && !unit.id().equals(exceptId) && code.equalsIgnoreCase(unit.code()))) throw SummerException.withMessage(OrganizationErrorCode.CONFLICT, "Sibling organization code already exists");
    }

    private int subtreeHeight(String unitId) {
        return 1 + units.values().stream().filter(unit -> unitId.equals(unit.parentId())).mapToInt(unit -> subtreeHeight(unit.id())).max().orElse(0);
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

    private void requireEtag(UnitRecord unit, String ifMatch) { if (ifMatch == null || !ifMatch.equals(quote(unitRevision(unit)))) throw SummerException.withMessage(PRECONDITION_FAILED, "The resource has changed"); }
    private void requireEtagForEnterprise(String enterpriseId, String ifMatch) { if (ifMatch == null || !ifMatch.equals(quote(enterpriseRevision(enterpriseId)))) throw SummerException.withMessage(PRECONDITION_FAILED, "The organization revision has changed"); }
    private static void requireKey(String key) { if (key == null || key.isBlank() || key.length() > 128) throw SummerException.withMessage(DirectoryErrorCode.IDEMPOTENCY_CONFLICT, "Idempotency-Key is required"); }
    private void remember(String enterpriseId, String key, String fingerprint, Object result) { journal.remember(enterpriseId, "organization.mutation", key, fingerprint, result); }

    private <T> T replay(com.abluepoint.summer.sample.enterprise.DirectoryJournal.Receipt receipt, String fingerprint, Class<T> type) {
        if (!receipt.fingerprint().equals(fingerprint)) throw SummerException.withMessage(DirectoryErrorCode.IDEMPOTENCY_CONFLICT, "Idempotency-Key was already used for another mutation");
        return journal.decode(receipt, type);
    }

    private void record(String enterpriseId, String resourceId, String action, String before, String after, String requestId, Authentication authentication) {
        journal.audit(enterpriseId, "organization.audit", resourceId, new OrganizationAuditEvent(authentication.getName(), enterpriseId, resourceId, action, before, after, requestId));
        if (!"ORGANIZATION_CONTEXT_SELECTED".equals(action)) publisher.publishEvent(new OrganizationAuthorizationChange(enterpriseId, resourceId, action, requestId, false));
    }

    private String enterpriseRevision(String enterpriseId) { return revision(enterpriseId, "enterprise", unitsFor(enterpriseId).stream().sorted(Comparator.comparing(UnitRecord::id)).toList().toString() + assignments.values().stream().filter(a -> enterpriseId.equals(a.enterpriseId())).sorted(Comparator.comparing(Assignment::memberId).thenComparing(Assignment::organizationId)).toList() + memberDirectory.list(enterpriseId)); }
    private String unitRevision(UnitRecord unit) { return revision(unit.enterpriseId(), "unit", unit.toString()); }
    private static String fingerprint(String operation, Object request, String ifMatch) { return operation + "|" + request + "|" + ifMatch; }
    private static String quote(String value) { return "\"" + value + "\""; }
    private static String assignmentKey(String memberId, String organizationId) { return memberId + ":" + organizationId; }
    private static String valueOf(Map<?, ?> map, String first, String second) { Object value = map.get(first); if (value == null) value = map.get(second); return value == null ? null : value.toString(); }
    private static Comparator<UnitRecord> unitComparator() { return Comparator.comparingInt(UnitRecord::sortOrder).thenComparing(UnitRecord::id); }

    private void persistMemberships(String enterpriseId, String memberId, List<String> organizationIds, String primaryOrganizationId) {
        membershipRepository.deleteByEnterpriseIdAndMemberId(enterpriseId, memberId);
        membershipRepository.flush();
        for (String organizationId : organizationIds.stream().distinct().toList()) {
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
    private record Assignment(String enterpriseId, String organizationId, String memberId, boolean primary) {}
    private record UnitRecord(String enterpriseId, String id, String parentId, String unitType, String name, String code, String leaderMemberId,
                              String description, int sortOrder, String status, long version) {
        UnitRecord withVersion(long value) { return new UnitRecord(enterpriseId, id, parentId, unitType, name, code, leaderMemberId, description, sortOrder, status, value); }
        OrganizationUnit toPublic(String enterpriseId, int memberCount, List<OrganizationUnit> children) {
            return new OrganizationUnit(id, enterpriseId, parentId, unitType, name, code, leaderMemberId, description, sortOrder, status, memberCount, children);
        }
    }
}
