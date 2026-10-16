package com.abluepoint.summer.sample.identity;

import io.lettuce.core.RedisCommandTimeoutException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import java.sql.SQLTimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.connection.lettuce.LettuceExceptionConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.MapSession;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.web.http.SessionRepositoryFilter;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionFailureConfigurationTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void servletSessionReadTimeoutReturns503WithFailureEnvelope() throws Exception {
        // Same Lettuce -> Spring Data translation as the native paused-Redis failure.
        var timeout = new LettuceExceptionConverter().convert(new RedisCommandTimeoutException("command timed out"));
        assertThat(timeout).isInstanceOf(QueryTimeoutException.class);
        @SuppressWarnings("unchecked")
        SessionRepository<MapSession> repository = mock(SessionRepository.class);
        when(repository.findById("existing-session")).thenThrow(timeout);
        var session = new SessionRepositoryFilter<>(repository);
        var serializer = new DefaultCookieSerializer();
        serializer.setCookieName("JSESSIONID");
        serializer.setUseBase64Encoding(false);
        var resolver = new CookieHttpSessionIdResolver();
        resolver.setCookieSerializer(serializer);
        session.setHttpSessionIdResolver(resolver);
        var request = new MockHttpServletRequest("GET", "/internal/v1/release/readiness");
        request.setCookies(new Cookie("JSESSIONID", "existing-session"));
        var response = new MockHttpServletResponse();
        var filter = new SessionFailureConfiguration().sharedSessionFailureFilter(mapper).getFilter();

        assertThatCode(() -> filter.doFilter(request, response, (req, res) ->
                session.doFilter(req, res, (sessionRequest, sessionResponse) ->
                        ((jakarta.servlet.http.HttpServletRequest) sessionRequest).getSession(false))))
                .doesNotThrowAnyException();

        assertThat(response.getStatus()).isEqualTo(503);
        var body = mapper.readTree(response.getContentAsString());
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("data").isNull()).isTrue();
        assertThat(body.path("error").path("code").asText()).isEqualTo("SUMMER_SESSION_STORE_UNAVAILABLE");
        assertThat(body.path("requestId").asText()).isNotBlank().isEqualTo(response.getHeader("X-Request-Id"));
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    void jdbcQueryTimeoutIsNotMisclassifiedAsSessionRedisFailure() {
        var timeout = new QueryTimeoutException("query timed out", new SQLTimeoutException("query timed out"));
        var wrapper = new ServletException(timeout);
        var filter = new SessionFailureConfiguration().sharedSessionFailureFilter(mapper).getFilter();
        var response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), response,
                (req, res) -> { throw wrapper; })).isSameAs(wrapper);
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getHeader("X-Request-Id")).isNull();
    }

    @ParameterizedTest
    @MethodSource("redisTimeouts")
    void mapsRedisTimeoutCauseThroughServletAndRuntimeWrappers(Exception failure) throws Exception {
        var response = new MockHttpServletResponse();
        var filter = new SessionFailureConfiguration().sharedSessionFailureFilter(mapper).getFilter();

        filter.doFilter(new MockHttpServletRequest(), response, (req, res) -> {
            if (failure instanceof ServletException servlet) throw servlet;
            throw (RuntimeException) failure;
        });

        assertThat(response.getStatus()).isEqualTo(503);
        var body = mapper.readTree(response.getContentAsString());
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("data").isNull()).isTrue();
        assertThat(body.path("error").path("code").asText()).isEqualTo("SUMMER_SESSION_STORE_UNAVAILABLE");
        assertThat(body.path("requestId").asText()).isNotBlank().isEqualTo(response.getHeader("X-Request-Id"));
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    }

    static Stream<Exception> redisTimeouts() {
        var timeout = new LettuceExceptionConverter().convert(new RedisCommandTimeoutException("command timed out"));
        return Stream.of(timeout, new ServletException(timeout),
                new IllegalStateException(new ServletException(timeout)));
    }

    @Test
    void queryTimeoutWithoutRedisCauseIsRethrownUnchanged() {
        var timeout = new QueryTimeoutException("query timed out");
        var response = new MockHttpServletResponse();
        var filter = new SessionFailureConfiguration().sharedSessionFailureFilter(mapper).getFilter();

        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), response,
                (req, res) -> { throw timeout; })).isSameAs(timeout);
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @Test
    void cannotReplaceAnAlreadyCommittedResponse() throws Exception {
        var timeout = new LettuceExceptionConverter().convert(new RedisCommandTimeoutException("command timed out"));
        var response = new MockHttpServletResponse();
        response.setStatus(200);
        response.getWriter().write("already sent");
        response.flushBuffer();
        var filter = new SessionFailureConfiguration().sharedSessionFailureFilter(mapper).getFilter();

        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), response,
                (req, res) -> { throw timeout; })).isSameAs(timeout);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo("already sent");
    }

    @Test
    void servletErrorDispatchDoesNotTurnSessionTimeoutIntoSuccessWrapped500() throws Exception {
        // Real HTTP/container/error dispatch, with a fault injected at the session repository.
        // No Redis, authentication server or production readiness implementation is started.
        try (var context = new org.springframework.boot.builder.SpringApplicationBuilder(HttpFixture.class)
                .properties("spring.config.name=session-timeout-isolated", "server.port=0",
                        "summer.security.storage.session=redis", "spring.main.banner-mode=off",
                        "logging.level.root=OFF").run()) {
            int port = ((org.springframework.boot.web.server.context.WebServerApplicationContext) context)
                    .getWebServer().getPort();
            var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(
                            "http://127.0.0.1:" + port + "/internal/v1/release/readiness"))
                    .header("Cookie", "JSESSIONID=existing-session")
                    .header("Accept", "application/json")
                    .timeout(java.time.Duration.ofSeconds(5)).GET().build();
            var response = java.net.http.HttpClient.newHttpClient().send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            var body = mapper.readTree(response.body());

            assertThat(response.statusCode()).as("HTTP status; success=%s, data.status=%s, error.code=%s",
                    body.path("success"), body.path("data").path("status"), body.path("error").path("code"))
                    .isEqualTo(503);
            assertThat(body.path("success").asBoolean()).isFalse();
            assertThat(body.path("data").isNull()).isTrue();
            assertThat(body.path("error").path("code").asText()).isEqualTo("SUMMER_SESSION_STORE_UNAVAILABLE");
            assertThat(body.path("requestId").asText()).isNotBlank()
                    .isEqualTo(response.headers().firstValue("X-Request-Id").orElseThrow());
            assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        }
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    @org.springframework.boot.autoconfigure.ImportAutoConfiguration({
            org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration.class,
            org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration.class,
            org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration.class,
            org.springframework.boot.webmvc.autoconfigure.error.ErrorMvcAutoConfiguration.class,
            org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration.class
    })
    @org.springframework.context.annotation.Import(SessionFailureConfiguration.class)
    static class HttpFixture {
        @org.springframework.context.annotation.Bean
        com.abluepoint.summer.mvc.support.SummerResponseBodyAdvice responseAdvice(tools.jackson.databind.ObjectMapper mapper) {
            return new com.abluepoint.summer.mvc.support.SummerResponseBodyAdvice(mapper,
                    new com.abluepoint.summer.mvc.request.ServletRequestIdAccessor());
        }

        @org.springframework.context.annotation.Bean
        org.springframework.boot.web.servlet.FilterRegistrationBean<SessionRepositoryFilter<MapSession>> sessionFilter() {
            @SuppressWarnings("unchecked")
            SessionRepository<MapSession> repository = mock(SessionRepository.class);
            when(repository.findById("existing-session")).thenThrow(new LettuceExceptionConverter()
                    .convert(new RedisCommandTimeoutException("command timed out")));
            var filter = new SessionRepositoryFilter<>(repository);
            var serializer = new DefaultCookieSerializer();
            serializer.setCookieName("JSESSIONID");
            serializer.setUseBase64Encoding(false);
            var resolver = new CookieHttpSessionIdResolver();
            resolver.setCookieSerializer(serializer);
            filter.setHttpSessionIdResolver(resolver);
            var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
            registration.setOrder(SessionRepositoryFilter.DEFAULT_ORDER);
            return registration;
        }

        @org.springframework.context.annotation.Bean
        org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter> sessionReader() {
            var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter>();
            registration.setFilter((request, response, chain) -> {
                ((jakarta.servlet.http.HttpServletRequest) request).getSession(false);
                chain.doFilter(request, response);
            });
            registration.setOrder(SessionRepositoryFilter.DEFAULT_ORDER + 1);
            return registration;
        }
    }
}
