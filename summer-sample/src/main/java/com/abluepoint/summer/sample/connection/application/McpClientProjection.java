package com.abluepoint.summer.sample.connection.application;


import java.time.Instant;
import java.util.List;

public record McpClientProjection(String externalRegistrationRef, String clientId, String clientName, String clientVersion, List<String> redirectUris, String authMethod, String registrationType) {}
