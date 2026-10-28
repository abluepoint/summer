package com.abluepoint.summer.sample.connection.domain;
import com.abluepoint.summer.sample.connection.application.ConnectionService;

/** Narrow seam owned by the independent ConnectionService data plane. */
@org.springframework.modulith.NamedInterface("execution")
public interface ConnectionServicePort {
    ResourceToken acquireResourceToken(ResourceTokenRequest request);

    ConnectionSession openSession(ConnectionSessionRequest request);

    McpProviderResult invoke(McpProviderRequest request);

    void publish(ExecutionStatusReceipt receipt);
}
