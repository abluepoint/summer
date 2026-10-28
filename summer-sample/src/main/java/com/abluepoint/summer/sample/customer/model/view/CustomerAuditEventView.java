package com.abluepoint.summer.sample.customer.model.view;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record CustomerAuditEventView(String eventId, String tenantId, String organizationId, String principal,
                              String resource, String action, String result, String requestId, String traceId,
                              Map<String, Object> detail, Instant occurredAt) {}
