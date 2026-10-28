package com.abluepoint.summer.sample.invitation.application;

import java.util.List;

public record InvitationView(
        String invitationId,
        String enterpriseId,
        String inviteeEmail,
        String organizationId,
        List<String> roleIds,
        List<String> capabilityCodes,
        String expiresAt,
        String state,
        String memberId,
        String revision
) {
    public InvitationView {
        roleIds = List.copyOf(roleIds);
        capabilityCodes = List.copyOf(capabilityCodes);
    }
}
