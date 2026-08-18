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

class SummerRequestContextFilterTest {

    private final SummerRequestContextFilter filter = new SummerRequestContextFilter(true);

    @Test
    void cachesJsonButLeavesContainerManagedFormsUntouched() throws Exception {
        MockHttpServletRequest json = request("application/json", "{\"name\":\"summer\"}");
        MockHttpServletRequest form = request("application/x-www-form-urlencoded", "grant_type=client_credentials");

        assertThat(filteredRequest(json)).isInstanceOf(CachedBodyHttpServletRequest.class);
        assertThat(filteredRequest(form)).isSameAs(form);
    }

    @Test
    void restoresUpstreamRequestIdAfterFiltering() throws Exception {
        MockHttpServletRequest request = request("application/json", "{}");
        request.addHeader(SummerRequestContextFilter.REQUEST_ID_HEADER, "inner-request");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDC.put(SummerRequestContextFilter.REQUEST_ID_MDC_KEY, "outer-request");

        try {
            filter.doFilter(request, response, (servletRequest, servletResponse) ->
                    assertThat(MDC.get(SummerRequestContextFilter.REQUEST_ID_MDC_KEY)).isEqualTo("inner-request"));

            assertThat(MDC.get(SummerRequestContextFilter.REQUEST_ID_MDC_KEY)).isEqualTo("outer-request");
        } finally {
            MDC.remove(SummerRequestContextFilter.REQUEST_ID_MDC_KEY);
        }
    }

    private ServletRequest filteredRequest(MockHttpServletRequest request) throws Exception {
        AtomicReference<ServletRequest> observed = new AtomicReference<>();
        FilterChain chain = (ServletRequest servletRequest, ServletResponse response) -> observed.set(servletRequest);
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return observed.get();
    }

    private static MockHttpServletRequest request(String contentType, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/test");
        request.setContentType(contentType);
        request.setContent(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return request;
    }
}
