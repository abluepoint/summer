package com.abluepoint.summer.sample.authorization.infrastructure;

import com.abluepoint.summer.sample.authorization.domain.OAuthClientManagementStore;
import com.abluepoint.summer.sample.authorization.domain.OAuthClientRules;
import com.abluepoint.summer.sample.authorization.domain.OAuthClientAlreadyExistsException;
import com.abluepoint.summer.sample.authorization.domain.OAuthClientNotFoundException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import java.util.List;
import java.util.Map;

/** JDBC registered-client store with a narrow, redacted management projection. */
public class JdbcSummerOAuthClientStore extends JdbcRegisteredClientRepository
        implements com.abluepoint.summer.security.oauth2.spi.SummerOAuthClientStore, OAuthClientManagementStore {
    private final JdbcOperations jdbc;

    JdbcSummerOAuthClientStore(JdbcOperations jdbc) {
        super(jdbc);
        this.jdbc = jdbc;
    }

    @Override
    public RegisteredClient findById(String id) {
        RegisteredClient client = super.findById(id);
        return client == null || isEnabled(client.getClientId()) ? client : null;
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        RegisteredClient client = super.findByClientId(clientId);
        return client == null || isEnabled(clientId) ? client : null;
    }

    boolean isRegistered(String clientId) {
        // Protocol lookups intentionally hide disabled clients; startup must still see them.
        return super.findByClientId(clientId) != null;
    }

    @Override
    public void registerPublicClient(String clientId, String clientName, List<String> redirectUris, List<String> scopes) {
        RegisteredClient client = RegisteredClient.withId(java.util.UUID.randomUUID().toString())
                .clientId(clientId).clientName(clientName)
                .clientAuthenticationMethod(new ClientAuthenticationMethod(OAuthClientRules.PUBLIC_AUTHENTICATION_METHOD))
                .authorizationGrantType(new AuthorizationGrantType(OAuthClientRules.PUBLIC_GRANT_TYPE))
                .clientSettings(ClientSettings.builder().requireProofKey(OAuthClientRules.REQUIRE_PROOF_KEY)
                        .requireAuthorizationConsent(OAuthClientRules.REQUIRE_AUTHORIZATION_CONSENT).build())
                .redirectUris(set -> set.addAll(redirectUris)).scopes(set -> set.addAll(scopes)).build();
        register(client);
    }

    private void register(RegisteredClient client) {
        if (isRegistered(client.getClientId())) throw new OAuthClientAlreadyExistsException();
        try {
            super.save(client);
        } catch (IllegalArgumentException | org.springframework.dao.DataIntegrityViolationException failure) {
            if (isRegistered(client.getClientId())) throw new OAuthClientAlreadyExistsException();
            throw failure;
        }
    }

    public List<Map<String, Object>> managementProjection() {
        return jdbc.query("""
                select c.client_id, c.client_name, c.client_authentication_methods,
                       c.authorization_grant_types, c.redirect_uris, c.post_logout_redirect_uris,
                       c.scopes, coalesce(s.status, 'ACTIVE') as status, coalesce(s.version, 1) as version
                  from oauth2_registered_client c
                  left join summer_oauth_client_status s on s.client_id = c.client_id
                 order by c.client_id
                """, (rs, row) -> Map.of(
                "clientId", rs.getString("client_id"),
                "clientName", rs.getString("client_name"),
                "authenticationMethods", split(rs.getString("client_authentication_methods")),
                "grantTypes", split(rs.getString("authorization_grant_types")),
                "redirectUris", split(rs.getString("redirect_uris")),
                "postLogoutRedirectUris", split(rs.getString("post_logout_redirect_uris")),
                "scopes", split(rs.getString("scopes")),
                "status", rs.getString("status"),
                "version", rs.getInt("version")));
    }

    public List<Map<String, Object>> managementConsents() {
        return jdbc.query("select registered_client_id, principal_name, authorities from oauth2_authorization_consent order by registered_client_id, principal_name",
                (rs, row) -> Map.of("clientId", rs.getString("registered_client_id"),
                        "principalName", rs.getString("principal_name"),
                        "authorities", split(rs.getString("authorities"))));
    }

    /** Serializes status and metadata changes on the immutable registered-client row. */
    @Transactional
    public Map<String, Object> updateManagement(String clientId, String status, String clientName,
                                                 List<String> redirectUris, List<String> scopes,
                                                 int expectedVersion) {
        if (jdbc.queryForList("select id from oauth2_registered_client where client_id=? for update", String.class, clientId).isEmpty())
            throw new OAuthClientNotFoundException();
        RegisteredClient existing = super.findByClientId(clientId);
        List<Map<String, Object>> states = jdbc.queryForList("select status, version from summer_oauth_client_status where client_id=?", clientId);
        int version = states.isEmpty() ? 1 : ((Number) states.getFirst().get("version")).intValue();
        OAuthClientRules.requireVersion(version, expectedVersion);
        String nextStatus = status == null ? (states.isEmpty() ? "ACTIVE" : (String) states.getFirst().get("status")) : status;
        OAuthClientRules.requireStatus(nextStatus);
        RegisteredClient.Builder builder = RegisteredClient.from(existing);
        if (clientName != null) builder.clientName(clientName);
        if (redirectUris != null) builder.redirectUris(set -> { set.clear(); set.addAll(redirectUris); });
        if (scopes != null) builder.scopes(set -> { set.clear(); set.addAll(scopes); });
        super.save(builder.build());
        if (states.isEmpty()) {
            jdbc.update("insert into summer_oauth_client_status(client_id,status,version,updated_at) values(?,?,?,current_timestamp)", clientId, nextStatus, version + 1);
        } else {
            jdbc.update("update summer_oauth_client_status set status=?, version=version+1, updated_at=current_timestamp where client_id=?", nextStatus, clientId);
        }
        return Map.of("clientId", clientId, "status", nextStatus, "version", version + 1);
    }

    private boolean isEnabled(String clientId) {
        Integer count = jdbc.queryForObject("select count(*) from summer_oauth_client_status where client_id=? and status='ACTIVE'", Integer.class, clientId);
        Integer rows = jdbc.queryForObject("select count(*) from summer_oauth_client_status where client_id=?", Integer.class, clientId);
        return rows == null || rows == 0 || (count != null && count > 0);
    }

    private static List<String> split(String value) {
        if (value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split(",")).map(String::trim).filter(v -> !v.isBlank()).toList();
    }

}
