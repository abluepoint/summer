package com.abluepoint.summer.sample.bff;

/** Fails closed until an application supplies a real Summer HTTP adapter. */
final class UnavailableSummerMcpClient implements SummerMcpClient {
    @Override
    public BffMcpInvocationResult invoke(String enterpriseId, OidcIdentity identity, BffMcpInvokeRequest request) {
        throw new SummerMcpUnavailableException("Summer MCP adapter is not configured");
    }
}

final class SummerMcpUnavailableException extends RuntimeException {
    SummerMcpUnavailableException(String message) {
        super(message);
    }
}
