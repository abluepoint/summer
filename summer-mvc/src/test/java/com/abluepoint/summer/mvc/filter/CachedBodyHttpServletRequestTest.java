package com.abluepoint.summer.mvc.filter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void acceptsExactLimitAndRejectsUnknownLengthAfterOneExtraByte() throws Exception {
        MockHttpServletRequest exact = requestWithUnknownLength("12345678");
        MockHttpServletRequest over = requestWithUnknownLength("123456789");

        CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(exact, 8);
        assertThat(cached.body()).isEqualTo("12345678".getBytes(StandardCharsets.UTF_8));
        assertThat(cached.getInputStream().readAllBytes()).isEqualTo(cached.body());
        assertThatThrownBy(() -> new CachedBodyHttpServletRequest(over, 8))
                .isInstanceOf(RequestBodyTooLargeException.class);
    }

    @Test
    void propagatesInterruptedReadInsteadOfDispatchingPartialBody() {
        MockHttpServletRequest interrupted = new MockHttpServletRequest() {
            @Override
            public long getContentLengthLong() {
                return -1;
            }

            @Override
            public ServletInputStream getInputStream() {
                return new ServletInputStream() {
                    private boolean readOnce;

                    @Override
                    public int read() throws IOException {
                        if (readOnce) {
                            throw new IOException("interrupted request stream");
                        }
                        readOnce = true;
                        return 'x';
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

        assertThatThrownBy(() -> new CachedBodyHttpServletRequest(interrupted, 8))
                .isInstanceOf(IOException.class)
                .isNotInstanceOf(RequestBodyTooLargeException.class)
                .hasMessage("interrupted request stream");
    }

    private static MockHttpServletRequest requestWithUnknownLength(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest() {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
