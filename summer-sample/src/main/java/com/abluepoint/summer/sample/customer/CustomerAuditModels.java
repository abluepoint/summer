package com.abluepoint.summer.sample.customer;

import java.time.Instant;
import java.util.List;
import java.util.Map;

record CustomerAuditEventView(String eventId, String tenantId, String organizationId, String principal,
                              String resource, String action, String result, String requestId,
                              Map<String, Object> detail, Instant occurredAt) {}

record CustomerAuditPage(List<CustomerAuditEventView> items, int page, int size,
                         long totalElements, int totalPages) {}
