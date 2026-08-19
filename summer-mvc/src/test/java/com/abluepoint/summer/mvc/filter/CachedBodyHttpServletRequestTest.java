package com.abluepoint.summer.mvc.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CachedBodyHttpServletRequestTest {

    @Test
    void requestBodyCanBeReadMoreThanOnce() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContentType("application/json");
        request.setContent("{\"name\":\"summer\"}".getBytes(StandardCharsets.UTF_8));

        CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(request);

        assertThat(cached.getInputStream().readAllBytes()).isEqualTo(cached.body());
        assertThat(cached.getInputStream().readAllBytes()).isEqualTo(cached.body());
        assertThat(cached.getReader().readLine()).isEqualTo("{\"name\":\"summer\"}");
    }
}
