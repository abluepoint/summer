package com.abluepoint.summer.sample.organization;

import java.util.List;

record OrganizationUnit(
        String id,
        String enterpriseId,
        String parentId,
        String unitType,
        String name,
        String code,
        String leaderMemberId,
        String description,
        int sortOrder,
        String status,
        int memberCount,
        List<OrganizationUnit> children
) {
    OrganizationUnit {
        children = List.copyOf(children);
    }

    OrganizationUnit withoutChildren() {
        return new OrganizationUnit(id, enterpriseId, parentId, unitType, name, code, leaderMemberId,
                description, sortOrder, status, memberCount, List.of());
    }
}
