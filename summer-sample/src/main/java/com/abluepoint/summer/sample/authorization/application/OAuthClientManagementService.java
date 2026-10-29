package com.abluepoint.summer.sample.authorization.application;

import com.abluepoint.summer.sample.authorization.application.command.OAuthClientCreateCommand;
import com.abluepoint.summer.sample.authorization.application.command.OAuthClientUpdateCommand;
import com.abluepoint.summer.sample.authorization.domain.OAuthClientAlreadyExistsException;
import com.abluepoint.summer.sample.authorization.domain.OAuthClientManagementStore;
import com.abluepoint.summer.sample.authorization.domain.OAuthClientNotFoundException;
import com.abluepoint.summer.sample.authorization.domain.OAuthClientRules;
import com.abluepoint.summer.sample.authorization.domain.OAuthClientVersionConflictException;
import com.abluepoint.summer.security.audit.spi.SecurityAuditLogger;
import com.abluepoint.summer.security.oauth2.spi.SummerConsentStore;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import static com.abluepoint.summer.sample.authorization.application.OAuthClientManagementException.Reason.*;

/** Coordinates administrative client/consent use cases, preserving validation and audit order. */
public class OAuthClientManagementService {
    private final OAuthClientManagementStore clients;
    private final SummerConsentStore consents;
    private final SecurityAuditLogger audit;

    public OAuthClientManagementService(OAuthClientManagementStore clients, SummerConsentStore consents,
                                         SecurityAuditLogger audit) {
        this.clients = clients; this.consents = consents; this.audit = audit;
    }

    public List<Map<String, Object>> listClients(String actor, String requestId) {
        var items = clients.managementProjection();
        audit.record("oauth_client_metadata_read", actor, Map.of("count", items.size(), "requestId", requestId));
        return items;
    }

    public Map<String, Object> create(OAuthClientCreateCommand command, String actor, String requestId) {
        try {
            if (command == null)
                throw new IllegalArgumentException("clientId, clientName, redirectUris and scopes are required");
            OAuthClientRules.requireCreation(command.clientId(), command.clientName(), command.publicClient(),
                    command.redirectUris(), command.scopes());
            // Preserve the original left-to-right validation before writing protocol state.
            String clientId = OAuthClientRules.clientId(command.clientId());
            String clientName = OAuthClientRules.clientName(command.clientName());
            var redirectUris = OAuthClientRules.redirectUris(command.redirectUris());
            var scopes = OAuthClientRules.scopes(command.scopes());
            clients.registerPublicClient(clientId, clientName, redirectUris, scopes);
            audit.record("oauth_client_created", actor, Map.of("clientId", clientId, "requestId", requestId, "publicClient", true));
            return Map.of("clientId", clientId, "status", "ACTIVE");
        } catch (OAuthClientAlreadyExistsException conflict) {
            throw failure(EXISTS, "Client ID already exists", actor, requestId);
        } catch (IllegalArgumentException invalid) {
            throw failure(INVALID, invalid.getMessage(), actor, requestId);
        }
    }

    public Map<String, Object> update(String clientId, OAuthClientUpdateCommand command, String actor, String requestId) {
        try {
            if (command == null) throw new IllegalArgumentException("version is required");
            OAuthClientRules.requireUpdate(clientId, command.version(), command.status(), command.clientName(),
                    command.redirectUris(), command.scopes());
            var result = clients.updateManagement(clientId,
                    command.status() == null ? null : command.status().trim().toUpperCase(Locale.ROOT),
                    command.clientName() == null ? null : OAuthClientRules.clientName(command.clientName()),
                    command.redirectUris() == null ? null : OAuthClientRules.redirectUris(command.redirectUris()),
                    command.scopes() == null ? null : OAuthClientRules.scopes(command.scopes()), command.version());
            audit.record("oauth_client_updated", actor, Map.of("clientId", clientId, "version", result.get("version"), "requestId", requestId));
            return result;
        } catch (OAuthClientVersionConflictException conflict) {
            throw failure(VERSION_CONFLICT, "Client version has changed; reload before retrying", actor, requestId);
        } catch (OAuthClientNotFoundException missing) {
            throw failure(NOT_FOUND, "Client was not found", actor, requestId);
        } catch (IllegalArgumentException invalid) {
            throw failure(INVALID, invalid.getMessage(), actor, requestId);
        }
    }

    public List<Map<String, Object>> listConsents(String actor, String requestId) {
        var items = clients.managementConsents();
        audit.record("oauth_consent_metadata_read", actor, Map.of("count", items.size(), "requestId", requestId));
        return items;
    }

    public Map<String, Object> revokeConsent(String clientId, String principalName, String actor, String requestId) {
        var consent = consents.findById(clientId, principalName);
        if (consent == null) throw failure(CONSENT_NOT_FOUND, "Authorization consent was not found", actor, requestId);
        consents.remove(consent);
        audit.record("oauth_consent_revoked", actor, Map.of("clientId", clientId, "principal", principalName, "requestId", requestId));
        return Map.of("clientId", clientId, "principalName", principalName, "status", "REVOKED");
    }

    private OAuthClientManagementException failure(OAuthClientManagementException.Reason reason, String message,
                                                     String actor, String requestId) {
        audit.record("oauth_management_failed", actor == null ? "unknown" : actor,
                Map.of("code", reason.code(), "requestId", requestId));
        return new OAuthClientManagementException(reason, message);
    }
}
