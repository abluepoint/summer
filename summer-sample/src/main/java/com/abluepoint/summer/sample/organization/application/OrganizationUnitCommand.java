package com.abluepoint.summer.sample.organization.application;

/** Includes the parent field's presence independently from its nullable value. */
public record OrganizationUnitCommand(boolean parentIdPresent, String parentId, String unitType, String name,
                                      String code, String leaderMemberId, String description, Integer sortOrder, String status) {
    @Override public String toString() {
        return java.util.Arrays.asList(parentIdPresent, parentId, unitType, name, code, leaderMemberId, description, sortOrder, status).toString();
    }
}
