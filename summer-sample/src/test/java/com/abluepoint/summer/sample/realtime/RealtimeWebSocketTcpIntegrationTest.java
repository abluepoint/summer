package com.abluepoint.summer.sample.realtime;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Signed JWT, embedded HTTP server and real JDK WebSocket clients; no handler/session mocks. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "summer.security.storage.keys=memory",
        "summer.security.resource-server.protected-paths[0]=/api/**",
        "summer.security.resource-server.protected-paths[1]=/ws/**",
        "summer.sample.realtime.idle-timeout-ms=120000"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@org.springframework.context.annotation.Import(com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture.class)
class RealtimeWebSocketTcpIntegrationTest {
    @org.springframework.beans.factory.annotation.Value("${local.server.port}") int port;
    @Autowired JWKSource<SecurityContext> keys;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture current;
    private final ObjectMapper json = new ObjectMapper();
    private String subject;

    @BeforeEach void provisionCurrentGrants() {
        subject = "realtime-tcp-" + UUID.randomUUID();
        var enterprise = com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise("ent-acme");
        current.grantInstalled(enterprise, subject, mapping -> mapping.source().equals("RUNTIME")
                && mapping.handler().startsWith("com.abluepoint.summer.sample.realtime.RealtimeAccess@")
                && (mapping.handler().endsWith("#read") || mapping.handler().endsWith("#send"))
                && mapping.method().equals("WS") && mapping.path().equals("/ws/v1/messages"));
        current.grantHandlers(enterprise, subject, (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.realtime.RealtimeMessageController")
                        && method.equals("GET")
                        && path.equals("/api/v1/enterprises/{enterpriseId}/messages/connections"));
    }

    @Test void malformedCursorIsRejectedBeforeUpgradeWithoutBusinessJson() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            ExecutionException rejected = assertThrows(ExecutionException.class,
                    () -> open(client, token(), new Frames(), "&after=-1"));
            assertThat(rejected.getCause()).isInstanceOf(WebSocketHandshakeException.class);
            var response = ((WebSocketHandshakeException) rejected.getCause()).getResponse();
            assertThat(response.statusCode()).isEqualTo(400);
            // Failure is a native handshake rejection, never a successful upgraded connection.
            assertThat(response.headers().firstValue("Upgrade")).isEmpty();
        }
    }

    @Test void upgradedBusinessErrorsRemainFramesAndValidPingStillWorks() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            Frames frames = new Frames(); WebSocket socket = open(client, token(), frames, "");
            try {
                assertThat(frame(frames, "connected").path("enterpriseId").asString()).isEqualTo("ent-acme");
                socket.sendText("{\"type\":\"send\",\"clientMessageId\":\"bad-payload\",\"payload\":{}}", true).get(5, TimeUnit.SECONDS);
                assertError(frame(frames, "error"), "SUMMER_MESSAGE_INVALID", "payload must not be empty");
                socket.sendText("{\"type\":\"ack\",\"messageId\":\"missing-message\"}", true).get(5, TimeUnit.SECONDS);
                assertError(frame(frames, "error"), "SUMMER_NOT_FOUND", "Message was not found");
                socket.sendText("{\"type\":\"ping\"}", true).get(5, TimeUnit.SECONDS);
                assertThat(frame(frames, "pong").size()).isEqualTo(1);
                assertThat(frames.closed.isDone()).isFalse();
            } finally { socket.abort(); }
            awaitConnections(client, token(), 0);
        }
    }

    @Test void thirtyThirdUpgradeCloses1013AndReleasedSlotAcceptsAnotherConnection() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            String bearer = token(); var sockets = new ArrayList<WebSocket>(); var listeners = new ArrayList<Frames>();
            try {
                for (int i = 0; i < 32; i++) {
                    Frames frames = new Frames(); WebSocket socket = open(client, bearer, frames, "");
                    sockets.add(socket); listeners.add(frames);
                    assertThat(frame(frames, "connected").path("type").asString()).isEqualTo("connected");
                }
                awaitConnections(client, bearer, 32);
                Frames rejected = new Frames(); WebSocket excess = open(client, bearer, rejected, "");
                sockets.add(excess);
                assertThat(rejected.opened.get(5, TimeUnit.SECONDS)).isNotNull();
                Closed close = rejected.closed.get(10, TimeUnit.SECONDS);
                assertThat(close.code()).isEqualTo(1013); assertThat(close.reason()).isEqualTo("connection limit");
                assertThat(rejected.messages).isEmpty();
                awaitConnections(client, bearer, 32);
                sockets.getFirst().sendClose(1000, "release slot").get(5, TimeUnit.SECONDS);
                assertThat(listeners.getFirst().closed.get(10, TimeUnit.SECONDS).code()).isEqualTo(1000);
                awaitConnections(client, bearer, 31);
                Frames replacement = new Frames(); WebSocket accepted = open(client, bearer, replacement, "");
                sockets.add(accepted); frame(replacement, "connected"); awaitConnections(client, bearer, 32);
                accepted.sendText("{\"type\":\"ping\"}", true).get(5, TimeUnit.SECONDS);
                frame(replacement, "pong");
            } finally {
                sockets.forEach(WebSocket::abort);
                awaitConnections(client, bearer, 0);
            }
        }
    }

    @Test void persistedRevocationClosesAnAlreadyUpgradedConnection1008() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            String bearer = token(); Frames frames = new Frames(); WebSocket socket = open(client, bearer, frames, "");
            try {
                frame(frames, "connected");
                socket.sendText("{\"type\":\"ping\"}", true).get(5, TimeUnit.SECONDS); frame(frames, "pong");
                jdbc.update("update summer_enterprise_member set status='DISABLED' where enterprise_id=? and subject=?", "ent-acme", subject);
                socket.sendText("{\"type\":\"ping\"}", true).get(5, TimeUnit.SECONDS);
                assertThat(frames.closed.get(10, TimeUnit.SECONDS).code()).isEqualTo(1008);
                assertThat(frames.messages.stream().map(json::readTree).noneMatch(node -> "pong".equals(node.path("type").asString()))).isTrue();
            } finally { socket.abort(); }
        }
    }

    private WebSocket open(HttpClient client, String bearer, Frames frames, String suffix) throws Exception {
        return client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + bearer)
                .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws/v1/messages?enterpriseId=ent-acme" + suffix), frames)
                .get(15, TimeUnit.SECONDS);
    }

    private JsonNode frame(Frames frames, String type) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < until) {
            String value = frames.messages.poll(1, TimeUnit.SECONDS);
            if (value == null) continue;
            JsonNode node = json.readTree(value);
            if ("heartbeat".equals(node.path("type").asString())) continue;
            assertThat(node.path("type").asString()).isEqualTo(type); return node;
        }
        throw new AssertionError("Missing WebSocket frame: " + type);
    }

    private void awaitConnections(HttpClient client, String bearer, int expected) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10); int actual = -1;
        while (System.nanoTime() < until) {
            var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/enterprises/ent-acme/messages/connections"))
                    .header("Authorization", "Bearer " + bearer).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            actual = json.readTree(response.body()).path("data").path("connections").asInt();
            if (actual == expected) return;
            Thread.sleep(25);
        }
        assertThat(actual).isEqualTo(expected);
    }

    private static void assertError(JsonNode node, String code, String message) {
        assertThat(node.path("code").asString()).isEqualTo(code); assertThat(node.path("message").asString()).isEqualTo(message);
        assertThat(node.has("success")).isFalse(); assertThat(node.has("error")).isFalse(); assertThat(node.has("cause")).isFalse();
    }

    private String token() {
        Instant now = Instant.now(); List<String> grants = List.of(RealtimeMessageService.READ, RealtimeMessageService.WRITE);
        var claims = JwtClaimsSet.builder().issuer("http://127.0.0.1:8080").subject(subject)
                .issuedAt(now.minusSeconds(5)).expiresAt(now.plusSeconds(300)).claim("scope", String.join(" ", grants))
                .claim("summer_managed_grants", true).claim("active_enterprise_id", "ent-acme")
                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                .claim("enterprise_capabilities", Map.of("ent-acme", grants)).build();
        return new NimbusJwtEncoder(keys).encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
    }

    private record Closed(int code, String reason) {}
    private static final class Frames implements WebSocket.Listener {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        final CompletableFuture<WebSocket> opened = new CompletableFuture<>();
        final CompletableFuture<Closed> closed = new CompletableFuture<>();
        final StringBuilder fragments = new StringBuilder();
        @Override public void onOpen(WebSocket socket) { opened.complete(socket); socket.request(1); }
        @Override public CompletionStage<?> onText(WebSocket socket, CharSequence text, boolean last) {
            fragments.append(text); if (last) { messages.add(fragments.toString()); fragments.setLength(0); }
            socket.request(1); return null;
        }
        @Override public CompletionStage<?> onClose(WebSocket socket, int code, String reason) { closed.complete(new Closed(code, reason)); return null; }
        @Override public void onError(WebSocket socket, Throwable failure) { closed.completeExceptionally(failure); }
    }
}
