package com.abluepoint.summer.sample.connection.application;


import java.time.Instant;
import java.util.List;

public record McpConnectionCreateCommand(String connectionServiceId, String externalConnectionId) {}
