package com.abluepoint.summer.sample.invitation.application;
import java.util.*;

public record InvitationCreateCommand(
        String inviteeEmail,
        String organizationId,
        List<String> roleIds,
        List<String> capabilityCodes,
        Long expiresInSeconds
) {
    public InvitationCreateCommand {
        roleIds = roleIds == null ? List.of() : List.copyOf(roleIds);
        capabilityCodes = capabilityCodes == null ? List.of() : List.copyOf(capabilityCodes);
    }

    @Override public String toString() { return "InvitationCreateRequest[" + "inviteeEmail=" + inviteeEmail + ", " + "organizationId=" + organizationId + ", " + "roleIds=" + roleIds + ", " + "capabilityCodes=" + capabilityCodes + ", " + "expiresInSeconds=" + expiresInSeconds + "]"; }
}
