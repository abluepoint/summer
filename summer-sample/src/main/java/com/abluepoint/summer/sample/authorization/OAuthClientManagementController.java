package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.common.error.SummerException;

import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import com.abluepoint.summer.security.audit.SecurityAuditLogger;
import com.abluepoint.summer.security.autoconfigure.storage.SummerConsentStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@ConditionalOnBean({JdbcSummerOAuthClientStore.class, SummerConsentStore.class})
@ConditionalOnProperty(prefix = "summer.security.storage", name = "consent", havingValue = "jdbc")
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
                    .clientId(clientId(body.clientId())).clientName(clientName(body.clientName()))
                    .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(false).build())
                    .redirectUris(set -> set.addAll(redirectUris(body.redirectUris())))
                    .scopes(set -> set.addAll(scopes(body.scopes()))).build();
            clients.register(client);
            audit.record("oauth_client_created", authentication.getName(), Map.of("clientId", client.getClientId(), "requestId", requestId.currentRequestId(), "publicClient", true));
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(Map.of("clientId", client.getClientId(), "status", "ACTIVE"), requestId.currentRequestId()));
        } catch (SummerException e) {
            if (e.errorCode() != OAuthManagementErrorCode.CLIENT_EXISTS) throw e;
            recordFailure(e, authentication);
            throw e;
        } catch (IllegalArgumentException e) {
            SummerException failure = SummerException.withMessage(OAuthManagementErrorCode.CLIENT_INVALID, e.getMessage(), e);
            recordFailure(failure, authentication);
            throw failure;
        }
    }

    @PatchMapping("/api/security/oauth-clients/{clientId}")
    @PreAuthorize("hasAuthority('SCOPE_security.oauth-client.write')")
    ResponseEntity<ApiResponse<Map<String, Object>>> changeStatus(@PathVariable String clientId,
                                                                   @RequestBody UpdateRequest body,
                                                                   Authentication authentication) {
        try {
            if (blank(clientId) || body == null || body.version() < 1) throw new IllegalArgumentException("version is required");
            if (body.status() == null && body.clientName() == null && body.redirectUris() == null && body.scopes() == null)
                throw new IllegalArgumentException("at least one client field is required");
            Map<String, Object> result = clients.updateManagement(clientId,
                    body.status() == null ? null : body.status().trim().toUpperCase(java.util.Locale.ROOT),
                    body.clientName() == null ? null : clientName(body.clientName()),
                    body.redirectUris() == null ? null : redirectUris(body.redirectUris()),
                    body.scopes() == null ? null : scopes(body.scopes()), body.version());
            audit.record("oauth_client_updated", authentication.getName(), Map.of("clientId", clientId, "version", result.get("version"), "requestId", requestId.currentRequestId()));
            return ok(result);
        } catch (SummerException e) {
            if (e.errorCode() != com.abluepoint.summer.sample.error.SampleTransportErrorCode.OAUTH_CLIENT_VERSION_CONFLICT
                    && e.errorCode() != OAuthManagementErrorCode.CLIENT_NOT_FOUND) throw e;
            recordFailure(e, authentication);
            throw e;
        } catch (IllegalArgumentException e) {
            SummerException failure = SummerException.withMessage(OAuthManagementErrorCode.CLIENT_INVALID, e.getMessage(), e);
            recordFailure(failure, authentication);
            throw failure;
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
        if (consent == null) {
            SummerException failure = SummerException.withMessage(OAuthManagementErrorCode.CONSENT_NOT_FOUND, "Authorization consent was not found");
            recordFailure(failure, authentication);
            throw failure;
        }
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

    private void recordFailure(SummerException failure, Authentication authentication) {
        audit.record("oauth_management_failed", authentication == null ? "unknown" : authentication.getName(),
                Map.of("code", failure.errorCode().code(), "requestId", requestId.currentRequestId()));
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String clientId(String value) {
        if (blank(value) || value.trim().length() > 100 || value.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("clientId must contain 1 to 100 characters without control characters");
        return value.trim();
    }

    private static String clientName(String value) {
        if (blank(value) || value.trim().length() > 200 || value.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("clientName must contain 1 to 200 characters without control characters");
        return value.trim();
    }

    private static List<String> values(List<String> value, String field, int maxLength) {
        if (value.isEmpty() || value.stream().anyMatch(v -> v == null || v.isBlank()))
            throw new IllegalArgumentException(field + " must contain non-empty values");
        List<String> result = value.stream().map(String::trim).distinct().toList();
        if (String.join(",", result).length() > maxLength)
            throw new IllegalArgumentException(field + " exceeds the storage limit");
        return result;
    }

    private static List<String> redirectUris(List<String> values) {
        List<String> result = values(values, "redirectUris", 2000);
        for (String value : result) {
            URI uri;
            try { uri = URI.create(value); }
            catch (IllegalArgumentException e) { throw new IllegalArgumentException("redirectUris must contain valid absolute URIs"); }
            boolean loopback = java.util.Set.of("localhost", "127.0.0.1", "::1", "[::1]").contains(String.valueOf(uri.getHost()));
            if (uri.getHost() == null || uri.getRawFragment() != null || uri.getRawUserInfo() != null
                    || value.contains("*") || value.contains(",")
                    || !("https".equalsIgnoreCase(uri.getScheme()) || (loopback && "http".equalsIgnoreCase(uri.getScheme()))))
                throw new IllegalArgumentException("redirectUris require HTTPS (HTTP is allowed only for loopback), without fragments, user info, wildcards or commas");
        }
        return result;
    }

    private static List<String> scopes(List<String> values) {
        List<String> result = values(values, "scopes", 4096);
        if (result.stream().anyMatch(v -> v.chars().anyMatch(c -> c < 0x21 || c > 0x7e || c == '"' || c == 0x5c || c == ',')))
            throw new IllegalArgumentException("scopes must be individual OAuth scope tokens without commas");
        return result;
    }

    record ClientRequest(String clientId, String clientName, Boolean publicClient, List<String> redirectUris, List<String> scopes) {}
    record UpdateRequest(String status, String clientName, List<String> redirectUris, List<String> scopes, int version) {}
}
