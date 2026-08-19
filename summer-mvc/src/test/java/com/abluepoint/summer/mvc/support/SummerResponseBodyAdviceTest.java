package com.abluepoint.summer.mvc.support;

import com.abluepoint.summer.mvc.api.RawResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.ResourceRegionHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class SummerResponseBodyAdviceTest {

    private final SummerResponseBodyAdvice advice = new SummerResponseBodyAdvice(
            new ObjectMapper(),
            () -> "request-123"
    );

    @Test
    void leavesBinaryDownloadsUnwrapped() throws Exception {
        byte[] body = new byte[]{1, 2, 3};
        Method method = DownloadController.class.getDeclaredMethod("download");

        Object result = advice.beforeBodyWrite(
                body,
                new MethodParameter(method, -1),
                MediaType.APPLICATION_OCTET_STREAM,
                ByteArrayHttpMessageConverter.class,
                new ServletServerHttpRequest(new MockHttpServletRequest("GET", "/download")),
                new ServletServerHttpResponse(new MockHttpServletResponse())
        );

        assertThat(result).isSameAs(body);
    }

    @Test
    void leavesResourceDownloadsUnwrapped() throws Exception {
        ByteArrayResource body = new ByteArrayResource(new byte[]{1, 2, 3});
        Method method = DownloadController.class.getDeclaredMethod("resource");

        Object result = advice.beforeBodyWrite(
                body,
                new MethodParameter(method, -1),
                MediaType.APPLICATION_OCTET_STREAM,
                ResourceHttpMessageConverter.class,
                new ServletServerHttpRequest(new MockHttpServletRequest("GET", "/resource")),
                new ServletServerHttpResponse(new MockHttpServletResponse())
        );

        assertThat(result).isSameAs(body);
    }

    @Test
    void leavesRangeDownloadsUnwrapped() throws Exception {
        ResourceRegion body = new ResourceRegion(new ByteArrayResource(new byte[]{1, 2, 3}), 0, 2);
        Method method = DownloadController.class.getDeclaredMethod("region");

        Object result = advice.beforeBodyWrite(
                body,
                new MethodParameter(method, -1),
                MediaType.APPLICATION_OCTET_STREAM,
                ResourceRegionHttpMessageConverter.class,
                new ServletServerHttpRequest(new MockHttpServletRequest("GET", "/region")),
                new ServletServerHttpResponse(new MockHttpServletResponse())
        );

        assertThat(result).isSameAs(body);
    }

    @Test
    void wrapsApplicationResponsesWithTheCommonContractAndRequestId() throws Exception {
        Method method = DownloadController.class.getDeclaredMethod("text");
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        Object result = advice.beforeBodyWrite(
                "hello",
                new MethodParameter(method, -1),
                MediaType.TEXT_PLAIN,
                StringHttpMessageConverter.class,
                new ServletServerHttpRequest(new MockHttpServletRequest("GET", "/hello")),
                new ServletServerHttpResponse(servletResponse)
        );

        assertThat(result).isInstanceOf(String.class);
        assertThat((String) result)
                .contains("\"success\":true")
                .contains("\"data\":\"hello\"")
                .contains("\"requestId\":\"request-123\"");
        assertThat(servletResponse.getContentType()).startsWith("application/json");
    }

    @Test
    void explicitRawResponseDisablesWrapping() throws Exception {
        Method method = DownloadController.class.getDeclaredMethod("raw");

        assertThat(advice.supports(new MethodParameter(method, -1), StringHttpMessageConverter.class)).isFalse();
    }

    private static final class DownloadController {
        byte[] download() {
            return new byte[0];
        }

        ByteArrayResource resource() {
            return new ByteArrayResource(new byte[0]);
        }

        ResourceRegion region() {
            return new ResourceRegion(new ByteArrayResource(new byte[0]), 0, 0);
        }

        String text() {
            return "hello";
        }

        @RawResponse
        String raw() {
            return "raw";
        }
    }
}
