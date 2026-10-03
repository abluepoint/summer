package com.abluepoint.summer.sample.connection;

public record ConnectionSessionRequest(
        String enterpriseId,
        String connectionServiceId,
        String externalConnectionId,
        String resourceUri,
        String tokenRef,
        String requestedSessionRef) {}
