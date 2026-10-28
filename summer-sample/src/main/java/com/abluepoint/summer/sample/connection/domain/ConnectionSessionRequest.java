package com.abluepoint.summer.sample.connection.domain;


@org.springframework.modulith.NamedInterface("execution")
public record ConnectionSessionRequest(
        String enterpriseId,
        String connectionServiceId,
        String externalConnectionId,
        String resourceUri,
        String tokenRef,
        String requestedSessionRef) {}
