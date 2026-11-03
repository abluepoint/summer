package com.abluepoint.summer.mvc.filter;

import com.abluepoint.summer.mvc.error.DefaultErrorStatusResolver;
import com.abluepoint.summer.mvc.request.ServletRequestIdAccessor;
import com.abluepoint.summer.mvc.support.ApiExceptionHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class CachedBodyFilterTest {

    private final CachedBodyFilter filter = new CachedBodyFilter();

    @Test
    void cachesJsonButLeavesContainerManagedFormsUntouched() throws Exception {
        MockHttpServletRequest json = request("application/json", "{\"name\":\"summer\"}");
        MockHttpServletRequest form = request("application/x-www-form-urlencoded", "grant_type=client_credentials");

        assertThat(filteredRequest(json)).isInstanceOf(CachedBodyHttpServletRequest.class);
        assertThat(filteredRequest(form)).isSameAs(form);
    }

    @Test
    void letsARequestLogConsumerAndAFieldlessDtoConsumerReadTheCompleteBody() throws Exception {
        AtomicReference<String> loggedBody = new AtomicReference<>();
        OncePerRequestFilter requestLogConsumer = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(
                    HttpServletRequest request,
                    jakarta.servlet.http.HttpServletResponse response,
                    FilterChain filterChain
            ) throws IOException, jakarta.servlet.ServletException {
                String firstRead = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                String secondRead = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                loggedBody.set(firstRead + "|" + secondRead);
                filterChain.doFilter(request, response);
            }
        };
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FieldlessBodyController())
                .addFilters(new CachedBodyFilter(), requestLogConsumer)
                .build();

        String body = "{\"displayName\":\"Ada\",\"description\":\"complete body\"}";
        String response = mockMvc.perform(post("/fieldless-body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(loggedBody).hasValue(body + "|" + body);
        assertThat(response).isEqualTo(body);
    }

    @Test
    void rejectsOversizedBodyWithStableHttpResponseBeforeController() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FieldlessBodyController())
                .addFilters(new RequestCorrelationFilter(), CachedBodyFilter.withExceptionResolver(8, mvcResolver()))
                .build();

        var response = mockMvc.perform(post("/fieldless-body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("123456789"))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("SUMMER_REQUEST_BODY_TOO_LARGE");
        assertThat(response.getContentAsString()).contains(response.getHeader("X-Request-Id"));
    }

    @Test
    void rejectsUnknownLengthAtHttpSeamWithoutDispatchingPartialBody() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FieldlessBodyController())
                .addFilters(new RequestCorrelationFilter(), CachedBodyFilter.withExceptionResolver(8, mvcResolver()))
                .build();
        RequestBuilder unknownLength = context -> {
            MockHttpServletRequest request = new MockHttpServletRequest(context) {
                @Override
                public long getContentLengthLong() {
                    return -1;
                }
            };
            request.setMethod("POST");
            request.setRequestURI("/fieldless-body");
            request.setContentType(MediaType.APPLICATION_JSON_VALUE);
            request.setContent("123456789".getBytes(StandardCharsets.UTF_8));
            return request;
        };

        var response = mockMvc.perform(unknownLength).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("SUMMER_REQUEST_BODY_TOO_LARGE");
        assertThat(response.getContentAsString()).contains(response.getHeader("X-Request-Id"));
    }

    @Test
    void interruptedHttpStreamNeverDispatchesPartialBody() {
        AtomicBoolean dispatched = new AtomicBoolean();
        IOException interrupted = new IOException("interrupted request stream");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/fieldless-body") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }

            @Override
            public ServletInputStream getInputStream() {
                return new ServletInputStream() {
                    @Override
                    public int read() throws IOException {
                        throw interrupted;
                    }

                    @Override
                    public boolean isFinished() {
                        return false;
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setReadListener(ReadListener listener) {
                    }
                };
            }
        };
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        FilterChain chain = (servletRequest, servletResponse) -> dispatched.set(true);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> new CachedBodyFilter(8).doFilter(request, new MockHttpServletResponse(), chain)
        ).isSameAs(interrupted);
        assertThat(dispatched).isFalse();
    }

    @Test
    @SuppressWarnings("removal")
    void unresolvedBodyLimitFailsClosedWithoutContinuingTheChain() throws Exception {
        for (CachedBodyFilter limited : new CachedBodyFilter[]{new CachedBodyFilter(8),
                new CachedBodyFilter(8, new tools.jackson.databind.ObjectMapper()),
                CachedBodyFilter.withExceptionResolver(8, (request, response, handler, exception) -> null)}) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicBoolean dispatched = new AtomicBoolean();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> limited.doFilter(
                            request("application/json", "123456789"), response,
                            (request, result) -> dispatched.set(true)))
                    .isInstanceOf(jakarta.servlet.ServletException.class)
                    .hasCauseInstanceOf(com.abluepoint.summer.common.error.SummerException.class);
            assertThat(dispatched).isFalse();
            assertThat(response.getContentAsString()).isEmpty();
        }
    }

    @Test
    void resolverFailurePropagatesWithoutContinuingTheChain() {
        IllegalStateException unavailable = new IllegalStateException("MVC unavailable");
        CachedBodyFilter limited = CachedBodyFilter.withExceptionResolver(8, (request, response, handler, exception) -> {
            throw unavailable;
        });
        AtomicBoolean dispatched = new AtomicBoolean();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> limited.doFilter(
                        request("application/json", "123456789"), new MockHttpServletResponse(),
                        (request, response) -> dispatched.set(true)))
                .isSameAs(unavailable);
        assertThat(dispatched).isFalse();
    }

    @Test
    void committedResponseIsNotWrittenAgainAfterBodyLimitFailure() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(202);
        response.getWriter().write("already committed");
        response.flushBuffer();
        AtomicBoolean dispatched = new AtomicBoolean();
        CachedBodyFilter.withExceptionResolver(8, (request, result, handler, exception) -> {
            throw new AssertionError("committed response reached MVC resolution");
        }).doFilter(request("application/json", "123456789"), response,
                (request, result) -> dispatched.set(true));
        assertThat(response.getStatus()).isEqualTo(202);
        assertThat(response.getContentAsString()).isEqualTo("already committed");
        assertThat(dispatched).isFalse();
    }

    @Test
    void downstreamExceptionsAreNotMistakenForCacheFailures() {
        var downstream = new com.abluepoint.summer.common.error.SummerException(
                com.abluepoint.summer.mvc.error.MvcErrorCode.REQUEST_BODY_TOO_LARGE);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new CachedBodyFilter(8).doFilter(
                        request("application/json", "{}"), new MockHttpServletResponse(),
                        (request, response) -> { throw downstream; }))
                .isSameAs(downstream);
    }

    @Test
    @SuppressWarnings("removal")
    void legacyNullMapperCallRemainsUnambiguousAndRejectsNull() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new CachedBodyFilter(8, null))
                .isInstanceOf(NullPointerException.class).hasMessage("objectMapper");
    }

    private static HandlerExceptionResolver mvcResolver() {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new FieldlessBodyController())
                .setControllerAdvice(new ApiExceptionHandler(
                        (code, arguments, locale) -> code.defaultMessage(),
                        new DefaultErrorStatusResolver(), new ServletRequestIdAccessor()))
                .build();
        return mvc.getDispatcherServlet().getWebApplicationContext()
                .getBean("handlerExceptionResolver", HandlerExceptionResolver.class);
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

    @RestController
    private static final class FieldlessBodyController {

        @PostMapping(path = "/fieldless-body", consumes = MediaType.APPLICATION_JSON_VALUE)
        String body(@RequestBody String body) {
            return body;
        }
    }
}
