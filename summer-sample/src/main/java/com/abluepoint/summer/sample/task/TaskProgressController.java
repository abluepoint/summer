package com.abluepoint.summer.sample.task;

import com.abluepoint.summer.common.response.ApiError;
import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
class TaskProgressController {
    private final TaskProgressEventService progress;
    private final RequestIdAccessor requestId;

    TaskProgressController(TaskProgressEventService progress, RequestIdAccessor requestId) {
        this.progress = progress; this.requestId = requestId;
    }

    @GetMapping(value = "/api/v1/enterprises/{enterpriseId}/tasks/{taskId}/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RawResponse
    @PreAuthorize("hasAuthority('SCOPE_enterprise.tasks.read')")
    ResponseEntity<?> subscribe(@PathVariable String enterpriseId, @PathVariable String taskId,
                                @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        try {
            long id = parseLastEventId(lastEventId);
            return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM)
                    .header("Cache-Control", "no-cache")
                    .header("X-Request-Id", requestId.currentRequestId())
                    .body(progress.subscribe(enterpriseId, taskId, id));
        } catch (TaskNotFoundException exception) {
            return failure(HttpStatus.NOT_FOUND, "SUMMER_NOT_FOUND", "Task was not found");
        } catch (TaskProgressConnectionLimitException exception) {
            return failure(HttpStatus.TOO_MANY_REQUESTS, "SUMMER_TASK_SSE_LIMIT", "Too many task progress connections");
        } catch (IllegalArgumentException exception) {
            return failure(HttpStatus.BAD_REQUEST, "SUMMER_TASK_SSE_LAST_EVENT_ID", "Last-Event-ID must be a non-negative integer");
        }
    }

    private static long parseLastEventId(String value) {
        if (value == null || value.isBlank()) return 0;
        long parsed = Long.parseLong(value.trim());
        if (parsed < 0) throw new IllegalArgumentException();
        return parsed;
    }

    private ResponseEntity<ApiResponse<Void>> failure(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).header("X-Request-Id", requestId.currentRequestId())
                .body(ApiResponse.failure(new ApiError(code, message, List.of()), requestId.currentRequestId()));
    }
}
