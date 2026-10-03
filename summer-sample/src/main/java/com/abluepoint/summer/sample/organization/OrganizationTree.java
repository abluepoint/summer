package com.abluepoint.summer.sample.organization;

import java.util.List;

record OrganizationTree(String enterpriseId, String revision, List<OrganizationUnit> items) {
    OrganizationTree {
        items = List.copyOf(items);
    }
}
