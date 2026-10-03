package com.abluepoint.summer.sample.connection;

public record McpProviderResult(
        String status,
        int attempts,
        String resultDigest,
        String remoteRequestRef,
        long latencyMs) {}
