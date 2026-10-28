package com.abluepoint.summer.sample.invitation.api;
import com.abluepoint.summer.sample.invitation.application.JoinApplicationSupplementCommand;

import java.util.List;

public record JoinApplicationSupplementRequest(List<String> materials) {
    public JoinApplicationSupplementRequest {
        materials = materials == null ? List.of() : List.copyOf(materials);
    }

    JoinApplicationSupplementCommand command() { return new JoinApplicationSupplementCommand(materials); }
}
