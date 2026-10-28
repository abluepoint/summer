package com.abluepoint.summer.sample.organization.application;

import java.util.List;

public record OrganizationUnitView(
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
        List<OrganizationUnitView> children
) {
    public OrganizationUnitView {
        children = List.copyOf(children);
    }

    OrganizationUnitView withoutChildren() {
        return new OrganizationUnitView(id, enterpriseId, parentId, unitType, name, code, leaderMemberId,
                description, sortOrder, status, memberCount, List.of());
    }

    @Override public String toString() { return "OrganizationUnit[" + "id=" + id + ", " + "enterpriseId=" + enterpriseId + ", " + "parentId=" + parentId + ", " + "unitType=" + unitType + ", " + "name=" + name + ", " + "code=" + code + ", " + "leaderMemberId=" + leaderMemberId + ", " + "description=" + description + ", " + "sortOrder=" + sortOrder + ", " + "status=" + status + ", " + "memberCount=" + memberCount + ", " + "children=" + children + "]"; }
}
