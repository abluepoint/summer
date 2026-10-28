package com.abluepoint.summer.sample.agent.api;

import java.util.List;
import com.abluepoint.summer.sample.agent.application.*;

record AgentInstanceHeartbeatRequest(Long extendSeconds) {
    AgentInstanceHeartbeatCommand command() { return new AgentInstanceHeartbeatCommand(extendSeconds); }
}
