package com.abluepoint.summer.sample.connection;

import java.time.Instant;
import java.util.List;

record ConnectionServiceCreateRequest(String serviceCode, String name, String permitAudience, String protocolVersion) {}
record ConnectionServiceView(String serviceId, String serviceCode, String name, String permitAudience, String protocolVersion, String status, long version, Instant lastSeenAt) {}
record McpClientProjectionRequest(String externalRegistrationRef, String clientId, String clientName, String clientVersion, List<String> redirectUris, String authMethod, String registrationType) {}
record McpServerProjectionRequest(String name, String resourceUri, String audience, String authorizationServerIssuer, String metadataVersion) {}
record McpToolProjectionRequest(String externalToolId, String toolName, String schemaDigest, long catalogRevision, String riskLevel, String requiredScope, String requiredCapabilityCode) {}
record CatalogSyncRequest(String enterpriseId, long connectionRevision, String catalogDigest, McpClientProjectionRequest mcpClient, McpServerProjectionRequest mcpServer, List<McpToolProjectionRequest> tools) {}
record McpClientView(String externalRegistrationRef, String clientId, String clientName, String clientVersion, List<String> redirectUris, String authMethod, String registrationType) {}
record McpServerView(String serverId, String name, String resourceUri, String audience, String authorizationServerIssuer, String metadataVersion) {}
record McpToolView(String toolId, String externalToolId, String toolName, String schemaDigest, long catalogRevision, String riskLevel, String requiredScope, String requiredCapabilityCode, String status) {}
record McpConnectionView(String enterpriseId, String connectionServiceId, String externalConnectionId, long connectionRevision, String catalogDigest, McpClientView mcpClient, McpServerView mcpServer, List<McpToolView> tools, String status, long version, Instant lastSyncedAt) {}
record McpConnectionCreateRequest(String connectionServiceId, String externalConnectionId) {}
record McpConnectionHealthView(String enterpriseId, String connectionServiceId, String externalConnectionId, String status, long version, Instant lastSyncedAt) {}
