package com.abluepoint.summer.sample.task;

import com.abluepoint.summer.common.response.ApiError;
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

    @GetMapping
    @RawResponse
    @PreAuthorize("hasAuthority('SCOPE_enterprise.tasks.read')")
    ResponseEntity<ApiResponse<Map<String, Object>>> list(@PathVariable String enterpriseId) {
        return body(HttpStatus.OK, Map.of("items", service.list(enterpriseId)));
    }

    @GetMapping("/types")
    @RawResponse
    @PreAuthorize("hasAuthority('SCOPE_enterprise.tasks.read')")
    ResponseEntity<ApiResponse<List<TrustedTaskTypeView>>> types() { return body(HttpStatus.OK, service.types()); }

    @PostMapping
    @RawResponse
    @PreAuthorize("hasAuthority('SCOPE_enterprise.tasks.write')")
    ResponseEntity<ApiResponse<TaskView>> create(@PathVariable String enterpriseId, @RequestBody TaskCreateRequest request, Authentication authentication) {
        try { TaskView result = service.create(enterpriseId, request, rid(), authentication); return body(HttpStatus.CREATED, result, result.version()); }
        catch (TaskUntrustedTypeException e) { return failure(HttpStatus.BAD_REQUEST, "SUMMER_TASK_TYPE_UNTRUSTED", e.getMessage()); }
        catch (TaskValidationException e) { return failure(HttpStatus.BAD_REQUEST, "SUMMER_TASK_INVALID", e.getMessage()); }
        catch (TaskConflictException e) { return failure(HttpStatus.CONFLICT, "SUMMER_TASK_CONFLICT", e.getMessage()); }
    }

    @PatchMapping("/{taskId}")
    @RawResponse
    @PreAuthorize("hasAuthority('SCOPE_enterprise.tasks.write')")
    ResponseEntity<ApiResponse<TaskView>> update(@PathVariable String enterpriseId, @PathVariable String taskId,
                                                   @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                                   @RequestBody TaskUpdateRequest request, Authentication authentication) {
        try { TaskView result = service.update(enterpriseId, taskId, request, ifMatch, rid(), authentication); return body(HttpStatus.OK, result, result.version()); }
        catch (TaskNotFoundException e) { return failure(HttpStatus.NOT_FOUND, "SUMMER_NOT_FOUND", e.getMessage()); }
        catch (TaskPreconditionException e) { return failure(HttpStatus.PRECONDITION_FAILED, "SUMMER_PRECONDITION_FAILED", e.getMessage()); }
        catch (TaskValidationException e) { return failure(HttpStatus.BAD_REQUEST, "SUMMER_TASK_INVALID", e.getMessage()); }
        catch (TaskConflictException e) { return failure(HttpStatus.CONFLICT, "SUMMER_TASK_CONFLICT", e.getMessage()); }
    }

    @PostMapping("/{taskId}:pause")
    @RawResponse @PreAuthorize("hasAuthority('SCOPE_enterprise.tasks.write')")
    ResponseEntity<ApiResponse<TaskView>> pause(@PathVariable String enterpriseId, @PathVariable String taskId,
                                                 @RequestHeader(value = "If-Match", required = false) String ifMatch, Authentication authentication) {
        return status(enterpriseId, taskId, TaskStatus.PAUSED, ifMatch, authentication);
    }

    @PostMapping("/{taskId}:resume")
    @RawResponse @PreAuthorize("hasAuthority('SCOPE_enterprise.tasks.write')")
    ResponseEntity<ApiResponse<TaskView>> resume(@PathVariable String enterpriseId, @PathVariable String taskId,
                                                  @RequestHeader(value = "If-Match", required = false) String ifMatch, Authentication authentication) {
        return status(enterpriseId, taskId, TaskStatus.ACTIVE, ifMatch, authentication);
    }

    @PostMapping("/{taskId}:trigger")
    @RawResponse @PreAuthorize("hasAuthority('SCOPE_enterprise.tasks.write')")
    ResponseEntity<ApiResponse<Map<String, String>>> trigger(@PathVariable String enterpriseId, @PathVariable String taskId,
                                                               Authentication authentication) {
        try { service.trigger(enterpriseId, taskId, rid(), authentication); return body(HttpStatus.ACCEPTED, Map.of("taskId", taskId, "status", "TRIGGERED")); }
        catch (TaskNotFoundException e) { return failure(HttpStatus.NOT_FOUND, "SUMMER_NOT_FOUND", e.getMessage()); }
        catch (TaskConflictException e) { return failure(HttpStatus.CONFLICT, "SUMMER_TASK_CONFLICT", e.getMessage()); }
    }

    @GetMapping("/{taskId}/executions")
    @RawResponse @PreAuthorize("hasAuthority('SCOPE_enterprise.tasks.read')")
    ResponseEntity<ApiResponse<TaskExecutionPage>> executions(@PathVariable String enterpriseId, @PathVariable String taskId) {
        try { return body(HttpStatus.OK, service.executions(enterpriseId, taskId)); }
        catch (TaskNotFoundException e) { return failure(HttpStatus.NOT_FOUND, "SUMMER_NOT_FOUND", e.getMessage()); }
    }

    private ResponseEntity<ApiResponse<TaskView>> status(String enterpriseId, String taskId, TaskStatus status, String ifMatch, Authentication auth) {
        try { TaskView result = service.setStatus(enterpriseId, taskId, status, ifMatch, rid(), auth); return body(HttpStatus.OK, result, result.version()); }
        catch (TaskNotFoundException e) { return failure(HttpStatus.NOT_FOUND, "SUMMER_NOT_FOUND", e.getMessage()); }
        catch (TaskPreconditionException e) { return failure(HttpStatus.PRECONDITION_FAILED, "SUMMER_PRECONDITION_FAILED", e.getMessage()); }
        catch (TaskConflictException e) { return failure(HttpStatus.CONFLICT, "SUMMER_TASK_CONFLICT", e.getMessage()); }
    }

    private String rid() { return requestId.currentRequestId(); }
    private <T> ResponseEntity<ApiResponse<T>> body(HttpStatus status, T data) { return body(status, data, null); }
    private <T> ResponseEntity<ApiResponse<T>> body(HttpStatus status, T data, Long version) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status).header("X-Request-Id", rid());
        if (version != null) builder.header("ETag", "\"" + version + "\"");
        return builder.body(ApiResponse.success(data, rid()));
    }
    private <T> ResponseEntity<ApiResponse<T>> failure(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).header("X-Request-Id", rid()).body(ApiResponse.failure(new ApiError(code, message, List.of()), rid()));
    }
}
