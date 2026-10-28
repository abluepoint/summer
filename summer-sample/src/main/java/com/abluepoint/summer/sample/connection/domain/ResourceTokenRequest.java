package com.abluepoint.summer.sample.connection.domain;


@org.springframework.modulith.NamedInterface("execution")
public record ResourceTokenRequest(
        String enterpriseId,
        String connectionServiceId,
        String externalConnectionId,
        String resourceUri,
        String audience,
        String permitId) {}
