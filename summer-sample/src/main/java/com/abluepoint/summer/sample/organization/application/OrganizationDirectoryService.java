package com.abluepoint.summer.sample.organization.application;

import com.abluepoint.summer.sample.organization.infrastructure.OrganizationMembershipEntity;
import com.abluepoint.summer.sample.organization.domain.OrganizationConflictException;
import com.abluepoint.summer.sample.organization.domain.OrganizationValidationException;
import com.abluepoint.summer.sample.organization.domain.OrganizationPreconditionException;
import com.abluepoint.summer.sample.organization.domain.OrganizationIdempotencyConflictException;
import com.abluepoint.summer.sample.organization.infrastructure.OrganizationMembershipRepository;
import com.abluepoint.summer.sample.organization.infrastructure.OrganizationUnitRepository;
import com.abluepoint.summer.sample.organization.infrastructure.OrganizationContextEntity;
import com.abluepoint.summer.sample.organization.infrastructure.OrganizationUnitEntity;
import com.abluepoint.summer.sample.organization.infrastructure.OrganizationContextRepository;
import com.abluepoint.summer.sample.organization.domain.OrganizationNode;
import com.abluepoint.summer.sample.organization.domain.OrganizationAssignment;

import com.abluepoint.summer.sample.authorization.application.AuthorizationService;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import com.abluepoint.summer.sample.organization.domain.OrganizationRules;
import static com.abluepoint.summer.sample.organization.infrastructure.OrganizationAccess.*;
import org.springframework.transaction.annotation.Transactional;
import com.abluepoint.summer.sample.enterprise.application.EnterpriseRegistry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrganizationDirectoryService {
    private static final String ENTERPRISE = "ent-acme";
    private static final String ORGANIZATION_READ = "enterprise.organization.read";
    private static final String ORGANIZATIONS_READ = "enterprise.organizations.read";
    private static final String ORGANIZATION_WRITE = "enterprise.organization.write";
    private static final String ORGANIZATIONS_WRITE = "enterprise.organizations.write";

    private final Map<String, OrganizationNode> units = new LinkedHashMap<>();
    private final Map<String, OrganizationAssignment> assignments = new LinkedHashMap<>();
    private final OrganizationRules treeRules = new OrganizationRules(units);
    private final com.abluepoint.summer.sample.member.infrastructure.MemberDirectory memberDirectory;
    private final com.abluepoint.summer.sample.enterprise.infrastructure.DirectoryJournal journal;
    private final EnterpriseRegistry enterprises;
    private final OrganizationUnitRepository unitRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationContextRepository contextRepository;
    private final com.abluepoint.summer.sample.authorization.application.AuthorizationService authorization;

    OrganizationDirectoryService(EnterpriseRegistry enterprises, OrganizationUnitRepository unitRepository, OrganizationMembershipRepository membershipRepository, OrganizationContextRepository contextRepository, com.abluepoint.summer.sample.member.infrastructure.MemberDirectory memberDirectory, com.abluepoint.summer.sample.enterprise.infrastructure.DirectoryJournal journal, com.abluepoint.summer.sample.authorization.application.AuthorizationService authorization) {
        this.authorization = authorization;
        this.journal = journal;
        this.memberDirectory = memberDirectory;
        this.enterprises = enterprises;
        this.unitRepository = unitRepository;
        this.membershipRepository = membershipRepository;
        this.contextRepository = contextRepository;
        boolean seedAssignments = !unitRepository.existsByUnitId("org-acme");
        units.put("org-acme", new OrganizationNode(ENTERPRISE, "org-acme", null, "COMPANY", "Acme Corporation", "ACME", "member-alice", "Head office", 0, "ACTIVE", 0));
        units.put("org-engineering", new OrganizationNode(ENTERPRISE, "org-engineering", "org-acme", "DEPARTMENT", "Engineering", "ENG", "member-alice", "Product engineering", 10, "ACTIVE", 0));
        units.put("org-operations", new OrganizationNode(ENTERPRISE, "org-operations", "org-acme", "DEPARTMENT", "Operations", "OPS", null, "Business operations", 20, "ACTIVE", 0));
        persistSeed("org-acme", null, "COMPANY", "Acme Corporation", "ACME", "member-alice", "Head office", 0, "ACTIVE");
        persistSeed("org-engineering", "org-acme", "DEPARTMENT", "Engineering", "ENG", "member-alice", "Product engineering", 10, "ACTIVE");
        persistSeed("org-operations", "org-acme", "DEPARTMENT", "Operations", "OPS", null, "Business operations", 20, "ACTIVE");
        if (seedAssignments) membershipRepository.save(new OrganizationMembershipEntity(ENTERPRISE, "member-alice", "org-engineering", true));
        refresh();
    }

    public synchronized OrganizationTreeView tree(String enterpriseId, Authentication authentication) {
        requireEnterpriseRead(enterpriseId, authentication);
        var visible = readableUnits(enterpriseId, authentication);
        var ids = visible.stream().map(OrganizationNode::id).collect(java.util.stream.Collectors.toSet());
        List<OrganizationUnitView> roots = visible.stream().filter(unit -> unit.parentId() == null || !ids.contains(unit.parentId())).sorted(unitComparator())
                .map(unit -> treeNode(enterpriseId, unit, ids)).toList();
        return new OrganizationTreeView(enterpriseId, enterpriseRevision(enterpriseId), roots);
    }

    public synchronized OrganizationTreeView list(String enterpriseId, String parentId, String query, Authentication authentication) {
        requireEnterpriseRead(enterpriseId, authentication);
        List<OrganizationUnitView> result = readableUnits(enterpriseId, authentication).stream()
                .filter(unit -> parentId == null || parentId.equals(unit.parentId()))
                .filter(unit -> query == null || query.isBlank() || unit.name().toLowerCase().contains(query.toLowerCase())
                        || (unit.code() != null && unit.code().toLowerCase().contains(query.toLowerCase())))
                .sorted(unitComparator()).map(unit -> flatNode(enterpriseId, unit)).toList();
        return new OrganizationTreeView(enterpriseId, revision(enterpriseId, "list", parentId + "|" + query + "|" + result), result);
    }

    public synchronized OrganizationUnitView detail(String enterpriseId, String unitId, Authentication authentication) {
        requireEnterpriseRead(enterpriseId, authentication);
        OrganizationNode unit = unitFor(enterpriseId, unitId);
        requireUnitRead(enterpriseId, unitId, authentication);
        return view(unit, enterpriseId, memberCount(enterpriseId, unitId), List.of());
    }

    public synchronized String unitEtag(String enterpriseId, String unitId) {
        return quote(unitRevision(unitFor(enterpriseId, unitId)));
    }

    public synchronized String unitRevisionValue(String enterpriseId, String unitId) {
        return unitRevision(unitFor(enterpriseId, unitId));
    }

    public synchronized OrganizationMemberListView members(String enterpriseId, String organizationId, Authentication authentication) {
        requireEnterpriseRead(enterpriseId, authentication);
        unitFor(enterpriseId, organizationId);
        requireUnitRead(enterpriseId, organizationId, authentication);
        List<OrganizationMemberView> result = assignments.values().stream()
                .filter(assignment -> enterpriseId.equals(assignment.enterpriseId()) && organizationId.equals(assignment.organizationId()))
                .sorted(Comparator.comparing(OrganizationAssignment::memberId)).map(assignment -> {
                    var member = memberDirectory.find(enterpriseId, assignment.memberId()).orElse(null);
                    return new OrganizationMemberView(assignment.enterpriseId(), assignment.organizationId(), assignment.memberId(),
                            member == null ? "" : member.displayName(), member == null ? "" : member.email(),
                            member == null ? "UNKNOWN" : member.status(), assignment.primary());
                }).toList();
        return new OrganizationMemberListView(enterpriseId, organizationId, revision(enterpriseId, "members", organizationId + "|" + result), result);
    }

    @Transactional
    public synchronized OrganizationUnitView create(String enterpriseId, OrganizationUnitCommand request, String key, String requestId, Authentication authentication) {
        requireEnterpriseWrite(enterpriseId, authentication);
        requireKey(key);
        String fingerprint = fingerprint("create", request, null);
        var previous = journal.find(enterpriseId, "organization.mutation", key).orElse(null);
        if (previous != null) return replay(previous, fingerprint, OrganizationUnitView.class);
        validateRequest(request, true);
        if (!enterprises.exists(enterpriseId)) throw new OrganizationConflictException("Enterprise does not exist");
        OrganizationNode parent = request.parentId() == null ? null : units.get(request.parentId());
        treeRules.requireParent(enterpriseId, request.parentId());
        treeRules.ensureSiblingCodeAvailable(enterpriseId, request.parentId(), request.code(), null);
        treeRules.requireDepth(parent == null ? 0 : treeRules.depth(parent.id()), 1);
        String id = "org-" + java.util.UUID.randomUUID();
        if (units.containsKey(id)) throw new OrganizationConflictException("Organization code already exists");
        OrganizationNode created = new OrganizationNode(enterpriseId, id, request.parentId(), OrganizationRules.normalizeType(request.unitType()), request.name().trim(), request.code().trim(),
                request.leaderMemberId(), request.description(), request.sortOrder() == null ? 0 : request.sortOrder(), OrganizationRules.normalizeStatus(request.status()), 0);
        units.put(id, created);
        unitRepository.save(new OrganizationUnitEntity(id, enterpriseId, created.parentId(), created.unitType(), created.name(), created.code(), created.leaderMemberId(), created.description(), created.sortOrder(), created.status()));
        OrganizationUnitView result = view(created, enterpriseId, 0, List.of());
        remember(enterpriseId, key, fingerprint, result);
        record(enterpriseId, id, "ORGANIZATION_CREATED", null, created.status(), requestId, authentication);
        return result;
    }

    @Transactional
    public synchronized OrganizationUnitView update(String enterpriseId, String unitId, OrganizationUnitCommand request, String ifMatch, String key,
                                          String requestId, Authentication authentication) {
        requireEnterpriseWrite(enterpriseId, authentication);
        requireKey(key);
        String fingerprint = fingerprint("update:" + unitId, request, ifMatch);
        var previous = journal.find(enterpriseId, "organization.mutation", key).orElse(null);
        if (previous != null) return replay(previous, fingerprint, OrganizationUnitView.class);
        OrganizationNode current = unitFor(enterpriseId, unitId);
        requireEtag(current, ifMatch);
        validateRequest(request, false);
        String parentId = request.parentIdPresent() ? request.parentId() : current.parentId();
        String code = request.code() == null ? current.code() : request.code().trim();
        String name = request.name() == null ? current.name() : request.name().trim();
        String type = request.unitType() == null ? current.unitType() : OrganizationRules.normalizeType(request.unitType());
        String status = request.status() == null ? current.status() : OrganizationRules.normalizeStatus(request.status());
        treeRules.requireAcyclic(unitId, parentId);
        treeRules.requireParent(enterpriseId, parentId);
        treeRules.requireDepth(parentId == null ? 0 : treeRules.depth(parentId), treeRules.subtreeHeight(unitId));
        treeRules.ensureSiblingCodeAvailable(enterpriseId, parentId, code, unitId);
        OrganizationNode updated = new OrganizationNode(enterpriseId, unitId, parentId, type, name, code,
                request.leaderMemberId() == null ? current.leaderMemberId() : request.leaderMemberId(),
                request.description() == null ? current.description() : request.description(),
                request.sortOrder() == null ? current.sortOrder() : request.sortOrder(), status, current.version() + 1);
        var entity = unitRepository.findByUnitId(unitId).orElseThrow();
        entity.update(updated.parentId(), updated.unitType(), updated.name(), updated.code(), updated.leaderMemberId(), updated.description(), updated.sortOrder(), updated.status());
        unitRepository.saveAndFlush(entity);
        updated = updated.withVersion(entity.version());
        units.put(unitId, updated);
        OrganizationUnitView result = view(updated, enterpriseId, memberCount(enterpriseId, unitId), List.of());
        remember(enterpriseId, key, fingerprint, result);
        record(enterpriseId, unitId, "ORGANIZATION_UPDATED", current.status(), updated.status(), requestId, authentication);
        return result;
    }

    @Transactional
    public synchronized void delete(String enterpriseId, String unitId, String ifMatch, String key, String requestId, Authentication authentication) {
        requireEnterpriseWrite(enterpriseId, authentication);
        requireKey(key);
        String fingerprint = fingerprint("delete", unitId, ifMatch);
        var previous = journal.find(enterpriseId, "organization.mutation", key).orElse(null);
        if (previous != null) {
            replay(previous, fingerprint, Boolean.class);
            return;
        }
        OrganizationNode current = unitFor(enterpriseId, unitId);
        requireEtag(current, ifMatch);
        treeRules.requireDeletable(unitId, memberCount(enterpriseId, unitId));
        units.remove(unitId);
        unitRepository.findByUnitId(unitId).ifPresent(unitRepository::delete);
        remember(enterpriseId, key, fingerprint, Boolean.TRUE);
        record(enterpriseId, unitId, "ORGANIZATION_DELETED", current.status(), "DELETED", requestId, authentication);
    }

    @Transactional
    public synchronized OrganizationAssignmentBatchView assign(String enterpriseId, OrganizationAssignmentsCommand request, String ifMatch, String key,
                                                           String requestId, Authentication authentication) {
        requireEnterpriseWrite(enterpriseId, authentication);
        requireKey(key);
        String fingerprint = fingerprint("assign", request, ifMatch);
        var previous = journal.find(enterpriseId, "organization.mutation", key).orElse(null);
        if (previous != null) return replay(previous, fingerprint, OrganizationAssignmentBatchView.class);
        requireEtagForEnterprise(enterpriseId, ifMatch);
        if (request == null || request.memberIds() == null || request.organizationIds() == null || request.memberIds().isEmpty() || request.memberIds().stream().anyMatch(java.util.Objects::isNull) || request.organizationIds().stream().anyMatch(java.util.Objects::isNull)) {
            throw new OrganizationValidationException("memberIds are required");
        }
        OrganizationRules.requirePrimary(request.organizationIds(), request.primaryOrganizationId());
        for (String memberId : request.memberIds().stream().distinct().toList()) {
            var member = memberDirectory.find(enterpriseId, memberId).orElse(null);
            if (member == null || !"ACTIVE".equals(member.status())) throw new OrganizationConflictException("Member is not active in the enterprise");
        }
        for (String organizationId : request.organizationIds().stream().distinct().toList()) {
            OrganizationNode organization = unitFor(enterpriseId, organizationId);
            if ("DISABLED".equals(organization.status())) throw new OrganizationConflictException("Cannot assign a member to a disabled organization");
        }
        for (String memberId : request.memberIds().stream().distinct().toList()) {
            assignments.entrySet().removeIf(entry -> memberId.equals(entry.getValue().memberId()) && enterpriseId.equals(entry.getValue().enterpriseId()));
            persistMemberships(enterpriseId, memberId, request.organizationIds(), request.primaryOrganizationId());
            for (String organizationId : request.organizationIds().stream().distinct().toList()) assignments.put(assignmentKey(memberId, organizationId),
                    new OrganizationAssignment(enterpriseId, organizationId, memberId, organizationId.equals(request.primaryOrganizationId())));
            record(enterpriseId, memberId, "MEMBER_ORGANIZATION_ASSIGNMENTS_UPDATED", null, request.organizationIds().toString(), requestId, authentication);
        }
        OrganizationAssignmentBatchView result = new OrganizationAssignmentBatchView(enterpriseId, request.memberIds(), request.organizationIds(), request.primaryOrganizationId(), enterpriseRevision(enterpriseId));
        remember(enterpriseId, key, fingerprint, result);
        return result;
    }

    public synchronized List<OrganizationAuditView> audit(String enterpriseId, String resourceId, Authentication authentication) {
        requireEnterpriseRead(enterpriseId, authentication);
        if (managesOrganizations(enterpriseId, authentication))
            return journal.audit(enterpriseId, "organization.audit", resourceId, OrganizationAuditView.class);
        // Unit IDs, member IDs and subjects share the historical resource column. A subject
        // matching a unit ID must not turn an unassigned organization's audit into "own" data.
        if (units.containsKey(resourceId)) {
            requireUnitRead(enterpriseId, resourceId, authentication);
            return journal.audit(enterpriseId, "organization.audit", resourceId, OrganizationAuditView.class).stream()
                    .filter(event -> !"ORGANIZATION_CONTEXT_SELECTED".equals(event.action())
                            && !"MEMBER_ORGANIZATION_ASSIGNMENTS_UPDATED".equals(event.action())).toList();
        }
        var member = memberDirectory.forSubject(enterpriseId, authentication.getName());
        boolean own = authentication.getName().equals(resourceId) || member.map(m -> m.memberId().equals(resourceId)).orElse(false);
        if (!own) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        return journal.audit(enterpriseId, "organization.audit", resourceId, OrganizationAuditView.class).stream()
                .filter(event -> ("ORGANIZATION_CONTEXT_SELECTED".equals(event.action()) && authentication.getName().equals(event.actorSubject()))
                        || ("MEMBER_ORGANIZATION_ASSIGNMENTS_UPDATED".equals(event.action()) && member.map(m -> m.memberId().equals(resourceId)).orElse(false))).toList();
    }

    public synchronized OrganizationContextView currentContext(String enterpriseId, Authentication authentication) {
        requireEnterpriseRead(enterpriseId, authentication);
        String organizationId = contextRepository.findBySubject(authentication.getName())
                .filter(context -> enterpriseId.equals(context.enterpriseId)).map(context -> context.organizationId).orElse(null);
        if (organizationId != null && !selectable(enterpriseId, organizationId, authentication.getName())) organizationId = null;
        return new OrganizationContextView(authentication.getName(), enterpriseId, organizationId, enterpriseRevision(enterpriseId));
    }

    @Transactional
    public synchronized OrganizationContextView selectContext(String enterpriseId, String organizationId, Authentication authentication) {
        requireEnterpriseRead(enterpriseId, authentication);
        enterprises.lock(enterpriseId);
        refresh();
        if (organizationId != null) {
            unitFor(enterpriseId, organizationId);
            if (!selectable(enterpriseId, organizationId, authentication.getName())) throw new AccessDeniedException("An active assigned membership is required");
        }
        String subject = authentication.getName();
        OrganizationContextView result = new OrganizationContextView(subject, enterpriseId, organizationId, enterpriseRevision(enterpriseId));
        var context = contextRepository.findBySubject(subject).orElseGet(() -> new OrganizationContextEntity(subject, enterpriseId, organizationId, result.revision()));
        context.enterpriseId = enterpriseId; context.organizationId = organizationId; context.revision = result.revision();
        contextRepository.saveAndFlush(context);
        record(enterpriseId, subject, "ORGANIZATION_CONTEXT_SELECTED", null, organizationId, null, authentication);
        return result;
    }

    private boolean selectable(String enterpriseId, String organizationId, String subject) {
        var member = memberDirectory.forSubject(enterpriseId, subject).filter(value -> OrganizationRules.activeMember(value.status()));
        var unit = units.get(organizationId);
        return OrganizationRules.selectable(member.isPresent(), unit != null && OrganizationRules.activeOrganization(enterpriseId, unit.enterpriseId(), unit.status()),
                member.isPresent() && assignments.values().stream().anyMatch(a -> enterpriseId.equals(a.enterpriseId()) && organizationId.equals(a.organizationId()) && member.get().memberId().equals(a.memberId())));
    }

    private void refresh() {
        units.clear();
        unitRepository.findAll().forEach(value -> units.put(value.unitId(), new OrganizationNode(value.enterpriseId(), value.unitId(), value.parentId(), value.unitType(), value.name(), value.code(), value.leaderMemberId(), value.description(), value.sortOrder(), value.status(), value.version())));
        assignments.clear();
        membershipRepository.findAll().forEach(value -> assignments.put(assignmentKey(value.memberId(), value.organizationId()), new OrganizationAssignment(value.enterpriseId(), value.organizationId(), value.memberId(), value.primaryOrganization())));
    }

    private void requireEnterpriseRead(String enterpriseId, Authentication authentication) {
        requireCapability(requireJwtMember(enterpriseId, authentication), enterpriseId, ORGANIZATION_READ, ORGANIZATIONS_READ);
        refresh();
    }

    private void requireEnterpriseWrite(String enterpriseId, Authentication authentication) {
        requireCapability(requireJwtMember(enterpriseId, authentication), enterpriseId, ORGANIZATION_WRITE, ORGANIZATIONS_WRITE);
        enterprises.lock(enterpriseId);
        refresh();
    }

    private OrganizationNode unitFor(String enterpriseId, String unitId) {
        if (!enterprises.exists(enterpriseId)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        OrganizationNode unit = units.get(unitId);
        if (unit != null && !enterpriseId.equals(unit.enterpriseId())) unit = null;
        if (unit == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        return unit;
    }

    private List<OrganizationNode> unitsFor(String enterpriseId) { return enterprises.exists(enterpriseId) ? units.values().stream().filter(unit -> enterpriseId.equals(unit.enterpriseId())).toList() : List.of(); }

    private OrganizationUnitView treeNode(String enterpriseId, OrganizationNode unit, java.util.Set<String> visible) {
        List<OrganizationUnitView> children = units.values().stream().filter(child -> enterpriseId.equals(child.enterpriseId()) && visible.contains(child.id()) && unit.id().equals(child.parentId())).sorted(unitComparator()).map(child -> treeNode(enterpriseId, child, visible)).toList();
        return view(unit, enterpriseId, memberCount(enterpriseId, unit.id()), children);
    }

    private boolean managesOrganizations(String enterpriseId, Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwt)) return false;
        try { requireCapability(jwt, enterpriseId, ORGANIZATION_WRITE, ORGANIZATIONS_WRITE); }
        catch (AccessDeniedException denied) { return false; }
        // Check the persisted grant as well, so a stale write claim cannot broaden a reader's view.
        return authorization.decision(authentication, enterpriseId, ORGANIZATION_WRITE, null).allowed();
    }

    private List<OrganizationNode> readableUnits(String enterpriseId, Authentication authentication) {
        if (managesOrganizations(enterpriseId, authentication)) return unitsFor(enterpriseId);
        return unitsFor(enterpriseId).stream().filter(unit -> selectable(enterpriseId, unit.id(), authentication.getName())).toList();
    }

    private void requireUnitRead(String enterpriseId, String unitId, Authentication authentication) {
        if (!managesOrganizations(enterpriseId, authentication) && !selectable(enterpriseId, unitId, authentication.getName()))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
    }

    private OrganizationUnitView flatNode(String enterpriseId, OrganizationNode unit) { return view(unit, enterpriseId, memberCount(enterpriseId, unit.id()), List.of()); }

    private int memberCount(String enterpriseId, String organizationId) {
        return (int) assignments.values().stream().filter(assignment -> enterpriseId.equals(assignment.enterpriseId()) && organizationId.equals(assignment.organizationId())
                && memberDirectory.find(enterpriseId, assignment.memberId()).map(member -> OrganizationRules.activeMember(member.status())).orElse(false)).count();
    }

    private static void validateRequest(OrganizationUnitCommand request, boolean create) {
        if (request == null) throw new OrganizationValidationException("Request body is required");
        OrganizationRules.validateNameAndCode(request.name(), request.code(), create);
    }


    private void requireEtag(OrganizationNode unit, String ifMatch) { if (ifMatch == null || !ifMatch.equals(quote(unitRevision(unit)))) throw new OrganizationPreconditionException(); }
    private void requireEtagForEnterprise(String enterpriseId, String ifMatch) { if (ifMatch == null || !ifMatch.equals(quote(enterpriseRevision(enterpriseId)))) throw new OrganizationPreconditionException(); }
    private static void requireKey(String key) { if (key == null || key.isBlank() || key.length() > 128) throw new OrganizationIdempotencyConflictException("Idempotency-Key is required"); }
    private void remember(String enterpriseId, String key, String fingerprint, Object result) { journal.remember(enterpriseId, "organization.mutation", key, fingerprint, result); }

    private <T> T replay(com.abluepoint.summer.sample.enterprise.infrastructure.DirectoryReceipt receipt, String fingerprint, Class<T> type) {
        if (!receipt.fingerprint().equals(fingerprint)) throw new OrganizationIdempotencyConflictException("Idempotency-Key was already used for another mutation");
        return journal.decode(receipt, type);
    }

    private void record(String enterpriseId, String resourceId, String action, String before, String after, String requestId, Authentication authentication) {
        journal.audit(enterpriseId, "organization.audit", resourceId, new OrganizationAuditView(authentication.getName(), enterpriseId, resourceId, action, before, after, requestId));
    }

    private String enterpriseRevision(String enterpriseId) { return revision(enterpriseId, "enterprise", unitsFor(enterpriseId).stream().sorted(Comparator.comparing(OrganizationNode::id)).toList().toString() + assignments.values().stream().filter(a -> enterpriseId.equals(a.enterpriseId())).sorted(Comparator.comparing(OrganizationAssignment::memberId).thenComparing(OrganizationAssignment::organizationId)).toList() + memberDirectory.list(enterpriseId)); }
    private String unitRevision(OrganizationNode unit) { return revision(unit.enterpriseId(), "unit", unit.toString()); }
    private static String fingerprint(String operation, Object request, String ifMatch) { return operation + "|" + request + "|" + ifMatch; }
    private static String quote(String value) { return "\"" + value + "\""; }
    private static String assignmentKey(String memberId, String organizationId) { return memberId + ":" + organizationId; }
    private static String valueOf(Map<?, ?> map, String first, String second) { Object value = map.get(first); if (value == null) value = map.get(second); return value == null ? null : value.toString(); }
    private static Comparator<OrganizationNode> unitComparator() { return Comparator.comparingInt(OrganizationNode::sortOrder).thenComparing(OrganizationNode::id); }

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




    private static OrganizationUnitView view(OrganizationNode node, String enterpriseId, int memberCount, List<OrganizationUnitView> children) {
        return new OrganizationUnitView(node.id(), enterpriseId, node.parentId(), node.unitType(), node.name(), node.code(),
                node.leaderMemberId(), node.description(), node.sortOrder(), node.status(), memberCount, children);
    }
}
