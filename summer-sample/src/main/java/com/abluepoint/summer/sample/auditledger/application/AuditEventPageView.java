package com.abluepoint.summer.sample.auditledger.application;


import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AuditEventPageView(
        List<AuditEventView> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {}
