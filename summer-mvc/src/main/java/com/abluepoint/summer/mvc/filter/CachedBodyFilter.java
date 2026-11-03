package com.abluepoint.summer.mvc.filter;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.mvc.error.MvcErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.Objects;

import tools.jackson.databind.ObjectMapper;

public final class CachedBodyFilter extends OncePerRequestFilter {

    private final int maxBodyBytes;
    private final HandlerExceptionResolver exceptionResolver;

    public CachedBodyFilter() {
        this(CachedBodyHttpServletRequest.DEFAULT_MAX_BODY_BYTES);
    }

    public CachedBodyFilter(int maxBodyBytes) {
        this(maxBodyBytes, (HandlerExceptionResolver) null);
    }

    /**
     * Retained for source compatibility. Oversized requests require an MVC resolver;
     * serialization is owned by MVC rather than the supplied mapper.
     */
    @Deprecated(forRemoval = true)
    public CachedBodyFilter(int maxBodyBytes, ObjectMapper objectMapper) {
        this(maxBodyBytes);
        Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /** Creates a body limit filter that delegates pre-controller failures to MVC. */
    public static CachedBodyFilter withExceptionResolver(int maxBodyBytes, HandlerExceptionResolver exceptionResolver) {
        return new CachedBodyFilter(maxBodyBytes, Objects.requireNonNull(exceptionResolver, "exceptionResolver"));
    }

    private CachedBodyFilter(int maxBodyBytes, HandlerExceptionResolver exceptionResolver) {
        if (maxBodyBytes < 1) {
            throw new IllegalArgumentException("maxBodyBytes must be positive");
        }
        this.maxBodyBytes = maxBodyBytes;
        this.exceptionResolver = exceptionResolver;
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
        } catch (SummerException exception) {
            if (exception.errorCode() != MvcErrorCode.REQUEST_BODY_TOO_LARGE) {
                throw exception;
            }
            if (response.isCommitted()) {
                return;
            }
            if (exceptionResolver == null
                    || exceptionResolver.resolveException(request, response, null, exception) == null) {
                throw new ServletException("MVC could not resolve the request body limit failure", exception);
            }
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
