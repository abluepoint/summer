package com.abluepoint.summer.sample.connection.application;


import java.time.Instant;
import java.util.List;

public record McpClientView(String externalRegistrationRef, String clientId, String clientName, String clientVersion, List<String> redirectUris, String authMethod, String registrationType) {}
