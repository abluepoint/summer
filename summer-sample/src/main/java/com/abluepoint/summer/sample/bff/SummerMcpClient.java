package com.abluepoint.summer.sample.bff;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collections;

/** Narrow BFF adapter for the Summer MCP decision/permit/execution APIs. */
interface SummerMcpClient {
    BffMcpInvocationResult invoke(String enterpriseId, OidcIdentity identity, BffMcpInvokeRequest request);
}

record BffMcpInvokeRequest(String connectionId, String tool, Map<String, Object> arguments, String agentId) {
    BffMcpInvokeRequest {
        arguments = arguments == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
    }
}

record BffMcpInvocationIdentity(String initiator, String actor, String effectiveSubject, String transport) {}

record BffMcpInvocationResult(BffMcpInvocationIdentity identity, String resultStatus, String invocationId) {
    static BffMcpInvocationResult deferred(OidcIdentity identity) {
        return new BffMcpInvocationResult(
                new BffMcpInvocationIdentity(identity.subject(), identity.subject(), identity.subject(), "connection-service"),
                "DEFERRED_TO_SUMMER", null);
    }
}
