package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.common.response.ApiError;
import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import com.abluepoint.summer.security.audit.SecurityAuditLogger;
import com.abluepoint.summer.security.autoconfigure.storage.SummerConsentStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@ConditionalOnBean({JdbcSummerOAuthClientStore.class, SummerConsentStore.class})
class OAuthClientManagementController {
    private final JdbcSummerOAuthClientStore clients;
    private final SummerConsentStore consents;
    private final RequestIdAccessor requestId;
    private final SecurityAuditLogger audit;

    OAuthClientManagementController(JdbcSummerOAuthClientStore clients,
                                    SummerConsentStore consents,
                                    RequestIdAccessor requestId,
                                    SecurityAuditLogger audit) {
        this.clients = clients;
        this.consents = consents;
        this.requestId = requestId;
        this.audit = audit;
    }

    @GetMapping("/api/security/oauth-clients")
    @PreAuthorize("hasAuthority('SCOPE_security.oauth-client.read')")
    ResponseEntity<ApiResponse<Map<String, Object>>> listClients(Authentication authentication) {
        List<Map<String, Object>> items = clients.managementProjection();
        audit.record("oauth_client_metadata_read", authentication.getName(), Map.of("count", items.size(), "requestId", requestId.currentRequestId()));
        return ok(Map.of("items", items));
    }

    @PostMapping("/api/security/oauth-clients")
    @PreAuthorize("hasAuthority('SCOPE_security.oauth-client.write')")
    ResponseEntity<ApiResponse<Map<String, Object>>> create(@RequestBody ClientRequest body, Authentication authentication) {
        try {
            if (body == null || blank(body.clientId()) || blank(body.clientName()) || body.redirectUris() == null || body.redirectUris().isEmpty()
                    || body.scopes() == null || body.scopes().isEmpty()) throw new IllegalArgumentException("clientId, clientName, redirectUris and scopes are required");
            if (!Boolean.TRUE.equals(body.publicClient())) throw new IllegalArgumentException("only public PKCE clients are supported by this endpoint");
            RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId(body.clientId().trim()).clientName(body.clientName().trim())
                    .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(false).build())
                    .redirectUris(set -> set.addAll(values(body.redirectUris())))
                    .scopes(set -> set.addAll(values(body.scopes()))).build();
            clients.save(client);
            audit.record("oauth_client_created", authentication.getName(), Map.of("clientId", client.getClientId(), "requestId", requestId.currentRequestId(), "publicClient", true));
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(Map.of("clientId", client.getClientId(), "status", "ACTIVE"), requestId.currentRequestId()));
        } catch (IllegalArgumentException e) {
            return failure(HttpStatus.BAD_REQUEST, "SUMMER_OAUTH_CLIENT_INVALID", e.getMessage(), authentication);
        }
    }

    @PatchMapping("/api/security/oauth-clients/{clientId}")
    @PreAuthorize("hasAuthority('SCOPE_security.oauth-client.write')")
    ResponseEntity<ApiResponse<Map<String, Object>>> changeStatus(@PathVariable String clientId,
                                                                   @RequestBody StatusRequest body,
                                                                   Authentication authentication) {
        try {
            if (blank(clientId) || body == null || blank(body.status()) || body.version() < 1) throw new IllegalArgumentException("status and version are required");
            clients.setStatus(clientId, body.status().trim().toUpperCase(), body.version());
            audit.record("oauth_client_status_changed", authentication.getName(), Map.of("clientId", clientId, "status", body.status().trim().toUpperCase(), "requestId", requestId.currentRequestId()));
            return ok(Map.of("clientId", clientId, "status", body.status().trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            return failure(HttpStatus.BAD_REQUEST, "SUMMER_OAUTH_CLIENT_INVALID", e.getMessage(), authentication);
        }
    }

    @GetMapping("/api/security/oauth-consents")
    @PreAuthorize("hasAuthority('SCOPE_security.oauth-client.read')")
    ResponseEntity<ApiResponse<Map<String, Object>>> listConsents(Authentication authentication) {
        List<Map<String, Object>> items = clientsConsents();
        audit.record("oauth_consent_metadata_read", authentication.getName(), Map.of("count", items.size(), "requestId", requestId.currentRequestId()));
        return ok(Map.of("items", items));
    }

    @DeleteMapping("/api/security/oauth-consents/{clientId}/{principalName}")
    @PreAuthorize("hasAuthority('SCOPE_security.oauth-client.write')")
    ResponseEntity<ApiResponse<Map<String, Object>>> revokeConsent(@PathVariable String clientId,
                                                                    @PathVariable String principalName,
                                                                    Authentication authentication) {
        OAuth2AuthorizationConsent consent = consents.findById(clientId, principalName);
        if (consent == null) return failure(HttpStatus.NOT_FOUND, "SUMMER_OAUTH_CONSENT_NOT_FOUND", "Authorization consent was not found", authentication);
        consents.remove(consent);
        audit.record("oauth_consent_revoked", authentication.getName(), Map.of("clientId", clientId, "principal", principalName, "requestId", requestId.currentRequestId()));
        return ok(Map.of("clientId", clientId, "principalName", principalName, "status", "REVOKED"));
    }

    private List<Map<String, Object>> clientsConsents() {
        return clients.managementConsents();
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(T value) {
        return ResponseEntity.ok().header("X-Request-Id", requestId.currentRequestId()).body(ApiResponse.success(value, requestId.currentRequestId()));
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> failure(HttpStatus status, String code, String message, Authentication authentication) {
        audit.record("oauth_management_failed", authentication == null ? "unknown" : authentication.getName(), Map.of("code", code, "requestId", requestId.currentRequestId()));
        return ResponseEntity.status(status).header("X-Request-Id", requestId.currentRequestId()).body(ApiResponse.failure(new ApiError(code, message, List.of()), requestId.currentRequestId()));
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static List<String> values(List<String> value) { return value.stream().map(String::trim).filter(v -> !v.isBlank()).toList(); }

    record ClientRequest(String clientId, String clientName, Boolean publicClient, List<String> redirectUris, List<String> scopes) {}
    record StatusRequest(String status, int version) {}
}
