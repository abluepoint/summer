package com.abluepoint.summer.sample.connection;

import java.time.Duration;

public record McpProviderRequest(
        String remoteRequestRef,
        String sessionRef,
        String toolName,
        String schemaDigest,
        String argumentsDigest,
        Duration timeout,
        int maxRetries) {}
