package com.abluepoint.summer.sample.connection.application;


import java.time.Instant;
import java.util.List;

public record McpServerProjection(String name, String resourceUri, String audience, String authorizationServerIssuer, String metadataVersion) {}
