package com.abluepoint.summer.sample.agent.api;

import java.util.List;
import com.abluepoint.summer.sample.agent.application.*;

record AgentUpdateRequest(String name, String description, String ownerMemberId, String runtimeType) {
    AgentUpdateCommand command() { return new AgentUpdateCommand(name, description, ownerMemberId, runtimeType); }
}
