package com.abluepoint.summer.sample.customer;

import com.abluepoint.summer.mvc.logging.RequestLogRecord;
import com.abluepoint.summer.mvc.logging.RequestLogSink;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "summer.mvc.request-body.max-bytes=256", "summer.security.storage.keys=memory",
        "summer.mvc.request-logging.body.enabled=true"})
@AutoConfigureMockMvc
@Import({com.abluepoint.summer.sample.authorization.ExplicitLegacyBusinessDomains.AcmeAndPlatform.class, RequestBodyLimitHttpTest.Logging.class})
@org.springframework.test.annotation.DirtiesContext
class RequestBodyLimitHttpTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JWKSource<SecurityContext> keys;
    @Autowired RecordingSink sink;
    @Value("${local.server.port}") int port;

    @Test void completeFilterChainAcceptsValidBodiesBelowAndExactlyAtTheLimit() throws Exception {
        for (int size : new int[]{0, 256}) {
            String username = username();
            String body = body(username, size);
            try {
                mvc.perform(post("/api/customers").with(jwt().jwt(j -> j.claim("scope", "customer.write")).authorities(authority("write")))
                                .contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.data.username").value(username));
                mvc.perform(get("/api/customers/" + username).with(jwt().jwt(j -> j.claim("scope", "customer.read")).authorities(authority("read"))))
                        .andExpect(status().isOk());
            } finally {
                mvc.perform(delete("/api/customers/" + username).with(jwt().jwt(j -> j.claim("scope", "customer.write")).authorities(authority("write"))));
            }
        }
    }

    @Test void knownAndUnknownLengthOversizeRequestsKeepTheContractAndNeverCreateCustomers() throws Exception {
        for (boolean unknownLength : new boolean[]{false, true}) {
            String username = username();
            String body = body(username, 257);
            var builder = post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(body);
            var response = unknownLength ? mvc.perform(context -> {
                MockHttpServletRequest request = new MockHttpServletRequest(context) {
                    @Override public long getContentLengthLong() { return -1; }
                    @Override public int getContentLength() { return -1; }
                };
                request.setMethod("POST");
                request.setRequestURI("/api/customers");
                request.setContentType(MediaType.APPLICATION_JSON_VALUE);
                request.setContent(body.getBytes(StandardCharsets.UTF_8));
                return jwt().jwt(j -> j.claim("scope", "customer.write")).authorities(authority("write")).postProcessRequest(request);
            }).andReturn().getResponse() : mvc.perform(builder.with(jwt().jwt(j -> j.claim("scope", "customer.write")).authorities(authority("write")))
                    .header("X-Request-Id", "body-limit-known")).andReturn().getResponse();
            assertLimit(response.getStatus(), response.getContentAsString(), response.getHeader("X-Request-Id"), response.getContentType());
            if (!unknownLength) assertThat(response.getHeader("X-Request-Id")).isEqualTo("body-limit-known");
            assertMissing(username);
            assertThat(sink.records).anySatisfy(record -> {
                assertThat(record.requestId()).isEqualTo(response.getHeader("X-Request-Id"));
                assertThat(record.status()).isEqualTo(413);
                assertThat(record.bodyPreview()).isNull();
            });
        }
    }

    @Test void realHttpFixedAndChunkedBodiesRespectTheExactLimitAndStopBeforeBusiness() throws Exception {
        String token = token();
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1).build()) {
            for (boolean chunked : new boolean[]{false, true}) {
                String accepted = username();
                try {
                    var success = send(client, token, accepted, 256, chunked);
                    assertThat(success.statusCode()).isEqualTo(200);
                    assertThat(mapper.readTree(success.body()).path("data").path("username").asString()).isEqualTo(accepted);
                } finally {
                    mvc.perform(delete("/api/customers/" + accepted).with(jwt().jwt(j -> j.claim("scope", "customer.write")).authorities(authority("write"))));
                }
                String rejected = username();
                var failure = send(client, token, rejected, 257, chunked);
                assertLimit(failure.statusCode(), failure.body(), failure.headers().firstValue("X-Request-Id").orElseThrow(),
                        failure.headers().firstValue("Content-Type").orElseThrow());
                assertThat(failure.headers().firstValue("X-Request-Id")).contains("body-limit-tcp");
                assertMissing(rejected);
            }
            var missing = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                            + "/api/customers/" + username())).header("Authorization", "Bearer " + token)
                    .header("X-Request-Id", "body-limit-missing").timeout(Duration.ofSeconds(15)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(missing.statusCode()).isEqualTo(404);
            assertThat(mapper.readTree(missing.body()).path("error").path("code").asString()).isEqualTo("SUMMER_NOT_FOUND");
            assertThat(mapper.readTree(missing.body()).path("requestId").asString()).isEqualTo("body-limit-missing");
        }
    }

    private HttpResponse<String> send(HttpClient client, String token, String username, int size, boolean chunked) throws Exception {
        byte[] bytes = body(username, size).getBytes(StandardCharsets.UTF_8);
        HttpRequest.BodyPublisher publisher = chunked ? HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(bytes))
                : HttpRequest.BodyPublishers.ofByteArray(bytes);
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/customers"))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                .header("X-Request-Id", "body-limit-tcp").timeout(Duration.ofSeconds(15)).POST(publisher).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private void assertLimit(int status, String json, String requestId, String contentType) {
        assertThat(MediaType.parseMediaType(contentType).isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
        assertThat(status).isEqualTo(413);
        assertThat(requestId).isNotBlank().isNotEqualTo("unbound");
        var body = mapper.readTree(json);
        assertThat(body.path("requestId").asString()).isEqualTo(requestId);
        assertThat(body.path("error").path("code").asString()).isEqualTo("SUMMER_REQUEST_BODY_TOO_LARGE");
        assertThat(body.path("error").path("message").asString()).isEqualTo("Request body too large");
        assertThat(body.path("error").path("violations").size()).isZero();
        assertThat(body.has("cause")).isFalse();
        assertThat(body.path("error").has("cause")).isFalse();
        assertThat(body.path("error").has("stackTrace")).isFalse();
        assertThat(json.split("SUMMER_REQUEST_BODY_TOO_LARGE", -1)).hasSize(2);
    }

    private void assertMissing(String username) throws Exception {
        mvc.perform(get("/api/customers/" + username).with(jwt().jwt(j -> j.claim("scope", "customer.read")).authorities(authority("read"))))
                .andExpect(status().isNotFound());
    }

    private String body(String username, int size) {
        String json = mapper.writeValueAsString(Map.of("username", username, "email", "limit@example.com", "displayName", "Limit"));
        return size == 0 ? json : json + " ".repeat(size - json.getBytes(StandardCharsets.UTF_8).length);
    }

    private static String username() { return "limit-" + UUID.randomUUID(); }
    private static SimpleGrantedAuthority authority(String operation) { return new SimpleGrantedAuthority("SCOPE_customer." + operation); }
    private String token() {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("http://127.0.0.1:8080").subject("body-limit-check")
                .issuedAt(now).expiresAt(now.plusSeconds(120)).claim("scope", "customer.read customer.write").build();
        return new NimbusJwtEncoder(keys).encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
    }

    @TestConfiguration
    static class Logging { @Bean @Primary RecordingSink bodyLimitLogSink() { return new RecordingSink(); } }
    static final class RecordingSink implements RequestLogSink {
        final ConcurrentLinkedQueue<RequestLogRecord> records = new ConcurrentLinkedQueue<>();
        @Override public void record(RequestLogRecord record) { records.add(record); }
    }
}
