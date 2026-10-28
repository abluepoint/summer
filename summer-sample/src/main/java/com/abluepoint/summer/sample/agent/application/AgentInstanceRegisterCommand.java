package com.abluepoint.summer.sample.agent.application;

import java.util.List;

public record AgentInstanceRegisterCommand(
        String agentId,
        String runtimeId,
        String workloadIdentity,
        String attestationType,
        String attestationDigest,
        String environment,
        Long expiresInSeconds) {}
