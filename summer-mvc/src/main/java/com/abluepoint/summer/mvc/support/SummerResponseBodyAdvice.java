package com.abluepoint.summer.mvc.support;

import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.ResourceRegionHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
public class SummerResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    private final ObjectMapper objectMapper;
    private final RequestIdAccessor requestIdAccessor;

    public SummerResponseBodyAdvice(ObjectMapper objectMapper, RequestIdAccessor requestIdAccessor) {
        this.objectMapper = objectMapper;
        this.requestIdAccessor = requestIdAccessor;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return !returnType.hasMethodAnnotation(RawResponse.class)
                && !returnType.getContainingClass().isAnnotationPresent(RawResponse.class);
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response
    ) {
        if (body instanceof ApiResponse<?>
                || ByteArrayHttpMessageConverter.class.isAssignableFrom(selectedConverterType)
                || ResourceHttpMessageConverter.class.isAssignableFrom(selectedConverterType)
                || ResourceRegionHttpMessageConverter.class.isAssignableFrom(selectedConverterType)
                || isFrameworkEndpoint(request)) {
            return body;
        }

        ApiResponse<Object> wrapped = ApiResponse.success(body, requestIdAccessor.currentRequestId());
        if (StringHttpMessageConverter.class.isAssignableFrom(selectedConverterType)) {
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            try {
                return objectMapper.writeValueAsString(wrapped);
            } catch (JacksonException exception) {
                throw new IllegalStateException("Unable to serialize unified response", exception);
            }
        }
        return wrapped;
    }

    private static boolean isFrameworkEndpoint(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        return path.equals("/csrf")
                || path.startsWith("/oauth2/")
                || path.startsWith("/.well-known/")
                || path.startsWith("/actuator/");
    }

}
