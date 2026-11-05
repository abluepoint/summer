package com.abluepoint.summer.sample.dictionary;

import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;


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
    @PreAuthorize("@businessMethodAccess.require(authentication, 'platform.dictionary.read') or (hasAuthority('SCOPE_platform.dictionary.read'))")
    ResponseEntity<?> subscribe(@PathVariable String enterpriseId, @PathVariable String code,
                                @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
                                Authentication authentication) {
        try {
            return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM)
                    .header("Cache-Control", "no-cache")
                    .header("X-Request-Id", requestId.currentRequestId())
                    .body(notifications.subscribe(enterpriseId, code, parseLastEventId(lastEventId), authentication));
        } catch (AccessDeniedException exception) {
            throw SummerException.withMessage(CommonErrorCode.FORBIDDEN, "Dictionary capability denied", exception);
        } catch (IllegalArgumentException exception) {
            throw SummerException.withMessage(DictionaryNotificationErrorCode.LAST_EVENT_ID, "Last-Event-ID must be a non-negative integer", exception);
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

}
