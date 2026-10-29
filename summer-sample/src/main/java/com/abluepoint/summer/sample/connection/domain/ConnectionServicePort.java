package com.abluepoint.summer.sample.connection.domain;

/** Narrow seam owned by the independent ConnectionService data plane. */
@org.springframework.modulith.NamedInterface("execution")
public interface ConnectionServicePort {
    ResourceToken acquireResourceToken(ResourceTokenRequest request);

    ConnectionSession openSession(ConnectionSessionRequest request);

    McpProviderResult invoke(McpProviderRequest request);

    void publish(ExecutionStatusReceipt receipt);
}
