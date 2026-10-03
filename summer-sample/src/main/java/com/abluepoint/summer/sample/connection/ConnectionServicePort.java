package com.abluepoint.summer.sample.connection;

/** Narrow seam owned by the independent ConnectionService data plane. */
public interface ConnectionServicePort {
    ResourceToken acquireResourceToken(ResourceTokenRequest request);

    ConnectionSession openSession(ConnectionSessionRequest request);

    McpProviderResult invoke(McpProviderRequest request);

    void publish(ExecutionStatusReceipt receipt);
}
