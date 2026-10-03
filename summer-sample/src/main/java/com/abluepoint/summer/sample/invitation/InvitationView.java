package com.abluepoint.summer.sample.invitation;

import java.util.List;

record InvitationView(
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
    InvitationView {
        roleIds = List.copyOf(roleIds);
        capabilityCodes = List.copyOf(capabilityCodes);
    }
}
