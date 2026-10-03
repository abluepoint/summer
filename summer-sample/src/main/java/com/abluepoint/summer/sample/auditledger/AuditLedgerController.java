package com.abluepoint.summer.sample.auditledger;

import com.abluepoint.summer.common.response.ApiError;
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
        try {
            return response(HttpStatus.CREATED, service.append(request, key, rid(), auth));
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping(value = "/internal/v1/mcp/invocations/{invocationId}:complete", consumes = MediaType.APPLICATION_JSON_VALUE)
    @RawResponse
    ResponseEntity<ApiResponse<McpInvocationView>> complete(
            @PathVariable String invocationId,
            @RequestBody CompletionRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            Authentication auth) {
        try {
            return response(HttpStatus.OK, service.complete(invocationId, request, key, rid(), auth));
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping(value = "/internal/v1/mcp/invocations/{invocationId}:reconcile", consumes = MediaType.APPLICATION_JSON_VALUE)
    @RawResponse
    ResponseEntity<ApiResponse<McpInvocationView>> reconcile(
            @PathVariable String invocationId,
            @RequestBody ReconciliationRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            Authentication auth) {
        try {
            return response(HttpStatus.OK, service.reconcile(invocationId, request, key, rid(), auth));
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/api/v1/enterprises/{enterpriseId}/mcp-invocations/{invocationId}:mark-unknown")
    @RawResponse
    ResponseEntity<ApiResponse<McpInvocationView>> markUnknown(
            @PathVariable String enterpriseId,
            @PathVariable String invocationId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            Authentication auth) {
        try {
            return response(HttpStatus.OK, service.markUnknown(enterpriseId, invocationId, key, rid(), auth));
        } catch (Exception e) {
            return error(e);
        }
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
        try {
            Instant fromTime = from == null || from.isBlank() ? null : Instant.parse(from);
            Instant toTime = to == null || to.isBlank() ? null : Instant.parse(to);
            return response(HttpStatus.OK, service.events(enterpriseId, new AuditQuery(initiator, actor, effectiveSubject,
                    agentInstanceId, toolId, decision, resultStatus, invocationId, eventType, fromTime, toTime), auth));
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/api/v1/enterprises/{enterpriseId}/audit-events/{eventId}")
    @RawResponse
    ResponseEntity<ApiResponse<AuditEventView>> event(
            @PathVariable String enterpriseId, @PathVariable String eventId, Authentication auth) {
        try {
            return response(HttpStatus.OK, service.event(enterpriseId, eventId, auth));
        } catch (Exception e) {
            return error(e);
        }
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
        try {
            Instant fromTime = from == null || from.isBlank() ? null : Instant.parse(from);
            Instant toTime = to == null || to.isBlank() ? null : Instant.parse(to);
            return response(HttpStatus.OK, service.invocations(enterpriseId, new AuditQuery(initiator, actor, effectiveSubject,
                    agentInstanceId, toolId, decision, resultStatus, invocationId, null, fromTime, toTime), auth));
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/api/v1/enterprises/{enterpriseId}/mcp-invocations/{invocationId}")
    @RawResponse
    ResponseEntity<ApiResponse<McpInvocationView>> invocation(
            @PathVariable String enterpriseId, @PathVariable String invocationId, Authentication auth) {
        try {
            return response(HttpStatus.OK, service.invocation(enterpriseId, invocationId, auth));
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/api/v1/enterprises/{enterpriseId}/audit-anomalies")
    @RawResponse
    ResponseEntity<ApiResponse<List<AuditEventView>>> anomalies(
            @PathVariable String enterpriseId, Authentication auth) {
        try {
            return response(HttpStatus.OK, service.anomalies(enterpriseId, auth));
        } catch (Exception e) {
            return error(e);
        }
    }

    private String rid() {
        return requestId.currentRequestId();
    }

    private <T> ResponseEntity<ApiResponse<T>> response(HttpStatus status, T value) {
        return ResponseEntity.status(status).header("X-Request-Id", rid()).body(ApiResponse.success(value, rid()));
    }

    private <T> ResponseEntity<ApiResponse<T>> error(Exception exception) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String code = "SUMMER_INTERNAL_ERROR";
        if (exception instanceof org.springframework.security.access.AccessDeniedException) {
            status = HttpStatus.FORBIDDEN;
            code = "SUMMER_FORBIDDEN";
        } else if (exception instanceof AuditLedgerValidationException) {
            status = HttpStatus.BAD_REQUEST;
            code = "IAM_AUDIT_INVALID";
        } else if (exception instanceof AuditLedgerIdempotencyConflictException) {
            status = HttpStatus.CONFLICT;
            code = "SUMMER_IDEMPOTENCY_CONFLICT";
        } else if (exception instanceof AuditEventImmutableException) {
            status = HttpStatus.CONFLICT;
            code = "IAM_AUDIT_EVENT_IMMUTABLE";
        } else if (exception instanceof AuditContradictoryReceiptException) {
            status = HttpStatus.CONFLICT;
            code = "IAM_AUDIT_CONTRADICTORY_RECEIPT";
        } else if (exception instanceof AuditLedgerConflictException) {
            status = HttpStatus.CONFLICT;
            code = "IAM_AUDIT_CONFLICT";
        }
        return ResponseEntity.status(status).header("X-Request-Id", rid())
                .body(ApiResponse.failure(new ApiError(code, exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage(), List.of()), rid()));
    }
}
