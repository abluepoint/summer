package com.abluepoint.summer.mvc.filter;

import com.abluepoint.summer.mvc.logging.RequestLogRecord;
import com.abluepoint.summer.mvc.logging.RequestLogSink;
import com.abluepoint.summer.mvc.logging.RequestBodySnapshotter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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

        new CachedBodyFilter().doFilter(request, response, (cachedRequest, cachedResponse) ->
                logging.doFilter(cachedRequest, cachedResponse, (requestInChain, responseInChain) -> {
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
    void unstructuredBodiesNeverEnterTheLogPreview() throws Exception {
        assertUnavailablePreview("text/plain", "password=sample-secret");
        assertUnavailablePreview(null, "token=sample-secret");
        assertUnavailablePreview("application/json", "\"sample-secret\"");
        assertUnavailablePreview("application/json", "[\"sample-secret\"]");
        assertUnavailablePreview("application/json", "{\"password\":\"sample-secret\"");
        assertUnavailablePreview("application/octet-stream", "sample-secret");
    }

    @Test
    void objectArraysStillRedactNestedFields() throws Exception {
        RequestBodySnapshotter snapshotter = new RequestBodySnapshotter(
                new ObjectMapper(), true, 128, Set.of("password"));
        MockHttpServletRequest request = jsonRequest(
                "[{\"name\":\"summer\",\"nested\":{\"password\":\"sample-secret\"}}]");

        String preview = snapshotter.snapshot(new CachedBodyHttpServletRequest(request));

        assertThat(preview).contains("summer", RequestBodySnapshotter.REDACTED_VALUE)
                .doesNotContain("sample-secret");
    }

    @Test
    void containerManagedFormHasNoBodyPreview() throws Exception {
        AtomicReference<RequestLogRecord> observed = new AtomicReference<>();
        RequestLoggingFilter logging = new RequestLoggingFilter(observed::set,
                new RequestBodySnapshotter(new ObjectMapper(), true, 64, Set.of("password")));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/customers");
        request.setContentType("application/x-www-form-urlencoded");
        request.setContent("password=sample-secret".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        new CachedBodyFilter().doFilter(request, response, (cachedRequest, cachedResponse) ->
                logging.doFilter(cachedRequest, cachedResponse, (requestInChain, responseInChain) -> {
                    assertThat(requestInChain).isSameAs(request);
                }));

        assertThat(observed.get().bodyPreview()).isNull();
    }

    @Test
    void httpControllersReceiveCompleteBodiesAcrossSupportedAndUnsupportedPreviews() throws Exception {
        AtomicReference<RequestLogRecord> observed = new AtomicReference<>();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new BodyController())
                .addFilters(new RequestCorrelationFilter(), new CachedBodyFilter(),
                        new RequestLoggingFilter(observed::set,
                                new RequestBodySnapshotter(new ObjectMapper(), true, 128,
                                        Set.of("password"))))
                .build();

        for (String contentType : new String[]{"application/json", "text/plain", "application/octet-stream"}) {
            String body = contentType.equals("application/json")
                    ? "{\"password\":\"sample-secret\",\"name\":\"summer\"}"
                    : "password=sample-secret";
            String response = mvc.perform(post("/body-echo")
                            .contentType(contentType).content(body)
                            .header(RequestCorrelationFilter.REQUEST_ID_HEADER, "http-body-request"))
                    .andReturn().getResponse().getContentAsString();

            assertThat(response).isEqualTo(body);
            assertThat(observed.get().requestId()).isEqualTo("http-body-request");
            assertThat(observed.get().bodyPreview()).doesNotContain("sample-secret");
            if (contentType.equals("application/json")) {
                assertThat(observed.get().bodyPreview()).contains("summer", RequestBodySnapshotter.REDACTED_VALUE);
            } else {
                assertThat(observed.get().bodyPreview()).isEqualTo(RequestBodySnapshotter.UNAVAILABLE_VALUE);
            }
        }

        String malformed = "{\"password\":\"sample-secret\"";
        assertThat(mvc.perform(post("/body-echo")
                        .contentType("application/json").content(malformed))
                .andReturn().getResponse().getContentAsString()).isEqualTo(malformed);
        assertThat(observed.get().bodyPreview()).isEqualTo(RequestBodySnapshotter.UNAVAILABLE_VALUE);

        String formResponse = mvc.perform(post("/body-form")
                        .contentType("application/x-www-form-urlencoded")
                        .param("password", "sample-secret"))
                .andReturn().getResponse().getContentAsString();
        assertThat(formResponse).isEqualTo("sample-secret");
        assertThat(observed.get().bodyPreview()).isNull();
    }

    private static void assertUnavailablePreview(String contentType, String body) throws Exception {
        AtomicReference<RequestLogRecord> observed = new AtomicReference<>();
        RequestLoggingFilter logging = new RequestLoggingFilter(observed::set,
                new RequestBodySnapshotter(new ObjectMapper(), true, 64, Set.of("password")));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/customers");
        request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, "body-request");
        if (contentType != null) {
            request.setContentType(contentType);
        }
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RequestCorrelationFilter().doFilter(request, response, (correlated, correlatedResponse) ->
                new CachedBodyFilter().doFilter(correlated, correlatedResponse, (cachedRequest, cachedResponse) ->
                        logging.doFilter(cachedRequest, cachedResponse, (requestInChain, responseInChain) -> {
                            assertThat(new String(requestInChain.getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                                    .isEqualTo(body);
                        })));

        assertThat(observed.get().requestId()).isEqualTo("body-request");
        assertThat(observed.get().bodyPreview()).isEqualTo(RequestBodySnapshotter.UNAVAILABLE_VALUE);
    }

    private static MockHttpServletRequest jsonRequest(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/customers");
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    @RestController
    private static class BodyController {
        @PostMapping("/body-echo")
        String echo(@RequestBody String body) {
            return body;
        }

        @PostMapping("/body-form")
        String form(@RequestParam String password) {
            return password;
        }
    }
}
