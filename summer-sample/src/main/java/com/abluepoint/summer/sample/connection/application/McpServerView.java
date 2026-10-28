package com.abluepoint.summer.sample.connection.application;


import java.time.Instant;
import java.util.List;

public record McpServerView(String serverId, String name, String resourceUri, String audience, String authorizationServerIssuer, String metadataVersion) {}
