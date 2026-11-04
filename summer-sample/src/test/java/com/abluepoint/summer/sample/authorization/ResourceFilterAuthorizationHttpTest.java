package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.security.authorization.ResourceRequestAuthorization;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

/** Real TCP and the assembled Spring Security filter chain; no security mocks. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ResourceFilterAuthorizationHttpTest.Fixture.class)
class ResourceFilterAuthorizationHttpTest {
    @LocalServerPort int port;
    @Autowired com.nimbusds.jose.jwk.source.JWKSource<com.nimbusds.jose.proc.SecurityContext> keys;
    @TestConfiguration(proxyBeanMethods = false)
    static class Fixture {
        @Bean ResourceRequestAuthorization adapter() {
            return (authentication, context) -> context.getRequest().getRequestURI().endsWith("/allow")
                    ? new AuthorizationDecision(true) : null;
        }
        @Bean Probe probe() { return new Probe(); }
    }
    @TestComponent
    @RestController
    static class Probe {
        @GetMapping({"/api/filter-foundation/abstain", "/api/filter-foundation/allow"})
        Map<String, Boolean> read() { return Map.of("executed", true); }
    }
    @Test void anonymousAbstentionCannotReachBusinessHandler() throws Exception {
        assertThat(send("abstain", false).statusCode()).isEqualTo(401);
    }
    @Test void authenticatedAbstentionCannotReachBusinessHandler() throws Exception {
        assertThat(send("abstain", true).statusCode()).isEqualTo(403);
    }
    @Test void explicitAdapterAllowanceStillReachesBusinessHandler() throws Exception {
        var response = send("allow", false);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"executed\":true");
    }
    private HttpResponse<String> send(String route, boolean authenticated) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/filter-foundation/" + route));
        if (authenticated) {
            var now = Instant.now();
            var claims = JwtClaimsSet.builder().issuer("http://127.0.0.1:8080").subject("filter-reader")
                    .issuedAt(now).expiresAt(now.plusSeconds(120))
                    .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))).build();
            var token = new NimbusJwtEncoder(keys).encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims));
            request.header("Authorization", "Bearer " + token.getTokenValue());
        }
        return HttpClient.newHttpClient().send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
