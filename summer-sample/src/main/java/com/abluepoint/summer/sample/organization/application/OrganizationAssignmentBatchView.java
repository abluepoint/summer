package com.abluepoint.summer.sample.organization.application;

import java.util.List;

public record OrganizationAssignmentBatchView(
        String enterpriseId,
        List<String> updatedMemberIds,
        List<String> organizationIds,
        String primaryOrganizationId,
        String revision
) {
    public OrganizationAssignmentBatchView {
        updatedMemberIds = List.copyOf(updatedMemberIds);
        organizationIds = List.copyOf(organizationIds);
    }

    @Override public String toString() { return "OrganizationAssignmentBatchResult[" + "enterpriseId=" + enterpriseId + ", " + "updatedMemberIds=" + updatedMemberIds + ", " + "organizationIds=" + organizationIds + ", " + "primaryOrganizationId=" + primaryOrganizationId + ", " + "revision=" + revision + "]"; }
}
