package com.abluepoint.summer.sample.task;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "summer.security.storage.keys=memory", "spring.quartz.job-store-type=memory",
        "spring.datasource.hikari.maximum-pool-size=40"})
@org.springframework.context.annotation.Import(com.abluepoint.summer.sample.authorization.ExplicitLegacyBusinessDomains.AcmeAndPlatform.class)
@org.springframework.test.annotation.DirtiesContext
class TaskProgressTcpIntegrationTest {
    @Value("${local.server.port}") int port;
    @Autowired JWKSource<SecurityContext> keys;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void liveStreamsUseRawFramesLimitAdmissionAndReleaseClosedConnections() throws Exception {
        List<InputStream> streams = new ArrayList<>();
        try (var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
            String token = token();
            var created = client.send(request("/api/v1/enterprises/ent-acme/tasks", token)
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("""
                            {"name":"TCP progress admission","taskType":"system.noop","cronExpression":"0 0 0 1 1 ? 2099"}
                            """)).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(created.statusCode()).isEqualTo(201);
            String task = json.readTree(created.body()).path("data").path("taskId").asString();
            String events = "/api/v1/enterprises/ent-acme/tasks/" + task + "/events";
            try {
                for (int index = 0; index < 32; index++) {
                    var response = subscribe(client, events, token);
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("text/event-stream");
                    streams.add(response.body());
                    var reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8));
                    StringBuilder firstFrame = new StringBuilder();
                    for (String line; (line = reader.readLine()) != null && !line.isEmpty();) firstFrame.append(line).append('\n');
                    assertThat(firstFrame.toString()).contains("event:connected").doesNotContain("\"success\"", "\"error\"");
                }
                var limit = subscribe(client, events, token);
                try (var body = limit.body()) {
                    assertThat(limit.statusCode()).isEqualTo(429);
                    assertThat(limit.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/json");
                    var error = json.readTree(body.readAllBytes());
                    assertThat(error.path("error").path("code").asString()).isEqualTo("SUMMER_TASK_SSE_LIMIT");
                    assertThat(error.path("error").path("message").asString()).isEqualTo("Too many task progress connections");
                    assertThat(error.path("requestId").asString()).isEqualTo("task-progress-tcp");
                }

                streams.removeFirst().close();
                var trigger = client.send(request("/api/v1/enterprises/ent-acme/tasks/" + task + ":trigger", token)
                        .header("Idempotency-Key", "tcp-release-slot").POST(HttpRequest.BodyPublishers.noBody()).build(),
                        HttpResponse.BodyHandlers.ofString());
                assertThat(trigger.statusCode()).isEqualTo(202);
                int reopened = 429;
                Instant deadline = Instant.now().plusSeconds(20);
                while (reopened == 429 && Instant.now().isBefore(deadline)) {
                    var response = subscribe(client, events, token);
                    reopened = response.statusCode();
                    if (reopened == 200) streams.add(response.body());
                    else {
                        response.body().close();
                        Thread.sleep(100);
                    }
                }
                assertThat(reopened).as("closed socket frees a real admission slot").isEqualTo(200);
            } finally {
                for (var stream : streams) stream.close();
            }
        }
    }

    private HttpResponse<InputStream> subscribe(HttpClient client, String path, String token) throws Exception {
        return client.send(request(path, token).header("Accept", "text/event-stream").GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
    }

    private HttpRequest.Builder request(String path, String token) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token).header("X-Request-Id", "task-progress-tcp");
    }

    private String token() {
        Instant now = Instant.now();
        var capabilities = List.of("enterprise.tasks.write", "enterprise.tasks.read");
        var claims = JwtClaimsSet.builder().issuer("http://127.0.0.1:8080").subject("task-progress-tcp")
                .issuedAt(now.minusSeconds(5)).expiresAt(now.plusSeconds(180)).claim("scope", String.join(" ", capabilities))
                .claim("active_enterprise_id", "ent-acme")
                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                .claim("enterprise_capabilities", Map.of("ent-acme", capabilities)).build();
        return new NimbusJwtEncoder(keys).encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
    }
}
