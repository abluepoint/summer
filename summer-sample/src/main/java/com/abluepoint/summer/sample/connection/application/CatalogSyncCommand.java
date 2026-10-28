package com.abluepoint.summer.sample.connection.application;


import java.time.Instant;
import java.util.List;

public record CatalogSyncCommand(String enterpriseId, long connectionRevision, String catalogDigest, McpClientProjection mcpClient, McpServerProjection mcpServer, List<McpToolProjection> tools) {}
