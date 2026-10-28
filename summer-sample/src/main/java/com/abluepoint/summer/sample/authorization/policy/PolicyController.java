package com.abluepoint.summer.sample.authorization.policy;

import com.abluepoint.summer.common.response.api.ApiError;
import com.abluepoint.summer.common.response.api.ApiResponse;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@PreAuthorize("isAuthenticated()")
class PolicyController {
    private final PolicyService service;
    private final RequestIdAccessor requestId;

    PolicyController(PolicyService service, RequestIdAccessor requestId) {
        this.service = service;
        this.requestId = requestId;
    }

    @GetMapping("/api/v1/enterprises/{enterpriseId}/policies")
    @RawResponse
    ResponseEntity<ApiResponse<List<PolicyView>>> list(@PathVariable String enterpriseId, Authentication auth) {
        try {
            return body(HttpStatus.OK, service.list(enterpriseId, auth), null);
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping(value = "/api/v1/enterprises/{enterpriseId}/policies", consumes = MediaType.APPLICATION_JSON_VALUE)
    @RawResponse
    ResponseEntity<ApiResponse<PolicyView>> create(
            @PathVariable String enterpriseId,
            @RequestBody PolicyCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication auth) {
        try {
            PolicyView view = service.create(enterpriseId, request, idempotencyKey, rid(), auth);
            return body(HttpStatus.CREATED, view, view.version());
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/api/v1/enterprises/{enterpriseId}/policies/{policyId}")
    @RawResponse
    ResponseEntity<ApiResponse<PolicyDetailView>> detail(
            @PathVariable String enterpriseId, @PathVariable String policyId, Authentication auth) {
        try {
            PolicyDetailView view = service.detailBundle(enterpriseId, policyId, auth);
            return body(HttpStatus.OK, view, view.policy().version());
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/api/v1/enterprises/{enterpriseId}/policies/{policyId}/versions")
    @RawResponse
    ResponseEntity<ApiResponse<List<PolicyVersionView>>> versions(
            @PathVariable String enterpriseId, @PathVariable String policyId, Authentication auth) {
        try {
            return body(HttpStatus.OK, service.versions(enterpriseId, policyId, auth), null);
        } catch (Exception e) {
            return error(e);
        }
    }

    @PatchMapping(value = "/api/v1/enterprises/{enterpriseId}/policies/{policyId}/draft", consumes = MediaType.APPLICATION_JSON_VALUE)
    @RawResponse
    ResponseEntity<ApiResponse<PolicyView>> updateDraft(
            @PathVariable String enterpriseId,
            @PathVariable String policyId,
            @RequestBody PolicyDraftUpdateRequest request,
            @RequestHeader("If-Match") String ifMatch,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication auth) {
        try {
            PolicyView view = service.updateDraft(enterpriseId, policyId, request, ifMatch, idempotencyKey, rid(), auth);
            return body(HttpStatus.OK, view, view.version());
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/api/v1/enterprises/{enterpriseId}/policies/{policyId}:validate")
    @RawResponse
    ResponseEntity<ApiResponse<PolicyValidationView>> validate(
            @PathVariable String enterpriseId,
            @PathVariable String policyId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication auth) {
        try {
            return body(HttpStatus.OK, service.validate(enterpriseId, policyId, idempotencyKey, rid(), auth), null);
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/api/v1/enterprises/{enterpriseId}/policies/{policyId}:publish")
    @RawResponse
    ResponseEntity<ApiResponse<PolicyView>> publish(
            @PathVariable String enterpriseId,
            @PathVariable String policyId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication auth) {
        try {
            PolicyView view = service.publish(enterpriseId, policyId, ifMatch, idempotencyKey, rid(), auth);
            return body(HttpStatus.OK, view, view.version());
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/api/v1/enterprises/{enterpriseId}/policies/{policyId}:pause")
    @RawResponse
    ResponseEntity<ApiResponse<PolicyView>> pause(
            @PathVariable String enterpriseId,
            @PathVariable String policyId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication auth) {
        return lifecycle(enterpriseId, policyId, "PAUSED", ifMatch, idempotencyKey, auth);
    }

    @PostMapping("/api/v1/enterprises/{enterpriseId}/policies/{policyId}:revoke")
    @RawResponse
    ResponseEntity<ApiResponse<PolicyView>> revoke(
            @PathVariable String enterpriseId,
            @PathVariable String policyId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication auth) {
        return lifecycle(enterpriseId, policyId, "REVOKED", ifMatch, idempotencyKey, auth);
    }

    @PostMapping(value = "/api/v1/enterprises/{enterpriseId}/policies/{policyId}:rollback", consumes = MediaType.APPLICATION_JSON_VALUE)
    @RawResponse
    ResponseEntity<ApiResponse<PolicyView>> rollback(
            @PathVariable String enterpriseId,
            @PathVariable String policyId,
            @RequestBody PolicyRollbackRequest request,
            @RequestHeader("If-Match") String ifMatch,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication auth) {
        try {
            PolicyView view = service.rollback(enterpriseId, policyId, request, ifMatch, idempotencyKey, rid(), auth);
            return body(HttpStatus.OK, view, view.version());
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping(value = "/api/v1/enterprises/{enterpriseId}/policies/{policyId}:copy", consumes = MediaType.APPLICATION_JSON_VALUE)
    @RawResponse
    ResponseEntity<ApiResponse<PolicyView>> copy(
            @PathVariable String enterpriseId,
            @PathVariable String policyId,
            @RequestBody(required = false) PolicyCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication auth) {
        try {
            PolicyView view = service.copy(enterpriseId, policyId, request, idempotencyKey, rid(), auth);
            return body(HttpStatus.CREATED, view, view.version());
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/api/v1/enterprises/{enterpriseId}/policies/{policyId}/test-cases")
    @RawResponse
    ResponseEntity<ApiResponse<List<PolicyTestCaseView>>> testCases(
            @PathVariable String enterpriseId, @PathVariable String policyId, Authentication auth) {
        try {
            return body(HttpStatus.OK, service.testCases(enterpriseId, policyId, auth), null);
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping(value = "/api/v1/enterprises/{enterpriseId}/policies/{policyId}/test-cases", consumes = MediaType.APPLICATION_JSON_VALUE)
    @RawResponse
    ResponseEntity<ApiResponse<PolicyTestCaseView>> createTestCase(
            @PathVariable String enterpriseId,
            @PathVariable String policyId,
            @RequestBody PolicyTestCaseCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication auth) {
        try {
            return body(HttpStatus.CREATED, service.createTestCase(enterpriseId, policyId, request, idempotencyKey, rid(), auth), null);
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/api/v1/enterprises/{enterpriseId}/policies/{policyId}:test")
    @RawResponse
    ResponseEntity<ApiResponse<List<PolicyTestCaseView>>> runTests(
            @PathVariable String enterpriseId,
            @PathVariable String policyId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication auth) {
        try {
            return body(HttpStatus.OK, service.runTests(enterpriseId, policyId, idempotencyKey, rid(), auth), null);
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping(value = "/internal/v1/authorization/evaluate", consumes = MediaType.APPLICATION_JSON_VALUE)
    @RawResponse
    ResponseEntity<ApiResponse<PolicyEvaluationView>> evaluate(
            @RequestBody PolicyEvaluationRequest request, Authentication auth) {
        try {
            return body(HttpStatus.OK, service.evaluate(trustedIdentity(request, auth), auth), null);
        } catch (Exception e) {
            return error(e);
        }
    }

    private PolicyEvaluationRequest trustedIdentity(PolicyEvaluationRequest request, Authentication auth) {
        if (!(auth instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwt)) {
            throw new org.springframework.security.access.AccessDeniedException("Trusted execution identity required");
        }
        String subject = jwt.getName();
        String initiator = claim(jwt, "initiator", subject);
        String actor = claim(jwt, "actor", subject);
        String effectiveSubject = claim(jwt, "effectiveSubject", subject);
        if (request != null && (!matches(request.initiator(), initiator)
                || !matches(request.actor(), actor)
                || !matches(request.effectiveSubject(), effectiveSubject))) {
            throw new org.springframework.security.access.AccessDeniedException("Evaluation identity does not match trusted caller");
        }
        if (request == null) {
            throw new PolicyValidationException("Evaluation request is required");
        }
        return new PolicyEvaluationRequest(initiator, actor, effectiveSubject, request.enterpriseId(),
                request.resource(), request.action(), request.risk(), request.organizationId(), request.requestId());
    }

    private static String claim(org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwt,
                                String name, String fallback) {
        Object value = jwt.getToken().getClaims().get(name);
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    private static boolean matches(String supplied, String trusted) {
        return supplied == null || supplied.isBlank() || supplied.equals(trusted);
    }

    @GetMapping("/api/v1/enterprises/{enterpriseId}/policies/{policyId}/audit")
    @RawResponse
    ResponseEntity<ApiResponse<List<java.util.Map<String, Object>>>> audit(
            @PathVariable String enterpriseId, @PathVariable String policyId, Authentication auth) {
        try {
            return body(HttpStatus.OK, service.audit(enterpriseId, policyId, auth), null);
        } catch (Exception e) {
            return error(e);
        }
    }

    private ResponseEntity<ApiResponse<PolicyView>> lifecycle(
            String enterpriseId, String policyId, String state, String ifMatch, String idempotencyKey, Authentication auth) {
        try {
            PolicyView view = service.lifecycle(enterpriseId, policyId, state, ifMatch, idempotencyKey, rid(), auth);
            return body(HttpStatus.OK, view, view.version());
        } catch (Exception e) {
            return error(e);
        }
    }

    private String rid() {
        return requestId.currentRequestId();
    }

    private <T> ResponseEntity<ApiResponse<T>> body(HttpStatus status, T value, Long version) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status).header("X-Request-Id", rid());
        if (version != null) {
            builder.eTag("\"" + version + "\"");
        }
        return builder.body(ApiResponse.success(value, rid()));
    }

    private <T> ResponseEntity<ApiResponse<T>> error(Exception exception) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String code = "SUMMER_INTERNAL_ERROR";
        if (exception instanceof org.springframework.security.access.AccessDeniedException) {
            status = HttpStatus.FORBIDDEN;
            code = "SUMMER_FORBIDDEN";
        } else if (exception instanceof PolicyValidationException) {
            status = HttpStatus.BAD_REQUEST;
            code = "SUMMER_POLICY_INVALID";
        } else if (exception instanceof PolicyPreconditionException) {
            status = HttpStatus.PRECONDITION_FAILED;
            code = "SUMMER_PRECONDITION_FAILED";
        } else if (exception instanceof PolicyIdempotencyConflictException) {
            status = HttpStatus.CONFLICT;
            code = "SUMMER_IDEMPOTENCY_CONFLICT";
        } else if (exception instanceof PolicyConflictException) {
            status = HttpStatus.CONFLICT;
            code = "SUMMER_POLICY_CONFLICT";
        }
        return ResponseEntity.status(status).header("X-Request-Id", rid())
                .body(ApiResponse.failure(new ApiError(code, exception.getMessage(), List.of()), rid()));
    }
}
