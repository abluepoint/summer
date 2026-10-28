package com.abluepoint.summer.sample.invitation.api;
import com.abluepoint.summer.sample.invitation.application.JoinApplicationSubmitCommand;

import java.util.List;

public record JoinApplicationSubmitRequest(
        String applicantEmail,
        String applicantName,
        String organizationId,
        List<String> capabilityCodes,
        List<String> materials
) {
    public JoinApplicationSubmitRequest {
        capabilityCodes = capabilityCodes == null ? List.of() : List.copyOf(capabilityCodes);
        materials = materials == null ? List.of() : List.copyOf(materials);
    }

    JoinApplicationSubmitCommand command() { return new JoinApplicationSubmitCommand(applicantEmail, applicantName, organizationId, capabilityCodes, materials); }
}
