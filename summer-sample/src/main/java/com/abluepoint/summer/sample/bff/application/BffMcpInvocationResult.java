package com.abluepoint.summer.sample.bff.application;

import com.abluepoint.summer.sample.bff.domain.OidcIdentity;


public record BffMcpInvocationResult(BffMcpInvocationIdentity identity, String resultStatus, String invocationId) {
    public static BffMcpInvocationResult deferred(OidcIdentity identity) {
        return new BffMcpInvocationResult(
                new BffMcpInvocationIdentity(identity.subject(), identity.subject(), identity.subject(), "connection-service"),
                "DEFERRED_TO_SUMMER", null);
    }
}
