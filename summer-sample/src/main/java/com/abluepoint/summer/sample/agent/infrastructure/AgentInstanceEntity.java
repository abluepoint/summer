package com.abluepoint.summer.sample.agent.infrastructure;

import com.abluepoint.summer.sample.agent.application.*;
import java.util.*;
import java.time.Instant;

public final class AgentInstanceEntity {
        public final String enterpriseId, id, agentId, runtimeId, workloadIdentity, attestationType, attestationDigest, environment;
        public String status; public final Instant startedAt; public Instant lastSeenAt, expiresAt; public long version;
        public AgentInstanceEntity(String enterpriseId, String id, String agentId, String runtimeId, String workloadIdentity,
                            String attestationType, String attestationDigest, String environment, String status,
                            Instant startedAt, Instant lastSeenAt, Instant expiresAt, long version) {
            this.enterpriseId = enterpriseId; this.id = id; this.agentId = agentId; this.runtimeId = runtimeId;
            this.workloadIdentity = workloadIdentity; this.attestationType = attestationType; this.attestationDigest = attestationDigest;
            this.environment = environment; this.status = status; this.startedAt = startedAt; this.lastSeenAt = lastSeenAt;
            this.expiresAt = expiresAt; this.version = version;
        }
    }
