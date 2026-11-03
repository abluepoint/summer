package com.abluepoint.summer.sample.realtime;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.common.error.CommonErrorCode;

import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/enterprises/{enterpriseId}/messages")
class RealtimeMessageController {
    private final RealtimeMessageService service;
    private final RealtimeWebSocketHub hub;
    private final RequestIdAccessor requestId;

    RealtimeMessageController(RealtimeMessageService service, RealtimeWebSocketHub hub, RequestIdAccessor requestId) {
        this.service = service; this.hub = hub; this.requestId = requestId;
    }

    @PostMapping
    @RawResponse
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('SCOPE_enterprise.messages.write')")
    ResponseEntity<ApiResponse<RealtimeMessageView>> send(@PathVariable String enterpriseId,
                                                            @RequestBody RealtimeMessageCreateRequest request,
                                                            Authentication authentication) {
        try {
            RealtimeMessageView result = service.send(enterpriseId, request, authentication);
            return body(result.duplicate() ? HttpStatus.OK : HttpStatus.CREATED, result);
        }
        catch (AccessDeniedException e) { throw SummerException.withMessage(CommonErrorCode.FORBIDDEN, "Message capability denied", e); }
    }

    @GetMapping
    @RawResponse
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('SCOPE_enterprise.messages.read')")
    ResponseEntity<ApiResponse<RealtimeMessagePage>> list(@PathVariable String enterpriseId,
                                                            @RequestParam(required = false) String after,
                                                            Authentication authentication) {
        try { return body(HttpStatus.OK, service.list(enterpriseId, RealtimeMessageService.cursor(after), authentication)); }
        catch (AccessDeniedException e) { throw SummerException.withMessage(CommonErrorCode.FORBIDDEN, "Message capability denied", e); }
    }

    @GetMapping("/{messageId}")
    @RawResponse
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('SCOPE_enterprise.messages.read')")
    ResponseEntity<ApiResponse<RealtimeMessageView>> find(@PathVariable String enterpriseId, @PathVariable String messageId,
                                                           Authentication authentication) {
        try { return body(HttpStatus.OK, service.find(enterpriseId, messageId, authentication)); }
        catch (AccessDeniedException e) { throw SummerException.withMessage(CommonErrorCode.FORBIDDEN, "Message capability denied", e); }
    }

    @PostMapping("/{messageId}:ack")
    @RawResponse
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('SCOPE_enterprise.messages.write')")
    ResponseEntity<ApiResponse<RealtimeMessageView>> acknowledge(@PathVariable String enterpriseId, @PathVariable String messageId,
                                                                   Authentication authentication) {
        try {
            RealtimeMessageView result = service.acknowledge(enterpriseId, messageId, authentication);
            hub.broadcast(enterpriseId, Map.of("type", "ack", "messageId", messageId, "status", result.status(), "sequence", result.sequence()));
            return body(HttpStatus.OK, result);
        }
        catch (AccessDeniedException e) { throw SummerException.withMessage(CommonErrorCode.FORBIDDEN, "Message capability denied", e); }
    }

    @GetMapping("/connections")
    @RawResponse
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('SCOPE_enterprise.messages.read')")
    ResponseEntity<ApiResponse<RealtimeConnectionView>> connections(@PathVariable String enterpriseId, Authentication authentication) {
        try { service.require(enterpriseId, authentication, RealtimeMessageService.READ); }
        catch (AccessDeniedException e) { throw SummerException.withMessage(CommonErrorCode.FORBIDDEN, "Message capability denied", e); }
        return body(HttpStatus.OK, hub.status(enterpriseId));
    }

    private <T> ResponseEntity<ApiResponse<T>> body(HttpStatus status, T value) {
        return ResponseEntity.status(status).header("X-Request-Id", requestId.currentRequestId())
                .body(ApiResponse.success(value, requestId.currentRequestId()));
    }

}
