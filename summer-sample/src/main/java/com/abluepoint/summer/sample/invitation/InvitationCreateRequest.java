package com.abluepoint.summer.sample.invitation;

import java.util.List;

record InvitationCreateRequest(
        String inviteeEmail,
        String organizationId,
        List<String> roleIds,
        List<String> capabilityCodes,
        Long expiresInSeconds
) {
    InvitationCreateRequest {
        roleIds = roleIds == null ? List.of() : List.copyOf(roleIds);
        capabilityCodes = capabilityCodes == null ? List.of() : List.copyOf(capabilityCodes);
    }
}
