package com.abluepoint.summer.sample.realtime;

import com.abluepoint.summer.common.response.ApiError;
import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
        } catch (RealtimeMessageValidationException e) { return failure(HttpStatus.BAD_REQUEST, "SUMMER_MESSAGE_INVALID", e.getMessage()); }
        catch (AccessDeniedException e) { return failure(HttpStatus.FORBIDDEN, "SUMMER_FORBIDDEN", "Message capability denied"); }
    }

    @GetMapping
    @RawResponse
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('SCOPE_enterprise.messages.read')")
    ResponseEntity<ApiResponse<RealtimeMessagePage>> list(@PathVariable String enterpriseId,
                                                            @RequestParam(required = false) String after,
                                                            Authentication authentication) {
        try { return body(HttpStatus.OK, service.list(enterpriseId, RealtimeMessageService.cursor(after), authentication)); }
        catch (RealtimeMessageCursorException e) { return failure(HttpStatus.BAD_REQUEST, "SUMMER_MESSAGE_CURSOR_INVALID", e.getMessage()); }
        catch (AccessDeniedException e) { return failure(HttpStatus.FORBIDDEN, "SUMMER_FORBIDDEN", "Message capability denied"); }
    }

    @GetMapping("/{messageId}")
    @RawResponse
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('SCOPE_enterprise.messages.read')")
    ResponseEntity<ApiResponse<RealtimeMessageView>> find(@PathVariable String enterpriseId, @PathVariable String messageId,
                                                           Authentication authentication) {
        try { return body(HttpStatus.OK, service.find(enterpriseId, messageId, authentication)); }
        catch (RealtimeMessageNotFoundException e) { return failure(HttpStatus.NOT_FOUND, "SUMMER_NOT_FOUND", e.getMessage()); }
        catch (AccessDeniedException e) { return failure(HttpStatus.FORBIDDEN, "SUMMER_FORBIDDEN", "Message capability denied"); }
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
        } catch (RealtimeMessageNotFoundException e) { return failure(HttpStatus.NOT_FOUND, "SUMMER_NOT_FOUND", e.getMessage()); }
        catch (AccessDeniedException e) { return failure(HttpStatus.FORBIDDEN, "SUMMER_FORBIDDEN", "Message capability denied"); }
    }

    @GetMapping("/connections")
    @RawResponse
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('SCOPE_enterprise.messages.read')")
    ResponseEntity<ApiResponse<RealtimeConnectionView>> connections(@PathVariable String enterpriseId, Authentication authentication) {
        try { service.require(enterpriseId, authentication, RealtimeMessageService.READ); }
        catch (AccessDeniedException e) { return failure(HttpStatus.FORBIDDEN, "SUMMER_FORBIDDEN", "Message capability denied"); }
        return body(HttpStatus.OK, hub.status(enterpriseId));
    }

    private <T> ResponseEntity<ApiResponse<T>> body(HttpStatus status, T value) {
        return ResponseEntity.status(status).header("X-Request-Id", requestId.currentRequestId())
                .body(ApiResponse.success(value, requestId.currentRequestId()));
    }

    private <T> ResponseEntity<ApiResponse<T>> failure(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).header("X-Request-Id", requestId.currentRequestId())
                .body(ApiResponse.failure(new ApiError(code, message, List.of()), requestId.currentRequestId()));
    }
}
