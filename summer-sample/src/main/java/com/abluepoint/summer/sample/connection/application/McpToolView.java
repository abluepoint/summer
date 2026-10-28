package com.abluepoint.summer.sample.connection.application;


import java.time.Instant;
import java.util.List;

public record McpToolView(String toolId, String externalToolId, String toolName, String schemaDigest, long catalogRevision, String riskLevel, String requiredScope, String requiredCapabilityCode, String status) {}
