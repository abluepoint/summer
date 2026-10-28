package com.abluepoint.summer.sample.connection.domain;


import java.time.Duration;

@org.springframework.modulith.NamedInterface("execution")
public record McpProviderRequest(
        String remoteRequestRef,
        String sessionRef,
        String toolName,
        String schemaDigest,
        String argumentsDigest,
        Duration timeout,
        int maxRetries) {}
