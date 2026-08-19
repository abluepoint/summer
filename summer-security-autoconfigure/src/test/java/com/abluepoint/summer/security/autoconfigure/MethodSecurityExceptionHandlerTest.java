package com.abluepoint.summer.security.autoconfigure;

import com.abluepoint.summer.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

class MethodSecurityExceptionHandlerTest {

    @Test
    void returnsTheCommonForbiddenResponseContract() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("com.abluepoint.summer.requestId", "method-request-403");

        ResponseEntity<ApiResponse<Void>> response = new MethodSecurityExceptionHandler()
                .handleAccessDenied(new AccessDeniedException("forbidden"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().requestId()).isEqualTo("method-request-403");
        assertThat(response.getBody().error().code()).isEqualTo("SUMMER_FORBIDDEN");
    }
}
