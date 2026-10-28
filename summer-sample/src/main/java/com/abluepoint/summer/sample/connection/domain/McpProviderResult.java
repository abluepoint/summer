package com.abluepoint.summer.sample.connection.domain;


@org.springframework.modulith.NamedInterface("execution")
public record McpProviderResult(
        String status,
        int attempts,
        String resultDigest,
        String remoteRequestRef,
        long latencyMs) {}
