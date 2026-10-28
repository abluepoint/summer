package com.abluepoint.summer.sample.agent.application;

import java.util.List;
import java.time.Instant;

public record AgentInstanceView(
        String enterpriseId,
        String instanceId,
        String agentId,
        String runtimeId,
        String workloadIdentity,
        String attestationType,
        String attestationDigest,
        String environment,
        String status,
        Instant startedAt,
        Instant lastSeenAt,
        Instant expiresAt,
        long version) {}
