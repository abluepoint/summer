package com.abluepoint.summer.sample.bff.api;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collections;

public record BffMcpInvokeRequest(String connectionId, String tool, Map<String, Object> arguments, String agentId) {
    public BffMcpInvokeRequest {
        arguments = arguments == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
    }
}
