package com.abluepoint.summer.sample.organization.api;
import com.abluepoint.summer.sample.organization.application.OrganizationAssignmentsCommand;

import java.util.List;

public record OrganizationAssignmentsRequest(
        List<String> memberIds,
        List<String> organizationIds,
        String primaryOrganizationId
) {
    public OrganizationAssignmentsRequest {
        memberIds = memberIds == null ? List.of() : List.copyOf(memberIds);
        organizationIds = organizationIds == null ? List.of() : List.copyOf(organizationIds);
    }

    OrganizationAssignmentsCommand command() { return new OrganizationAssignmentsCommand(memberIds, organizationIds, primaryOrganizationId); }
}
