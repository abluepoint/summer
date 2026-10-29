package com.abluepoint.summer.sample.authorization.api;

import com.abluepoint.summer.sample.authorization.application.OAuthClientManagementService;
import com.abluepoint.summer.sample.authorization.application.OAuthClientManagementException;
import com.abluepoint.summer.sample.authorization.application.command.OAuthClientCreateCommand;
import com.abluepoint.summer.sample.authorization.application.command.OAuthClientUpdateCommand;
import com.abluepoint.summer.common.response.api.ApiError;
import com.abluepoint.summer.common.response.api.ApiResponse;
import com.abluepoint.summer.mvc.request.api.RequestIdAccessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@ConditionalOnBean(OAuthClientManagementService.class)
@ConditionalOnProperty(prefix = "summer.security.storage", name = "consent", havingValue = "jdbc")
public class OAuthClientManagementController {
    private final OAuthClientManagementService management;
    private final RequestIdAccessor requestId;

    public OAuthClientManagementController(OAuthClientManagementService management, RequestIdAccessor requestId) {
        this.management = management; this.requestId = requestId;
    }

    @GetMapping("/api/security/oauth-clients")
    @PreAuthorize("hasAuthority('SCOPE_security.oauth-client.read')")
    ResponseEntity<ApiResponse<Map<String, Object>>> listClients(Authentication authentication) {
        return ok(Map.of("items", management.listClients(authentication.getName(), requestId.currentRequestId())));
    }

    @PostMapping("/api/security/oauth-clients")
    @PreAuthorize("hasAuthority('SCOPE_security.oauth-client.write')")
    ResponseEntity<ApiResponse<Map<String, Object>>> create(@RequestBody ClientRequest body, Authentication authentication) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                    management.create(body, authentication.getName(), requestId.currentRequestId()), requestId.currentRequestId()));
        } catch (OAuthClientManagementException failure) {
            return failure(failure);
        }
    }

    @PatchMapping("/api/security/oauth-clients/{clientId}")
    @PreAuthorize("hasAuthority('SCOPE_security.oauth-client.write')")
    ResponseEntity<ApiResponse<Map<String, Object>>> changeStatus(@PathVariable String clientId,
                                                                   @RequestBody UpdateRequest body,
                                                                   Authentication authentication) {
        try {
            return ok(management.update(clientId, body, authentication.getName(), requestId.currentRequestId()));
        } catch (OAuthClientManagementException failure) {
            return failure(failure);
        }
    }

    @GetMapping("/api/security/oauth-consents")
    @PreAuthorize("hasAuthority('SCOPE_security.oauth-client.read')")
    ResponseEntity<ApiResponse<Map<String, Object>>> listConsents(Authentication authentication) {
        return ok(Map.of("items", management.listConsents(authentication.getName(), requestId.currentRequestId())));
    }

    @DeleteMapping("/api/security/oauth-consents/{clientId}/{principalName}")
    @PreAuthorize("hasAuthority('SCOPE_security.oauth-client.write')")
    ResponseEntity<ApiResponse<Map<String, Object>>> revokeConsent(@PathVariable String clientId,
                                                                    @PathVariable String principalName,
                                                                    Authentication authentication) {
        try {
            return ok(management.revokeConsent(clientId, principalName, authentication.getName(), requestId.currentRequestId()));
        } catch (OAuthClientManagementException failure) {
            return failure(failure);
        }
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(T value) {
        return ResponseEntity.ok().header("X-Request-Id", requestId.currentRequestId()).body(ApiResponse.success(value, requestId.currentRequestId()));
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> failure(OAuthClientManagementException failure) {
        HttpStatus status = switch (failure.reason()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case EXISTS -> HttpStatus.CONFLICT;
            case VERSION_CONFLICT -> HttpStatus.PRECONDITION_FAILED;
            case NOT_FOUND, CONSENT_NOT_FOUND -> HttpStatus.NOT_FOUND;
        };
        return ResponseEntity.status(status).header("X-Request-Id", requestId.currentRequestId())
                .body(ApiResponse.failure(new ApiError(failure.reason().code(), failure.getMessage(), List.of()), requestId.currentRequestId()));
    }

    private record ClientRequest(String clientId, String clientName, Boolean publicClient, List<String> redirectUris, List<String> scopes) implements OAuthClientCreateCommand {}
    private record UpdateRequest(String status, String clientName, List<String> redirectUris, List<String> scopes, int version) implements OAuthClientUpdateCommand {}
}
