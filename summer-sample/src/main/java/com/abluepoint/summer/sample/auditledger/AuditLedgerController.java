package com.abluepoint.summer.sample.auditledger;
import java.util.function.Supplier;
import org.springframework.security.access.AccessDeniedException;
import com.abluepoint.summer.common.error.CommonErrorCode;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@PreAuthorize("isAuthenticated()")
class AuditLedgerController {
    private final AuditLedgerService service;
    private final RequestIdAccessor requestId;

    AuditLedgerController(AuditLedgerService service, RequestIdAccessor requestId) {
        this.service = service;
        this.requestId = requestId;
    }

    @PostMapping(value = "/internal/v1/audit-events", consumes = MediaType.APPLICATION_JSON_VALUE)
    @RawResponse
    ResponseEntity<ApiResponse<AuditEventView>> append(
            @RequestBody AuditEventAppendRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            Authentication auth) {
        return response(HttpStatus.CREATED, preserveAccessDenied(() -> service.append(request, key, rid(), auth)));
    }

    @PostMapping(value = "/internal/v1/mcp/invocations/{invocationId}:complete", consumes = MediaType.APPLICATION_JSON_VALUE)
    @RawResponse
    ResponseEntity<ApiResponse<McpInvocationView>> complete(
            @PathVariable String invocationId,
            @RequestBody CompletionRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            Authentication auth) {
        return response(HttpStatus.OK, preserveAccessDenied(() -> service.complete(invocationId, request, key, rid(), auth)));
    }

    @PostMapping(value = "/internal/v1/mcp/invocations/{invocationId}:reconcile", consumes = MediaType.APPLICATION_JSON_VALUE)
    @RawResponse
    ResponseEntity<ApiResponse<McpInvocationView>> reconcile(
            @PathVariable String invocationId,
            @RequestBody ReconciliationRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            Authentication auth) {
        return response(HttpStatus.OK, preserveAccessDenied(() -> service.reconcile(invocationId, request, key, rid(), auth)));
    }

    @PostMapping("/api/v1/enterprises/{enterpriseId}/mcp-invocations/{invocationId}:mark-unknown")
    @RawResponse
    ResponseEntity<ApiResponse<McpInvocationView>> markUnknown(
            @PathVariable String enterpriseId,
            @PathVariable String invocationId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            Authentication auth) {
        return response(HttpStatus.OK, preserveAccessDenied(() -> service.markUnknown(enterpriseId, invocationId, key, rid(), auth)));
    }

    @GetMapping("/api/v1/enterprises/{enterpriseId}/audit-events")
    @RawResponse
    ResponseEntity<ApiResponse<List<AuditEventView>>> events(
            @PathVariable String enterpriseId,
            @RequestParam(required = false) String initiator,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String effectiveSubject,
            @RequestParam(required = false) String agentInstanceId,
            @RequestParam(required = false) String toolId,
            @RequestParam(required = false) String decision,
            @RequestParam(required = false) String resultStatus,
            @RequestParam(required = false) String invocationId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            Authentication auth) {
        Instant fromTime = from == null || from.isBlank() ? null : Instant.parse(from);
        Instant toTime = to == null || to.isBlank() ? null : Instant.parse(to);
        return response(HttpStatus.OK, preserveAccessDenied(() -> service.events(enterpriseId, new AuditQuery(initiator, actor, effectiveSubject,
                agentInstanceId, toolId, decision, resultStatus, invocationId, eventType, fromTime, toTime), auth)));
    }

    @GetMapping("/api/v1/enterprises/{enterpriseId}/audit-events:query")
    @RawResponse
    ResponseEntity<ApiResponse<AuditEventPage>> query(
            @PathVariable String enterpriseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String initiator,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String effectiveSubject,
            @RequestParam(required = false) String agentInstanceId,
            @RequestParam(required = false) String toolId,
            @RequestParam(required = false) String decision,
            @RequestParam(required = false) String resultStatus,
            @RequestParam(required = false) String invocationId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            Authentication auth) {
        AuditQuery query = new AuditQuery(initiator, actor, effectiveSubject, agentInstanceId, toolId,
                decision, resultStatus, invocationId, eventType, parseInstant(from), parseInstant(to), requestId, traceId);
        return response(HttpStatus.OK, preserveAccessDenied(() -> service.queryEvents(enterpriseId, query, page, size, auth)));
    }

    @GetMapping(value = "/api/v1/enterprises/{enterpriseId}/audit-events:export", produces = "text/csv")
    @RawResponse
    ResponseEntity<?> export(
            @PathVariable String enterpriseId,
            @RequestParam(required = false) String initiator,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String effectiveSubject,
            @RequestParam(required = false) String agentInstanceId,
            @RequestParam(required = false) String toolId,
            @RequestParam(required = false) String decision,
            @RequestParam(required = false) String resultStatus,
            @RequestParam(required = false) String invocationId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            Authentication auth) {
        AuditQuery query = new AuditQuery(initiator, actor, effectiveSubject, agentInstanceId, toolId,
                decision, resultStatus, invocationId, eventType, parseInstant(from), parseInstant(to), requestId, traceId);
        return ResponseEntity.ok().header("X-Request-Id", rid())
                .header("Content-Disposition", "attachment; filename=audit-events.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(preserveAccessDenied(() -> service.exportEvents(enterpriseId, query, auth)));
    }

    @GetMapping("/api/v1/enterprises/{enterpriseId}/audit-events/{eventId}")
    @RawResponse
    ResponseEntity<ApiResponse<AuditEventView>> event(
            @PathVariable String enterpriseId, @PathVariable String eventId, Authentication auth) {
        return response(HttpStatus.OK, preserveAccessDenied(() -> service.event(enterpriseId, eventId, auth)));
    }

    @GetMapping("/api/v1/enterprises/{enterpriseId}/mcp-invocations")
    @RawResponse
    ResponseEntity<ApiResponse<List<McpInvocationView>>> invocations(
            @PathVariable String enterpriseId,
            @RequestParam(required = false) String initiator,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String effectiveSubject,
            @RequestParam(required = false) String agentInstanceId,
            @RequestParam(required = false) String toolId,
            @RequestParam(required = false) String decision,
            @RequestParam(required = false) String resultStatus,
            @RequestParam(required = false) String invocationId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            Authentication auth) {
        Instant fromTime = from == null || from.isBlank() ? null : Instant.parse(from);
        Instant toTime = to == null || to.isBlank() ? null : Instant.parse(to);
        return response(HttpStatus.OK, preserveAccessDenied(() -> service.invocations(enterpriseId, new AuditQuery(initiator, actor, effectiveSubject,
                agentInstanceId, toolId, decision, resultStatus, invocationId, null, fromTime, toTime), auth)));
    }

    @GetMapping("/api/v1/enterprises/{enterpriseId}/mcp-invocations/{invocationId}")
    @RawResponse
    ResponseEntity<ApiResponse<McpInvocationView>> invocation(
            @PathVariable String enterpriseId, @PathVariable String invocationId, Authentication auth) {
        return response(HttpStatus.OK, preserveAccessDenied(() -> service.invocation(enterpriseId, invocationId, auth)));
    }

    @GetMapping("/api/v1/enterprises/{enterpriseId}/audit-anomalies")
    @RawResponse
    ResponseEntity<ApiResponse<List<AuditEventView>>> anomalies(
            @PathVariable String enterpriseId, Authentication auth) {
        return response(HttpStatus.OK, preserveAccessDenied(() -> service.anomalies(enterpriseId, auth)));
    }

    private String rid() {
        return requestId.currentRequestId();
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            throw SummerException.withMessage(AuditLedgerErrorCode.INVALID, "Invalid audit time range", exception);
        }
    }

    private <T> ResponseEntity<ApiResponse<T>> response(HttpStatus status, T value) {
        return ResponseEntity.status(status).header("X-Request-Id", rid()).body(ApiResponse.success(value, rid()));
    }

    /** Retains this application endpoint's original safe security message and native cause. */
    private <T> T preserveAccessDenied(Supplier<T> action) {
        try {
            return action.get();
        } catch (AccessDeniedException denied) {
            throw SummerException.withMessage(CommonErrorCode.FORBIDDEN, denied.getMessage(), denied);
        }
    }
}
