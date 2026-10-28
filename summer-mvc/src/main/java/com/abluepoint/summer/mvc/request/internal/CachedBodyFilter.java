package com.abluepoint.summer.mvc.request.internal;

import com.abluepoint.summer.common.response.api.ApiError;
import com.abluepoint.summer.common.response.api.ApiResponse;
import com.abluepoint.summer.mvc.request.internal.ServletRequestIdAccessor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import tools.jackson.databind.ObjectMapper;

public final class CachedBodyFilter extends OncePerRequestFilter {

    private final int maxBodyBytes;
    private final ObjectMapper objectMapper;

    public CachedBodyFilter() {
        this(CachedBodyHttpServletRequest.DEFAULT_MAX_BODY_BYTES);
    }

    public CachedBodyFilter(int maxBodyBytes) {
        this(maxBodyBytes, new ObjectMapper());
    }

    public CachedBodyFilter(int maxBodyBytes, ObjectMapper objectMapper) {
        if (maxBodyBytes < 1) {
            throw new IllegalArgumentException("maxBodyBytes must be positive");
        }
        this.maxBodyBytes = maxBodyBytes;
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        HttpServletRequest effectiveRequest;
        try {
            effectiveRequest = hasBody(request)
                    ? new CachedBodyHttpServletRequest(request, maxBodyBytes)
                    : request;
        } catch (RequestBodyTooLargeException exception) {
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            response.setContentType("application/json");
            ApiError error = new ApiError(
                    "SUMMER_REQUEST_BODY_TOO_LARGE", "Request body too large", List.of()
            );
            objectMapper.writeValue(response.getOutputStream(),
                    ApiResponse.failure(error, ServletRequestIdAccessor.requestId(request)));
            return;
        }
        if (effectiveRequest instanceof CachedBodyHttpServletRequest) {
            request.setAttribute(CachedBodyHttpServletRequest.class.getName(), effectiveRequest);
        }
        filterChain.doFilter(effectiveRequest, response);
    }

    private static boolean hasBody(HttpServletRequest request) {
        String contentType = request.getContentType();
        boolean containerManagedForm = contentType != null
                && (contentType.toLowerCase().startsWith("application/x-www-form-urlencoded")
                || contentType.toLowerCase().startsWith("multipart/"));
        return !containerManagedForm
                && request.getContentLengthLong() != 0
                && !"GET".equalsIgnoreCase(request.getMethod())
                && !"HEAD".equalsIgnoreCase(request.getMethod());
    }
}
