package com.abluepoint.summer.sample.connection.api;
import com.abluepoint.summer.sample.connection.application.McpToolProjection;
import com.abluepoint.summer.sample.connection.application.McpServerProjection;
import com.abluepoint.summer.sample.connection.application.McpClientProjection;
import java.time.Instant;
import java.util.List;
import com.abluepoint.summer.sample.connection.application.CatalogSyncCommand;
public record CatalogSyncRequest(String enterpriseId, long connectionRevision, String catalogDigest, McpClientProjection mcpClient, McpServerProjection mcpServer, List<McpToolProjection> tools) {
 public CatalogSyncCommand toCommand() { return new CatalogSyncCommand(enterpriseId,connectionRevision,catalogDigest,mcpClient,mcpServer,tools); }
}
