package com.abluepoint.summer.sample.customer.model.query;

import java.time.Instant;

public record CustomerAuditQuery(String principal, String resource, String action, String result,
                          String requestId, Instant from, Instant to) {}
