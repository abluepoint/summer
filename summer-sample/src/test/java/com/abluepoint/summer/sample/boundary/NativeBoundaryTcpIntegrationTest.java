package com.abluepoint.summer.sample.boundary;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.abluepoint.summer.security.autoconfigure.SummerSecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;

import static org.assertj.core.api.Assertions.assertThat;

/** Real TCP HTTP/WS checks, separate from MockMvc and handler/session evidence. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "summer.security.storage.keys=memory",
        "management.endpoints.web.exposure.include=health,info,metrics,prometheus",
        "summer.security.resource-server.protected-paths[0]=/api/**",
        "summer.security.resource-server.protected-paths[1]=/ws/**"})
@Import({com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture.class, NativeBoundaryHttpIntegrationTest.CommittedEndpoint.class, NativeBoundaryTcpIntegrationTest.NativeFilter.class})
@org.springframework.test.annotation.DirtiesContext
class NativeBoundaryTcpIntegrationTest {
    @org.springframework.beans.factory.annotation.Value("${local.server.port}") int port;
    @Autowired JWKSource<SecurityContext> keys;
    @Autowired SummerSecurityProperties security;
    private final ObjectMapper json = new ObjectMapper();

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class NativeFilter {
        @org.springframework.context.annotation.Bean
        org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter> nativeIoFailureFilter() {
            var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter>();
            registration.setFilter((request, response, chain) -> { throw new IOException("private filter diagnostic"); });
            registration.addUrlPatterns("/boundary/native-filter-error");
            registration.setDispatcherTypes(jakarta.servlet.DispatcherType.REQUEST);
            return registration;
        }
    }

    @Autowired com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture current;

    @org.junit.jupiter.api.BeforeEach
    void grantCurrentInterfaces() {
        var platform = com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform();
        current.grantHandlers(platform, "native-boundary-tcp", (handler, path, method) ->
                (handler.getBeanType().getName().equals("com.abluepoint.summer.sample.customer.CustomerController")
                        && java.util.Set.of("POST", "DELETE").contains(method))
                || (handler.getBeanType().getName().equals("com.abluepoint.summer.sample.customer.CustomerAuditController")
                        && method.equals("GET")));
        current.grantInstalled(platform, "native-boundary-tcp", mapping -> mapping.source().equals("MONITORING")
                && mapping.handler().startsWith("org.springframework.boot.webmvc.actuate.endpoint.web.AbstractWebMvcEndpointHandlerMapping$OperationHandler@")
                && mapping.method().equals("GET")
                && java.util.Set.of("/actuator/metrics", "/actuator/prometheus").contains(mapping.path()));
        current.grantInstalled(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise("ent-acme"), "native-boundary-tcp",
                mapping -> mapping.source().equals("RUNTIME") && mapping.handler().startsWith("com.abluepoint.summer.sample.realtime.RealtimeAccess@")
                        && mapping.handler().endsWith("#read") && mapping.method().equals("WS")
                        && mapping.path().equals("/ws/v1/messages"));
    }

    @Test void filterIOExceptionBeforeDispatcherRemainsANativeContainerError() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var response = send(client, "GET", "/boundary/native-filter-error", null, null);
            assertThat(response.statusCode()).isEqualTo(500);
            var body = json.readTree(response.body());
            assertThat(body.path("status").asInt()).isEqualTo(500);
            assertThat(body.path("error").asString()).isEqualTo("Internal Server Error");
            assertThat(body.has("success")).isFalse();
            assertThat(response.body()).doesNotContain("private filter diagnostic", "stackTrace", "SUMMER_INTERNAL_ERROR");
        }
    }

    @Test void jsonFailureAndUnsupportedMethodRetainStatusAllowAndCorrelationOnTheWire() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var missing = send(client, "GET", "/api/audit-events/no-native-tcp-event", null, token("customer.read"));
            assertThat(missing.statusCode()).isEqualTo(404);
            var body = json.readTree(missing.body());
            assertThat(body.path("error").path("code").asString()).isEqualTo("SUMMER_NOT_FOUND");
            assertThat(body.path("requestId").asString()).isEqualTo("native-tcp");
            assertThat(missing.headers().firstValue("X-Request-Id")).contains("native-tcp");
            var method = send(client, "PATCH", "/api/audit-events/no-native-tcp-event", "{}", token("customer.read"));
            assertThat(method.statusCode()).isEqualTo(405);
            assertThat(method.headers().firstValue("Allow").orElseThrow()).contains("GET");
            assertThat(json.readTree(method.body()).path("error").path("code").asString()).isEqualTo("SUMMER_METHOD_NOT_ALLOWED");
            var anonymous = send(client, "GET", "/api/customers", null, null);
            assertThat(anonymous.statusCode()).isEqualTo(401);
            assertThat(json.readTree(anonymous.body()).path("error").path("code").asString()).isEqualTo("SUMMER_UNAUTHORIZED");
        }
    }

    @Test void nativeProtocolErrorsAndDiscoveryRemainOutsideTheBusinessEnvelope() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var failure = send(client, "POST", "/oauth2/token", "client_id=summer-ui&grant_type=password", null);
            assertThat(failure.statusCode()).isBetween(400, 499);
            // An unauthenticated public-client attempt may have an empty native error body.
            assertThat(failure.body()).doesNotContain("\"success\"", "\"data\"");
            var confidential = security.getAuthorizationServer().getClients().stream()
                    .filter(candidate -> candidate.getId().equals("summer-client")).findFirst().orElseThrow();
            String credentials = Base64.getEncoder().encodeToString((confidential.getId() + ":" + confidential.getSecret())
                    .getBytes(StandardCharsets.UTF_8));
            var protocol = client.send(request("/oauth2/token").header("Authorization", "Basic " + credentials)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("grant_type=password")).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(protocol.statusCode()).isEqualTo(400);
            var body = json.readTree(protocol.body());
            assertThat(body.path("error").asString()).isEqualTo("unsupported_grant_type");
            assertThat(body.has("success")).isFalse();
            assertThat(body.has("data")).isFalse();
            var discovery = send(client, "GET", "/.well-known/openid-configuration", null, null);
            assertThat(discovery.statusCode()).isEqualTo(200);
            var metadata = json.readTree(discovery.body());
            assertThat(metadata.path("issuer").asString()).isEqualTo("http://127.0.0.1:8080");
            assertThat(metadata.path("end_session_endpoint").asString()).endsWith("/connect/logout");
            assertThat(metadata.has("success")).isFalse();
        }
    }

    @Test void actualLoginSessionKeepsCsrfAndMethodSecuritySeparateFromTheGenericFallback() throws Exception {
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        try (var client = HttpClient.newBuilder().cookieHandler(cookies).build()) {
            var csrf = send(client, "GET", "/csrf", null, null);
            assertThat(csrf.statusCode()).isEqualTo(200);
            var csrfBody = json.readTree(csrf.body());
            assertThat(csrfBody.has("success")).isFalse();
            assertThat(csrf.headers().firstValue("Cache-Control").orElseThrow()).contains("no-store");
            var account = security.getAuthorizationServer().getUser();
            // Consume configured test credentials in-memory; never emit them in assertions/logs.
            String login = "username=" + encode(account.getUsername()) + "&password=" + encode(account.getPassword());
            assertThat(send(client, "POST", "/login", login, null).statusCode()).isEqualTo(403);
            assertThat(send(client, "POST", "/login", login + "&" + encode(csrfBody.path("parameterName").asString())
                    + "=invalid", null).statusCode()).isEqualTo(403);
            var loggedIn = send(client, "POST", "/login", login + "&" + encode(csrfBody.path("parameterName").asString())
                    + "=" + encode(csrfBody.path("token").asString()), null);
            assertThat(loggedIn.statusCode()).isEqualTo(302);
            assertThat(loggedIn.headers().firstValue("Location").orElseThrow()).doesNotContain("error");
            var denied = send(client, "GET", "/api/customers", null, null);
            assertThat(denied.statusCode()).isEqualTo(403);
            assertThat(json.readTree(denied.body()).path("error").path("code").asString()).isEqualTo("SUMMER_FORBIDDEN");
            assertThat(json.readTree(denied.body()).path("requestId").asString()).isEqualTo("native-tcp");
        }
    }

    @Test void rawCsvAndNoContentRemainRawThroughTheContainer() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            String name = "native." + UUID.randomUUID().toString().replace("-", "");
            String bearer = token("customer.write", "customer.read");
            var created = send(client, "POST", "/api/customers", "{\"username\":\"" + name
                    + "\",\"email\":\"" + name + "@example.test\",\"displayName\":\"Native boundary\"}", bearer);
            assertThat(created.statusCode()).isEqualTo(200);
            try {
                var export = send(client, "GET", "/api/audit-events/export?resource=customer:" + name, null, bearer);
                assertThat(export.statusCode()).isEqualTo(200);
                assertThat(export.headers().firstValue("Content-Type").orElseThrow()).startsWith("text/csv");
                assertThat(export.headers().firstValue("Content-Disposition")).contains("attachment; filename=audit-events.csv");
                assertThat(export.body()).startsWith("eventId,tenantId").doesNotContain("\"success\":");
            } finally {
                var deleted = send(client, "DELETE", "/api/customers/" + name, null, bearer);
                assertThat(deleted.statusCode()).isEqualTo(204);
                assertThat(deleted.body()).isEmpty();
            }
        }
    }

    @Test void actualStaticResourceHandlerKeepsRangeAndNativeNotFound() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var range = client.send(request("/boundary/native-transfer.txt").header("Range", "bytes=0-5").GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(range.statusCode()).isEqualTo(206);
            assertThat(range.headers().firstValue("Content-Range")).contains("bytes 0-5/27");
            assertThat(range.body()).isEqualTo("native");
            var missing = send(client, "GET", "/boundary/no-such-native-file.txt", null, null);
            assertThat(missing.statusCode()).isEqualTo(404);
            assertThat(missing.body()).doesNotContain("\"success\"", "SUMMER_INTERNAL_ERROR");
        }
    }

    @Test void monitoringKeepsNativeBodiesAndTheExistingSecurityFailureContract() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var anonymous = send(client, "GET", "/actuator/metrics", null, null);
            assertThat(anonymous.statusCode()).isEqualTo(401);
            assertThat(json.readTree(anonymous.body()).path("error").path("code").asString()).isEqualTo("SUMMER_UNAUTHORIZED");
            var forbidden = send(client, "GET", "/actuator/metrics", null, token("customer.read"));
            assertThat(forbidden.statusCode()).isEqualTo(403);
            assertThat(json.readTree(forbidden.body()).path("error").path("code").asString()).isEqualTo("SUMMER_FORBIDDEN");
            String monitor = token("platform.capabilities.read");
            var metrics = send(client, "GET", "/actuator/metrics", null, monitor);
            assertThat(metrics.statusCode()).isEqualTo(200);
            assertThat(json.readTree(metrics.body()).path("names").isArray()).isTrue();
            assertThat(json.readTree(metrics.body()).has("success")).isFalse();
            var prometheus = send(client, "GET", "/actuator/prometheus", null, monitor);
            assertThat(prometheus.statusCode()).isEqualTo(200);
            assertThat(prometheus.body()).contains("jvm_memory_used_bytes").doesNotContain("\"success\"");
        }
    }

    @Test void websocketHandshakeRejectsAnonymousAndInvalidCursorsAtTheProtocolBoundary() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var uri = URI.create("ws://127.0.0.1:" + port + "/ws/v1/messages?enterpriseId=ent-acme");
            var anonymous = org.junit.jupiter.api.Assertions.assertThrows(ExecutionException.class,
                    () -> client.newWebSocketBuilder().buildAsync(uri, new Frames()).get(10, TimeUnit.SECONDS));
            assertThat(anonymous.getCause()).isInstanceOf(WebSocketHandshakeException.class);
            assertThat(((WebSocketHandshakeException) anonymous.getCause()).getResponse().statusCode()).isEqualTo(401);
            var invalid = org.junit.jupiter.api.Assertions.assertThrows(ExecutionException.class,
                    () -> client.newWebSocketBuilder().header("Authorization", "Bearer " + token("enterprise.messages.read"))
                            .buildAsync(URI.create(uri + "&after=invalid"), new Frames()).get(10, TimeUnit.SECONDS));
            assertThat(invalid.getCause()).isInstanceOf(WebSocketHandshakeException.class);
            assertThat(((WebSocketHandshakeException) invalid.getCause()).getResponse().statusCode()).isEqualTo(400);
        }
    }

    @Test void committedTechnicalAndSummerFailuresPreserveOnlyPreviouslySentBytes() throws Exception {
        try (var client = HttpClient.newHttpClient(); var readers = Executors.newVirtualThreadPerTaskExecutor()) {
            for (String kind : new String[]{"technical", "summer"}) {
                var response = client.send(request("/boundary/committed/" + kind).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
                assertThat(response.statusCode()).isEqualTo(200);
                try (var stream = response.body()) {
                    var bytes = readers.submit(() -> {
                        var received = new ByteArrayOutputStream();
                        try {
                            int value;
                            while ((value = stream.read()) != -1) received.write(value);
                        } catch (IOException disconnected) {
                            // Container may terminate chunked transfer after the native error.
                        }
                        return received.toString(StandardCharsets.UTF_8);
                    });
                    assertThat(bytes.get(10, TimeUnit.SECONDS)).isEqualTo("already-streamed\n");
                }
            }
        }
    }

    @Test void websocketUsesRealHandshakeErrorFramesAndPolicyCloseRatherThanHttpJson() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            String bearer = token("enterprise.messages.read");
            var frames = new Frames();
            WebSocket socket = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + bearer)
                    .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws/v1/messages?enterpriseId=ent-acme"), frames)
                    .get(15, TimeUnit.SECONDS);
            try {
                assertThat(frame(frames).path("type").asString()).isEqualTo("connected");
                socket.sendText("{\"type\":\"unsupported\"}", true).get(5, TimeUnit.SECONDS);
                var error = frame(frames);
                assertThat(error.path("type").asString()).isEqualTo("error");
                assertThat(error.path("code").asString()).isEqualTo("SUMMER_MESSAGE_INVALID");
                assertThat(error.has("success")).isFalse();
                socket.sendText("{\"type\":\"ping\"}", true).get(5, TimeUnit.SECONDS);
                assertThat(frame(frames).path("type").asString()).isEqualTo("pong");
                socket.sendText("{\"type\":\"send\",\"clientMessageId\":\"native-no-write\",\"payload\":{\"text\":\"denied\"}}", true)
                        .get(5, TimeUnit.SECONDS);
                assertThat(frames.close.get(10, TimeUnit.SECONDS)).isEqualTo(1008);
            } finally { socket.abort(); }
        }
    }

    private JsonNode frame(Frames frames) throws Exception {
        String frame = frames.messages.poll(10, TimeUnit.SECONDS);
        assertThat(frame).as("a real WebSocket text frame arrives").isNotNull();
        return json.readTree(frame);
    }

    private static final class Frames implements WebSocket.Listener {
        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private final CompletableFuture<Integer> close = new CompletableFuture<>();
        private final StringBuilder fragments = new StringBuilder();
        @Override public void onOpen(WebSocket socket) { socket.request(1); }
        @Override public CompletionStage<?> onText(WebSocket socket, CharSequence text, boolean last) {
            fragments.append(text);
            if (last) { messages.add(fragments.toString()); fragments.setLength(0); }
            socket.request(1);
            return null;
        }
        @Override public CompletionStage<?> onClose(WebSocket socket, int statusCode, String reason) {
            close.complete(statusCode); return null;
        }
        @Override public void onError(WebSocket socket, Throwable error) { close.completeExceptionally(error); }
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(15))
                .header("X-Request-Id", "native-tcp");
    }
    private HttpResponse<String> send(HttpClient client, String method, String path, String body, String bearer) throws Exception {
        var request = request(path);
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        if (body != null) request.header("Content-Type", path.equals("/login") || path.equals("/oauth2/token")
                ? "application/x-www-form-urlencoded" : "application/json");
        return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private String token(String... scopes) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("http://127.0.0.1:8080").subject("native-boundary-tcp")
                .issuedAt(now.minusSeconds(5)).expiresAt(now.plusSeconds(180)).claim("scope", String.join(" ", scopes))
                .claim("active_enterprise_id", "ent-acme")
                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                .claim("enterprise_capabilities", Map.of("ent-acme", List.of(scopes))).build();
        return new NimbusJwtEncoder(keys).encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
