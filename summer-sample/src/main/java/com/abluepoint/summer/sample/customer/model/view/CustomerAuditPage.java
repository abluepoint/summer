package com.abluepoint.summer.sample.customer.model.view;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record CustomerAuditPage(List<CustomerAuditEventView> items, int page, int size,
                         long totalElements, int totalPages) {}
