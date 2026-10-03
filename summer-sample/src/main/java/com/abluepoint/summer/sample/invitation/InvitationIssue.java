package com.abluepoint.summer.sample.invitation;

import java.util.List;

record InvitationIssue(
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
    InvitationIssue {
        roleIds = List.copyOf(roleIds);
        capabilityCodes = List.copyOf(capabilityCodes);
    }
}
