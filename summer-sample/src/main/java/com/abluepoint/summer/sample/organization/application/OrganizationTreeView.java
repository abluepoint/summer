package com.abluepoint.summer.sample.organization.application;

import java.util.List;

public record OrganizationTreeView(String enterpriseId, String revision, List<OrganizationUnitView> items) {
    public OrganizationTreeView {
        items = List.copyOf(items);
    }

    @Override public String toString() { return "OrganizationTree[" + "enterpriseId=" + enterpriseId + ", " + "revision=" + revision + ", " + "items=" + items + "]"; }
}
