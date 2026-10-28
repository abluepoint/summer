package com.abluepoint.summer.sample.invitation.api;
import com.abluepoint.summer.sample.invitation.application.InvitationCreateCommand;

import java.util.List;

public record InvitationCreateRequest(
        String inviteeEmail,
        String organizationId,
        List<String> roleIds,
        List<String> capabilityCodes,
        Long expiresInSeconds
) {
    public InvitationCreateRequest {
        roleIds = roleIds == null ? List.of() : List.copyOf(roleIds);
        capabilityCodes = capabilityCodes == null ? List.of() : List.copyOf(capabilityCodes);
    }

    InvitationCreateCommand command() { return new InvitationCreateCommand(inviteeEmail, organizationId, roleIds, capabilityCodes, expiresInSeconds); }
}
