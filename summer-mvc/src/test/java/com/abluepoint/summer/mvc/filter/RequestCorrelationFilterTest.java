package com.abluepoint.summer.mvc.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestCorrelationFilterTest {

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @Test
    void exposesNormalizedRequestIdThroughAttributeHeaderAndMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, "request id/1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> observedMdc = new AtomicReference<>();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                observedMdc.set(MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY)));

        assertThat(request.getAttribute(RequestCorrelationFilter.REQUEST_ID_ATTRIBUTE)).isEqualTo("request_id_1");
        assertThat(response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER)).isEqualTo("request_id_1");
        assertThat(observedMdc).hasValue("request_id_1");
    }

    @Test
    void restoresUpstreamRequestIdAfterFiltering() throws Exception {
        MockHttpServletRequest request = request("application/json", "{}");
        request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, "inner-request");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDC.put(RequestCorrelationFilter.REQUEST_ID_MDC_KEY, "outer-request");

        try {
            filter.doFilter(request, response, (servletRequest, servletResponse) ->
                    assertThat(MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY)).isEqualTo("inner-request"));

            assertThat(MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY)).isEqualTo("outer-request");
        } finally {
            MDC.remove(RequestCorrelationFilter.REQUEST_ID_MDC_KEY);
        }
    }

    private static MockHttpServletRequest request(String contentType, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/test");
        request.setContentType(contentType);
        request.setContent(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return request;
    }
}
