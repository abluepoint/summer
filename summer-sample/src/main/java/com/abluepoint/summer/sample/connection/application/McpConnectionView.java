package com.abluepoint.summer.sample.connection.application;


import java.time.Instant;
import java.util.List;

public record McpConnectionView(String enterpriseId, String connectionServiceId, String externalConnectionId, long connectionRevision, String catalogDigest, McpClientView mcpClient, McpServerView mcpServer, List<McpToolView> tools, String status, long version, Instant lastSyncedAt) {}
