package com.abluepoint.summer.sample.invitation.application;

import java.util.List;

public record JoinApplicationView(
        String applicationId,
        String enterpriseId,
        String applicantEmail,
        String applicantName,
        String organizationId,
        List<String> capabilityCodes,
        List<String> materials,
        String state,
        String memberId,
        String revision
) {
    public JoinApplicationView {
        capabilityCodes = List.copyOf(capabilityCodes);
        materials = List.copyOf(materials);
    }
}
