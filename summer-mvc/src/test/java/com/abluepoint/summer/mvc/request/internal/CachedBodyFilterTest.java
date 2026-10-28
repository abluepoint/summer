package com.abluepoint.summer.mvc.request.internal;

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
                .addFilters(new RequestCorrelationFilter(), new CachedBodyFilter(8))
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
                .addFilters(new RequestCorrelationFilter(), new CachedBodyFilter(8))
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
                        throw new IOException("interrupted request stream");
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
        ).isInstanceOf(IOException.class).hasMessage("interrupted request stream");
        assertThat(dispatched).isFalse();
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
