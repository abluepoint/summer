package com.abluepoint.summer.sample.agent;

import com.abluepoint.summer.common.response.api.ApiError;
import com.abluepoint.summer.common.response.api.ApiResponse;
import com.abluepoint.summer.mvc.response.api.RawResponse;
import com.abluepoint.summer.mvc.request.api.RequestIdAccessor;
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
        try { return body(HttpStatus.OK, service.list(enterpriseId, auth), null); } catch (Exception e) { return error(e); }
    }

    @PostMapping(value = "/api/v1/enterprises/{enterpriseId}/agents", consumes = MediaType.APPLICATION_JSON_VALUE) @RawResponse
    ResponseEntity<ApiResponse<AgentView>> create(@PathVariable String enterpriseId, @RequestBody AgentCreateRequest request,
                                                   @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication auth) {
        try { AgentView view = service.create(enterpriseId, request, key, rid(), auth); return body(HttpStatus.CREATED, view, view.version()); }
        catch (Exception e) { return error(e); }
    }

    @GetMapping("/api/v1/enterprises/{enterpriseId}/agents/{agentId}") @RawResponse
    ResponseEntity<ApiResponse<AgentView>> detail(@PathVariable String enterpriseId, @PathVariable String agentId, Authentication auth) {
        try { AgentView view = service.detail(enterpriseId, agentId, auth); return body(HttpStatus.OK, view, view.version()); }
        catch (Exception e) { return error(e); }
    }

    @PatchMapping(value = "/api/v1/enterprises/{enterpriseId}/agents/{agentId}", consumes = MediaType.APPLICATION_JSON_VALUE) @RawResponse
    ResponseEntity<ApiResponse<AgentView>> update(@PathVariable String enterpriseId, @PathVariable String agentId,
                                                   @RequestBody AgentUpdateRequest request, @RequestHeader("If-Match") String ifMatch,
                                                   @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication auth) {
        try { AgentView view = service.update(enterpriseId, agentId, request, ifMatch, key, rid(), auth); return body(HttpStatus.OK, view, view.version()); }
        catch (Exception e) { return error(e); }
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
        try { return body(HttpStatus.OK, service.instances(enterpriseId, agentId, auth), null); } catch (Exception e) { return error(e); }
    }

    @PostMapping(value = "/internal/v1/agent-instances", consumes = MediaType.APPLICATION_JSON_VALUE) @RawResponse
    ResponseEntity<ApiResponse<AgentInstanceView>> register(@RequestBody AgentInstanceRegisterRequest request, Authentication auth) {
        try { AgentInstanceView view = service.registerInstance(request, rid(), auth); return body(HttpStatus.CREATED, view, view.version()); }
        catch (Exception e) { return error(e); }
    }

    @PostMapping(value = "/internal/v1/agent-instances/{instanceId}:heartbeat", consumes = MediaType.APPLICATION_JSON_VALUE) @RawResponse
    ResponseEntity<ApiResponse<AgentInstanceView>> heartbeat(@PathVariable String instanceId, @RequestBody(required = false) AgentInstanceHeartbeatRequest request, Authentication auth) {
        try { AgentInstanceView view = service.heartbeat(instanceId, request, rid(), auth); return body(HttpStatus.OK, view, view.version()); }
        catch (Exception e) { return error(e); }
    }

    @PostMapping("/api/v1/enterprises/{enterpriseId}/agent-instances/{instanceId}:revoke") @RawResponse
    ResponseEntity<ApiResponse<AgentInstanceView>> revoke(@PathVariable String enterpriseId, @PathVariable String instanceId,
                                                           @RequestHeader("If-Match") String ifMatch, @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication auth) {
        try { AgentInstanceView view = service.revoke(enterpriseId, instanceId, ifMatch, key, rid(), auth); return body(HttpStatus.OK, view, view.version()); }
        catch (Exception e) { return error(e); }
    }

    @GetMapping("/api/v1/enterprises/{enterpriseId}/agents/{agentId}/audit") @RawResponse
    ResponseEntity<ApiResponse<List<AgentAuditView>>> audit(@PathVariable String enterpriseId, @PathVariable String agentId, Authentication auth) {
        try { return body(HttpStatus.OK, service.audit(enterpriseId, agentId, auth), null); } catch (Exception e) { return error(e); }
    }

    private ResponseEntity<ApiResponse<AgentView>> lifecycle(String enterpriseId, String agentId, String state, String ifMatch, String key, Authentication auth) {
        try { AgentView view = service.setStatus(enterpriseId, agentId, state, ifMatch, key, rid(), auth); return body(HttpStatus.OK, view, view.version()); }
        catch (Exception e) { return error(e); }
    }
    private String rid() { return requestId.currentRequestId(); }
    private <T> ResponseEntity<ApiResponse<T>> body(HttpStatus status, T value, Long version) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status).header("X-Request-Id", rid());
        if (version != null) builder.eTag("\"" + version + "\"");
        return builder.body(ApiResponse.success(value, rid()));
    }
    private <T> ResponseEntity<ApiResponse<T>> error(Exception exception) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR; String code = "SUMMER_INTERNAL_ERROR";
        if (exception instanceof org.springframework.security.access.AccessDeniedException) { status = HttpStatus.FORBIDDEN; code = "SUMMER_FORBIDDEN"; }
        else if (exception instanceof AgentValidationException) { status = HttpStatus.BAD_REQUEST; code = "SUMMER_AGENT_INVALID"; }
        else if (exception instanceof AgentPreconditionException) { status = HttpStatus.PRECONDITION_FAILED; code = "SUMMER_PRECONDITION_FAILED"; }
        else if (exception instanceof AgentIdempotencyConflictException || exception instanceof AgentConflictException) { status = HttpStatus.CONFLICT; code = exception instanceof AgentIdempotencyConflictException ? "SUMMER_IDEMPOTENCY_CONFLICT" : "SUMMER_AGENT_CONFLICT"; }
        return ResponseEntity.status(status).header("X-Request-Id", rid()).body(ApiResponse.failure(new ApiError(code, exception.getMessage(), List.of()), rid()));
    }
}
