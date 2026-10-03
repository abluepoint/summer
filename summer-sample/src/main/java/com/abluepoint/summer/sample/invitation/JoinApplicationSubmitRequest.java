package com.abluepoint.summer.sample.invitation;

import java.util.List;

record JoinApplicationSubmitRequest(
        String applicantEmail,
        String applicantName,
        String organizationId,
        List<String> capabilityCodes,
        List<String> materials
) {
    JoinApplicationSubmitRequest {
        capabilityCodes = capabilityCodes == null ? List.of() : List.copyOf(capabilityCodes);
        materials = materials == null ? List.of() : List.copyOf(materials);
    }
}
