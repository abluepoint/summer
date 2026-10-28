package com.abluepoint.summer.sample.agent.api;

import java.util.List;
import com.abluepoint.summer.sample.agent.application.*;

record AgentInstanceRegisterRequest(
        String agentId,
        String runtimeId,
        String workloadIdentity,
        String attestationType,
        String attestationDigest,
        String environment,
        Long expiresInSeconds) {
    AgentInstanceRegisterCommand command() { return new AgentInstanceRegisterCommand(agentId, runtimeId, workloadIdentity, attestationType, attestationDigest, environment, expiresInSeconds); }
}
