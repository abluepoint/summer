package com.abluepoint.summer.sample.organization;

import java.util.List;

record OrganizationMemberList(String enterpriseId, String organizationId, String revision, List<OrganizationMember> items) {
    OrganizationMemberList {
        items = List.copyOf(items);
    }
}
