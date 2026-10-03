package com.abluepoint.summer.sample.invitation;

import java.util.List;

record JoinApplicationView(
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
    JoinApplicationView {
        capabilityCodes = List.copyOf(capabilityCodes);
        materials = List.copyOf(materials);
    }
}
