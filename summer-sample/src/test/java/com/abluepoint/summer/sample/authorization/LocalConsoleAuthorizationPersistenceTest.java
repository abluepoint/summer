package com.abluepoint.summer.sample.authorization;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import static org.assertj.core.api.Assertions.assertThat;

class LocalConsoleAuthorizationPersistenceTest {
    @Test void jdbcRestoresLocalLoginClaimsAndPasswordFactorWithoutChangingTokenTimes() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:local-login-" + java.util.UUID.randomUUID(), "sa", "");
        try (var connection = uncheckedConnection(dataSource)) {
            new ResourceDatabasePopulator(new ClassPathResource(
                    "org/springframework/security/oauth2/server/authorization/oauth2-authorization-schema.sql")).execute(dataSource);
            var client = RegisteredClient.withId("console").clientId("console")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("https://console.example/callback").scope("openid").build();
            var store = new JdbcSummerAuthorizationStore(new JdbcTemplate(dataSource), new InMemoryRegisteredClientRepository(client));
            Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
            var jwt = Jwt.withTokenValue("local-console-session").header("alg", "none").subject("operator")
                    .issuedAt(now).expiresAt(now.plusSeconds(300))
                    .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "example", "status", "ACTIVE")))
                    .claim("enterprise_capabilities", Map.of("example", List.of("enterprise.members.read"))).build();
            var factor = FactorGrantedAuthority.fromFactor("PASSWORD");
            var login = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER"), factor), "operator");
            var authorization = OAuth2Authorization.withRegisteredClient(client).principalName(login.getName())
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .attribute(Principal.class.getName(), login).build();
            store.save(authorization);
            JwtAuthenticationToken restored = store.findById(authorization.getId()).getAttribute(Principal.class.getName());
            assertThat(restored.getName()).isEqualTo("operator");
            assertThat(restored.getToken().getClaims()).isEqualTo(jwt.getClaims());
            assertThat(restored.getToken().getIssuedAt()).isEqualTo(now);
            assertThat(restored.getToken().getExpiresAt()).isEqualTo(now.plusSeconds(300));
            assertThat(restored.getAuthorities()).contains(factor);
            var jdbc = new JdbcTemplate(dataSource);
            String attributes = jdbc.queryForObject("select attributes from oauth2_authorization where id = ?",
                    String.class, authorization.getId());
            jdbc.update("update oauth2_authorization set attributes = ? where id = ?",
                    attributes.replace(JwtAuthenticationToken.class.getName(), "java.io.File"), authorization.getId());
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> store.findById(authorization.getId()))
                    .hasRootCauseInstanceOf(tools.jackson.databind.exc.InvalidTypeIdException.class);
        } catch (java.sql.SQLException failure) { throw new IllegalStateException(failure); }
    }

    private static java.sql.Connection uncheckedConnection(DriverManagerDataSource source) {
        try { return source.getConnection(); }
        catch (java.sql.SQLException failure) { throw new IllegalStateException(failure); }
    }
}
