package com.abluepoint.summer.sample.invitation.application;
import java.util.*;

public record JoinApplicationSubmitCommand(
        String applicantEmail,
        String applicantName,
        String organizationId,
        List<String> capabilityCodes,
        List<String> materials
) {
    public JoinApplicationSubmitCommand {
        capabilityCodes = capabilityCodes == null ? List.of() : List.copyOf(capabilityCodes);
        materials = materials == null ? List.of() : List.copyOf(materials);
    }

    @Override public String toString() { return "JoinApplicationSubmitRequest[" + "applicantEmail=" + applicantEmail + ", " + "applicantName=" + applicantName + ", " + "organizationId=" + organizationId + ", " + "capabilityCodes=" + capabilityCodes + ", " + "materials=" + materials + "]"; }
}
