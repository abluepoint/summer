package com.abluepoint.summer.sample.agent;
import java.util.function.Supplier;
import org.springframework.security.access.AccessDeniedException;
import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.common.error.CommonErrorCode;

import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@PreAuthorize("isAuthenticated()")
class AgentController {
    private final AgentService service;
    private final RequestIdAccessor requestId;

    AgentController(AgentService service, RequestIdAccessor requestId) { this.service = service; this.requestId = requestId; }

    @GetMapping("/api/v1/enterprises/{enterpriseId}/agents") @RawResponse
    ResponseEntity<ApiResponse<List<AgentView>>> list(@PathVariable String enterpriseId, Authentication auth) {
        return body(HttpStatus.OK, preserveAccessDenied(() -> service.list(enterpriseId, auth)), null);
    }

    @PostMapping(value = "/api/v1/enterprises/{enterpriseId}/agents", consumes = MediaType.APPLICATION_JSON_VALUE) @RawResponse
    ResponseEntity<ApiResponse<AgentView>> create(@PathVariable String enterpriseId, @RequestBody AgentCreateRequest request,
                                                   @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication auth) {
        AgentView view = preserveAccessDenied(() -> service.create(enterpriseId, request, key, rid(), auth)); return body(HttpStatus.CREATED, view, view.version());
    }

    @GetMapping("/api/v1/enterprises/{enterpriseId}/agents/{agentId}") @RawResponse
    ResponseEntity<ApiResponse<AgentView>> detail(@PathVariable String enterpriseId, @PathVariable String agentId, Authentication auth) {
        AgentView view = preserveAccessDenied(() -> service.detail(enterpriseId, agentId, auth)); return body(HttpStatus.OK, view, view.version());
    }

    @PatchMapping(value = "/api/v1/enterprises/{enterpriseId}/agents/{agentId}", consumes = MediaType.APPLICATION_JSON_VALUE) @RawResponse
    ResponseEntity<ApiResponse<AgentView>> update(@PathVariable String enterpriseId, @PathVariable String agentId,
                                                   @RequestBody AgentUpdateRequest request, @RequestHeader("If-Match") String ifMatch,
                                                   @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication auth) {
        AgentView view = preserveAccessDenied(() -> service.update(enterpriseId, agentId, request, ifMatch, key, rid(), auth)); return body(HttpStatus.OK, view, view.version());
    }

    @PostMapping("/api/v1/enterprises/{enterpriseId}/agents/{agentId}:enable") @RawResponse
    ResponseEntity<ApiResponse<AgentView>> enable(@PathVariable String enterpriseId, @PathVariable String agentId,
                                                  @RequestHeader("If-Match") String ifMatch, @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication auth) {
        return lifecycle(enterpriseId, agentId, "ACTIVE", ifMatch, key, auth);
    }

    @PostMapping("/api/v1/enterprises/{enterpriseId}/agents/{agentId}:disable") @RawResponse
    ResponseEntity<ApiResponse<AgentView>> disable(@PathVariable String enterpriseId, @PathVariable String agentId,
                                                   @RequestHeader("If-Match") String ifMatch, @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication auth) {
        return lifecycle(enterpriseId, agentId, "DISABLED", ifMatch, key, auth);
    }

    @GetMapping("/api/v1/enterprises/{enterpriseId}/agents/{agentId}/instances") @RawResponse
    ResponseEntity<ApiResponse<List<AgentInstanceView>>> instances(@PathVariable String enterpriseId, @PathVariable String agentId, Authentication auth) {
        return body(HttpStatus.OK, preserveAccessDenied(() -> service.instances(enterpriseId, agentId, auth)), null);
    }

    @PostMapping(value = "/internal/v1/agent-instances", consumes = MediaType.APPLICATION_JSON_VALUE) @RawResponse
    ResponseEntity<ApiResponse<AgentInstanceView>> register(@RequestBody AgentInstanceRegisterRequest request, Authentication auth) {
        AgentInstanceView view = preserveAccessDenied(() -> service.registerInstance(request, rid(), auth)); return body(HttpStatus.CREATED, view, view.version());
    }

    @PostMapping(value = "/internal/v1/agent-instances/{instanceId}:heartbeat", consumes = MediaType.APPLICATION_JSON_VALUE) @RawResponse
    ResponseEntity<ApiResponse<AgentInstanceView>> heartbeat(@PathVariable String instanceId, @RequestBody(required = false) AgentInstanceHeartbeatRequest request, Authentication auth) {
        AgentInstanceView view = preserveAccessDenied(() -> service.heartbeat(instanceId, request, rid(), auth)); return body(HttpStatus.OK, view, view.version());
    }

    @PostMapping("/api/v1/enterprises/{enterpriseId}/agent-instances/{instanceId}:revoke") @RawResponse
    ResponseEntity<ApiResponse<AgentInstanceView>> revoke(@PathVariable String enterpriseId, @PathVariable String instanceId,
                                                           @RequestHeader("If-Match") String ifMatch, @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication auth) {
        AgentInstanceView view = preserveAccessDenied(() -> service.revoke(enterpriseId, instanceId, ifMatch, key, rid(), auth)); return body(HttpStatus.OK, view, view.version());
    }

    @GetMapping("/api/v1/enterprises/{enterpriseId}/agents/{agentId}/audit") @RawResponse
    ResponseEntity<ApiResponse<List<AgentAuditView>>> audit(@PathVariable String enterpriseId, @PathVariable String agentId, Authentication auth) {
        return body(HttpStatus.OK, preserveAccessDenied(() -> service.audit(enterpriseId, agentId, auth)), null);
    }

    private ResponseEntity<ApiResponse<AgentView>> lifecycle(String enterpriseId, String agentId, String state, String ifMatch, String key, Authentication auth) {
        AgentView view = preserveAccessDenied(() -> service.setStatus(enterpriseId, agentId, state, ifMatch, key, rid(), auth)); return body(HttpStatus.OK, view, view.version());
    }
    private String rid() { return requestId.currentRequestId(); }
    private <T> ResponseEntity<ApiResponse<T>> body(HttpStatus status, T value, Long version) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status).header("X-Request-Id", rid());
        if (version != null) builder.eTag("\"" + version + "\"");
        return builder.body(ApiResponse.success(value, rid()));
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
