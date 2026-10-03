package com.abluepoint.summer.sample.organization;

import java.util.List;

record OrganizationAssignmentBatchResult(
        String enterpriseId,
        List<String> updatedMemberIds,
        List<String> organizationIds,
        String primaryOrganizationId,
        String revision
) {
    OrganizationAssignmentBatchResult {
        updatedMemberIds = List.copyOf(updatedMemberIds);
        organizationIds = List.copyOf(organizationIds);
    }
}
