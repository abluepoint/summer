package com.abluepoint.summer.sample.identity;

import java.net.URI;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

/** Real sockets, two servlet containers and Redis; never shares MockHttpSession objects. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SharedSessionNodesHttpTest {
    private ConfigurableApplicationContext a, b;
    private final String namespace = "summer:test:nodes:" + UUID.randomUUID();
    private final String password = UUID.randomUUID().toString();
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeAll void startNodes() {
        a = start();
        b = start();
    }

    private ConfigurableApplicationContext start() { return start(6379); }

    private ConfigurableApplicationContext start(int redisPort) { return start(redisPort, false); }

    private ConfigurableApplicationContext start(int redisPort, boolean resourceOnly) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("spring.config.name", "t25-isolated");
        properties.put("server.port", "0");
        properties.put("server.servlet.session.cookie.name", "JSESSIONID");
        properties.put("server.servlet.session.cookie.same-site", "lax");
        properties.put("spring.data.redis.host", "127.0.0.1");
        properties.put("spring.data.redis.port", Integer.toString(redisPort));
        properties.put("spring.data.redis.connect-timeout", "250ms");
        properties.put("spring.data.redis.timeout", "250ms");
        properties.put("spring.data.redis.database", "15");
        properties.put("spring.data.redis.username", System.getenv().getOrDefault("SUMMER_REDIS_USERNAME", "default"));
        properties.put("spring.data.redis.password", System.getenv().getOrDefault("SUMMER_REDIS_PASSWORD", ""));
        properties.put("spring.session.data.redis.namespace", namespace + ":servlet");
        properties.put("summer.sample.session-index.namespace", namespace + ":index");
        properties.put("summer.sample.session-index.enabled", "true");
        properties.put("summer.sample.session-index.timeout-ms", "2000");
        properties.put("summer.security.storage.session", "redis");
        properties.put("summer.security.authorization-server.enabled", Boolean.toString(!resourceOnly));
        properties.put("summer.security.authorization-server.user.username", "node-user");
        properties.put("summer.security.authorization-server.user.password", password);
        properties.put("summer.security.authorization-server.clients[0].id", "node-client");
        properties.put("summer.security.authorization-server.clients[0].public-client", "true");
        properties.put("summer.security.authorization-server.clients[0].grant-types[0]", "authorization_code");
        properties.put("summer.security.authorization-server.clients[0].redirect-uris[0]", "http://127.0.0.1/callback");
        properties.put("summer.security.authorization-server.clients[0].post-logout-redirect-uris[0]", "http://127.0.0.1/signed-out");
        properties.put("summer.security.resource-server.enabled", "true");
        properties.put("spring.main.banner-mode", "off");
        properties.put("logging.level.root", "ERROR");
        return new SpringApplicationBuilder(resourceOnly ? ResourceOnlyNode.class : Node.class).properties(properties).run();
    }

    @AfterAll void stopNodes() {
        try {
            if (a != null) {
                var redis = a.getBean(StringRedisTemplate.class);
                var keys = redis.keys(namespace + ":*");
                if (keys != null && !keys.isEmpty()) redis.delete(keys);
                assertThat(redis.keys(namespace + ":*").isEmpty()).isTrue();
            }
        } finally {
            if (b != null) b.close();
            if (a != null) a.close();
        }
    }

    @Test void sameCookieAuthenticatesOnAnotherNode() throws Exception {
        var browser = login();
        assertThat(send(browser, a, "/api/probe", "GET", null).statusCode()).isEqualTo(200);
        assertThat(send(browser, b, "/api/probe", "GET", null).statusCode()).isEqualTo(200);
    }

    @Test void cookieWriteRequiresCsrf() throws Exception {
        var browser = login();
        assertThat(send(browser, a, "/api/probe", "POST", "").statusCode()).isEqualTo(403);
    }

    @Test void revokedSharedRecordCannotAuthenticateResourceApi() throws Exception {
        var browser = login();
        var response = send(browser, a, "/api/probe", "GET", null);
        assertThat(response.statusCode()).isEqualTo(200);
        var ref = json.readTree(response.body()).path("data").get("reference").asText();
        var admin = org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                "node-user", null, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")));
        a.getBean(SessionIndexService.class).revoke(ref, admin, new org.springframework.mock.web.MockHttpServletRequest());
        assertThat(send(browser, a, "/api/probe", "GET", null).statusCode()).isEqualTo(401);
    }

    @Test void csrfFromOneNodeAuthorizesACookieWriteOnTheOther() throws Exception {
        var browser = login();
        assertThat(writeWithCsrf(browser, b, "/api/probe").statusCode()).isEqualTo(200);
    }

    @Test void bearerValidityIsIndependentOfRevokedCookieAndInvalidBearerNeverFallsBack() throws Exception {
        var browser = login();
        String reference = reference(browser);
        revoke(reference);
        assertThat(send(browser, b, "/api/probe", "GET", null).statusCode()).isEqualTo(401);
        String valid = token(java.time.Instant.now().plusSeconds(300));
        assertThat(bearer(browser, a, valid).statusCode()).isEqualTo(200);
        assertThat(bearer(browser, b, valid).statusCode()).isEqualTo(200);
        var activeBrowser = login();
        assertThat(bearer(activeBrowser, b, "invalid-bearer").statusCode()).isEqualTo(401);
        assertThat(bearer(activeBrowser, b, token(java.time.Instant.now().minusSeconds(120))).statusCode()).isEqualTo(401);
    }

    @Test void logoutRevokesBeforeInvalidatingTheSharedSession() throws Exception {
        var browser = login();
        String reference = reference(browser);
        assertThat(writeWithCsrf(browser, b, "/logout").statusCode()).isEqualTo(302);
        assertThat(send(browser, a, "/api/probe", "GET", null).statusCode()).isEqualTo(401);
        var raw = a.getBean(StringRedisTemplate.class).opsForValue().get(namespace + ":index:session:" + reference);
        assertThat(json.readTree(raw).get("status").asText()).isEqualTo("REVOKED");
    }

    @Test void missingIndexDoesNotRestoreServletAuthentication() throws Exception {
        var browser = login();
        a.getBean(StringRedisTemplate.class).delete(namespace + ":index:session:" + reference(browser));
        assertThat(send(browser, b, "/api/probe", "GET", null).statusCode()).isEqualTo(401);
    }

    @Test void anotherBrowserSessionSurvivesRevocation() throws Exception {
        var first = login();
        var second = login();
        revoke(reference(first));
        assertThat(send(first, b, "/api/probe", "GET", null).statusCode()).isEqualTo(401);
        assertThat(send(second, b, "/api/probe", "GET", null).statusCode()).isEqualTo(200);
    }

    @Test void cookieHasTheConfiguredBrowserBoundary() throws Exception {
        var response = send(HttpClient.newHttpClient(), a, "/csrf", "GET", null);
        String cookie = response.headers().firstValue("set-cookie").orElse("");
        assertThat(cookie.startsWith("JSESSIONID=")).isTrue();
        assertThat(cookie.contains("HttpOnly") && cookie.contains("SameSite=Lax") && cookie.contains("Path=/")).isTrue();
        assertThat(cookie.contains("Secure")).isFalse();
    }

    @Test void redisFailureIsDiagnosticAndDoesNotDisableBearerValidation() throws Exception {
        var browser = login();
        try (var unavailable = start(1)) {
            var failed = send(browser, unavailable, "/api/probe", "GET", null);
            assertThat(failed.statusCode()).isEqualTo(503);
            assertThat(json.readTree(failed.body()).path("error").path("code").asText()).isEqualTo("SUMMER_SESSION_STORE_UNAVAILABLE");
            assertThat(failed.headers().firstValue("X-Request-Id")).isPresent();
            assertThat(bearer(browser, unavailable, token(java.time.Instant.now().plusSeconds(300))).statusCode()).isEqualTo(200);
        }
    }

    @Test void idleExpiryRejectsTheOriginalCookieOnAnotherNode() throws Exception {
        var repository = a.getBean(org.springframework.session.data.redis.RedisSessionRepository.class);
        repository.setDefaultMaxInactiveInterval(java.time.Duration.ofSeconds(1));
        HttpClient browser;
        try { browser = login(); }
        finally { repository.setDefaultMaxInactiveInterval(java.time.Duration.ofMinutes(30)); }
        Thread.sleep(1500);
        assertThat(send(browser, b, "/api/probe", "GET", null).statusCode()).isEqualTo(401);
    }

    @Test void concurrentRefreshCannotResurrectARevokedSession() throws Exception {
        var browser = login();
        String reference = reference(browser);
        var cookies = (CookieManager) browser.cookieHandler().orElseThrow();
        String encoded = cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("JSESSIONID"))
                .findFirst().orElseThrow().getValue();
        String sessionId = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        var service = a.getBean(SessionIndexService.class);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(3)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            var refreshes = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 2; i++) refreshes.add(pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                for (int j = 0; j < 20; j++) service.touch(sessionId, 1800);
            }));
            start.countDown();
            revoke(reference);
            for (var future : refreshes) future.get();
        }
        assertThat(service.load(sessionId).status()).isEqualTo("REVOKED");
        assertThat(send(browser, b, "/api/probe", "GET", null).statusCode()).isEqualTo(401);
    }

    @Test void resourceOnlyApplicationStillValidatesBearerWithoutSessionStorage() throws Exception {
        try (var resource = start(1, true)) {
            assertThat(bearer(HttpClient.newHttpClient(), resource, token(java.time.Instant.now().plusSeconds(300))).statusCode()).isEqualTo(200);
            assertThat(bearer(HttpClient.newHttpClient(), resource, "invalid-bearer").statusCode()).isEqualTo(401);
        }
    }

    @Test void oidcLogoutRevokesTheSharedLoginWhileTheIssuedAccessTokenRemainsValid() throws Exception {
        var browser = login();
        String reference = reference(browser);
        String verifier = UUID.randomUUID().toString() + UUID.randomUUID();
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                java.security.MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        String authorize = "/oauth2/authorize?response_type=code&client_id=node-client&scope=openid%20profile"
                + "&redirect_uri=" + encode("http://127.0.0.1/callback") + "&code_challenge_method=S256&code_challenge=" + challenge;
        var authorization = send(browser, a, authorize, "GET", null);
        assertThat(authorization.statusCode()).isEqualTo(302);
        var location = URI.create(authorization.headers().firstValue("Location").orElseThrow());
        String code = Arrays.stream(location.getRawQuery().split("&")).filter(value -> value.startsWith("code="))
                .map(value -> java.net.URLDecoder.decode(value.substring(5), StandardCharsets.UTF_8)).findFirst().orElseThrow();
        var exchanged = send(browser, a, "/oauth2/token", "POST", "grant_type=authorization_code&client_id=node-client&code="
                + encode(code) + "&redirect_uri=" + encode("http://127.0.0.1/callback") + "&code_verifier=" + encode(verifier));
        assertThat(exchanged.statusCode()).isEqualTo(200);
        var tokens = json.readTree(exchanged.body());
        var logout = send(browser, a, "/connect/logout?id_token_hint=" + encode(tokens.get("id_token").asText())
                + "&post_logout_redirect_uri=" + encode("http://127.0.0.1/signed-out"), "GET", null);
        assertThat(logout.statusCode()).isEqualTo(302);
        assertThat(send(browser, b, "/api/probe", "GET", null).statusCode()).isEqualTo(401);
        assertThat(bearer(browser, b, tokens.get("access_token").asText()).statusCode()).isEqualTo(200);
        var raw = a.getBean(StringRedisTemplate.class).opsForValue().get(namespace + ":index:session:" + reference);
        assertThat(json.readTree(raw).get("status").asText()).isEqualTo("REVOKED");
    }

    private String reference(HttpClient browser) throws Exception {
        var response = send(browser, a, "/api/probe", "GET", null);
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body()).path("data").get("reference").asText();
    }

    private void revoke(String reference) {
        var admin = org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                "node-user", null, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")));
        a.getBean(SessionIndexService.class).revoke(reference, admin, new org.springframework.mock.web.MockHttpServletRequest());
    }

    private HttpResponse<String> writeWithCsrf(HttpClient browser, ConfigurableApplicationContext node, String path) throws Exception {
        var csrf = json.readTree(send(browser, a, "/csrf", "GET", null).body());
        return send(browser, node, path, "POST", encode(csrf.get("parameterName").asText()) + "=" + encode(csrf.get("token").asText()));
    }

    private String token(java.time.Instant expiry) throws Exception {
        var claims = new com.nimbusds.jwt.JWTClaimsSet.Builder().subject("node-user")
                .issueTime(Date.from(java.time.Instant.now().minusSeconds(300))).expirationTime(Date.from(expiry)).build();
        var signed = new com.nimbusds.jwt.SignedJWT(new com.nimbusds.jose.JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.RS256)
                .keyID(Node.KEY.getKeyID()).build(), claims);
        signed.sign(new com.nimbusds.jose.crypto.RSASSASigner(Node.KEY));
        return signed.serialize();
    }

    private HttpResponse<String> bearer(HttpClient client, ConfigurableApplicationContext node, String token) throws Exception {
        int port = ((WebServerApplicationContext) node).getWebServer().getPort();
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/probe"))
                .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpClient login() throws Exception {
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var browser = HttpClient.newBuilder().cookieHandler(cookies).build();
        var csrf = send(browser, a, "/csrf", "GET", null);
        assertThat(csrf.statusCode()).isEqualTo(200);
        var body = json.readTree(csrf.body());
        String form = "username=node-user&password=" + encode(password) + "&"
                + encode(body.get("parameterName").asText()) + "=" + encode(body.get("token").asText());
        assertThat(send(browser, a, "/login", "POST", form).statusCode()).isEqualTo(302);
        return browser;
    }

    private HttpResponse<String> send(HttpClient client, ConfigurableApplicationContext node, String path,
                                      String method, String body) throws Exception {
        int port = ((WebServerApplicationContext) node).getWebServer().getPort();
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (body != null) builder.header("Content-Type", "application/x-www-form-urlencoded");
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({SessionIndexConfiguration.class, SessionIndexService.class, Probe.class})
    static class Node {
        static final com.nimbusds.jose.jwk.RSAKey KEY = signingKey();
        private static com.nimbusds.jose.jwk.RSAKey signingKey() {
            try { return new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048).keyID("node-test").generate(); }
            catch (com.nimbusds.jose.JOSEException failure) { throw new IllegalStateException("Cannot generate test key"); }
        }
        @Bean com.nimbusds.jose.jwk.source.JWKSource<com.nimbusds.jose.proc.SecurityContext> nodeKeys() {
            return (selector, context) -> selector.select(new com.nimbusds.jose.jwk.JWKSet(KEY));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(Probe.class)
    static class ResourceOnlyNode {
        @Bean org.springframework.security.oauth2.jwt.JwtDecoder decoder() throws com.nimbusds.jose.JOSEException {
            return org.springframework.security.oauth2.jwt.NimbusJwtDecoder.withPublicKey(Node.KEY.toRSAPublicKey()).build();
        }
    }

    @RestController
    static class Probe {
        @RequestMapping("/api/probe") Map<String, String> probe(Authentication authentication,
                jakarta.servlet.http.HttpServletRequest request) {
            return Map.of("principal", authentication.getName(), "reference",
                    com.abluepoint.summer.security.autoconfigure.storage.BearerOrSessionSecurityContextRepository.hasBearerCredentials(request)
                            ? "" : SessionIndexService.reference(request.getSession().getId()));
        }
    }
}
