package com.abluepoint.summer.sample.authorization.application.view;

import java.time.Instant;

public record DataScopeAuditView(
        String auditId,
        String enterpriseId,
        String resource,
        long version,
        String checksum,
        String action,
        String principal,
        String requestId,
        Instant occurredAt) {}
