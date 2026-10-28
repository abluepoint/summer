package com.abluepoint.summer.sample.bff.application;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collections;

public record BffMcpInvokeCommand(String connectionId, String tool, Map<String, Object> arguments, String agentId) {
    public BffMcpInvokeCommand {
        arguments = arguments == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
    }
}
