package com.abluepoint.summer.mvc.filter;

import com.abluepoint.summer.mvc.logging.RequestLogRecord;
import com.abluepoint.summer.mvc.logging.RequestLogSink;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

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
}
