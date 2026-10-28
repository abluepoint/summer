package com.abluepoint.summer.sample.agent.infrastructure;

import com.abluepoint.summer.sample.agent.application.*;
import java.util.*;
import java.time.Instant;

public final class AgentStore {
    public final Map<String, AgentEntity> agents = new LinkedHashMap<>();
    public final Map<String, AgentReceipt> receipts = new HashMap<>();
    public final Map<String, List<AgentAuditView>> audits = new HashMap<>();
}
