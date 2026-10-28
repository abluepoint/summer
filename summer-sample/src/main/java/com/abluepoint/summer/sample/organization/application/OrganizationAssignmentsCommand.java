package com.abluepoint.summer.sample.organization.application;
import java.util.*;

public record OrganizationAssignmentsCommand(
        List<String> memberIds,
        List<String> organizationIds,
        String primaryOrganizationId
) {
    public OrganizationAssignmentsCommand {
        memberIds = memberIds == null ? List.of() : List.copyOf(memberIds);
        organizationIds = organizationIds == null ? List.of() : List.copyOf(organizationIds);
    }

    @Override public String toString() { return "OrganizationAssignmentsRequest[" + "memberIds=" + memberIds + ", " + "organizationIds=" + organizationIds + ", " + "primaryOrganizationId=" + primaryOrganizationId + "]"; }
}
