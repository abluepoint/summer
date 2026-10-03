package com.abluepoint.summer.sample.organization;

import java.util.List;

record OrganizationAssignmentsRequest(
        List<String> memberIds,
        List<String> organizationIds,
        String primaryOrganizationId
) {
    OrganizationAssignmentsRequest {
        memberIds = memberIds == null ? List.of() : List.copyOf(memberIds);
        organizationIds = organizationIds == null ? List.of() : List.copyOf(organizationIds);
    }
}
