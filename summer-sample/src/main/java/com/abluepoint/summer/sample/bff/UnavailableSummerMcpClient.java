package com.abluepoint.summer.sample.bff;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.error.SampleTransportErrorCode;
/** Fails closed until an application supplies a real Summer HTTP adapter. */
final class UnavailableSummerMcpClient implements SummerMcpClient {
    @Override
    public BffMcpInvocationResult invoke(String enterpriseId, OidcIdentity identity, BffMcpInvokeRequest request) {
        throw SummerException.withMessage(SampleTransportErrorCode.BFF_UNAVAILABLE, "Summer MCP adapter is not configured");
    }
}

