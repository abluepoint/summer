package com.abluepoint.summer.sample.organization.application;

import java.util.List;

public record OrganizationMemberListView(String enterpriseId, String organizationId, String revision, List<OrganizationMemberView> items) {
    public OrganizationMemberListView {
        items = List.copyOf(items);
    }

    @Override public String toString() { return "OrganizationMemberList[" + "enterpriseId=" + enterpriseId + ", " + "organizationId=" + organizationId + ", " + "revision=" + revision + ", " + "items=" + items + "]"; }
}
