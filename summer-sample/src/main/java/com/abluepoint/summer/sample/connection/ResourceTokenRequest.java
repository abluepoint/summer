package com.abluepoint.summer.sample.connection;

public record ResourceTokenRequest(
        String enterpriseId,
        String connectionServiceId,
        String externalConnectionId,
        String resourceUri,
        String audience,
        String permitId) {}
