package com.abluepoint.summer.sample.dictionary;

import com.abluepoint.summer.common.response.api.ApiError;
import com.abluepoint.summer.common.response.api.ApiResponse;
import com.abluepoint.summer.mvc.response.api.RawResponse;
import com.abluepoint.summer.mvc.request.api.RequestIdAccessor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
class DictionaryNotificationController {
    private final DictionaryNotificationService notifications;
    private final RequestIdAccessor requestId;

    DictionaryNotificationController(DictionaryNotificationService notifications, RequestIdAccessor requestId) {
        this.notifications = notifications;
        this.requestId = requestId;
    }

    @GetMapping(value = "/api/v1/enterprises/{enterpriseId}/dictionaries/{code}/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RawResponse
    @com.abluepoint.summer.sample.resource.ResourcePermission("platform.dictionary.read")
    @PreAuthorize("hasAuthority('SCOPE_platform.dictionary.read')")
    ResponseEntity<?> subscribe(@PathVariable String enterpriseId, @PathVariable String code,
                                @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
                                Authentication authentication) {
        try {
            return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM)
                    .header("Cache-Control", "no-cache")
                    .header("X-Request-Id", requestId.currentRequestId())
                    .body(notifications.subscribe(enterpriseId, code, parseLastEventId(lastEventId), authentication));
        } catch (DictionaryNotificationNotFoundException exception) {
            return failure(HttpStatus.NOT_FOUND, "SUMMER_NOT_FOUND", "Dictionary was not found");
        } catch (DictionaryNotificationConnectionLimitException exception) {
            return failure(HttpStatus.TOO_MANY_REQUESTS, "SUMMER_DICTIONARY_SSE_LIMIT", "Too many dictionary notification connections");
        } catch (AccessDeniedException exception) {
            return failure(HttpStatus.FORBIDDEN, "SUMMER_FORBIDDEN", "Dictionary capability denied");
        } catch (IllegalArgumentException exception) {
            return failure(HttpStatus.BAD_REQUEST, "SUMMER_DICTIONARY_SSE_LAST_EVENT_ID", "Last-Event-ID must be a non-negative integer");
        }
    }

    private static long parseLastEventId(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            long parsed = Long.parseLong(value.trim());
            if (parsed < 0) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException();
        }
    }

    private ResponseEntity<ApiResponse<Void>> failure(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .header("X-Request-Id", requestId.currentRequestId())
                .body(ApiResponse.failure(new ApiError(code, message, List.of()), requestId.currentRequestId()));
    }
}
