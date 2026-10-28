package com.abluepoint.summer.sample.organization.domain;

import java.util.*;

/** Tenant-owned organization tree invariants; persistence and request types stay outside. */
public final class OrganizationRules {
    private final Map<String, OrganizationNode> units;
    public OrganizationRules(Map<String, OrganizationNode> units) { this.units = units; }
    public void requireParent(String tenant, String parentId) {
        OrganizationNode parent = parentId == null ? null : units.get(parentId);
        if (parentId != null && (parent == null || !tenant.equals(parent.enterpriseId()))) throw new OrganizationConflictException("Parent organization must belong to the enterprise");
        if (parent != null && "DISABLED".equals(parent.status())) throw new OrganizationConflictException("Parent organization is disabled");
    }
    public void requireAcyclic(String unitId, String parentId) {
        if (unitId.equals(parentId) || (parentId != null && isDescendant(unitId, parentId))) throw new OrganizationConflictException("Organization parent would create a cycle");
    }
    public void requireDepth(int parentDepth, int subtreeHeight) {
        if (parentDepth + subtreeHeight > 8) throw new OrganizationConflictException("Organization tree depth exceeded");
    }
    public void requireDeletable(String unitId, int members) {
        if (units.values().stream().anyMatch(unit -> unitId.equals(unit.parentId()))) throw new OrganizationConflictException("Organization has child units");
        if (members > 0) throw new OrganizationConflictException("Organization has active members");
    }
    public static void requirePrimary(List<String> organizations, String primary) {
        if ((!organizations.isEmpty() && primary == null) || (primary != null && !organizations.contains(primary)))
            throw new OrganizationValidationException("primaryOrganizationId must be one of organizationIds");
    }
    public static void validateNameAndCode(String name, String code, boolean create) {
        if (create && (name == null || name.isBlank() || code == null || code.isBlank())) throw new OrganizationValidationException("name and code are required");
        if (name != null && name.isBlank()) throw new OrganizationValidationException("name must not be blank");
        if (code != null && code.isBlank()) throw new OrganizationValidationException("code must not be blank");
    }
    public static String normalizeType(String value) {
        String normalized = value == null || value.isBlank() ? "DEPARTMENT" : value.toUpperCase();
        if (!List.of("COMPANY", "DEPARTMENT", "TEAM").contains(normalized)) throw new OrganizationValidationException("unitType is invalid");
        return normalized;
    }

    public static String normalizeStatus(String value) {
        String normalized = value == null || value.isBlank() ? "ACTIVE" : value.toUpperCase();
        if (!List.of("ACTIVE", "DISABLED").contains(normalized)) throw new OrganizationValidationException("status is invalid");
        return normalized;
    }

    public void ensureSiblingCodeAvailable(String enterpriseId, String parentId, String code, String exceptId) {
        if (code == null) return;
        if (units.values().stream().anyMatch(unit -> enterpriseId.equals(unit.enterpriseId()) && (parentId == null ? unit.parentId() == null : parentId.equals(unit.parentId()))
                && !unit.id().equals(exceptId) && code.equalsIgnoreCase(unit.code()))) throw new OrganizationConflictException("Sibling organization code already exists");
    }

    public int subtreeHeight(String unitId) {
        return 1 + units.values().stream().filter(unit -> unitId.equals(unit.parentId())).mapToInt(unit -> subtreeHeight(unit.id())).max().orElse(0);
    }

    public int depth(String unitId) {
        int depth = 1;
        OrganizationNode current = units.get(unitId);
        while (current != null && current.parentId() != null) { depth++; current = units.get(current.parentId()); }
        return depth;
    }

    public boolean isDescendant(String ancestorId, String candidateId) {
        OrganizationNode current = units.get(candidateId);
        while (current != null && current.parentId() != null) { if (ancestorId.equals(current.parentId())) return true; current = units.get(current.parentId()); }
        return false;
    }

    public static boolean activeMember(String status) { return "ACTIVE".equals(status); }
    public static boolean activeOrganization(String tenant, String unitTenant, String status) {
        return tenant.equals(unitTenant) && "ACTIVE".equals(status);
    }
    public static boolean selectable(boolean activeMember, boolean activeOrganization, boolean assigned) {
        return activeMember && activeOrganization && assigned;
    }
}
