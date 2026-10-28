package com.abluepoint.summer.sample.agent.application;

import java.util.List;
import java.time.Instant;

public record AgentAuditView(
        String action,
        String enterpriseId,
        String agentId,
        String instanceId,
        String principal,
        String requestId,
        Instant occurredAt) {}
