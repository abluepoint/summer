package com.abluepoint.summer.sample.task;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;


@RestController
class TaskProgressController {
    private final TaskProgressEventService progress;
    private final RequestIdAccessor requestId;
    private final com.abluepoint.summer.sample.identity.SessionStreamAccess sessionAccess;

    TaskProgressController(TaskProgressEventService progress, RequestIdAccessor requestId,
                           com.abluepoint.summer.sample.identity.SessionStreamAccess sessionAccess) {
        this.progress = progress; this.requestId = requestId;
        this.sessionAccess = sessionAccess;
    }

    @GetMapping(value = "/api/v1/enterprises/{enterpriseId}/tasks/{taskId}/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RawResponse
    @PreAuthorize("@businessMethodAccess.require(authentication, 'enterprise.tasks.read') or (hasAuthority('SCOPE_enterprise.tasks.read'))")
    ResponseEntity<?> subscribe(@PathVariable String enterpriseId, @PathVariable String taskId,
                                @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
                                Authentication authentication, jakarta.servlet.http.HttpServletRequest request) {
        try {
            long id = parseLastEventId(lastEventId);
            return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM)
                    .header("Cache-Control", "no-cache")
                    .header("X-Request-Id", requestId.currentRequestId())
                    .body(progress.subscribe(enterpriseId, taskId, id, authentication,
                            sessionAccess.capture(request, authentication)));
        } catch (IllegalArgumentException exception) {
            throw new SummerException(TaskErrorCode.SSE_LAST_EVENT_ID, exception);
        }
    }

    private static long parseLastEventId(String value) {
        if (value == null || value.isBlank()) return 0;
        long parsed = Long.parseLong(value.trim());
        if (parsed < 0) throw new IllegalArgumentException();
        return parsed;
    }

}
