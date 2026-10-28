package com.abluepoint.summer.sample.invitation.application;

import java.util.List;

public record InvitationIssueView(
        String invitationId,
        String enterpriseId,
        String organizationId,
        List<String> roleIds,
        List<String> capabilityCodes,
        String expiresAt,
        String state,
        String token,
        String revision
) {
    public InvitationIssueView {
        roleIds = List.copyOf(roleIds);
        capabilityCodes = List.copyOf(capabilityCodes);
    }

    @Override public String toString() { return "InvitationIssue[" + "invitationId=" + invitationId + ", " + "enterpriseId=" + enterpriseId + ", " + "organizationId=" + organizationId + ", " + "roleIds=" + roleIds + ", " + "capabilityCodes=" + capabilityCodes + ", " + "expiresAt=" + expiresAt + ", " + "state=" + state + ", " + "token=" + token + ", " + "revision=" + revision + "]"; }
}
