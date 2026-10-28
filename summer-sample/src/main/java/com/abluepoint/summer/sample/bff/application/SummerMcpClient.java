package com.abluepoint.summer.sample.bff.application;

import com.abluepoint.summer.sample.bff.application.BffMcpInvokeCommand;
import com.abluepoint.summer.sample.bff.domain.OidcIdentity;


/** Narrow BFF adapter for the Summer MCP decision/permit/execution APIs. */
public interface SummerMcpClient {
    BffMcpInvocationResult invoke(String enterpriseId, OidcIdentity identity, BffMcpInvokeCommand request);
}
