package com.abluepoint.summer.sample.identity.application.view;

import java.time.Instant;

public record SessionIndexView(
        String sessionRef,
        String principal,
        String clientId,
        String status,
        Instant createdAt,
        Instant lastSeenAt,
        Instant expiresAt,
        boolean current
) {}
