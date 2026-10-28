package com.abluepoint.summer.sample.customer.controller;

import com.abluepoint.summer.sample.customer.exception.CustomerAuditNotFoundException;
import com.abluepoint.summer.sample.customer.model.query.CustomerAuditQuery;
import com.abluepoint.summer.sample.customer.model.view.CustomerAuditEventView;
import com.abluepoint.summer.sample.customer.model.view.CustomerAuditPage;
import com.abluepoint.summer.sample.customer.service.CustomerAuditService;

import com.abluepoint.summer.common.response.api.ApiError;
import com.abluepoint.summer.common.response.api.ApiResponse;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/audit-events")
@PreAuthorize("hasAnyAuthority('SCOPE_enterprise.audit.read', 'SCOPE_customer.read')")
class CustomerAuditController {
    private final CustomerAuditService service;
    private final RequestIdAccessor requestId;

    CustomerAuditController(CustomerAuditService service, RequestIdAccessor requestId) {
        this.service = service;
        this.requestId = requestId;
    }

    @GetMapping
    @RawResponse
    ResponseEntity<ApiResponse<CustomerAuditPage>> search(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String principal,
            @RequestParam(required = false) String resource,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        if (page < 0 || size < 1 || size > 100)
            return failure(HttpStatus.BAD_REQUEST, "SUMMER_INVALID_ARGUMENT", "Invalid audit page");
        if (tenantId != null && !CustomerAuditService.TENANT_ID.equals(tenantId))
            return failure(HttpStatus.FORBIDDEN, "SUMMER_FORBIDDEN", "Audit tenant access denied");
        try {
            Instant fromTime = from == null || from.isBlank() ? null : Instant.parse(from);
            Instant toTime = to == null || to.isBlank() ? null : Instant.parse(to);
            CustomerAuditPage value = service.search(new CustomerAuditQuery(principal, resource, action, result,
                    requestId, fromTime, toTime), PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt")));
            return body(HttpStatus.OK, value);
        } catch (IllegalArgumentException exception) {
            return failure(HttpStatus.BAD_REQUEST, "SUMMER_INVALID_ARGUMENT", "Invalid audit query");
        }
    }

    @GetMapping("/{eventId}")
    @RawResponse
    ResponseEntity<ApiResponse<CustomerAuditEventView>> detail(@PathVariable String eventId) {
        try {
            return body(HttpStatus.OK, service.find(eventId));
        } catch (CustomerAuditNotFoundException exception) {
            return failure(HttpStatus.NOT_FOUND, "SUMMER_NOT_FOUND", exception.getMessage());
        }
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @RawResponse
    ResponseEntity<?> export(
            @RequestParam(required = false) String principal,
            @RequestParam(required = false) String resource,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        if (tenantId != null && !CustomerAuditService.TENANT_ID.equals(tenantId))
            return failure(HttpStatus.FORBIDDEN, "SUMMER_FORBIDDEN", "Audit tenant access denied");
        try {
            Instant fromTime = from == null || from.isBlank() ? null : Instant.parse(from);
            Instant toTime = to == null || to.isBlank() ? null : Instant.parse(to);
            String csv = service.export(new CustomerAuditQuery(principal, resource, action, result,
                    requestId, fromTime, toTime));
            return ResponseEntity.ok().header("X-Request-Id", requestId())
                    .header("Content-Disposition", "attachment; filename=audit-events.csv")
                    .contentType(org.springframework.http.MediaType.parseMediaType("text/csv"))
                    .body(csv);
        } catch (RuntimeException exception) {
            return failure(HttpStatus.BAD_REQUEST, "SUMMER_INVALID_ARGUMENT", "Invalid audit query");
        }
    }

    private <T> ResponseEntity<ApiResponse<T>> body(HttpStatus status, T value) {
        return ResponseEntity.status(status).header("X-Request-Id", requestId.currentRequestId())
                .body(ApiResponse.success(value, requestId.currentRequestId()));
    }

    private <T> ResponseEntity<ApiResponse<T>> failure(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).header("X-Request-Id", requestId.currentRequestId())
                .body(ApiResponse.failure(new ApiError(code, message, List.of()), requestId.currentRequestId()));
    }

    private String requestId() { return this.requestId.currentRequestId(); }
}
