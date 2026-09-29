package com.abluepoint.summer.mvc.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

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
