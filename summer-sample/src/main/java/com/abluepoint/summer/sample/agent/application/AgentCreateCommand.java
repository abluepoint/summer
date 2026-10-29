package com.abluepoint.summer.sample.agent.application;

import java.util.List;

public record AgentCreateCommand(String name, String code, String description, String ownerMemberId, String runtimeType) {
    // Preserve the pre-migration fingerprint label without rewriting field values.
    @Override public String toString() {
        return "AgentCreateRequest[name=" + name + ", code=" + code + ", description=" + description + ", ownerMemberId=" + ownerMemberId + ", runtimeType=" + runtimeType + "]";
    }
}
