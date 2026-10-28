package com.abluepoint.summer.sample.agent.application;

import java.util.List;
import java.time.Instant;

public record AgentView(
        String enterpriseId,
        String agentId,
        String principalId,
        String agentCode,
        String name,
        String description,
        String ownerMemberId,
        String runtimeType,
        String status,
        long version,
        int activeInstanceCount) {}
