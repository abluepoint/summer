package com.abluepoint.summer.sample.agent.api;

import java.util.List;
import com.abluepoint.summer.sample.agent.application.*;

record AgentCreateRequest(String name, String code, String description, String ownerMemberId, String runtimeType) {
    AgentCreateCommand command() { return new AgentCreateCommand(name, code, description, ownerMemberId, runtimeType); }
}
