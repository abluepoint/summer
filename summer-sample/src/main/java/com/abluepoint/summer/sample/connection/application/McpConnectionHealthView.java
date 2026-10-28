package com.abluepoint.summer.sample.connection.application;


import java.time.Instant;
import java.util.List;

public record McpConnectionHealthView(String enterpriseId, String connectionServiceId, String externalConnectionId, String status, long version, Instant lastSyncedAt) {}
