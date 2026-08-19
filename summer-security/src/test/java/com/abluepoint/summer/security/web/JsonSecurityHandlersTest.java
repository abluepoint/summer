package com.abluepoint.summer.security.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class JsonSecurityHandlersTest {

    private static final String REQUEST_ID_ATTRIBUTE = "com.abluepoint.summer.requestId";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void authenticationEntryPointUsesTheCommonUnauthorizedResponseContract() throws Exception {
        MockHttpServletRequest request = requestWithId("request-401");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JsonAuthenticationEntryPoint(objectMapper).commence(
                request,
                response,
                new BadCredentialsException("invalid token")
        );

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString())
                .contains("\"success\":false")
                .contains("\"code\":\"SUMMER_UNAUTHORIZED\"")
                .contains("\"requestId\":\"request-401\"");
    }

    @Test
    void accessDeniedHandlerUsesTheCommonForbiddenResponseContract() throws Exception {
        MockHttpServletRequest request = requestWithId("request-403");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JsonAccessDeniedHandler(objectMapper).handle(
                request,
                response,
                new AccessDeniedException("forbidden")
        );

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString())
                .contains("\"success\":false")
                .contains("\"code\":\"SUMMER_FORBIDDEN\"")
                .contains("\"requestId\":\"request-403\"");
    }

    private static MockHttpServletRequest requestWithId(String requestId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        return request;
    }
}
