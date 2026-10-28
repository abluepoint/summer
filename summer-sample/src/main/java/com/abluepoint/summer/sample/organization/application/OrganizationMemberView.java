package com.abluepoint.summer.sample.organization.application;

public record OrganizationMemberView(
        String enterpriseId,
        String organizationId,
        String memberId,
        String displayName,
        String email,
        String status,
        boolean primary
) {
    @Override public String toString() { return "OrganizationMember[" + "enterpriseId=" + enterpriseId + ", " + "organizationId=" + organizationId + ", " + "memberId=" + memberId + ", " + "displayName=" + displayName + ", " + "email=" + email + ", " + "status=" + status + ", " + "primary=" + primary + "]"; }
}
