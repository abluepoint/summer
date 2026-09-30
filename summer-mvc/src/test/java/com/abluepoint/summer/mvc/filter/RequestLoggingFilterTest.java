package com.abluepoint.summer.mvc.filter;

import com.abluepoint.summer.mvc.logging.RequestLogRecord;
import com.abluepoint.summer.mvc.logging.RequestLogSink;
import com.abluepoint.summer.mvc.logging.RequestBodySnapshotter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingFilterTest {

    @Test
    void recordsRequestMetadataAfterTheHttpChainCompletes() throws Exception {
        AtomicReference<RequestLogRecord> observed = new AtomicReference<>();
        RequestLogSink sink = observed::set;
        RequestLoggingFilter logging = new RequestLoggingFilter(sink);
        RequestCorrelationFilter correlation = new RequestCorrelationFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/customers");
        request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, "customer-request");
        MockHttpServletResponse response = new MockHttpServletResponse();

        correlation.doFilter(request, response, (servletRequest, servletResponse) ->
                logging.doFilter(servletRequest, servletResponse, (requestInChain, responseInChain) ->
                        ((MockHttpServletResponse) responseInChain).setStatus(201)));

        assertThat(observed).hasValueSatisfying(record -> {
            assertThat(record.requestId()).isEqualTo("customer-request");
            assertThat(record.method()).isEqualTo("POST");
            assertThat(record.path()).isEqualTo("/customers");
            assertThat(record.status()).isEqualTo(201);
            assertThat(record.durationMillis()).isGreaterThanOrEqualTo(0);
        });
    }

    @Test
    void outputFailureDoesNotChangeTheHttpResult() throws Exception {
        RequestLoggingFilter logging = new RequestLoggingFilter(record -> {
            throw new IllegalStateException("sink unavailable");
        });
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        request.setAttribute(RequestCorrelationFilter.REQUEST_ID_ATTRIBUTE, "health-request");
        MockHttpServletResponse response = new MockHttpServletResponse();

        logging.doFilter(request, response, (servletRequest, servletResponse) ->
                ((MockHttpServletResponse) servletResponse).setStatus(204));

        assertThat(response.getStatus()).isEqualTo(204);
    }

    @Test
    void recordsAnUnboundRequestIdWhenARequestCorrelationFilterIsNotInstalled() throws Exception {
        AtomicReference<RequestLogRecord> observed = new AtomicReference<>();
        RequestLoggingFilter logging = new RequestLoggingFilter(observed::set);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        logging.doFilter(request, response, (servletRequest, servletResponse) -> {
        });

        assertThat(observed).hasValueSatisfying(record ->
                assertThat(record.requestId()).isEqualTo("unbound"));
    }

    @Test
    void bodyLoggingIsDisabledByDefault() throws Exception {
        AtomicReference<RequestLogRecord> observed = new AtomicReference<>();
        RequestLoggingFilter logging = new RequestLoggingFilter(observed::set);
        MockHttpServletRequest request = jsonRequest("{\"password\":\"do-not-log\"}");
        MockHttpServletResponse response = new MockHttpServletResponse();

        logging.doFilter(request, response, (requestInChain, responseInChain) -> {
        });

        assertThat(observed).hasValueSatisfying(record -> assertThat(record.bodyPreview()).isNull());
    }

    @Test
    void bodySnapshotIsRedactedAndTruncatedWithoutChangingBusinessBody() throws Exception {
        AtomicReference<RequestLogRecord> observed = new AtomicReference<>();
        RequestBodySnapshotter snapshotter = new RequestBodySnapshotter(
                new ObjectMapper(), true, 72, Set.of("password")
        );
        RequestLoggingFilter logging = new RequestLoggingFilter(observed::set, snapshotter);
        MockHttpServletRequest request = jsonRequest(
                "{\"password\":\"secret-value\",\"description\":\"complete business body\"}"
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        logging.doFilter(request, response, (requestInLogging, responseInLogging) ->
                new CachedBodyFilter().doFilter(requestInLogging, responseInLogging, (requestInChain, responseInChain) -> {
                    CachedBodyHttpServletRequest cached = (CachedBodyHttpServletRequest) requestInChain;
                    assertThat(new String(cached.body(), StandardCharsets.UTF_8))
                            .contains("secret-value", "complete business body");
                    assertThat(new String(cached.getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                            .contains("secret-value", "complete business body");
                }));

        assertThat(observed).hasValueSatisfying(record -> {
            assertThat(record.bodyPreview()).hasSizeLessThanOrEqualTo(72);
            assertThat(record.bodyPreview()).contains(RequestBodySnapshotter.REDACTED_VALUE);
            assertThat(record.bodyPreview()).doesNotContain("secret-value");
        });
    }

    @Test
    void oversizedRequestStillProducesCorrelatedStatusLogWithoutBodyPreview() throws Exception {
        AtomicReference<RequestLogRecord> observed = new AtomicReference<>();
        RequestBodySnapshotter snapshotter = new RequestBodySnapshotter(
                new ObjectMapper(), true, 72, Set.of("password")
        );
        RequestLoggingFilter logging = new RequestLoggingFilter(observed::set, snapshotter);
        MockHttpServletRequest request = jsonRequest("{\"password\":\"sensitive\"}");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RequestCorrelationFilter().doFilter(request, response, (correlated, correlatedResponse) ->
                logging.doFilter(correlated, correlatedResponse, (logged, loggedResponse) ->
                        new CachedBodyFilter(8).doFilter(logged, loggedResponse, (body, bodyResponse) -> {
                            throw new AssertionError("oversized request reached downstream chain");
                        })));

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(observed).hasValueSatisfying(record -> {
            assertThat(record.status()).isEqualTo(413);
            assertThat(record.requestId()).isEqualTo(response.getHeader("X-Request-Id"));
            assertThat(record.bodyPreview()).isNull();
        });
    }

    private static MockHttpServletRequest jsonRequest(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/customers");
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
