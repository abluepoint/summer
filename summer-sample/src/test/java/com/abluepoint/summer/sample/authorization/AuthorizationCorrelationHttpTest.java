package com.abluepoint.summer.sample.authorization;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Real HTTP and signature validation; the audit is read through its persisted public API. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "summer.security.storage.keys=memory",
        "summer.sample.authorization.legacy-scope-compatibility=true"
})
@org.springframework.context.annotation.Import(CurrentManagedAuthorizationTestFixture.class)
class AuthorizationCorrelationHttpTest {
    @Value("${local.server.port}") int port;
    @Autowired JWKSource<SecurityContext> keys;
    @Autowired ObjectMapper mapper;
    @Autowired CurrentManagedAuthorizationTestFixture fixture;

    @ParameterizedTest
    @ValueSource(strings = {"", "correlation id:/sample"})
    void allowedRequestAndPersistedDecisionShareTheResponseCorrelation(String suppliedId) throws Exception {
        verifyCorrelation(suppliedId, false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "correlation id:/sample"})
    void deniedRequestAndPersistedDecisionShareTheResponseCorrelation(String suppliedId) throws Exception {
        verifyCorrelation(suppliedId, true);
    }

    private void verifyCorrelation(String suppliedId, boolean denied) throws Exception {
        String subject = "correlation-" + UUID.randomUUID();
        var domain=com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise("ent-acme");
        fixture.activeUser(domain,subject);
        if (!denied) fixture.grantHandlers(domain,subject,(handler,path,method)->path.equals("/api/authorization-events") && method.equals("GET"));
        fixture.grantHandlers(domain,"correlation-auditor",(handler,path,method)->path.equals("/api/v1/enterprises/{enterpriseId}/authorization-audit/{id}") && method.equals("GET"));
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            var response = get(client, "/api/authorization-events?enterpriseId=ent-acme",
                    token(subject, denied, "enterprise.roles.read"), suppliedId);
            assertThat(response.statusCode()).isEqualTo(denied ? 403 : 200);
            String correlation = response.headers().firstValue("X-Request-Id").orElseThrow();
            assertThat(correlation).isNotBlank();
            if (suppliedId.isEmpty()) assertThat(UUID.fromString(correlation).toString()).isEqualTo(correlation);
            else assertThat(correlation).isEqualTo("correlation_id__sample");
            JsonNode body = mapper.readTree(response.body());
            assertThat(body.path("requestId").asText()).isEqualTo(correlation);
            if (denied) assertThat(body.path("error").path("code").asText()).isEqualTo("SUMMER_FORBIDDEN");

            var audit = get(client, "/api/v1/enterprises/ent-acme/authorization-audit/" + subject,
                    token("correlation-auditor", false, "enterprise.roles.read"), "audit-read");
            assertThat(audit.statusCode()).isEqualTo(200);
            JsonNode rows = mapper.readTree(audit.body()).path("data");
            assertThat(rows.size()).isPositive();
            // Security, final-handler and service gates each persist their current resource evidence.
            for (JsonNode row : rows) {
                assertThat(row.path("decision").asText()).isEqualTo(denied ? "DENY" : "ALLOW");
                assertThat(row.path("requestId").asText()).isEqualTo(correlation);
                assertThat(row.path("resourceId").asText()).isNotBlank();
                assertThat(row.path("authorizationRevision").isNumber()).isTrue();
            }
        }
    }

    private HttpResponse<String> get(HttpClient client, String path, String token, String suppliedId) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15)).header("Authorization", "Bearer " + token).GET();
        if (!suppliedId.isEmpty()) request.header("X-Request-Id", suppliedId);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String token(String subject, boolean managed, String capability) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("http://127.0.0.1:8080").subject(subject)
                .issuedAt(now).expiresAt(now.plusSeconds(120)).claim("scope", capability)
                .claim("summer_managed_grants", managed)
                .claim("platform_capabilities", List.of(capability))
                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                .claim("enterprise_capabilities", Map.of("ent-acme", List.of(capability))).build();
        return new NimbusJwtEncoder(keys).encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
    }
}
