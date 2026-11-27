package com.abluepoint.summer.sample.task;

import com.abluepoint.summer.mvc.annotation.Field;

import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/enterprises/{enterpriseId}/tasks")
class TaskSchedulerController {
    private final TaskSchedulerService service;
    private final RequestIdAccessor requestId;
    TaskSchedulerController(TaskSchedulerService service, RequestIdAccessor requestId) { this.service = service; this.requestId = requestId; }

    @Field(param = "enterpriseId", style = "requestTextStyle")
    @GetMapping
    @RawResponse
    @PreAuthorize("@businessMethodAccess.require(authentication, 'enterprise.tasks.read') or (hasAuthority('SCOPE_enterprise.tasks.read'))")
    ResponseEntity<ApiResponse<Map<String, Object>>> list(@PathVariable String enterpriseId) {
        return body(HttpStatus.OK, Map.of("items", service.list(enterpriseId)));
    }

    @Field(param = "enterpriseId", style = "requestTextStyle")
    @GetMapping("/types")
    @RawResponse
    @PreAuthorize("@businessMethodAccess.require(authentication, 'enterprise.tasks.read') or (hasAuthority('SCOPE_enterprise.tasks.read'))")
    ResponseEntity<ApiResponse<List<TrustedTaskTypeView>>> types() { return body(HttpStatus.OK, service.types()); }

    @PostMapping
    @RawResponse
    @PreAuthorize("@businessMethodAccess.require(authentication, 'enterprise.tasks.write') or (hasAuthority('SCOPE_enterprise.tasks.write'))")
    ResponseEntity<ApiResponse<TaskView>> create(@PathVariable String enterpriseId, @RequestBody TaskCreateRequest request, Authentication authentication) {
        TaskView result = service.create(enterpriseId, request, rid(), authentication); return body(HttpStatus.CREATED, result, result.version());
    }

    @PatchMapping("/{taskId}")
    @RawResponse
    @PreAuthorize("@businessMethodAccess.require(authentication, 'enterprise.tasks.write') or (hasAuthority('SCOPE_enterprise.tasks.write'))")
    ResponseEntity<ApiResponse<TaskView>> update(@PathVariable String enterpriseId, @PathVariable String taskId,
                                                   @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                                   @RequestBody TaskUpdateRequest request, Authentication authentication) {
        TaskView result = service.update(enterpriseId, taskId, request, ifMatch, rid(), authentication); return body(HttpStatus.OK, result, result.version());
    }

    @Field(param = "enterpriseId", style = "requestTextStyle")
    @Field(param = "taskId", style = "requestTextStyle")
    @PostMapping("/{taskId}:pause")
    @RawResponse @PreAuthorize("@businessMethodAccess.require(authentication, 'enterprise.tasks.write') or (hasAuthority('SCOPE_enterprise.tasks.write'))")
    ResponseEntity<ApiResponse<TaskView>> pause(@PathVariable String enterpriseId, @PathVariable String taskId,
                                                 @RequestHeader(value = "If-Match", required = false) String ifMatch, Authentication authentication) {
        return status(enterpriseId, taskId, TaskStatus.PAUSED, ifMatch, authentication);
    }

    @Field(param = "enterpriseId", style = "requestTextStyle")
    @Field(param = "taskId", style = "requestTextStyle")
    @PostMapping("/{taskId}:resume")
    @RawResponse @PreAuthorize("@businessMethodAccess.require(authentication, 'enterprise.tasks.write') or (hasAuthority('SCOPE_enterprise.tasks.write'))")
    ResponseEntity<ApiResponse<TaskView>> resume(@PathVariable String enterpriseId, @PathVariable String taskId,
                                                  @RequestHeader(value = "If-Match", required = false) String ifMatch, Authentication authentication) {
        return status(enterpriseId, taskId, TaskStatus.ACTIVE, ifMatch, authentication);
    }

    @Field(param = "enterpriseId", style = "requestTextStyle")
    @Field(param = "taskId", style = "requestTextStyle")
    @PostMapping("/{taskId}:trigger")
    @RawResponse @PreAuthorize("@businessMethodAccess.require(authentication, 'enterprise.tasks.write') or (hasAuthority('SCOPE_enterprise.tasks.write'))")
    ResponseEntity<ApiResponse<Map<String, String>>> trigger(@PathVariable String enterpriseId, @PathVariable String taskId,
                                                               @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                               Authentication authentication) {
        service.trigger(enterpriseId, taskId, idempotencyKey, rid(), authentication); return body(HttpStatus.ACCEPTED, Map.of("taskId", taskId, "status", "TRIGGERED"));
    }

    @Field(param = "enterpriseId", style = "requestTextStyle")
    @Field(param = "taskId", style = "requestTextStyle")
    @GetMapping("/{taskId}/executions")
    @RawResponse @PreAuthorize("@businessMethodAccess.require(authentication, 'enterprise.tasks.read') or (hasAuthority('SCOPE_enterprise.tasks.read'))")
    ResponseEntity<ApiResponse<TaskExecutionPage>> executions(@PathVariable String enterpriseId, @PathVariable String taskId) {
        return body(HttpStatus.OK, service.executions(enterpriseId, taskId));
    }

    private ResponseEntity<ApiResponse<TaskView>> status(String enterpriseId, String taskId, TaskStatus status, String ifMatch, Authentication auth) {
        TaskView result = service.setStatus(enterpriseId, taskId, status, ifMatch, rid(), auth); return body(HttpStatus.OK, result, result.version());
    }

    private String rid() { return requestId.currentRequestId(); }
    private <T> ResponseEntity<ApiResponse<T>> body(HttpStatus status, T data) { return body(status, data, null); }
    private <T> ResponseEntity<ApiResponse<T>> body(HttpStatus status, T data, Long version) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status).header("X-Request-Id", rid());
        if (version != null) builder.header("ETag", "\"" + version + "\"");
        return builder.body(ApiResponse.success(data, rid()));
    }
}
