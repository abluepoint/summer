package com.abluepoint.summer.sample.agent.application;

import java.util.List;

public record AgentUpdateCommand(String name, String description, String ownerMemberId, String runtimeType) {
    // Preserve the pre-migration fingerprint label without rewriting field values.
    @Override public String toString() {
        return "AgentUpdateRequest[name=" + name + ", description=" + description + ", ownerMemberId=" + ownerMemberId + ", runtimeType=" + runtimeType + "]";
    }
}
