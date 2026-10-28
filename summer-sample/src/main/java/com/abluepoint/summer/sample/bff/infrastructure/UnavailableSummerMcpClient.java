package com.abluepoint.summer.sample.bff.infrastructure;

import com.abluepoint.summer.sample.bff.application.BffMcpInvokeCommand;
import com.abluepoint.summer.sample.bff.application.BffMcpInvocationResult;
import com.abluepoint.summer.sample.bff.application.SummerMcpClient;
import com.abluepoint.summer.sample.bff.application.SummerMcpUnavailableException;
import com.abluepoint.summer.sample.bff.domain.OidcIdentity;

/** Fails closed until an application supplies a real Summer HTTP adapter. */
public final class UnavailableSummerMcpClient implements SummerMcpClient {
    @Override
    public BffMcpInvocationResult invoke(String enterpriseId, OidcIdentity identity, BffMcpInvokeCommand request) {
        throw new SummerMcpUnavailableException("Summer MCP adapter is not configured");
    }
}
