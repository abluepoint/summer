package com.abluepoint.summer.sample.identity.application.view;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SessionAuthenticationSnapshot(
        String principal,
        Map<String, Object> claims,
        List<String> authorities,
        Instant issuedAt,
        Instant expiresAt
) {}
