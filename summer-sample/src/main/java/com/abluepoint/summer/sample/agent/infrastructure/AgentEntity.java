package com.abluepoint.summer.sample.agent.infrastructure;

import com.abluepoint.summer.sample.agent.application.*;
import java.util.*;
import java.time.Instant;

public final class AgentEntity {
        public final String enterpriseId, id, principalId, agentCode;
        public String name, description, ownerMemberId, runtimeType, status;
        public long version;
        public final Map<String, AgentInstanceEntity> instances = new LinkedHashMap<>();
        public AgentEntity(String enterpriseId, String id, String principalId, String agentCode, String name, String description,
                    String ownerMemberId, String runtimeType, String status, long version) {
            this.enterpriseId = enterpriseId; this.id = id; this.principalId = principalId; this.agentCode = agentCode;
            this.name = name; this.description = description; this.ownerMemberId = ownerMemberId;
            this.runtimeType = runtimeType; this.status = status; this.version = version;
        }
    }
