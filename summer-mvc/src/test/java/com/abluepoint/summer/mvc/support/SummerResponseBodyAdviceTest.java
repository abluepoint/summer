package com.abluepoint.summer.mvc.support;

import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.ResourceRegionHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SummerResponseBodyAdviceTest {

    private final SummerResponseBodyAdvice advice = new SummerResponseBodyAdvice(
            new ObjectMapper(),
            () -> "request-123"
    );

    @Test
    void preservesProtocolFailureDuringServletErrorDispatch() throws Exception {
        var request = new MockHttpServletRequest("GET", "/error");
        request.setDispatcherType(DispatcherType.ERROR);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/oauth2/jwks");
        var response = new MockHttpServletResponse();
        response.setStatus(500);
        Map<String, Object> body = Map.of("status", 500, "error", "server_error");

        Object result = advice.beforeBodyWrite(body,
                new MethodParameter(DownloadController.class.getDeclaredMethod("jsonError"), -1),
                MediaType.APPLICATION_JSON, JacksonJsonHttpMessageConverter.class,
                new ServletServerHttpRequest(request), new ServletServerHttpResponse(response));

        assertThat(result).isSameAs(body);
        assertThat(response.getStatus()).isEqualTo(500);
    }

    @Test
    void preservesProtocolErrorDispatchUnderAContextPath() throws Exception {
        var request = new MockHttpServletRequest("GET", "/summer/error");
        request.setContextPath("/summer");
        request.setDispatcherType(DispatcherType.ERROR);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/summer/oauth2/jwks");
        var response = new MockHttpServletResponse();
        response.setStatus(500);
        Map<String, Object> body = Map.of("status", 500, "error", "server_error");

        Object result = advice.beforeBodyWrite(body,
                new MethodParameter(DownloadController.class.getDeclaredMethod("jsonError"), -1),
                MediaType.APPLICATION_JSON, JacksonJsonHttpMessageConverter.class,
                new ServletServerHttpRequest(request), new ServletServerHttpResponse(response));

        assertThat(result).isSameAs(body);
        assertThat(response.getStatus()).isEqualTo(500);
    }

    @Test
    void ignoresErrorPathAttributesOnOrdinaryApplicationRequests() throws Exception {
        var request = new MockHttpServletRequest("GET", "/application");
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/oauth2/jwks");
        Map<String, Object> body = Map.of("value", "application-result");

        Object result = advice.beforeBodyWrite(body,
                new MethodParameter(DownloadController.class.getDeclaredMethod("jsonError"), -1),
                MediaType.APPLICATION_JSON, JacksonJsonHttpMessageConverter.class,
                new ServletServerHttpRequest(request), new ServletServerHttpResponse(new MockHttpServletResponse()));

        assertThat(result).isInstanceOf(ApiResponse.class);
        assertThat(((ApiResponse<?>) result).success()).isTrue();
        assertThat(((ApiResponse<?>) result).data()).isSameAs(body);
    }

    @Test
    void preservesApplicationFailureContractsDuringErrorDispatch() throws Exception {
        var request = new MockHttpServletRequest("GET", "/error");
        request.setDispatcherType(DispatcherType.ERROR);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/application");
        var response = new MockHttpServletResponse();
        response.setStatus(500);
        ApiResponse<Object> body = ApiResponse.failure(null, "request-123");

        Object result = advice.beforeBodyWrite(body,
                new MethodParameter(DownloadController.class.getDeclaredMethod("jsonError"), -1),
                MediaType.APPLICATION_JSON, JacksonJsonHttpMessageConverter.class,
                new ServletServerHttpRequest(request), new ServletServerHttpResponse(response));

        assertThat(result).isSameAs(body);
        assertThat(((ApiResponse<?>) result).success()).isFalse();
        assertThat(response.getStatus()).isEqualTo(500);
    }

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
        Map<String, Object> jsonError() {
            return Map.of("status", 500, "error", "server_error");
        }

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
